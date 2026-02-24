package com.dbs.symphony.controller;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Shared JWT {@link RequestPostProcessor} factories for {@code @WebMvcTest} security tests. */
final class JwtFixtures {
    private JwtFixtures() {}

    static RequestPostProcessor managerJwt() {
        return jwt().jwt(j -> j.subject("manager1"))
                .authorities(new SimpleGrantedAuthority("SCOPE_workbench.manager"));
    }

    static RequestPostProcessor userJwt() {
        return jwt().jwt(j -> j.subject("user1"))
                .authorities(new SimpleGrantedAuthority("SCOPE_workbench.user"));
    }
}
