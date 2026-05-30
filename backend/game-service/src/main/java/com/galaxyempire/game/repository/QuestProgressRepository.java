package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.QuestProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuestProgressRepository extends JpaRepository<QuestProgress, Long> {
    Optional<QuestProgress> findByPlayerIdAndQuestDefinitionIdAndLastResetDate(
        Long playerId, Long questDefinitionId, LocalDate lastResetDate);

    List<QuestProgress> findByPlayerIdAndLastResetDate(Long playerId, LocalDate lastResetDate);

    List<QuestProgress> findByPlayerIdAndCompletedAndClaimed(
        Long playerId, boolean completed, boolean claimed);
}
