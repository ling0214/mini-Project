package com.miniproject.backend.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectWorkspaceRepository extends JpaRepository<ProjectWorkspaceEntity, String> {

    Optional<ProjectWorkspaceEntity> findByActiveTrue();

    List<ProjectWorkspaceEntity> findAllByOrderByLastActivatedAtDesc();
}
