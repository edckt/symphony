package com.dbs.symphony.repository;

import com.dbs.symphony.entity.OperationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRecordRepository extends JpaRepository<OperationRecordEntity, String> {
}
