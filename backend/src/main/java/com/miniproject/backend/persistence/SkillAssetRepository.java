package com.miniproject.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SkillAssetRepository extends JpaRepository<SkillAssetEntity, String> {

    List<SkillAssetEntity> findByStatus(SkillAssetEntity.SkillAssetStatus status);

    List<SkillAssetEntity> findByStatusAndShareScope(
            SkillAssetEntity.SkillAssetStatus status,
            SkillAssetEntity.ShareScope shareScope);

    Optional<SkillAssetEntity> findBySkillNameAndVersion(String skillName, String version);

    List<SkillAssetEntity> findBySkillNameOrderByCreatedAtDesc(String skillName);
}
