package com.miniproject.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SkillScanRepository extends JpaRepository<SkillScanEntity, String> {

    List<SkillScanEntity> findBySkillAssetIdOrderByScannedAtDesc(String skillAssetId);

    Optional<SkillScanEntity> findFirstBySkillAssetIdOrderByScannedAtDesc(String skillAssetId);

    List<SkillScanEntity> findByStatus(SkillScanEntity.ScanStatus status);
}
