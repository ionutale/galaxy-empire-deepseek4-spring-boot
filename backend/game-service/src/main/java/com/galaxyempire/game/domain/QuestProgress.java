package com.galaxyempire.game.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "quest_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "quest_definition_id", "last_reset_date"}))
public class QuestProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "quest_definition_id", nullable = false)
    private Long questDefinitionId;

    @Column(nullable = false)
    private int progress = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private boolean claimed = false;

    @Column(name = "last_reset_date")
    private LocalDate lastResetDate;

    public QuestProgress() {}

    public QuestProgress(Long playerId, Long questDefinitionId, LocalDate lastResetDate) {
        this.playerId = playerId;
        this.questDefinitionId = questDefinitionId;
        this.lastResetDate = lastResetDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Long getQuestDefinitionId() { return questDefinitionId; }
    public void setQuestDefinitionId(Long questDefinitionId) { this.questDefinitionId = questDefinitionId; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }
}
