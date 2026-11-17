package com.mabini.parking.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.enums.VehicleType;
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

/**
 * End-to-end checks of vehicles, reservations, the gate and the security rules.
 * Every test uses its own usernames and plates because the in-memory database is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ParkingFlowTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired ParkingCapacityRepository capacities;

    /* ---------- helpers ---------- */

    private ResultActions send(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (json != null) builder.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(builder);
    }

    private ResultActions doPost(String path, String token, String json) throws Exception {
        return send(post(path), token, json);
    }

    private ResultActions doGet(String path, String token) throws Exception {
        return send(get(path), token, null);
    }

    private String loginToken(String username) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, PASSWORD);
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String registerUser(String username) throws Exception {
        String body = """
                {"username":"%s","fullName":"Flow %s","password":"%s","userType":"FACULTY"}
                """.formatted(username, username, PASSWORD);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return loginToken(username);
    }

    /** Staff accounts can't self-register, so the test creates one directly. */
    private String createManager(String username) throws Exception {
        User manager = new User();
        manager.setUsername(username);
        manager.setFullName("Manager " + username);
        manager.setPasswordHash(encoder.encode(PASSWORD));
        manager.setRole(Role.PARKING_MANAGER);
        manager.setActive(true);
        manager.setMustChangePassword(false);
        manager.setCreatedAt(LocalDateTime.now(clock));
        users.save(manager);
        return loginToken(username);
    }

    private long registerVehicle(String userToken, String type, String plate) throws Exception {
        String body = """
                {"vehicleType":"%s","identifier":"%s","make":"Test"}
                """.formatted(type, plate);
        String response = doPost("/api/vehicles/me", userToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void approve(String managerToken, long vehicleId) throws Exception {
        doPost("/api/manager/vehicles/" + vehicleId + "/approve", managerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    /** A window starting one minute from now, so it is valid to book AND to enter right away. */
    private String window(int lengthMinutes) {
        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(1);
        return """
                {"start":"%s","end":"%s"}
                """.formatted(start, start.plusMinutes(lengthMinutes));
    }

    private String gate(String plate, String direction) {
        return """
                {"identifier":"%s","direction":"%s"}
                """.formatted(plate, direction);
    }

    /* ---------- tests ---------- */

    @Test
    void fullFlowFromRegistrationToExit() throws Exception {
        String user = registerUser("flow.user1");
        String manager = createManager("flow.manager1");

        doGet("/api/vehicles/me", user).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_VEHICLE"));

        long vehicleId = registerVehicle(user, "MOTORCYCLE", "flw 0001");

        // The plate is stored in one normal form.
        doGet("/api/vehicles/me", user).andExpect(jsonPath("$.identifier").value("FLW0001"));

        // Not approved yet: no reservation allowed.
        doPost("/api/reservations", user, window(120)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_APPROVED"));

        approve(manager, vehicleId);

        doPost("/api/reservations", user, window(120)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // A second, overlapping reservation by the same user is refused.
        doPost("/api/reservations", user, window(60)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"));

        // Gate: in, then a repeat is refused, then out.
        doPost("/api/manager/gate/verify", manager, gate("FLW-0001", "IN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        doPost("/api/manager/gate/verify", manager, gate("FLW-0001", "IN"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("ALREADY_INSIDE"));

        doGet("/api/manager/gate/inside", manager).andExpect(status().isOk());
        doGet("/api/logs/me", user).andExpect(status().isOk()).andExpect(jsonPath("$[0].identifier").value("FLW0001"));

        doPost("/api/manager/gate/verify", manager, gate("FLW0001", "OUT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        // The reservation was used up by the visit.
        doGet("/api/reservations/me", user).andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void gateRefusesUnregisteredAndUnreservedPlates() throws Exception {
        String manager = createManager("flow.manager2");

        doPost("/api/manager/gate/verify", manager, gate("NOPE9999", "IN"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("UNREGISTERED"));

        String user = registerUser("flow.user2");
        approve(manager, registerVehicle(user, "CAR", "FLW0002"));

        // Approved but no reservation.
        doPost("/api/manager/gate/verify", manager, gate("FLW0002", "IN"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("NO_RESERVATION"));

        // Both refusals were recorded.
        doGet("/api/manager/denied?limit=10", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").exists());
    }

    @Test
    void capacityIsEnforcedPerVehicleType() throws Exception {
        String manager = createManager("flow.manager3");
        String userA = registerUser("flow.user3a");
        String userB = registerUser("flow.user3b");
        approve(manager, registerVehicle(userA, "TRICYCLE", "FLW0003"));
        approve(manager, registerVehicle(userB, "TRICYCLE", "FLW0004"));

        ParkingCapacity row = capacities.findById(VehicleType.TRICYCLE).orElseThrow();
        int original = row.getCapacity();
        row.setCapacity(1);
        capacities.save(row);
        try {
            String sameWindow = window(60);
            doPost("/api/reservations", userA, sameWindow).andExpect(status().isCreated());
            doPost("/api/reservations", userB, sameWindow).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FULL"));
        } finally {
            row.setCapacity(original);
            capacities.save(row);
        }
    }

    @Test
    void visitorsCanWalkInAndOut() throws Exception {
        String manager = createManager("flow.manager4");

        String response = doPost("/api/manager/gate/visitor-entry", manager,
                """
                {"name":"Visitor One","vehicleType":"CAR"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andReturn().getResponse().getContentAsString();
        // Find this visitor in the "inside" list and let them out by id.
        var inside = objectMapper.readTree(
                doGet("/api/manager/gate/inside", manager).andReturn().getResponse().getContentAsString());
        long visitLogId = -1;
        for (var item : inside) {
            if ("Visitor One".equals(item.get("name").asText())) visitLogId = item.get("id").asLong();
        }
        if (visitLogId < 0) throw new AssertionError("Visitor was not listed as inside. Response was: " + response);

        doPost("/api/manager/gate/exit/" + visitLogId, manager, null).andExpect(status().isOk());
        doPost("/api/manager/gate/exit/" + visitLogId, manager, null).andExpect(status().isConflict());
    }

    @Test
    void dashboardWorksForStaff() throws Exception {
        String manager = createManager("flow.manager5");
        doGet("/api/manager/dashboard", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(3));
    }

    @Test
    void securityRulesAreEnforced() throws Exception {
        String manager = createManager("flow.manager6");
        String userA = registerUser("flow.user6a");
        String userB = registerUser("flow.user6b");

        // A regular user cannot use staff endpoints.
        doGet("/api/manager/dashboard", userA).andExpect(status().isForbidden());
        doGet("/api/manager/vehicles", userA).andExpect(status().isForbidden());
        doPost("/api/manager/gate/verify", userA, gate("ANY1234", "IN")).andExpect(status().isForbidden());

        // Staff cannot use the user-only endpoints.
        doGet("/api/vehicles/me", manager).andExpect(status().isForbidden());

        // Nobody without a token can reach the API.
        doPost("/api/manager/gate/verify", null, gate("ANY1234", "IN")).andExpect(status().isUnauthorized());

        // Only the super admin can change capacity.
        doPut("/api/admin/capacity/CAR", manager, "{\"capacity\":5}").andExpect(status().isForbidden());

        // A user cannot cancel someone else's reservation: it looks like it doesn't exist.
        approve(manager, registerVehicle(userA, "MOTORCYCLE", "FLW0006"));
        // Book far enough ahead that the reservation could be cancelled by its owner.
        LocalDateTime start = LocalDateTime.now(clock).plusDays(2);
        String body = """
                {"start":"%s","end":"%s"}
                """.formatted(start, start.plusHours(2));
        String created = doPost("/api/reservations", userA, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(created).get("id").asLong();

        doPost("/api/reservations/" + reservationId + "/cancel", userB, null).andExpect(status().isNotFound());
        doPost("/api/reservations/" + reservationId + "/cancel", userA, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void vehicleInputIsValidatedAndPlatesAreUnique() throws Exception {
        String userA = registerUser("flow.user7a");
        String userB = registerUser("flow.user7b");

        doPost("/api/vehicles/me", userA, "{\"vehicleType\":\"CAR\",\"identifier\":\"!!\"}")
                .andExpect(status().isBadRequest());
        doPost("/api/vehicles/me", userA, "{\"vehicleType\":\"BICYCLE\",\"identifier\":\"FLW0007\"}")
                .andExpect(status().isBadRequest());

        registerVehicle(userA, "CAR", "FLW0007");
        // Same plate written differently is still the same plate.
        doPost("/api/vehicles/me", userB, "{\"vehicleType\":\"CAR\",\"identifier\":\"flw-0007\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTIFIER_TAKEN"));
        // One vehicle per user.
        doPost("/api/vehicles/me", userA, "{\"vehicleType\":\"CAR\",\"identifier\":\"FLW0008\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_REGISTERED"));
    }

    private ResultActions doPut(String path, String token, String json) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), token, json);
    }
}
