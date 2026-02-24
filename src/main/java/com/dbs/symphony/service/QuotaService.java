package com.dbs.symphony.service;

import com.dbs.symphony.dto.DefaultQuotaDto;
import com.dbs.symphony.dto.UserQuotaDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.entity.DefaultQuotaEntity;
import com.dbs.symphony.entity.QuotaAuditEntity;
import com.dbs.symphony.entity.UserQuotaEntity;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.repository.DefaultQuotaRepository;
import com.dbs.symphony.repository.QuotaAuditRepository;
import com.dbs.symphony.repository.UserQuotaRepository;
import com.dbs.symphony.security.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class QuotaService {
    private final UserQuotaRepository quotaRepo;
    private final DefaultQuotaRepository defaultQuotaRepo;
    private final QuotaAuditRepository auditRepo;
    private final JsonService json;

    public QuotaService(UserQuotaRepository quotaRepo, DefaultQuotaRepository defaultQuotaRepo,
                        QuotaAuditRepository auditRepo, JsonService json) {
        this.quotaRepo = quotaRepo;
        this.defaultQuotaRepo = defaultQuotaRepo;
        this.auditRepo = auditRepo;
        this.json = json;
    }

    @Transactional
    public UserQuotaDto upsertUserQuota(String projectId, String groupId, String userId, UserQuotaSpecDto spec) {
        String actor = CurrentPrincipal.principal();
        OffsetDateTime now = OffsetDateTime.now();

        Optional<UserQuotaEntity> existing = quotaRepo.findByProjectIdAndGroupIdAndUserId(projectId, groupId, userId);
        String oldSpecJson = existing.map(UserQuotaEntity::getSpecJson).orElse(null);

        UserQuotaEntity e = existing.orElseGet(UserQuotaEntity::new);
        if (e.getProjectId() == null) e.setProjectId(projectId);
        if (e.getGroupId() == null) e.setGroupId(groupId);
        if (e.getUserId() == null) e.setUserId(userId);
        e.setSpecJson(json.toJson(spec));
        e.setUpdatedAt(now);
        e.setUpdatedBy(actor);

        quotaRepo.save(e);

        auditRepo.save(QuotaAuditEntity.of(projectId, groupId, userId,
                "UPSERT", now, actor, oldSpecJson, e.getSpecJson()));

        return new UserQuotaDto(projectId, groupId, userId, spec, now, actor);
    }

    public UserQuotaLookupResultDto getUserQuota(String projectId, String groupId, String userId) {
        return quotaRepo.findByProjectIdAndGroupIdAndUserId(projectId, groupId, userId)
            .map(e -> {
                UserQuotaSpecDto spec = json.fromJson(e.getSpecJson());
                return new UserQuotaLookupResultDto(
                    projectId, groupId, userId,
                    "EXPLICIT",
                    new UserQuotaDto(projectId, e.getGroupId(), userId, spec, e.getUpdatedAt(), e.getUpdatedBy()),
                    "USER_QUOTA",
                    null,
                    spec
                );
            })
            .orElseGet(() -> defaultQuotaRepo.findByProjectId(projectId)
                .map(d -> {
                    UserQuotaSpecDto spec = json.fromJson(d.getSpecJson());
                    return new UserQuotaLookupResultDto(
                        projectId, groupId, userId,
                        "NONE",
                        null,
                        "DEFAULT_QUOTA",
                        null,
                        spec
                    );
                })
                .orElseGet(() -> new UserQuotaLookupResultDto(
                        projectId, groupId, userId,
                        "NONE",
                        null,
                        "GLOBAL_DEFAULT",
                        "No explicit quota set",
                        null
                ))
            );
    }

    public DefaultQuotaDto getDefaultQuota(String projectId) {
        return defaultQuotaRepo.findByProjectId(projectId)
                .map(d -> new DefaultQuotaDto(projectId, json.fromJson(d.getSpecJson()), d.getUpdatedAt(), d.getUpdatedBy()))
                .orElseThrow(() -> new NotFoundException("No default quota set for project: " + projectId));
    }

    @Transactional
    public DefaultQuotaDto upsertDefaultQuota(String projectId, UserQuotaSpecDto spec) {
        String actor = CurrentPrincipal.principal();
        OffsetDateTime now = OffsetDateTime.now();

        DefaultQuotaEntity e = defaultQuotaRepo.findByProjectId(projectId).orElseGet(DefaultQuotaEntity::new);
        if (e.getProjectId() == null) e.setProjectId(projectId);
        e.setSpecJson(json.toJson(spec));
        e.setUpdatedAt(now);
        e.setUpdatedBy(actor);
        defaultQuotaRepo.save(e);

        return new DefaultQuotaDto(projectId, spec, now, actor);
    }

    @Transactional
    public void deleteDefaultQuota(String projectId) {
        defaultQuotaRepo.deleteByProjectId(projectId);
    }

    @Transactional
    public void deleteUserQuota(String projectId, String groupId, String userId) {
        String actor = CurrentPrincipal.principal();
        OffsetDateTime now = OffsetDateTime.now();

        Optional<UserQuotaEntity> existing = quotaRepo.findByProjectIdAndGroupIdAndUserId(projectId, groupId, userId);
        String oldSpecJson = existing.map(UserQuotaEntity::getSpecJson).orElse(null);

        quotaRepo.deleteByProjectIdAndGroupIdAndUserId(projectId, groupId, userId);

        auditRepo.save(QuotaAuditEntity.of(projectId, groupId, userId,
                "DELETE", now, actor, oldSpecJson, null));
    }
}
