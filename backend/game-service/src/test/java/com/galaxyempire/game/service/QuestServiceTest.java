package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.QuestDefinition;
import com.galaxyempire.game.domain.QuestEvent;
import com.galaxyempire.game.domain.QuestProgress;
import com.galaxyempire.game.repository.QuestDefinitionRepository;
import com.galaxyempire.game.repository.QuestProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock private QuestDefinitionRepository questDefinitionRepository;
    @Mock private QuestProgressRepository questProgressRepository;
    @Mock private EconomyService economyService;
    @Mock private DarkMatterService darkMatterService;
    @Mock private PlanetService planetService;

    @InjectMocks private QuestService service;

    private QuestDefinition achievement(long id, int requirement) {
        QuestDefinition qd = mock(QuestDefinition.class);
        lenient().when(qd.getId()).thenReturn(id);
        lenient().when(qd.isDaily()).thenReturn(false);
        lenient().when(qd.getRequirementValue()).thenReturn(requirement);
        return qd;
    }

    // --- processQuestEvent ---

    @Test
    void progressAccumulatesWithoutCompletingBelowRequirement() {
        QuestDefinition qd = achievement(1L, 5);
        when(questDefinitionRepository.findByRequirementType("BUILDING_UPGRADED")).thenReturn(List.of(qd));
        when(questProgressRepository.findByPlayerIdAndQuestDefinitionIdAndLastResetDate(eq(7L), eq(1L), isNull()))
                .thenReturn(Optional.empty());
        when(questProgressRepository.save(any(QuestProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processQuestEvent(new QuestEvent(7L, "BUILDING_UPGRADED", "METAL_MINE", 2));

        verify(questProgressRepository, atLeastOnce()).save(argThat(qp ->
                qp.getProgress() == 2 && !qp.isCompleted()));
    }

    @Test
    void reachingRequirementMarksQuestCompleted() {
        QuestDefinition qd = achievement(1L, 1);
        QuestProgress existing = new QuestProgress(7L, 1L, null);
        when(questDefinitionRepository.findByRequirementType("BATTLE_WON")).thenReturn(List.of(qd));
        when(questProgressRepository.findByPlayerIdAndQuestDefinitionIdAndLastResetDate(eq(7L), eq(1L), isNull()))
                .thenReturn(Optional.of(existing));
        when(questProgressRepository.save(any(QuestProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processQuestEvent(new QuestEvent(7L, "BATTLE_WON", "", 1));

        assertThat(existing.isCompleted()).isTrue();
        assertThat(existing.getCompletedAt()).isNotNull();
    }

    @Test
    void alreadyClaimedQuestIsNotAdvanced() {
        QuestDefinition qd = achievement(1L, 5);
        QuestProgress claimed = new QuestProgress(7L, 1L, null);
        claimed.setProgress(5);
        claimed.setCompleted(true);
        claimed.setClaimed(true);
        when(questDefinitionRepository.findByRequirementType("BUILDING_UPGRADED")).thenReturn(List.of(qd));
        when(questProgressRepository.findByPlayerIdAndQuestDefinitionIdAndLastResetDate(eq(7L), eq(1L), isNull()))
                .thenReturn(Optional.of(claimed));

        service.processQuestEvent(new QuestEvent(7L, "BUILDING_UPGRADED", "METAL_MINE", 3));

        assertThat(claimed.getProgress()).isEqualTo(5); // untouched
        verify(questProgressRepository, never()).save(any());
    }

    @Test
    void dailyQuestProgressIsKeyedByToday() {
        QuestDefinition daily = mock(QuestDefinition.class);
        when(daily.getId()).thenReturn(2L);
        when(daily.isDaily()).thenReturn(true);
        when(daily.getRequirementValue()).thenReturn(1);
        when(questDefinitionRepository.findByRequirementType("RESEARCH_COMPLETED")).thenReturn(List.of(daily));
        when(questProgressRepository.findByPlayerIdAndQuestDefinitionIdAndLastResetDate(eq(7L), eq(2L), eq(LocalDate.now())))
                .thenReturn(Optional.empty());
        when(questProgressRepository.save(any(QuestProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processQuestEvent(new QuestEvent(7L, "RESEARCH_COMPLETED", "ENERGY_TECH", 1));

        verify(questProgressRepository, atLeastOnce()).save(argThat(qp ->
                LocalDate.now().equals(qp.getLastResetDate()) && qp.isCompleted()));
    }

    // --- claimReward ---

    @Test
    void claimRewardRejectsUnknownProgress() {
        when(questProgressRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.claimReward(7L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void claimRewardRejectsOtherPlayersQuest() {
        QuestProgress qp = new QuestProgress(8L, 1L, null);
        qp.setCompleted(true);
        when(questProgressRepository.findById(1L)).thenReturn(Optional.of(qp));
        assertThatThrownBy(() -> service.claimReward(7L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not your quest");
    }

    @Test
    void claimRewardRejectsIncompleteQuest() {
        QuestProgress qp = new QuestProgress(7L, 1L, null);
        when(questProgressRepository.findById(1L)).thenReturn(Optional.of(qp));
        assertThatThrownBy(() -> service.claimReward(7L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not completed");
    }

    @Test
    void claimRewardRejectsAlreadyClaimed() {
        QuestProgress qp = new QuestProgress(7L, 1L, null);
        qp.setCompleted(true);
        qp.setClaimed(true);
        when(questProgressRepository.findById(1L)).thenReturn(Optional.of(qp));
        assertThatThrownBy(() -> service.claimReward(7L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Already claimed");
    }

    @Test
    void claimRewardGrantsDarkMatterAndMarksClaimed() {
        QuestProgress qp = new QuestProgress(7L, 1L, null);
        qp.setCompleted(true);
        QuestDefinition qd = mock(QuestDefinition.class);
        when(qd.getRewardType()).thenReturn("DARK_MATTER");
        when(qd.getRewardAmount()).thenReturn(25);
        when(questProgressRepository.findById(1L)).thenReturn(Optional.of(qp));
        when(questDefinitionRepository.findById(1L)).thenReturn(Optional.of(qd));
        when(questProgressRepository.save(any(QuestProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.claimReward(7L, 1L);

        verify(darkMatterService).addDarkMatter(7L, 25);
        assertThat(qp.isClaimed()).isTrue();
        assertThat(result).containsEntry("success", true).containsEntry("rewardAmount", 25);
    }
}
