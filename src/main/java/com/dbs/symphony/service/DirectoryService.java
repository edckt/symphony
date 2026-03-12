package com.dbs.symphony.service;

import com.dbs.symphony.dto.GroupDto;
import com.dbs.symphony.dto.ManagedGroupPairDto;
import com.dbs.symphony.dto.UserSummaryDto;

import java.util.List;

public interface DirectoryService {

    /** Returns all manager↔user group pairs the given principal can manage. */
    List<ManagedGroupPairDto> listManagedGroupPairs(String principal);

    /** Returns metadata for the given group. Throws NotFoundException if not found. */
    GroupDto getGroup(String groupId);

    /** Returns the members of the given group. */
    List<UserSummaryDto> listGroupMembers(String groupId);
}
