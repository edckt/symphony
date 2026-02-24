package com.dbs.symphony.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "quota_audit")
public class QuotaAuditEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "action", nullable = false)
    private String action; // UPSERT/DELETE

    @Column(name = "at", nullable = false)
    private OffsetDateTime at;

    @Column(name = "actor_principal", nullable = false)
    private String actorPrincipal;

    @Column(name = "old_spec_json", columnDefinition = "TEXT")
    private String oldSpecJson;

    @Column(name = "new_spec_json", columnDefinition = "TEXT")
    private String newSpecJson;

    public static QuotaAuditEntity of(String projectId, String groupId, String userId,
                                      String action, OffsetDateTime at, String actorPrincipal,
                                      String oldSpecJson, String newSpecJson) {
        QuotaAuditEntity e = new QuotaAuditEntity();
        e.eventId = java.util.UUID.randomUUID().toString();
        e.projectId = projectId;
        e.groupId = groupId;
        e.userId = userId;
        e.action = action;
        e.at = at;
        e.actorPrincipal = actorPrincipal;
        e.oldSpecJson = oldSpecJson;
        e.newSpecJson = newSpecJson;
        return e;
    }

    // getters/setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public OffsetDateTime getAt() { return at; }
    public void setAt(OffsetDateTime at) { this.at = at; }
    public String getActorPrincipal() { return actorPrincipal; }
    public void setActorPrincipal(String actorPrincipal) { this.actorPrincipal = actorPrincipal; }
    public String getOldSpecJson() { return oldSpecJson; }
    public void setOldSpecJson(String oldSpecJson) { this.oldSpecJson = oldSpecJson; }
    public String getNewSpecJson() { return newSpecJson; }
    public void setNewSpecJson(String newSpecJson) { this.newSpecJson = newSpecJson; }
}