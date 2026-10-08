package com.chrisdev.supporthub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class MigrationsIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAreAppliedSuccessfully() {
        Long failed = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                Long.class);
        Long applied = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Long.class);
        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(7);
    }

    @Test
    void referenceDataIsSeeded() {
        assertThat(jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class))
                .containsExactly("ADMIN", "AGENT", "ASSET_MANAGER", "USER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_types", Long.class)).isPositive();
    }

}
