package com.dbs.symphony.integration;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public abstract class PostgresIntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("workbench_test")
            .withUsername("workbench")
            .withPassword("workbench");

    static {
        // Singleton container for the full test JVM to keep JDBC URL stable across cached Spring contexts.
        POSTGRES.start();
    }

    protected static RequestPostProcessor managerJwt() {
        return jwt().jwt(j -> j.subject("manager1"))
                .authorities(new SimpleGrantedAuthority("SCOPE_workbench.manager"));
    }

    protected static RequestPostProcessor userJwt(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("SCOPE_workbench.user"));
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
