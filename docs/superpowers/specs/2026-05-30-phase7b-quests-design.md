# Phase 7b-b: Quests & Achievements

## Overview

Data-driven quest system with achievements (one-time milestones) and daily quests (recurring, reset at midnight UTC). Event-driven progress tracking — services emit quest events, QuestService updates progress. Frontend quest panel with progress bars and claimable rewards.

## Database

### Migration V10: New tables

**quest_definition** — data-driven quest catalog:
| Column | Type | Description |
|---|---|---|
| id | BIGSERIAL PK | |
| quest_type | VARCHAR | ACHIEVEMENT or DAILY |
| category | VARCHAR | BUILDING, RESEARCH, COMBAT, RESOURCE, GENERAL |
| requirement_type | VARCHAR | BUILD_X, RESEARCH_X, PRODUCE_X, WIN_BATTLES, COLLECT_RESOURCES, etc. |
| requirement_value | INT | Target count to complete |
| reward_type | VARCHAR | DARK_MATTER, METAL, CRYSTAL, GAS |
| reward_amount | INT | Reward quantity |
| title | VARCHAR | Display name |
| description | VARCHAR | Quest description |
| icon | VARCHAR | Icon identifier |
| sort_order | INT | Display order |
| daily | BOOLEAN | Is this a daily quest? |

Seed data: ~20 achievements, 5 daily quests.

**quest_progress** — per-player per-quest tracking:
| Column | Type | Description |
|---|---|---|
| id | BIGSERIAL PK | |
| player_id | BIGINT FK | |
| quest_definition_id | BIGINT FK | |
| progress | INT | Current value toward target |
| completed | BOOLEAN | |
| completed_at | TIMESTAMP | |
| claimed | BOOLEAN | Reward claimed? |
| last_reset_date | DATE | For daily quests — tracks which day this progress is for |

Unique constraint on (player_id, quest_definition_id, last_reset_date) for daily quests.

## Event System

QuestEvent record:
```java
public record QuestEvent(Long playerId, String actionType, String actionTarget, int value) {}
```

Emitted by services on relevant actions:
- **BuildingService.buildConstructionComplete**: `QuestEvent(playerId, "BUILDING_UPGRADED", buildingType.name(), newLevel)`
- **ShipyardService.shipBuildComplete**: `QuestEvent(playerId, "SHIPS_BUILT", shipType.name(), quantity)`
- **ResearchService.researchComplete**: `QuestEvent(playerId, "RESEARCH_COMPLETED", technology.name(), newLevel)`
- **CombatService.resolveCombat** (attacker win): `QuestEvent(attackerId, "BATTLE_WON", "", shipsDestroyed)`

QuestService has an `onQuestEvent(QuestEvent event)` method that:
1. Finds all quest definitions matching the action
2. For each, gets or creates QuestProgress for the player
3. Increments progress by the event value
4. Marks as completed if progress >= requirement_value

## Services

### QuestService
- `processQuestEvent(QuestEvent event)` — event handler
- `getAvailableQuests(Long playerId)` — returns achievements + today's dailies with progress
- `claimReward(Long playerId, Long progressId)` — validates completed + unclaimed, gives reward
- `resetDailyQuests()` — called by GameLoop at midnight UTC

### QuestController
- `GET /api/game/quests` — returns available quests
- `POST /api/game/quests/{progressId}/claim` — claims reward

### Integration
- BuildingService emits QuestEvent on construction completion
- ShipyardService emits QuestEvent on ship/defense build completion
- ResearchService emits QuestEvent on research completion
- CombatService emits QuestEvent on battle win
- GameLoopService calls QuestService.resetDailyQuests() at midnight

## Frontend

### QuestComponent
- Accessible via quest icon in nav bar
- Two tabs: "Achievements" (all-time) and "Dailies" (today)
- Each quest card shows: icon, title, description, progress bar (progress/target), reward
- "Claim" button appears when progress >= target
- Refreshes quest list after claiming
- Badge count on nav icon showing number of claimable quests

### API Data Shape
```typescript
interface QuestInfo {
  progressId: number;
  title: string;
  description: string;
  icon: string;
  questType: string;
  progress: number;
  target: number;
  rewardType: string;
  rewardAmount: number;
  completed: boolean;
  claimed: boolean;
}
```

## Testing
- QuestService: verify progress updates on events, completion detection, claiming
- Daily reset: verify dailies reinitialize at midnight
- Event emission: verify each service emits correct events
- Frontend: verify progress bars render, claim button works
