package com.miniproject.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisArtifactRepository extends JpaRepository<AnalysisArtifactEntity, String> {

    List<AnalysisArtifactEntity> findAllByOrderByCreatedAtDesc();

    List<AnalysisArtifactEntity> findByParentTaskId(String parentTaskId);
}
