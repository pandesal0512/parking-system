package com.mabini.parking.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks registration, login and the basic security rules end to end.
 * Each test uses its own username because the in-memory database is shared between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthFlowTest {

    private static final String PASSWORD = "correct-horse-1";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private ResultActions register(String username, String password, String extraJson) throws Exception {
        String body = """
                {"username":"%s","fullName":"Test Person","password":"%s","userType":"STUDENT"%s}
                """.formatted(username, password, extraJson);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String username, String password) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registerIgnoresRoleInRequestAndCreatesPlainUser() throws Exception {
        // Someone tries to sneak in a higher role. It must be ignored.
        register("sneaky.user", PASSWORD, ",\"role\":\"SUPER_ADMIN\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginThenReadOwnProfile() throws Exception {
        register("juan.dela", PASSWORD, "").andExpect(status().isCreated());

        String response = login("juan.dela", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("juan.dela"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        register("Maria.Clara", PASSWORD, "").andExpect(status().isCreated());
        login("MARIA.CLARA", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        register("dup.user", PASSWORD, "").andExpect(status().isCreated());
        register("dup.user", PASSWORD, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void shortPasswordIs400() throws Exception {
        register("weak.pass", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void invalidUserTypeIs400() throws Exception {
        String body = """
                {"username":"bad.type","fullName":"Test Person","password":"%s","userType":"ALIEN"}
                """.formatted(PASSWORD);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordGivesGenericErrorAndLocksAfterFiveFailures() throws Exception {
        register("lock.me", PASSWORD, "").andExpect(status().isCreated());

        // Unknown user and wrong password give the SAME message (no hint about who exists).
        login("nobody.here", "whatever-123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        for (int i = 0; i < 5; i++) {
            login("lock.me", "wrong-password-" + i).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password."));
        }

        // Now even the CORRECT password is refused while the account is locked.
        login("lock.me", PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void changePasswordWorksWithCorrectCurrentPassword() throws Exception {
        register("change.me", PASSWORD, "").andExpect(status().isCreated());
        String response = login("change.me", PASSWORD).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();

        String wrong = """
                {"currentPassword":"nope-nope-1","newPassword":"brand-new-pass-2"}
                """;
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isBadRequest());

        String right = """
                {"currentPassword":"%s","newPassword":"brand-new-pass-2"}
                """.formatted(PASSWORD);
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(right))
                .andExpect(status().isNoContent());

        login("change.me", "brand-new-pass-2").andExpect(status().isOk());
    }
}
