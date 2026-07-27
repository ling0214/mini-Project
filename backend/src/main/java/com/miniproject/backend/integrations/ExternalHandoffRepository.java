package com.miniproject.backend.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalHandoffRepository extends JpaRepository<ExternalHandoffEntity, String> {

    List<ExternalHandoffEntity> findBySourceTaskIdOrderByCreatedAtDesc(String sourceTaskId);

    List<ExternalHandoffEntity> findBySourceTaskIdInAndStatusOrderByCreatedAtDesc(
            List<String> sourceTaskIds, String status);
}
