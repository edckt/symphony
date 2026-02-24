package com.dbs.symphony.service;

import com.dbs.symphony.dto.WorkbenchInstanceDto;

import java.util.List;

public interface CaiService {
    /**
     * Returns all Workbench-related Compute instances for the given user in the project.
     * Matches on the GCP label labels.user=bankUserId (the staff/bank ID, not the JWT subject).
     */
    List<WorkbenchInstanceDto> listUserInstances(String projectId, String bankUserId);
}
