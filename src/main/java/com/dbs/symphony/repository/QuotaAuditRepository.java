package com.dbs.symphony.repository;

import com.dbs.symphony.entity.QuotaAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotaAuditRepository extends JpaRepository<QuotaAuditEntity, String> {
    Optional<QuotaAuditEntity> findFirstByProjectIdAndGroupIdAndUserIdOrderByAtDesc(
            String projectId,
            String groupId,
            String userId
    );
}
