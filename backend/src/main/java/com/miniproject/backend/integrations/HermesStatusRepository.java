package com.miniproject.backend.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HermesStatusRepository extends JpaRepository<HermesStatusEntity, String> {

    List<HermesStatusEntity> findBySourceTaskIdOrderByCreateDateDesc(String sourceTaskId);

    Optional<HermesStatusEntity> findBySourceTaskIdAndDeleteDateIsNull(String sourceTaskId);

    List<HermesStatusEntity> findByDeleteDateIsNullOrderByCreateDateDesc();
}
