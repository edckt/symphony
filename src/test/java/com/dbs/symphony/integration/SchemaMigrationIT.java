package com.dbs.symphony.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationIT extends PostgresIntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayRan_andCoreTablesExist() {
        // Flyway history table should exist if migrations ran
        Integer flywayCount = jdbc.queryForObject(
                "select count(*) from flyway_schema_history",
                Integer.class
        );
        assertThat(flywayCount).isNotNull();
        assertThat(flywayCount).isGreaterThan(0);

        // Core tables should exist
        assertThat(tableExists("user_quotas")).isTrue();
        assertThat(tableExists("default_quotas")).isTrue();
        assertThat(tableExists("quota_audit")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer c = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class,
                tableName
        );
        return c != null && c > 0;
    }
}
