package com.dbs.symphony.repository;

import com.dbs.symphony.entity.UserQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserQuotaRepository extends JpaRepository<UserQuotaEntity, String> {
    Optional<UserQuotaEntity> findByProjectIdAndGroupIdAndUserId(String projectId, String groupId, String userId);
    void deleteByProjectIdAndGroupIdAndUserId(String projectId, String groupId, String userId);
}
