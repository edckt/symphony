package com.dbs.symphony.service;

import com.dbs.symphony.dto.*;
import com.dbs.symphony.entity.QuotaAuditEntity;
import com.dbs.symphony.repository.QuotaAuditRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuditService {
    private final QuotaAuditRepository repo;
    private final JsonService json;

    public AuditService(QuotaAuditRepository repo, JsonService json) {
        this.repo = repo;
        this.json = json;
    }

    public Optional<QuotaAuditEventDto> latestEvent(String projectId, String groupId, String userId) {
        return repo.findFirstByProjectIdAndGroupIdAndUserIdOrderByAtDesc(projectId, groupId, userId)
                .map(this::toDto);
    }

    private QuotaAuditEventDto toDto(QuotaAuditEntity e) {
        UserQuotaSpecDto oldSpec = e.getOldSpecJson() == null ? null : json.fromJson(e.getOldSpecJson());
        UserQuotaSpecDto newSpec = e.getNewSpecJson() == null ? null : json.fromJson(e.getNewSpecJson());

        return new QuotaAuditEventDto(
            e.getEventId(),
            e.getAction(),
            e.getAt(),
            new AuditActorDto(e.getActorPrincipal(), null, null),
            new AuditContextDto(e.getGroupId(), null),
            oldSpec,
            newSpec
        );
    }
}
