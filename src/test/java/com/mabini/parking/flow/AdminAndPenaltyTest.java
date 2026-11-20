package com.mabini.parking.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.enums.UserType;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.config.OffsetClock;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff account management, penalties (no-show, overstay), manual override and the faculty area.
 * It has its own in-memory database and turns on the demo clock, so it can move time forward.
 */
@SpringBootTest(properties = {
        "app.demo.time-control=true",
        "spring.datasource.url=jdbc:h2:mem:admin_penalty_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AdminAndPenaltyTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired OffsetClock clock;
    @Autowired ParkingCapacityRepository capacities;

    @AfterEach
    void backToRealTime() {
        clock.reset();
    }

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

    private ResultActions doPut(String path, String token, String json) throws Exception {
        return send(put(path), token, json);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }

    private String token(String username, String password) throws Exception {
        String body = login(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String registerUser(String username, UserType type) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","fullName":"Test %s","password":"%s","userType":"%s"}
                                """.formatted(username, username, PASSWORD, type)))
                .andExpect(status().isCreated());
        return token(username, PASSWORD);
    }

    private String createStaff(String username, Role role) throws Exception {
        User u = new User();
        u.setUsername(username);
        u.setFullName("Staff " + username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(role);
        u.setActive(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(LocalDateTime.now(clock));
        users.save(u);
        return token(username, PASSWORD);
    }

    private long idOf(String userToken) throws Exception {
        String body = doGet("/api/users/me", userToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void approvedVehicle(String manager, String userToken, String type, String plate) throws Exception {
        String response = doPost("/api/vehicles/me", userToken, """
                {"vehicleType":"%s","identifier":"%s"}
                """.formatted(type, plate)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        doPost("/api/manager/vehicles/" + id + "/approve", manager, null).andExpect(status().isOk());
    }

    /** One minute from now, so it can be booked and entered right away. */
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

    private void advance(String admin, int minutes) throws Exception {
        doPost("/api/admin/demo/time/advance", admin, "{\"minutes\":" + minutes + "}").andExpect(status().isOk());
    }

    private ResultActions runCheck(String admin) throws Exception {
        return doPost("/api/admin/demo/run-penalty-check", admin, null).andExpect(status().isOk());
    }

    /* ---------- staff accounts ---------- */

    @Test
    void superAdminCreatesManagerWhoMustChangePassword() throws Exception {
        String admin = createStaff("adm.super1", Role.SUPER_ADMIN);

        String created = doPost("/api/admin/staff", admin, """
                {"username":"new.manager1","fullName":"New Manager"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("PARKING_MANAGER"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String temporary = objectMapper.readTree(created).get("temporaryPassword").asText();

        // The temporary password works, but nothing else until it is changed.
        login("new.manager1", temporary).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        String temporaryToken = token("new.manager1", temporary);
        doGet("/api/manager/users", temporaryToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        doPost("/api/auth/change-password", temporaryToken, """
                {"currentPassword":"%s","newPassword":"brand-new-pass-1"}
                """.formatted(temporary)).andExpect(status().isNoContent());

        String managerToken = token("new.manager1", "brand-new-pass-1");
        doGet("/api/manager/users", managerToken).andExpect(status().isOk());
        // The new manager is NOT a super admin.
        doPost("/api/admin/staff", managerToken, """
                {"username":"sneaky.one","fullName":"Sneaky"}
                """).andExpect(status().isForbidden());
    }

    @Test
    void onlySuperAdminCanCreateStaffAndNamesMustBeUnique() throws Exception {
        String admin = createStaff("adm.super2", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.plain2", Role.PARKING_MANAGER);
        String user = registerUser("plain.user2", UserType.STUDENT);
        String body = """
                {"username":"another.manager","fullName":"Another Manager"}
                """;

        doPost("/api/admin/staff", user, body).andExpect(status().isForbidden());
        doPost("/api/admin/staff", manager, body).andExpect(status().isForbidden());
        doPost("/api/admin/staff", null, body).andExpect(status().isUnauthorized());

        doPost("/api/admin/staff", admin, body).andExpect(status().isCreated());
        doPost("/api/admin/staff", admin, body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        // The role cannot be chosen: an extra "role" field is ignored.
        doPost("/api/admin/staff", admin, """
                {"username":"third.manager","fullName":"Third Manager","role":"SUPER_ADMIN"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("PARKING_MANAGER"));
    }

    @Test
    void managerFindsUsersButOnlySuperAdminChangesThem() throws Exception {
        String admin = createStaff("adm.super3", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.acct3", Role.PARKING_MANAGER);
        String user = registerUser("acct.user3", UserType.STUDENT);
        long userId = idOf(user);
        long managerId = idOf(manager);
        long adminId = idOf(admin);

        doGet("/api/manager/users?q=acct.user3", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.username").value("acct.user3"));
        doGet("/api/manager/users", user).andExpect(status().isForbidden());

        // Managers can look, but reset / deactivate / activate / clear penalties are for the super admin only.
        doPost("/api/manager/users/" + userId + "/reset-password", manager, null).andExpect(status().isForbidden());
        doPost("/api/manager/users/" + userId + "/deactivate", manager, null).andExpect(status().isForbidden());
        doPost("/api/manager/users/" + userId + "/activate", manager, null).andExpect(status().isForbidden());
        doPost("/api/manager/users/" + userId + "/clear-penalties", manager, null).andExpect(status().isForbidden());

        // The super admin can only act on regular users here.
        doPost("/api/manager/users/" + managerId + "/reset-password", admin, null).andExpect(status().isNotFound());
        doPost("/api/manager/users/" + adminId + "/deactivate", admin, null).andExpect(status().isNotFound());

        String reset = doPost("/api/manager/users/" + userId + "/reset-password", admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String temporary = objectMapper.readTree(reset).get("temporaryPassword").asText();
        login("acct.user3", PASSWORD).andExpect(status().isUnauthorized());
        login("acct.user3", temporary).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));

        doPost("/api/manager/users/" + userId + "/deactivate", admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        login("acct.user3", temporary).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        doGet("/api/users/me", user).andExpect(status().isUnauthorized()); // the old token stops working at once

        doPost("/api/manager/users/" + userId + "/activate", admin, null).andExpect(status().isOk());
        login("acct.user3", temporary).andExpect(status().isOk());

        // The super admin manages managers, but a manager id is not valid on the user endpoints and vice versa.
        doPost("/api/admin/staff/" + userId + "/reset-password", admin, null).andExpect(status().isNotFound());
        doPost("/api/admin/staff/" + managerId + "/deactivate", admin, null).andExpect(status().isOk());
        login("mgr.acct3", PASSWORD).andExpect(status().isForbidden());
    }

    /* ---------- demo clock ---------- */

    @Test
    void demoControlsAreSuperAdminOnly() throws Exception {
        String admin = createStaff("adm.super4", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.demo4", Role.PARKING_MANAGER);

        doPost("/api/admin/demo/time/advance", manager, "{\"minutes\":10}").andExpect(status().isForbidden());
        doPost("/api/admin/demo/run-penalty-check", manager, null).andExpect(status().isForbidden());
        doPost("/api/admin/demo/time/advance", admin, "{\"minutes\":0}").andExpect(status().isBadRequest());
        doPost("/api/admin/demo/time/advance", admin, "{\"minutes\":90}").andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes").value(90));
        doGet("/api/time", manager).andExpect(status().isOk()).andExpect(jsonPath("$.demoTimeControl").value(true));
        doPost("/api/admin/demo/time/reset", admin, null).andExpect(jsonPath("$.offsetMinutes").value(0));
    }

    /* ---------- no-show ---------- */

    @Test
    void noShowsGiveWarningsThenDisableReservations() throws Exception {
        String admin = createStaff("adm.super5", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.pen5", Role.PARKING_MANAGER);
        String user = registerUser("pen.user5", UserType.STUDENT);
        approvedVehicle(manager, user, "MOTORCYCLE", "PEN0005");

        for (int i = 1; i <= 3; i++) {
            doPost("/api/reservations", user, window(10)).andExpect(status().isCreated());
            advance(admin, 30);              // the reservation ends and nobody came
            runCheck(admin);
            doGet("/api/users/me", user).andExpect(jsonPath("$.warningCount").value(i));
            // Running the check again must not punish twice.
            runCheck(admin);
            doGet("/api/users/me", user).andExpect(jsonPath("$.warningCount").value(i));
        }

        doGet("/api/users/me", user).andExpect(jsonPath("$.registrationBanned").value(true));
        doGet("/api/penalties/me", user).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("NO_SHOW"));
        doGet("/api/reservations/me", user).andExpect(jsonPath("$[0].status").value("NO_SHOW"));

        doPost("/api/reservations", user, window(10)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_BANNED"));

        // Only the super admin can clear it (an appeal, or a mistake).
        doPost("/api/manager/users/" + idOf(user) + "/clear-penalties", manager, null).andExpect(status().isForbidden());
        doPost("/api/manager/users/" + idOf(user) + "/clear-penalties", admin, null).andExpect(status().isNoContent());
        doGet("/api/users/me", user).andExpect(jsonPath("$.warningCount").value(0))
                .andExpect(jsonPath("$.registrationBanned").value(false));
        doPost("/api/reservations", user, window(10)).andExpect(status().isCreated());
    }

    @Test
    void aReservationThatWasUsedIsNeverANoShow() throws Exception {
        String admin = createStaff("adm.super6", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.pen6", Role.PARKING_MANAGER);
        String user = registerUser("pen.user6", UserType.STUDENT);
        approvedVehicle(manager, user, "MOTORCYCLE", "PEN0006");

        doPost("/api/reservations", user, window(10)).andExpect(status().isCreated());
        advance(admin, 2);
        doPost("/api/manager/gate/verify", manager, gate("PEN0006", "IN")).andExpect(jsonPath("$.allowed").value(true));
        doPost("/api/manager/gate/verify", manager, gate("PEN0006", "OUT")).andExpect(jsonPath("$.allowed").value(true));
        advance(admin, 60);
        runCheck(admin);
        doGet("/api/users/me", user).andExpect(jsonPath("$.warningCount").value(0));
        doGet("/api/reservations/me", user).andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    /* ---------- overstay: the vehicle is left behind ---------- */

    @Test
    void vehicleLeftInsideIsFlaggedAndOwnerSuspendedOnlyOnce() throws Exception {
        String admin = createStaff("adm.super7", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.pen7", Role.PARKING_MANAGER);
        String user = registerUser("pen.user7", UserType.STUDENT);
        approvedVehicle(manager, user, "MOTORCYCLE", "PEN0007");

        doPost("/api/reservations", user, window(5)).andExpect(status().isCreated());
        // A second reservation for tomorrow, which the suspension should release.
        LocalDateTime tomorrow = LocalDateTime.now(clock).plusDays(1);
        doPost("/api/reservations", user, """
                {"start":"%s","end":"%s"}
                """.formatted(tomorrow, tomorrow.plusHours(2))).andExpect(status().isCreated());
        advance(admin, 2);
        doPost("/api/manager/gate/verify", manager, gate("PEN0007", "IN")).andExpect(jsonPath("$.allowed").value(true));

        // Reservation over and the grace period passed, the vehicle is still there.
        advance(admin, 40);
        runCheck(admin);

        doGet("/api/manager/dashboard", manager).andExpect(jsonPath("$.overstayingNow").value(greaterThanOrEqualTo(1)));
        doGet("/api/users/me", user).andExpect(jsonPath("$.suspendedUntil").isNotEmpty());
        doGet("/api/penalties/me", user).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("OVERSTAY"));
        // Tomorrow's reservation was cancelled (it is the newest, so it is listed first).
        doGet("/api/reservations/me", user).andExpect(jsonPath("$[0].status").value("CANCELLED"));
        // Not a no-show: it did arrive.
        doGet("/api/users/me", user).andExpect(jsonPath("$.warningCount").value(0));

        doPost("/api/reservations", user, window(10)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        // Still counted inside, and the same plate cannot enter a second time.
        doGet("/api/manager/gate/inside", manager).andExpect(jsonPath("$[?(@.identifier=='PEN0007')].overstay").value(true));

        // Whenever security finally records the exit, no second penalty is added.
        doPost("/api/manager/gate/verify", manager, gate("PEN0007", "OUT"))
                .andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.reason").value("OVERSTAY"));
        runCheck(admin);
        doGet("/api/penalties/me", user).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void lateExitIsPenalisedEvenIfTheBackgroundCheckNeverRan() throws Exception {
        String admin = createStaff("adm.super8", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.pen8", Role.PARKING_MANAGER);
        String user = registerUser("pen.user8", UserType.STUDENT);
        approvedVehicle(manager, user, "MOTORCYCLE", "PEN0008");

        doPost("/api/reservations", user, window(5)).andExpect(status().isCreated());
        advance(admin, 2);
        doPost("/api/manager/gate/verify", manager, gate("PEN0008", "IN")).andExpect(jsonPath("$.allowed").value(true));
        advance(admin, 40);
        doPost("/api/manager/gate/verify", manager, gate("PEN0008", "OUT")).andExpect(jsonPath("$.reason").value("OVERSTAY"));

        doGet("/api/penalties/me", user).andExpect(jsonPath("$.length()").value(1));
        doGet("/api/users/me", user).andExpect(jsonPath("$.suspendedUntil").isNotEmpty());
    }

    /* ---------- manual override ---------- */

    @Test
    void manualOverrideNeedsAReasonAndIsMarkedInTheLog() throws Exception {
        String manager = createStaff("mgr.ovr9", Role.PARKING_MANAGER);
        String user = registerUser("ovr.user9", UserType.STUDENT);
        approvedVehicle(manager, user, "MOTORCYCLE", "OVR0009");

        // A reason is required.
        doPost("/api/manager/gate/override", manager, """
                {"identifier":"OVR9001","vehicleType":"CAR","name":"Ambulance"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        // An unknown plate needs a vehicle type.
        doPost("/api/manager/gate/override", manager, """
                {"identifier":"OVR9001","reason":"Medical emergency"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VEHICLE_TYPE_REQUIRED"));
        // Only staff can do it.
        doPost("/api/manager/gate/override", user, """
                {"identifier":"OVR9001","vehicleType":"CAR","reason":"Medical emergency"}
                """).andExpect(status().isForbidden());

        doPost("/api/manager/gate/override", manager, """
                {"identifier":"ovr-9001","vehicleType":"CAR","name":"Ambulance","reason":"Medical emergency"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("OVERRIDE"));
        doPost("/api/manager/gate/override", manager, """
                {"identifier":"OVR9001","vehicleType":"CAR","reason":"Medical emergency"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_INSIDE"));

        // A registered plate with no reservation: the override skips the reservation check.
        doPost("/api/manager/gate/verify", manager, gate("OVR0009", "IN")).andExpect(jsonPath("$.allowed").value(false));
        doPost("/api/manager/gate/override", manager, """
                {"identifier":"OVR0009","reason":"System was down"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));

        doGet("/api/manager/logs?limit=30", manager)
                .andExpect(jsonPath("$[?(@.identifier=='OVR9001')].override").value(true))
                .andExpect(jsonPath("$[?(@.identifier=='OVR9001')].overrideReason").value("Medical emergency"));

        // Leave the lot empty again so other tests' counts are not affected.
        doPost("/api/manager/gate/verify", manager, gate("OVR0009", "OUT")).andExpect(jsonPath("$.allowed").value(true));
        doPost("/api/manager/gate/verify", manager, gate("OVR9001", "OUT")).andExpect(jsonPath("$.allowed").value(true));
    }

    /* ---------- faculty area ---------- */

    @Test
    void facultyUsesTheirOwnParkingAreaAndStudentsDoNot() throws Exception {
        String admin = createStaff("adm.super10", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.area10", Role.PARKING_MANAGER);
        String student = registerUser("area.student10", UserType.STUDENT);
        String faculty = registerUser("area.faculty10", UserType.FACULTY);
        approvedVehicle(manager, student, "CAR", "ARE0010");
        approvedVehicle(manager, faculty, "CAR", "ARE0011");

        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(5);
        String query = "?vehicleType=CAR&start=" + start + "&end=" + start.plusHours(1);

        doGet("/api/reservations/availability" + query, faculty)
                .andExpect(jsonPath("$.area").value("FACULTY")).andExpect(jsonPath("$.capacity").value(12));
        doGet("/api/reservations/availability" + query, student)
                .andExpect(jsonPath("$.area").value("GENERAL")).andExpect(jsonPath("$.capacity").value(33));

        // Their reservations are counted in separate areas.
        doPost("/api/reservations", faculty, window(60)).andExpect(status().isCreated());
        doGet("/api/reservations/availability" + query, faculty).andExpect(jsonPath("$.remaining").value(11));
        doGet("/api/reservations/availability" + query, student).andExpect(jsonPath("$.remaining").value(33));

        // The faculty entry counts against the faculty area on the dashboard.
        advance(admin, 2);
        doPost("/api/manager/gate/verify", manager, gate("ARE0011", "IN")).andExpect(jsonPath("$.allowed").value(true));
        doGet("/api/manager/dashboard", manager)
                .andExpect(jsonPath("$.types[?(@.area=='FACULTY' && @.vehicleType=='CAR')].inside").value(1))
                .andExpect(jsonPath("$.types[?(@.area=='GENERAL' && @.vehicleType=='CAR')].inside").value(0));
        doPost("/api/manager/gate/verify", manager, gate("ARE0011", "OUT")).andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void facultyAreaWithZeroSpacesSendsThemToTheGeneralLot() throws Exception {
        String admin = createStaff("adm.super11", Role.SUPER_ADMIN);
        String manager = createStaff("mgr.area11", Role.PARKING_MANAGER);
        String faculty = registerUser("area.faculty11", UserType.PERSONNEL);
        approvedVehicle(manager, faculty, "MOTORCYCLE", "ARE0012");

        LocalDateTime start = LocalDateTime.now(clock).plusMinutes(5);
        String query = "?vehicleType=MOTORCYCLE&start=" + start + "&end=" + start.plusHours(1);

        // The faculty area has no motorcycle spaces by default, so a personnel member's motorcycle uses the general lot.
        doGet("/api/reservations/availability" + query, faculty).andExpect(jsonPath("$.area").value("GENERAL"));

        ParkingCapacity row = capacities.findByAreaAndVehicleType(ParkingArea.FACULTY, VehicleType.MOTORCYCLE).orElseThrow();
        try {
            doPut("/api/admin/capacity/FACULTY/MOTORCYCLE", admin, "{\"capacity\":4}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.capacity").value(4));
            doGet("/api/reservations/availability" + query, faculty)
                    .andExpect(jsonPath("$.area").value("FACULTY")).andExpect(jsonPath("$.capacity").value(4));
            doPut("/api/admin/capacity/FACULTY/MOTORCYCLE", admin, "{\"capacity\":-1}").andExpect(status().isBadRequest());
        } finally {
            row.setCapacity(0);
            capacities.save(row);
        }
    }

    @Test
    void visitorsAreCountedInTheGeneralLotAndNeedAReason() throws Exception {
        String manager = createStaff("mgr.vis12", Role.PARKING_MANAGER);
        doPost("/api/manager/gate/visitor-entry", manager, """
                {"name":"Visitor Twelve","vehicleType":"CAR","identifier":"VIS0012","reason":"Parent meeting"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        doGet("/api/manager/dashboard", manager)
                .andExpect(jsonPath("$.types[?(@.area=='GENERAL' && @.vehicleType=='CAR')].inside").value(1))
                .andExpect(jsonPath("$.types[?(@.area=='FACULTY' && @.vehicleType=='CAR')].inside").value(0));
        doPost("/api/manager/gate/verify", manager, gate("VIS0012", "OUT")).andExpect(jsonPath("$.allowed").value(true));
    }
}
