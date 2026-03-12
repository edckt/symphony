package com.dbs.symphony.service;

import com.dbs.symphony.config.InstanceSizeProperties;
import com.dbs.symphony.config.InstanceSizeProperties.SizeDefinition;
import com.dbs.symphony.dto.InstanceSizeDto;
import com.dbs.symphony.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstanceSizeCatalogService {

    private final InstanceSizeProperties properties;

    public InstanceSizeCatalogService(InstanceSizeProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns all configured sizes with an availability flag.
     *
     * <p>Availability rules:
     * <ul>
     *   <li>If {@code requiresApproval=false} (SMALL, MEDIUM): available unless the allowlist is
     *       non-empty and explicitly excludes this size's machine type.</li>
     *   <li>If {@code requiresApproval=true} (LARGE, XLARGE): available only when the allowlist
     *       explicitly contains this size's machine type (empty allowlist = blocked).</li>
     * </ul>
     *
     * @param allowedMachineTypes effective quota allowlist — empty means "no explicit grants"
     */
    public List<InstanceSizeDto> listSizes(List<String> allowedMachineTypes) {
        return properties.getSizes().stream()
                .map(s -> {
                    boolean available = s.isRequiresApproval()
                            ? allowedMachineTypes.contains(s.getMachineType())
                            : allowedMachineTypes.isEmpty() || allowedMachineTypes.contains(s.getMachineType());
                    return new InstanceSizeDto(
                            s.getSize(),
                            s.getDisplayName(),
                            s.getDescription(),
                            s.getMachineType(),
                            s.getVcpu(),
                            s.getMemoryGb(),
                            s.getBootDiskGb(),
                            available,
                            s.isRequiresApproval()
                    );
                })
                .toList();
    }

    /**
     * Resolves a size name to its definition. Throws 404 if the size is unknown.
     */
    public SizeDefinition resolve(String sizeName) {
        return properties.getSizes().stream()
                .filter(s -> s.getSize().equalsIgnoreCase(sizeName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Unknown instance size: " + sizeName));
    }
}
