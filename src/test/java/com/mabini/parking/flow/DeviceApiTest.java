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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The gate hardware endpoints: device key, same rules as the manual gate, online status. */
@SpringBootTest(properties = {
        "app.device.api-key=test-device-key-0123456789",
        "spring.datasource.url=jdbc:h2:mem:device_api_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class DeviceApiTest {

    private static final String KEY = "test-device-key-0123456789";
    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    private ResultActions send(MockHttpServletRequestBuilder b, String bearer, String deviceKey, String json) throws Exception {
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        if (deviceKey != null) b.header("X-Device-Key", deviceKey);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b);
    }

    private static String gateBody(String deviceId, String plate, String direction) {
        return """
                {"deviceId":"%s","identifier":"%s","direction":"%s"}
                """.formatted(deviceId, plate, direction);
    }

    private String token(String username, String password) throws Exception {
        String body = send(post("/api/auth/login"), null, null, """
                {"username":"%s","password":"%s"}
                """.formatted(username, password)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String manager(String username) throws Exception {
        User u = new User();
        u.setUsername(username);
        u.setFullName("Manager " + username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(Role.PARKING_MANAGER);
        u.setActive(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(LocalDateTime.now(clock));
        users.save(u);
        return token(username, PASSWORD);
    }

    @Test
    void deviceEndpointsNeedTheDeviceKey() throws Exception {
        String body = gateBody("gate-entry", "ABC1234", "IN");

        send(post("/api/device/gate"), null, null, body).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_KEY"));
        send(post("/api/device/gate"), null, "wrong-key", body).andExpect(status().isUnauthorized());
        send(post("/api/device/gate"), null, KEY + "x", body).andExpect(status().isUnauthorized());

        // A logged-in manager is NOT a device.
        String mgr = manager("dev.mgr1");
        send(post("/api/device/gate"), mgr, null, body).andExpect(status().isUnauthorized());
        send(post("/api/device/heartbeat"), mgr, null, "{\"deviceId\":\"gate-entry\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void deviceKeyOpensNothingElse() throws Exception {
        send(get("/api/manager/dashboard"), null, KEY, null).andExpect(status().isUnauthorized());
        send(get("/api/capacity"), null, KEY, null).andExpect(status().isUnauthorized());
        send(post("/api/manager/gate/verify"), null, KEY, gateBody("x", "ABC1234", "IN").replace("\"deviceId\":\"x\",", ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsAreClear() throws Exception {
        send(post("/api/device/gate"), null, KEY, """
                {"deviceId":"gate-entry","identifier":"","direction":"IN"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.identifier").exists());
        send(post("/api/device/gate"), null, KEY, """
                {"identifier":"ABC1234","direction":"IN"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.deviceId").exists());
        send(post("/api/device/gate"), null, KEY, """
                {"deviceId":"gate entry!","identifier":"ABC1234","direction":"IN"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void hardwareFollowsTheSameRulesAsTheManualGate() throws Exception {
        String mgr = manager("dev.mgr2");

        // Unknown plate: refused, with a reason the screen can show.
        send(post("/api/device/gate"), null, KEY, gateBody("gate-entry", "NOPE 9999", "IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("UNREGISTERED"));

        // Register, approve and reserve a vehicle through the normal API.
        send(post("/api/auth/register"), null, null, """
                {"username":"dev.user1","fullName":"Device User","password":"%s","userType":"STUDENT"}
                """.formatted(PASSWORD)).andExpect(status().isCreated());
        String user = token("dev.user1", PASSWORD);
        String vehicle = send(post("/api/vehicles/me"), user, null, """
                {"vehicleType":"MOTORCYCLE","identifier":"DEV0001"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long vehicleId = objectMapper.readTree(vehicle).get("id").asLong();

        // Not approved yet.
        send(post("/api/device/gate"), null, KEY, gateBody("gate-entry", "dev-0001", "IN"))
                .andExpect(jsonPath("$.allowed").value(false)).andExpect(jsonPath("$.reason").value("VEHICLE_NOT_APPROVED"));

        send(post("/api/manager/vehicles/" + vehicleId + "/approve"), mgr, null, null).andExpect(status().isOk());

        // Approved but no reservation.
        send(post("/api/device/gate"), null, KEY, gateBody("gate-entry", "DEV0001", "IN"))
                .andExpect(jsonPath("$.allowed").value(false)).andExpect(jsonPath("$.reason").value("NO_RESERVATION"));

        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(1);
        send(post("/api/reservations"), user, null, """
                {"start":"%s","end":"%s"}
                """.formatted(start, start.plusHours(2))).andExpect(status().isCreated());

        // The plate is cleaned the same way as on the manual page (spaces, dashes, lower case).
        send(post("/api/device/gate"), null, KEY, gateBody("gate-entry", "dev-0001", "IN"))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.identifier").value("DEV0001"));
        // Already inside.
        send(post("/api/device/gate"), null, KEY, gateBody("gate-entry", "DEV0001", "IN"))
                .andExpect(jsonPath("$.allowed").value(false)).andExpect(jsonPath("$.reason").value("ALREADY_INSIDE"));

        // Staff see the hardware entry in the logs, and the vehicle inside.
        send(get("/api/manager/gate/inside"), mgr, null, null)
                .andExpect(jsonPath("$[*].identifier").value(hasItem("DEV0001")));

        send(post("/api/device/gate"), null, KEY, gateBody("gate-exit", "DEV0001", "OUT"))
                .andExpect(jsonPath("$.allowed").value(true));
        send(get("/api/manager/gate/inside"), mgr, null, null)
                .andExpect(jsonPath("$[?(@.identifier=='DEV0001')]").isEmpty());
    }

    @Test
    void dashboardShowsWhichHardwareIsOnline() throws Exception {
        String mgr = manager("dev.mgr3");
        send(post("/api/device/heartbeat"), null, KEY, "{\"deviceId\":\"gate-heartbeat\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        send(get("/api/manager/dashboard"), mgr, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices[?(@.deviceId=='gate-heartbeat')].online").value(true));
    }
}
