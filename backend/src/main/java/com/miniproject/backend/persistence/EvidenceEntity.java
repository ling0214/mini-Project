package com.miniproject.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One citation row per Evidence entry on an AnalysisArtifactEntity. */
@Entity
@Table(name = "evidence")
public class EvidenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private AnalysisArtifactEntity artifact;

    @Lob
    @Column(nullable = false)
    private String claim;

    @Column(nullable = false)
    private String source;

    protected EvidenceEntity() {
        // JPA
    }

    public EvidenceEntity(AnalysisArtifactEntity artifact, String claim, String source) {
        this.artifact = artifact;
        this.claim = claim;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getClaim() {
        return claim;
    }

    public String getSource() {
        return source;
    }
}
