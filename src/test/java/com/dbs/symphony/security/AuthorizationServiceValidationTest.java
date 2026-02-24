package com.dbs.symphony.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationServiceValidationTest {

    @Test
    void configModeWithBothMapsEmpty_throwsIllegalStateOnStartup() {
        var props = new AuthorizationProperties();
        props.setMode(AuthorizationProperties.Mode.CONFIG);

        var service = new AuthorizationService(props);

        assertThatThrownBy(service::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOW_ALL");
    }

    @Test
    void configModeWithManagedGroupsPopulated_doesNotThrow() {
        var props = new AuthorizationProperties();
        props.setMode(AuthorizationProperties.Mode.CONFIG);
        props.setManagedGroupsByPrincipal(Map.of("admin", Set.of("group1")));

        var service = new AuthorizationService(props);

        assertThatCode(service::validate).doesNotThrowAnyException();
    }

    @Test
    void configModeWithUsersByGroupPopulated_doesNotThrow() {
        var props = new AuthorizationProperties();
        props.setMode(AuthorizationProperties.Mode.CONFIG);
        props.setUsersByGroup(Map.of("group1", Set.of("user1")));

        var service = new AuthorizationService(props);

        assertThatCode(service::validate).doesNotThrowAnyException();
    }

    @Test
    void allowAllModeWithEmptyMaps_doesNotThrow() {
        var props = new AuthorizationProperties();
        // mode defaults to ALLOW_ALL

        var service = new AuthorizationService(props);

        assertThatCode(service::validate).doesNotThrowAnyException();
    }
}
