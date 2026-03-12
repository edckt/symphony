package com.dbs.symphony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.instance-sizes")
public class InstanceSizeProperties {

    private List<SizeDefinition> sizes = defaultSizes();

    public List<SizeDefinition> getSizes() {
        return sizes;
    }

    public void setSizes(List<SizeDefinition> sizes) {
        this.sizes = sizes;
    }

    private static List<SizeDefinition> defaultSizes() {
        List<SizeDefinition> defaults = new ArrayList<>();
        defaults.add(new SizeDefinition("SMALL",  "Small",   "4 vCPU · 16 GB RAM",   "e2-standard-4",  4,  16,  150, false));
        defaults.add(new SizeDefinition("MEDIUM", "Medium",  "8 vCPU · 32 GB RAM",   "e2-standard-8",  8,  32,  200, false));
        defaults.add(new SizeDefinition("LARGE",  "Large",   "16 vCPU · 64 GB RAM",  "e2-standard-16", 16, 64,  300, true));
        defaults.add(new SizeDefinition("XLARGE", "X-Large", "32 vCPU · 128 GB RAM", "e2-standard-32", 32, 128, 500, true));
        return defaults;
    }

    public static class SizeDefinition {

        private String size;
        private String displayName;
        private String description;
        private String machineType;
        private int vcpu;
        private int memoryGb;
        private int bootDiskGb;
        private boolean requiresApproval;

        public SizeDefinition() {}

        public SizeDefinition(String size, String displayName, String description,
                              String machineType, int vcpu, int memoryGb, int bootDiskGb,
                              boolean requiresApproval) {
            this.size = size;
            this.displayName = displayName;
            this.description = description;
            this.machineType = machineType;
            this.vcpu = vcpu;
            this.memoryGb = memoryGb;
            this.bootDiskGb = bootDiskGb;
            this.requiresApproval = requiresApproval;
        }

        public String  getSize()             { return size; }
        public String  getDisplayName()      { return displayName; }
        public String  getDescription()      { return description; }
        public String  getMachineType()      { return machineType; }
        public int     getVcpu()             { return vcpu; }
        public int     getMemoryGb()         { return memoryGb; }
        public int     getBootDiskGb()       { return bootDiskGb; }
        public boolean isRequiresApproval()  { return requiresApproval; }

        public void setSize(String size)                          { this.size = size; }
        public void setDisplayName(String displayName)            { this.displayName = displayName; }
        public void setDescription(String description)            { this.description = description; }
        public void setMachineType(String machineType)            { this.machineType = machineType; }
        public void setVcpu(int vcpu)                             { this.vcpu = vcpu; }
        public void setMemoryGb(int memoryGb)                     { this.memoryGb = memoryGb; }
        public void setBootDiskGb(int bootDiskGb)                 { this.bootDiskGb = bootDiskGb; }
        public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    }
}
