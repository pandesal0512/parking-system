package com.mabini.parking.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The front-end files are public. Everything under /api (except login/register) is not. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class StaticPagesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void frontEndFilesAreReachableWithoutLogin() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/app.html")).andExpect(status().isOk());
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
    }

    @Test
    void apiStillNeedsLogin() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/anything-else")).andExpect(status().isUnauthorized());
    }
}
