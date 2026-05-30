package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.QuestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestDefinitionRepository extends JpaRepository<QuestDefinition, Long> {
    List<QuestDefinition> findByDailyOrderBySortOrder(boolean daily);
    List<QuestDefinition> findByRequirementType(String requirementType);
}
