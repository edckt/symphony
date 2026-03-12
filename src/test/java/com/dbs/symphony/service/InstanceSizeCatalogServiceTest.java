package com.dbs.symphony.service;

import com.dbs.symphony.config.InstanceSizeProperties;
import com.dbs.symphony.config.InstanceSizeProperties.SizeDefinition;
import com.dbs.symphony.dto.InstanceSizeDto;
import com.dbs.symphony.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceSizeCatalogServiceTest {

    InstanceSizeCatalogService service;

    // Catalog used for most tests: SMALL/MEDIUM don't require approval; LARGE does.
    @BeforeEach
    void setUp() {
        InstanceSizeProperties props = new InstanceSizeProperties();
        props.setSizes(List.of(
                new SizeDefinition("SMALL",  "Small",   "4 vCPU",  "e2-standard-4",  4,  16, 150, false),
                new SizeDefinition("MEDIUM", "Medium",  "8 vCPU",  "e2-standard-8",  8,  32, 200, false),
                new SizeDefinition("LARGE",  "Large",   "16 vCPU", "e2-standard-16", 16, 64, 300, true)
        ));
        service = new InstanceSizeCatalogService(props);
    }

    // ──────────────────────────────────────────────────────
    // listSizes — availability filtering
    // ──────────────────────────────────────────────────────

    @Test
    void listSizes_emptyAllowlist_defaultSizesAvailable_approvalSizesBlocked() {
        List<InstanceSizeDto> sizes = service.listSizes(List.of());

        assertThat(sizes).hasSize(3);
        // SMALL and MEDIUM are available by default; LARGE requires approval → blocked
        assertThat(sizes).filteredOn(s -> !s.requiresApproval()).allMatch(InstanceSizeDto::available);
        assertThat(sizes).filteredOn(InstanceSizeDto::requiresApproval).noneMatch(InstanceSizeDto::available);
    }

    @Test
    void listSizes_allowlistGrantsApprovalSize_approvalSizeBecomesAvailable() {
        // Manager explicitly allows LARGE's machine type
        List<InstanceSizeDto> sizes = service.listSizes(List.of("e2-standard-16"));

        assertThat(sizes).hasSize(3);
        // SMALL and MEDIUM: allowlist is non-empty and excludes their types → unavailable
        assertThat(sizes).filteredOn(s -> s.size().equals("SMALL")).noneMatch(InstanceSizeDto::available);
        assertThat(sizes).filteredOn(s -> s.size().equals("MEDIUM")).noneMatch(InstanceSizeDto::available);
        // LARGE: explicitly granted
        assertThat(sizes).filteredOn(s -> s.size().equals("LARGE")).allMatch(InstanceSizeDto::available);
    }

    @Test
    void listSizes_allowlistGrantsDefaultAndApprovalSize_allAvailable() {
        List<InstanceSizeDto> sizes = service.listSizes(
                List.of("e2-standard-4", "e2-standard-8", "e2-standard-16"));

        assertThat(sizes).allMatch(InstanceSizeDto::available);
    }

    @Test
    void listSizes_allowlistMatchesNone_allUnavailable() {
        List<InstanceSizeDto> sizes = service.listSizes(List.of("n2-standard-8"));

        assertThat(sizes).noneMatch(InstanceSizeDto::available);
    }

    @Test
    void listSizes_preservesCatalogOrder() {
        List<InstanceSizeDto> sizes = service.listSizes(List.of());

        assertThat(sizes).extracting(InstanceSizeDto::size)
                .containsExactly("SMALL", "MEDIUM", "LARGE");
    }

    @Test
    void listSizes_populatesAllFields() {
        InstanceSizeDto small = service.listSizes(List.of()).get(0);

        assertThat(small.size()).isEqualTo("SMALL");
        assertThat(small.displayName()).isEqualTo("Small");
        assertThat(small.machineType()).isEqualTo("e2-standard-4");
        assertThat(small.vcpu()).isEqualTo(4);
        assertThat(small.memoryGb()).isEqualTo(16);
        assertThat(small.bootDiskGb()).isEqualTo(150);
        assertThat(small.requiresApproval()).isFalse();
    }

    // ──────────────────────────────────────────────────────
    // resolve
    // ──────────────────────────────────────────────────────

    @Test
    void resolve_knownSize_returnsDefinition() {
        SizeDefinition def = service.resolve("MEDIUM");

        assertThat(def.getMachineType()).isEqualTo("e2-standard-8");
        assertThat(def.getBootDiskGb()).isEqualTo(200);
        assertThat(def.getVcpu()).isEqualTo(8);
    }

    @Test
    void resolve_caseInsensitive_returnsDefinition() {
        SizeDefinition def = service.resolve("small");

        assertThat(def.getMachineType()).isEqualTo("e2-standard-4");
    }

    @Test
    void resolve_unknownSize_throwsNotFoundException() {
        assertThatThrownBy(() -> service.resolve("HUGE"))
                .isInstanceOf(NotFoundException.class);
    }
}
