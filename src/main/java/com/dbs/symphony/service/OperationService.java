package com.dbs.symphony.service;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.entity.OperationRecordEntity;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.repository.OperationRecordRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class OperationService {

    private final OperationRecordRepository repo;

    public OperationService(OperationRecordRepository repo) {
        this.repo = repo;
    }

    /**
     * Records an initiated operation so ownership can be verified later.
     * Called immediately after each WorkbenchService call that returns an LRO.
     */
    public void record(AsyncOperationDto dto, String principal, String projectId) {
        repo.save(OperationRecordEntity.of(
                dto.id(),
                dto.id(), // lroName is recoverable from id but stored for debugging
                principal,
                projectId,
                OffsetDateTime.now()
        ));
    }

    /**
     * Verifies that {@code principal} initiated the operation identified by {@code encodedId}.
     * Throws {@link NotFoundException} (→ 404) if the operation is unknown,
     * or {@link ForbiddenException} (→ 403) if a different principal initiated it.
     */
    public void assertOwnership(String encodedId, String principal) {
        OperationRecordEntity record = repo.findById(encodedId)
                .orElseThrow(() -> new NotFoundException("Operation not found: " + encodedId));
        if (!record.getInitiatedBy().equals(principal)) {
            throw new ForbiddenException("Not authorized to access this operation");
        }
    }
}
