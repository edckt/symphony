package com.dbs.symphony.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MachineTypeServiceTest {

    private final MachineTypeService service = new MachineTypeService();

    @ParameterizedTest(name = "{0} -> {1} vCPUs")
    @CsvSource({
            "e2-standard-4,   4",
            "e2-standard-16,  16",
            "n2-standard-8,   8",
            "n2-standard-32,  32",
            "n2d-standard-16, 16",
            "n2d-standard-48, 48",
            "n1-standard-4,   4",
            "n1-standard-96,  96",
            "c2-standard-4,   4",
            "c2-standard-60,  60",
            "c2d-standard-8,  8",
            "m2-ultramem-208, 208",
    })
    void predefinedTypes_parsedFromLastToken(String machineType, int expected) {
        assertThat(service.vcpuCount(machineType)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "custom type {0} -> {1} vCPUs")
    @CsvSource({
            "custom-8-32768,  8",
            "custom-16-65536, 16",
            "custom-4-16384,  4",
            "custom-96-655360, 96",
    })
    void customTypes_parsedFromSecondToken(String machineType, int expected) {
        assertThat(service.vcpuCount(machineType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nullOrBlank_returnsFallback(String machineType) {
        assertThat(service.vcpuCount(machineType)).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "not-a-number", "e2-standard-foo"})
    void unparseableLastToken_returnsFallback(String machineType) {
        assertThat(service.vcpuCount(machineType)).isEqualTo(8);
    }

    @Test
    void singleToken_withNoHyphen_returnsFallback() {
        assertThat(service.vcpuCount("largememory")).isEqualTo(8);
    }
}
