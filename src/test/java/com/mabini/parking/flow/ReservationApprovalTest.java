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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reservations wait for a manager, and a user may reserve only once per day. */
@SpringBootTest(properties = {
        "app.reservation.auto-approve=false",
        "app.reservation.max-per-day=1",
        "spring.datasource.url=jdbc:h2:mem:approval_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ReservationApprovalTest {

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

    /** A user whose motorcycle is registered and approved. */
    private String userWithApprovedVehicle(String username, String plate, String manager) throws Exception {
        String token = registerUser(username);
        String body = "{\"vehicleType\":\"MOTORCYCLE\",\"identifier\":\"%s\",\"make\":\"Test\"}".formatted(plate);
        String response = send(post("/api/vehicles/me"), token, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        send(post("/api/manager/vehicles/" + id + "/approve"), manager, null).andExpect(status().isOk());
        return token;
    }

    /** A window starting "startMinutes" from now (all within the same booking range). */
    private String window(int startMinutes, int lengthMinutes) {
        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(startMinutes);
        return "{\"start\":\"%s\",\"end\":\"%s\"}".formatted(start, start.plusMinutes(lengthMinutes));
    }

    private long create(String token, String json) throws Exception {
        String response = send(post("/api/reservations"), token, json)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void newReservationWaitsThenManagerApproves() throws Exception {
        String manager = createManager("appr.manager1");
        String user = userWithApprovedVehicle("appr.user1", "apr 0001", manager);

        long id = create(user, window(1, 60));
        send(get("/api/reservations/me"), user, null)
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        send(get("/api/manager/reservations/pending"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status").value("PENDING"));

        // A normal user can't approve.
        send(post("/api/manager/reservations/" + id + "/approve"), user, null).andExpect(status().isForbidden());

        send(post("/api/manager/reservations/" + id + "/approve"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewedBy").value("Manager appr.manager1"));

        // Deciding twice is refused.
        send(post("/api/manager/reservations/" + id + "/approve"), manager, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PENDING"));
    }

    @Test
    void onlyOneReservationPerDay() throws Exception {
        String manager = createManager("appr.manager2");
        String user = userWithApprovedVehicle("appr.user2", "apr 0002", manager);

        long first = create(user, window(1, 30));

        // Different hours, same day: still refused.
        send(post("/api/reservations"), user, window(120, 30)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"));

        // Withdrawing the waiting request frees the day.
        send(post("/api/reservations/" + first + "/cancel"), user, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        create(user, window(120, 30));
    }

    @Test
    void rejectedReservationShowsReasonAndAllowsRetry() throws Exception {
        String manager = createManager("appr.manager3");
        String user = userWithApprovedVehicle("appr.user3", "apr 0003", manager);

        long id = create(user, window(1, 30));
        send(post("/api/manager/reservations/" + id + "/reject"), manager, "{\"note\":\"Event on campus\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        send(get("/api/reservations/me"), user, null)
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].reviewNote").value("Event on campus"));

        create(user, window(120, 30));
    }

    @Test
    void dashboardCountsPendingReservations() throws Exception {
        String manager = createManager("appr.manager4");
        String user = userWithApprovedVehicle("appr.user4", "apr 0004", manager);
        create(user, window(1, 30));
        send(get("/api/manager/dashboard"), manager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReservations").exists());
    }
}
