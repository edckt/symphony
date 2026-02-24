package com.dbs.symphony.repository;

import com.dbs.symphony.entity.DefaultQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DefaultQuotaRepository extends JpaRepository<DefaultQuotaEntity, String> {
    Optional<DefaultQuotaEntity> findByProjectId(String projectId);
    void deleteByProjectId(String projectId);
}