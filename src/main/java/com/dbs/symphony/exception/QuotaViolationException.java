package com.dbs.symphony.exception;

import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.dto.ViolationDto;

import java.util.List;

public class QuotaViolationException extends RuntimeException {
    private final List<ViolationDto> violations;
    private final UserQuotaUsageDto snapshot;

    public QuotaViolationException(List<ViolationDto> violations, UserQuotaUsageDto snapshot) {
        super("Quota violation: " + violations.stream().map(ViolationDto::code).toList());
        this.violations = violations;
        this.snapshot = snapshot;
    }

    public List<ViolationDto> getViolations() {
        return violations;
    }

    public UserQuotaUsageDto getSnapshot() {
        return snapshot;
    }
}
