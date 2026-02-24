package com.dbs.symphony.integration;

import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.entity.DefaultQuotaEntity;
import com.dbs.symphony.repository.DefaultQuotaRepository;
import com.dbs.symphony.repository.QuotaAuditRepository;
import com.dbs.symphony.repository.UserQuotaRepository;
import com.dbs.symphony.service.AuditService;
import com.dbs.symphony.service.JsonService;
import com.dbs.symphony.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuotaServiceIT extends PostgresIntegrationTestBase {

    @Autowired QuotaService quotaService;
    @Autowired AuditService auditService;

    @Autowired UserQuotaRepository userQuotaRepo;
    @Autowired QuotaAuditRepository auditRepo;
    @Autowired DefaultQuotaRepository defaultQuotaRepo;
    @Autowired JsonService jsonService;

    @Test
    void upsert_get_delete_recordsAudit() {
        String projectId = "p1";
        String groupId = "g1";
        String userId = "u123";

        var spec = new UserQuotaSpecDto(
                3,
                16,
                List.of("e2-standard-4", "n2-standard-8"),
                200,
                List.of("asia-southeast1-a")
        );

        // upsert
        var dto = quotaService.upsertUserQuota(projectId, groupId, userId, spec);
        assertThat(dto.projectId()).isEqualTo(projectId);
        assertThat(dto.groupId()).isEqualTo(groupId);
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.spec().maxInstances()).isEqualTo(3);

        // repo contains row
        assertThat(userQuotaRepo.findByProjectIdAndGroupIdAndUserId(projectId, groupId, userId)).isPresent();

        // get => EXPLICIT
        UserQuotaLookupResultDto lookup = quotaService.getUserQuota(projectId, groupId, userId);
        assertThat(lookup.status()).isEqualTo("EXPLICIT");
        assertThat(lookup.quota()).isNotNull();
        assertThat(lookup.quota().spec().maxTotalVcpu()).isEqualTo(16);

        // audit latest => UPSERT
        var latest1 = auditService.latestEvent(projectId, groupId, userId).orElseThrow();
        assertThat(latest1.action()).isEqualTo("UPSERT");
        assertThat(latest1.newSpec().maxInstances()).isEqualTo(3);

        // delete
        quotaService.deleteUserQuota(projectId, groupId, userId);
        assertThat(userQuotaRepo.findByProjectIdAndGroupIdAndUserId(projectId, groupId, userId)).isEmpty();

        // audit latest => DELETE
        var latest2 = auditService.latestEvent(projectId, groupId, userId).orElseThrow();
        assertThat(latest2.action()).isEqualTo("DELETE");
        assertThat(latest2.oldSpec()).isNotNull();
        assertThat(latest2.newSpec()).isNull();

        // optional: ensure at least 2 audit rows total
        assertThat(auditRepo.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void sameUserCanHaveDistinctQuotasPerGroup() {
        String projectId = "p2";
        String userId = "u456";
        String groupA = "gA";
        String groupB = "gB";

        var specA = new UserQuotaSpecDto(1, 4, List.of("e2-standard-2"), 100, List.of("asia-southeast1-a"));
        var specB = new UserQuotaSpecDto(3, 16, List.of("n2-standard-4"), 200, List.of("asia-southeast1-b"));

        quotaService.upsertUserQuota(projectId, groupA, userId, specA);
        quotaService.upsertUserQuota(projectId, groupB, userId, specB);

        var lookupA = quotaService.getUserQuota(projectId, groupA, userId);
        var lookupB = quotaService.getUserQuota(projectId, groupB, userId);

        assertThat(lookupA.quota()).isNotNull();
        assertThat(lookupB.quota()).isNotNull();
        assertThat(lookupA.quota().spec().maxInstances()).isEqualTo(1);
        assertThat(lookupB.quota().spec().maxInstances()).isEqualTo(3);

        quotaService.deleteUserQuota(projectId, groupA, userId);

        var afterDeleteA = quotaService.getUserQuota(projectId, groupA, userId);
        var stillPresentB = quotaService.getUserQuota(projectId, groupB, userId);
        assertThat(afterDeleteA.status()).isEqualTo("NONE");
        assertThat(stillPresentB.status()).isEqualTo("EXPLICIT");
    }

    @Test
    void latestAuditIsResolvedWithinGroupScope() {
        String projectId = "p3";
        String userId = "u789";
        String groupA = "gA";
        String groupB = "gB";

        var specA1 = new UserQuotaSpecDto(1, 4, List.of("e2-standard-2"), 100, List.of("asia-southeast1-a"));
        var specA2 = new UserQuotaSpecDto(2, 8, List.of("e2-standard-4"), 120, List.of("asia-southeast1-a"));
        var specB1 = new UserQuotaSpecDto(3, 16, List.of("n2-standard-4"), 200, List.of("asia-southeast1-b"));

        quotaService.upsertUserQuota(projectId, groupA, userId, specA1);
        quotaService.upsertUserQuota(projectId, groupB, userId, specB1);
        quotaService.upsertUserQuota(projectId, groupA, userId, specA2);

        var latestA = auditService.latestEvent(projectId, groupA, userId).orElseThrow();
        var latestB = auditService.latestEvent(projectId, groupB, userId).orElseThrow();

        assertThat(latestA.context().groupId()).isEqualTo(groupA);
        assertThat(latestB.context().groupId()).isEqualTo(groupB);
        assertThat(latestA.newSpec().maxInstances()).isEqualTo(2);
        assertThat(latestB.newSpec().maxInstances()).isEqualTo(3);
    }

    @Test
    void getUserQuota_withDefaultQuota_fallsBackToDefaultQuota() {
        String projectId = "p4";
        String groupId = "g1";
        String userId = "u_fallback";

        DefaultQuotaEntity defaultEntry = new DefaultQuotaEntity();
        defaultEntry.setProjectId(projectId);
        defaultEntry.setSpecJson(jsonService.toJson(
                new UserQuotaSpecDto(1, 4, List.of("e2-standard-2"), 100, List.of())));
        defaultEntry.setUpdatedAt(OffsetDateTime.now());
        defaultEntry.setUpdatedBy("admin");
        defaultQuotaRepo.save(defaultEntry);

        var result = quotaService.getUserQuota(projectId, groupId, userId);

        assertThat(result.status()).isEqualTo("NONE");
        assertThat(result.effectiveSource()).isEqualTo("DEFAULT_QUOTA");
        assertThat(result.quota()).isNull();
    }

    @Test
    void getUserQuota_withNoQuotaAtAll_fallsBackToGlobalDefault() {
        String projectId = "p5";
        String groupId = "g1";
        String userId = "u_no_quota";

        var result = quotaService.getUserQuota(projectId, groupId, userId);

        assertThat(result.status()).isEqualTo("NONE");
        assertThat(result.effectiveSource()).isEqualTo("GLOBAL_DEFAULT");
        assertThat(result.quota()).isNull();
    }
}
