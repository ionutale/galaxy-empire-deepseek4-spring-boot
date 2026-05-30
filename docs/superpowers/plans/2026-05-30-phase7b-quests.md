# Phase 7b-b: Quests & Achievements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task.

**Goal:** Data-driven quest system with achievements (one-time) and daily quests (recurring, midnight reset). Event-driven progress tracking. Frontend quest panel.

**Architecture:** Two new tables (quest_definition, quest_progress). QuestEvent record emitted by services. QuestService processes events, tracks progress, handles claiming. Frontend quest panel with progress bars.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 16, Angular 19, Flyway V10

---

## File Structure

### Backend — Create:
- `backend/game-service/src/main/resources/db/migration/V10__add_quests.sql`
- `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestDefinition.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestProgress.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestEvent.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/repository/QuestDefinitionRepository.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/repository/QuestProgressRepository.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/service/QuestService.java`
- `backend/game-service/src/main/java/com/galaxyempire/game/web/QuestController.java`

### Backend — Modify:
- `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java` (emit events)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java` (emit events)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java` (emit events)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/CombatService.java` (emit events)
- `backend/game-service/src/main/java/com/galaxyempire/game/service/GameLoopService.java` (midnight reset)

### Frontend — Create:
- `frontend/src/app/quest/quest.component.ts`

### Frontend — Modify:
- `frontend/src/app/core/models/models.ts` (QuestInfo)
- `frontend/src/app/core/services/game.service.ts` (quest methods)
- `frontend/src/app/app.component.ts` (quest nav link)

---

### Task 1: Migration V10 + QuestDefinition entity + repository

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V10__add_quests.sql`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestDefinition.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/QuestDefinitionRepository.java`

- [ ] **Create V10 migration**

```sql
CREATE TABLE quest_definition (
    id BIGSERIAL PRIMARY KEY,
    quest_type VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    requirement_type VARCHAR(40) NOT NULL,
    requirement_value INT NOT NULL,
    reward_type VARCHAR(20) NOT NULL,
    reward_amount INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    icon VARCHAR(40),
    sort_order INT NOT NULL DEFAULT 0,
    daily BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE quest_progress (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    quest_definition_id BIGINT NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    last_reset_date DATE,
    FOREIGN KEY (quest_definition_id) REFERENCES quest_definition(id),
    UNIQUE (player_id, quest_definition_id, last_reset_date)
);
```

- [ ] **Create QuestDefinition entity**

```java
package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "quest_definition")
public class QuestDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quest_type", nullable = false, length = 20)
    private String questType;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "requirement_type", nullable = false, length = 40)
    private String requirementType;

    @Column(name = "requirement_value", nullable = false)
    private int requirementValue;

    @Column(name = "reward_type", nullable = false, length = 20)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private int rewardAmount;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String description;

    @Column(length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean daily;

    public QuestDefinition() {}

    // Getters
    public Long getId() { return id; }
    public String getQuestType() { return questType; }
    public String getCategory() { return category; }
    public String getRequirementType() { return requirementType; }
    public int getRequirementValue() { return requirementValue; }
    public String getRewardType() { return rewardType; }
    public int getRewardAmount() { return rewardAmount; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public boolean isDaily() { return daily; }
}
```

- [ ] **Create QuestDefinitionRepository**

```java
package com.galaxyempire.game.repository;

import com.galaxyempire.game.domain.QuestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestDefinitionRepository extends JpaRepository<QuestDefinition, Long> {
    List<QuestDefinition> findByDailyOrderBySortOrder(boolean daily);
    List<QuestDefinition> findByRequirementType(String requirementType);
}
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 2: QuestProgress entity + repository

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestProgress.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/repository/QuestProgressRepository.java`

- [ ] **Create QuestProgress entity**

```java
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

    // Getters and setters
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
```

- [ ] **Create QuestProgressRepository**

```java
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
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 3: QuestEvent record + QuestService

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/domain/QuestEvent.java`
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/service/QuestService.java`

- [ ] **Create QuestEvent record**

```java
package com.galaxyempire.game.domain;

public record QuestEvent(Long playerId, String requirementType, String target, int value) {}
```

- [ ] **Create QuestService**

```java
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

    public QuestService(QuestDefinitionRepository questDefinitionRepository,
                        QuestProgressRepository questProgressRepository,
                        EconomyService economyService,
                        DarkMatterService darkMatterService) {
        this.questDefinitionRepository = questDefinitionRepository;
        this.questProgressRepository = questProgressRepository;
        this.economyService = economyService;
        this.darkMatterService = darkMatterService;
    }

    @Transactional
    public void processQuestEvent(QuestEvent event) {
        List<QuestDefinition> matching = questDefinitionRepository
            .findByRequirementType(event.requirementType());
        LocalDate today = LocalDate.now();

        for (QuestDefinition qd : matching) {
            QuestProgress qp = questProgressRepository
                .findByPlayerIdAndQuestDefinitionIdAndLastResetDate(
                    event.playerId(), qd.getId(), qd.isDaily() ? today : null)
                .orElseGet(() -> {
                    QuestProgress newQp = new QuestProgress(event.playerId(), qd.getId(), qd.isDaily() ? today : null);
                    return questProgressRepository.save(newQp);
                });

            if (qp.isCompleted() || qp.isClaimed()) continue;

            int increment = event.value();
            qp.setProgress(qp.getProgress() + increment);

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
            case "METAL" -> economyService.addResources(playerId, qd.getRewardAmount(), 0, 0);
            case "CRYSTAL" -> economyService.addResources(playerId, 0, qd.getRewardAmount(), 0);
            case "GAS" -> economyService.addResources(playerId, 0, 0, qd.getRewardAmount());
        }

        qp.setClaimed(true);
        questProgressRepository.save(qp);

        return Map.of("success", true, "rewardType", qd.getRewardType(), "rewardAmount", qd.getRewardAmount());
    }

    @Transactional
    public void resetDailyQuests() {
        // Daily quests are created on-demand when processQuestEvent or getAvailableQuests
        // runs with the new date. No explicit reset needed — last_reset_date mismatch
        // triggers new progress creation.
    }
}
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 4: QuestController

**Files:**
- Create: `backend/game-service/src/main/java/com/galaxyempire/game/web/QuestController.java`

- [ ] **Create QuestController**

```java
package com.galaxyempire.game.web;

import com.galaxyempire.game.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class QuestController {

    private final QuestService questService;

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping("/quests")
    public ResponseEntity<?> getQuests(@RequestHeader("X-Player-Id") Long playerId) {
        return ResponseEntity.ok(questService.getAvailableQuests(playerId));
    }

    @PostMapping("/quests/{progressId}/claim")
    public ResponseEntity<?> claimReward(@PathVariable Long progressId,
                                          @RequestHeader("X-Player-Id") Long playerId) {
        try {
            return ResponseEntity.ok(questService.claimReward(playerId, progressId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
```

- [ ] **Verify compiles**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`

---

### Task 5: Emit quest events from services

**Files:**
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/BuildingService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ShipyardService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/ResearchService.java`
- Modify: `backend/game-service/src/main/java/com/galaxyempire/game/service/CombatService.java`

For each service:
1. Inject `QuestService questService` (or use `ApplicationEventPublisher` if the service prefers — QuestService directly is simpler)
2. After the action completes, emit a QuestEvent:

**BuildingService.completeConstruction()** — after building.setLevel and queue.setCompleted:
```java
        questService.processQuestEvent(new QuestEvent(
            building.getPlayerId(), "BUILDING_UPGRADED",
            queue.getBuildingType().name(), queue.getTargetLevel()));
```

**ShipyardService.completeShipyardEntry()** — after the ship/defense build is added to planet:
```java
        questService.processQuestEvent(new QuestEvent(
            shipyardQueue.getPlanetId() != null ? planetRepository.findById(shipyardQueue.getPlanetId()).map(Planet::getPlayerId).orElse(null) : null,
            "SHIPS_BUILT",
            shipyardQueue.getShipType() != null ? shipyardQueue.getShipType().name() : "DEFENSE",
            shipyardQueue.getQuantity()));
```

**ResearchService.completeResearch()** — after research is completed:
```java
        questService.processQuestEvent(new QuestEvent(
            queue.getPlayerId(), "RESEARCH_COMPLETED",
            queue.getTechnology().name(), queue.getTargetLevel()));
```

**CombatService** — in the resolveCombat method, after determining winner. If attacker wins:
```java
        questService.processQuestEvent(new QuestEvent(
            attackerPlayerId, "BATTLE_WON", "", totalDefendersDestroyed));
```

Add the necessary imports: `com.galaxyempire.game.domain.QuestEvent`, `com.galaxyempire.game.service.QuestService`.

- [ ] **Verify compiles + tests pass**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am compile -DskipTests 2>&1 | tail -5`
Then: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am test -DskipTests=false 2>&1 | tail -10`

---

### Task 6: Seed data + final backend verification

**Files:**
- Create: `backend/game-service/src/main/resources/db/migration/V10__add_quests.sql` (add seed data after CREATE TABLE)

- [ ] **Add seed INSERTs to V10 migration**

After the CREATE TABLE statements, add:

```sql
-- Achievements (one-time)
INSERT INTO quest_definition (quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily) VALUES
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 5, 'First Steps', 'Upgrade a building to level 2', 'building', 1, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 5, 'DARK_MATTER', 10, 'Apprentice Builder', 'Upgrade a building to level 5', 'building', 2, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 10, 'DARK_MATTER', 25, 'Master Builder', 'Upgrade a building to level 10', 'building', 3, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 15, 'DARK_MATTER', 50, 'Grand Architect', 'Upgrade a building to level 15', 'building', 4, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 3, 'DARK_MATTER', 10, 'Scholar', 'Research a technology to level 3', 'research', 5, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 5, 'DARK_MATTER', 25, 'Researcher', 'Research a technology to level 5', 'research', 6, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 8, 'DARK_MATTER', 50, 'Scientist', 'Research a technology to level 8', 'research', 7, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 1, 'DARK_MATTER', 10, 'First Blood', 'Win your first battle', 'combat', 8, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 10, 'DARK_MATTER', 50, 'Warrior', 'Win 10 battles', 'combat', 9, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 50, 'DARK_MATTER', 200, 'Warlord', 'Win 50 battles', 'combat', 10, FALSE),
('ACHIEVEMENT', 'GENERAL', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 5, 'Settler', 'Build a colony', 'colony', 11, FALSE);

-- Daily quests
INSERT INTO quest_definition (quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily) VALUES
('DAILY', 'BUILDING', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 2, 'Daily Construction', 'Upgrade 1 building today', 'daily_build', 1, TRUE),
('DAILY', 'RESEARCH', 'RESEARCH_COMPLETED', 1, 'DARK_MATTER', 2, 'Daily Research', 'Complete 1 research today', 'daily_research', 2, TRUE),
('DAILY', 'COMBAT', 'BATTLE_WON', 1, 'DARK_MATTER', 3, 'Daily Combat', 'Win 1 battle today', 'daily_combat', 3, TRUE),
('DAILY', 'GENERAL', 'SHIPS_BUILT', 5, 'DARK_MATTER', 2, 'Daily Fleet', 'Build 5 ships today', 'daily_fleet', 4, TRUE);

-- Note: "First Steps" and "Settler" achievements have same requirement
-- In practice, BATTLE_WON and BUILDING_UPGRADED of value 1 are catch-alls
-- The quest system uses requirement_type + requirement_value to match events
```

- [ ] **Full backend build + tests**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am clean verify -DskipTests=false 2>&1 | tail -10`
Expected: BUILD SUCCESS

---

### Task 7: Frontend models + service methods

**Files:**
- Modify: `frontend/src/app/core/models/models.ts`
- Modify: `frontend/src/app/core/services/game.service.ts`

- [ ] **Add QuestInfo interface**

In `models.ts`:
```typescript
export interface QuestInfo {
  progressId: number | null;
  questDefinitionId: number;
  title: string;
  description: string;
  icon: string;
  questType: string;
  category: string;
  progress: number;
  target: number;
  rewardType: string;
  rewardAmount: number;
  completed: boolean;
  claimed: boolean;
}
```

- [ ] **Add quest methods to GameService**

```typescript
  getQuests() {
    return this.http.get<QuestInfo[]>(`${environment.apiUrl}/game/quests`);
  }

  claimQuestReward(progressId: number) {
    return this.http.post(`${environment.apiUrl}/game/quests/${progressId}/claim`, {});
  }
```

Update import to include `QuestInfo`.

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`

---

### Task 8: Frontend QuestComponent

**Files:**
- Create: `frontend/src/app/quest/quest.component.ts`

- [ ] **Create QuestComponent**

```typescript
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GameService } from '../core/services/game.service';
import { QuestInfo } from '../core/models/models';

@Component({
  selector: 'app-quest',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="quest-view">
      <h2>Quests</h2>

      <div class="tab-bar">
        <button [class.active]="tab === 'achievements'" (click)="tab = 'achievements'">Achievements</button>
        <button [class.active]="tab === 'dailies'" (click)="tab = 'dailies'">Daily Quests</button>
      </div>

      <div class="quest-list">
        <div *ngFor="let q of filteredQuests()" class="quest-card"
             [class.completed]="q.completed"
             [class.claimed]="q.claimed">
          <div class="quest-icon">{{ getIcon(q.icon) }}</div>
          <div class="quest-body">
            <div class="quest-title">{{ q.title }}</div>
            <div class="quest-desc">{{ q.description }}</div>
            <div class="progress-bar">
              <div class="progress-fill" [style.width.%]="pct(q)"></div>
            </div>
            <div class="quest-progress">{{ q.progress }} / {{ q.target }}</div>
          </div>
          <div class="quest-reward">
            <div class="reward-icon">{{ q.rewardType === 'DARK_MATTER' ? '&#9670;' : '&#9679;' }}</div>
            <div class="reward-amount">+{{ q.rewardAmount }}</div>
            <button *ngIf="q.completed && !q.claimed" class="claim-btn" (click)="claim(q.progressId!)">Claim</button>
            <span *ngIf="q.claimed" class="claimed-label">Done</span>
          </div>
        </div>
        <div *ngIf="filteredQuests().length === 0" class="empty">No quests available.</div>
      </div>
    </div>
  `,
  styles: [`
    .quest-view { padding: 20px; color: #ccc; max-width: 700px; margin: 0 auto; }
    h2 { color: #ffd700; margin: 0 0 12px 0; }
    .tab-bar { display: flex; gap: 4px; margin-bottom: 16px; }
    .tab-bar button { padding: 6px 16px; background: #1a1a2e; color: #888; border: 1px solid #333; border-radius: 4px 4px 0 0; cursor: pointer; }
    .tab-bar button.active { color: #ffd700; background: #222; border-bottom: 2px solid #ffd700; }
    .quest-list { display: flex; flex-direction: column; gap: 8px; }
    .quest-card { display: flex; gap: 12px; background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 12px; align-items: center; }
    .quest-card.completed { border-color: #4a9; }
    .quest-card.claimed { opacity: 0.5; }
    .quest-icon { font-size: 24px; width: 40px; text-align: center; }
    .quest-body { flex: 1; }
    .quest-title { color: #fff; font-weight: bold; font-size: 14px; }
    .quest-desc { color: #888; font-size: 12px; margin: 2px 0 6px; }
    .progress-bar { height: 6px; background: #333; border-radius: 3px; overflow: hidden; }
    .progress-fill { height: 100%; background: #4af; border-radius: 3px; transition: width 0.3s; }
    .quest-progress { color: #888; font-size: 11px; margin-top: 2px; }
    .quest-reward { text-align: center; min-width: 60px; }
    .reward-icon { font-size: 18px; color: #a855f7; }
    .reward-amount { color: #ffd700; font-size: 13px; font-weight: bold; }
    .claim-btn { margin-top: 4px; padding: 4px 12px; background: #7c3aed; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 11px; }
    .claim-btn:hover { background: #6d28d9; }
    .claimed-label { color: #4a9; font-size: 11px; }
    .empty { color: #666; text-align: center; padding: 20px; }
  `]
})
export class QuestComponent implements OnInit {
  tab: 'achievements' | 'dailies' = 'achievements';
  quests: QuestInfo[] = [];

  constructor(private game: GameService) {}

  ngOnInit() {
    this.loadQuests();
  }

  loadQuests() {
    this.game.getQuests().subscribe(data => {
      this.quests = data;
    });
  }

  filteredQuests() {
    return this.quests.filter(q => this.tab === 'dailies' ? q.questType === 'DAILY' : q.questType === 'ACHIEVEMENT');
  }

  pct(q: QuestInfo): number {
    return Math.min(100, (q.progress / q.target) * 100);
  }

  getIcon(icon: string): string {
    const icons: Record<string, string> = {
      'building': '🏗️', 'research': '🔬', 'combat': '⚔️', 'colony': '🚀',
      'daily_build': '🏗️', 'daily_research': '🔬', 'daily_combat': '⚔️', 'daily_fleet': '🚀'
    };
    return icons[icon] || '📋';
  }

  claim(progressId: number) {
    this.game.claimQuestReward(progressId).subscribe(() => {
      this.loadQuests();
    });
  }
}
```

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`

---

### Task 9: Frontend nav integration

**Files:**
- Modify: `frontend/src/app/app.component.ts`

- [ ] **Add quest route + nav link**

Read `/Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend/src/app/app.routes.ts`. Add before the catch-all route:
```typescript
  { path: 'quests', loadComponent: () => import('./quest/quest.component').then(m => m.QuestComponent), canActivate: [AuthGuard] },
```

In `app.component.ts`, add a nav link after Galaxy:
```html
        <a routerLink="/quests" routerLinkActive="active">Quests</a>
```

- [ ] **Verify frontend builds**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`

---

### Task 10: Final verification

- [ ] **Full backend build + tests**

Run: `docker run --rm -v /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot:/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -pl backend/game-service -am clean verify -DskipTests=false 2>&1 | tail -20`

- [ ] **Full frontend build**

Run: `cd /Users/ionutale/developer-playground/galaxy-empire-deepseek4-spring-boot/frontend && npx ng build --configuration production 2>&1 | tail -10`

- [ ] **Report results**
