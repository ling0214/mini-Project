package com.miniproject.backend.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HermesSetupProfileRepository extends JpaRepository<HermesSetupProfileEntity, String> {

    List<HermesSetupProfileEntity> findAllByOrderByUpdatedAtDesc();
}
