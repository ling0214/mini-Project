package com.miniproject.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillApprovalRepository extends JpaRepository<SkillApprovalEntity, String> {

    List<SkillApprovalEntity> findByApprovalStateOrderBySubmittedAtDesc(SkillApprovalEntity.ApprovalState state);

    List<SkillApprovalEntity> findBySkillAssetIdOrderBySubmittedAtDesc(String skillAssetId);
}
