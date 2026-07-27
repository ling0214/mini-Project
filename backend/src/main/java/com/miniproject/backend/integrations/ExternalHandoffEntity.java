package com.miniproject.backend.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_handoffs")
public class ExternalHandoffEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "source_task_id", nullable = false, length = 36)
    private String sourceTaskId;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private String status;

    @Column(name = "external_key")
    private String externalKey;

    @Column(name = "external_url")
    private String externalUrl;

    @Lob
    private String message;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExternalHandoffEntity() {
        // JPA
    }

    public ExternalHandoffEntity(String sourceTaskId, String destination, ConnectorResult result) {
        this.id = UUID.randomUUID().toString();
        this.sourceTaskId = sourceTaskId;
        this.destination = destination;
        this.status = result.status();
        this.externalKey = result.externalKey();
        this.externalUrl = result.externalUrl();
        this.message = result.message();
        this.dryRun = result.dryRun();
        this.createdAt = Instant.now();
    }

    public ExternalHandoffResult toResult() {
        return new ExternalHandoffResult(
                id, sourceTaskId, destination, status, externalKey, externalUrl,
                message, dryRun, createdAt.toString());
    }

    public String getId() {
        return id;
    }

    public String getSourceTaskId() {
        return sourceTaskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
