package com.dbs.symphony.service;

import com.dbs.symphony.dto.GroupDto;
import com.dbs.symphony.dto.ManagedGroupPairDto;
import com.dbs.symphony.dto.UserSummaryDto;
import com.dbs.symphony.exception.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ldap.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledDirectoryService implements DirectoryService {

    @Override
    public List<ManagedGroupPairDto> listManagedGroupPairs(String principal) {
        return List.of();
    }

    @Override
    public GroupDto getGroup(String groupId) {
        throw new NotFoundException("Directory service is not enabled");
    }

    @Override
    public List<UserSummaryDto> listGroupMembers(String groupId) {
        return List.of();
    }
}
