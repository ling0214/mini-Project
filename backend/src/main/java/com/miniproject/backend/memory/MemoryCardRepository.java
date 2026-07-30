package com.miniproject.backend.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCardRepository extends JpaRepository<MemoryCardEntity, String> {

    List<MemoryCardEntity> findBySkill(String skill);
}
