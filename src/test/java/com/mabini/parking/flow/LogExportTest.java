package com.mabini.parking.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff can download the log of parked vehicles as a CSV table. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:export_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class LogExportTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (json != null) builder.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(builder);
    }

    private String login(String username) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD);
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String registerUser(String username) throws Exception {
        String body = "{\"username\":\"%s\",\"fullName\":\"Appr %s\",\"password\":\"%s\",\"userType\":\"FACULTY\"}"
                .formatted(username, username, PASSWORD);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(username);
    }

    private String createManager(String username) throws Exception {
        User m = new User();
        m.setUsername(username);
        m.setFullName("Manager " + username);
        m.setPasswordHash(encoder.encode(PASSWORD));
        m.setRole(Role.PARKING_MANAGER);
        m.setActive(true);
        m.setMustChangePassword(false);
        m.setCreatedAt(LocalDateTime.now(clock));
        users.save(m);
        return login(username);
    }

    @Test
    void managerDownloadsParkedVehiclesAsCsv() throws Exception {
        String manager = createManager("exp.manager1");
        String visitor = "{\"name\":\"Maria, \\\"Mia\\\" Visitor\",\"vehicleType\":\"MOTORCYCLE\",\"identifier\":\"exp 0001\",\"reason\":\"=Delivery\"}";
        send(post("/api/manager/gate/visitor-entry"), manager, visitor).andExpect(status().isOk());

        String csv = send(get("/api/manager/logs/export"), manager, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Entered\",\"Left\",\"Minutes parked\""), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Visitor\""), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Still inside\""), csv);
        // commas and quotes inside a cell are kept safe, and a leading "=" can never run as a formula
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Maria, \"\"Mia\"\" Visitor\""), csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"'=Delivery\""), csv);
    }

    @Test
    void exportIsForStaffOnlyAndChecksTheRange() throws Exception {
        String manager = createManager("exp.manager2");
        String user = registerUser("exp.user2");
        send(get("/api/manager/logs/export"), user, null).andExpect(status().isForbidden());
        send(get("/api/manager/logs/export"), null, null).andExpect(status().isUnauthorized());
        send(get("/api/manager/logs/export?from=2026-02-01&to=2026-01-01"), manager, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
        send(get("/api/manager/logs/export?from=2020-01-01&to=2026-01-01"), manager, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
    }
}
