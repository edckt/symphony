package com.dbs.symphony.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SelfController.class)
class SelfControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void selfUsesJwtClaimsAndNormalizedAuthorities() throws Exception {
        mvc.perform(get("/v1/self")
                        .with(jwt().jwt(jwt -> jwt
                                        .subject("u123")
                                        .claim("name", "Alice Manager")
                                        .claim("email", "alice@example.com"))
                                .authorities(
                                        new SimpleGrantedAuthority("SCOPE_workbench.user"),
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u123"))
                .andExpect(jsonPath("$.displayName").value("Alice Manager"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles", hasItems("workbench.user", "ADMIN")));
    }
}
