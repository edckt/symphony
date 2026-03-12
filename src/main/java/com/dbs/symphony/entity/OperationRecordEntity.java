package com.dbs.symphony.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "operation_records")
public class OperationRecordEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "TEXT")
    private String id; // Base64url-encoded GCP LRO name

    @Column(name = "lro_name", nullable = false, columnDefinition = "TEXT")
    private String lroName;

    @Column(name = "initiated_by", nullable = false, columnDefinition = "TEXT")
    private String initiatedBy;

    @Column(name = "project_id", columnDefinition = "TEXT")
    private String projectId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static OperationRecordEntity of(String id, String lroName, String initiatedBy,
                                           String projectId, OffsetDateTime createdAt) {
        OperationRecordEntity e = new OperationRecordEntity();
        e.id = id;
        e.lroName = lroName;
        e.initiatedBy = initiatedBy;
        e.projectId = projectId;
        e.createdAt = createdAt;
        return e;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLroName() { return lroName; }
    public void setLroName(String lroName) { this.lroName = lroName; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
