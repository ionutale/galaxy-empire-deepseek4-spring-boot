package com.galaxyempire.game.service;

import com.galaxyempire.game.domain.*;
import com.galaxyempire.game.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class QuestService {

    private final QuestDefinitionRepository questDefinitionRepository;
    private final QuestProgressRepository questProgressRepository;
    private final EconomyService economyService;
    private final DarkMatterService darkMatterService;
    private final PlanetService planetService;

    public QuestService(QuestDefinitionRepository questDefinitionRepository,
                        QuestProgressRepository questProgressRepository,
                        EconomyService economyService,
                        DarkMatterService darkMatterService,
                        PlanetService planetService) {
        this.questDefinitionRepository = questDefinitionRepository;
        this.questProgressRepository = questProgressRepository;
        this.economyService = economyService;
        this.darkMatterService = darkMatterService;
        this.planetService = planetService;
    }

    @Transactional
    public void processQuestEvent(QuestEvent event) {
        List<QuestDefinition> matching = questDefinitionRepository
            .findByRequirementType(event.requirementType());
        LocalDate today = LocalDate.now();

        for (QuestDefinition qd : matching) {
            LocalDate resetDate = qd.isDaily() ? today : null;
            QuestProgress qp = questProgressRepository
                .findByPlayerIdAndQuestDefinitionIdAndLastResetDate(
                    event.playerId(), qd.getId(), resetDate)
                .orElseGet(() -> {
                    QuestProgress newQp = new QuestProgress(event.playerId(), qd.getId(), resetDate);
                    return questProgressRepository.save(newQp);
                });

            if (qp.isCompleted() || qp.isClaimed()) continue;

            qp.setProgress(qp.getProgress() + event.value());

            if (qp.getProgress() >= qd.getRequirementValue()) {
                qp.setCompleted(true);
                qp.setCompletedAt(Instant.now());
            }

            questProgressRepository.save(qp);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableQuests(Long playerId) {
        LocalDate today = LocalDate.now();
        List<QuestDefinition> achievements = questDefinitionRepository.findByDailyOrderBySortOrder(false);
        List<QuestDefinition> dailies = questDefinitionRepository.findByDailyOrderBySortOrder(true);

        List<Map<String, Object>> result = new ArrayList<>();

        for (QuestDefinition qd : achievements) {
            Optional<QuestProgress> qp = questProgressRepository
                .findByPlayerIdAndQuestDefinitionIdAndLastResetDate(playerId, qd.getId(), null);
            if (qp.isPresent() && qp.get().isClaimed()) continue;
            result.add(buildQuestInfo(qd, qp.orElse(null)));
        }

        for (QuestDefinition qd : dailies) {
            QuestProgress qp = questProgressRepository
                .findByPlayerIdAndQuestDefinitionIdAndLastResetDate(playerId, qd.getId(), today)
                .orElse(null);
            result.add(buildQuestInfo(qd, qp));
        }

        return result;
    }

    private Map<String, Object> buildQuestInfo(QuestDefinition qd, QuestProgress qp) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("progressId", qp != null ? qp.getId() : null);
        info.put("questDefinitionId", qd.getId());
        info.put("title", qd.getTitle());
        info.put("description", qd.getDescription());
        info.put("icon", qd.getIcon());
        info.put("questType", qd.getQuestType());
        info.put("category", qd.getCategory());
        info.put("progress", qp != null ? qp.getProgress() : 0);
        info.put("target", qd.getRequirementValue());
        info.put("rewardType", qd.getRewardType());
        info.put("rewardAmount", qd.getRewardAmount());
        info.put("completed", qp != null && qp.isCompleted());
        info.put("claimed", qp != null && qp.isClaimed());
        return info;
    }

    @Transactional
    public Map<String, Object> claimReward(Long playerId, Long progressId) {
        QuestProgress qp = questProgressRepository.findById(progressId)
            .orElseThrow(() -> new IllegalArgumentException("Quest progress not found"));

        if (!qp.getPlayerId().equals(playerId)) {
            throw new IllegalArgumentException("Not your quest");
        }
        if (!qp.isCompleted()) {
            throw new IllegalArgumentException("Quest not completed");
        }
        if (qp.isClaimed()) {
            throw new IllegalArgumentException("Already claimed");
        }

        QuestDefinition qd = questDefinitionRepository.findById(qp.getQuestDefinitionId())
            .orElseThrow(() -> new IllegalArgumentException("Quest definition not found"));

        switch (qd.getRewardType()) {
            case "DARK_MATTER" -> darkMatterService.addDarkMatter(playerId, qd.getRewardAmount());
            case "METAL", "CRYSTAL", "GAS" -> {
                List<Map<String, Object>> planets = planetService.getPlanetsByPlayer(playerId);
                if (!planets.isEmpty()) {
                    Long planetId = ((Number) planets.get(0).get("id")).longValue();
                    double metal = qd.getRewardType().equals("METAL") ? qd.getRewardAmount() : 0;
                    double crystal = qd.getRewardType().equals("CRYSTAL") ? qd.getRewardAmount() : 0;
                    double gas = qd.getRewardType().equals("GAS") ? qd.getRewardAmount() : 0;
                    economyService.addResources(planetId, metal, crystal, gas);
                }
            }
        }

        qp.setClaimed(true);
        questProgressRepository.save(qp);

        return Map.of("success", true, "rewardType", qd.getRewardType(), "rewardAmount", qd.getRewardAmount());
    }

    @Transactional
    public void resetDailyQuests() {
    }
}
