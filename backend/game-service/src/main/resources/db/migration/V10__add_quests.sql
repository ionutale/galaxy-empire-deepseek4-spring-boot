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

-- Achievements (one-time, cumulative count of completed actions)
INSERT INTO quest_definition (quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily) VALUES
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 5, 'First Steps', 'Complete your first building upgrade', 'building', 1, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 5, 'DARK_MATTER', 10, 'Apprentice Builder', 'Complete 5 building upgrades', 'building', 2, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 10, 'DARK_MATTER', 25, 'Master Builder', 'Complete 10 building upgrades', 'building', 3, FALSE),
('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 15, 'DARK_MATTER', 50, 'Grand Architect', 'Complete 15 building upgrades', 'building', 4, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 3, 'DARK_MATTER', 10, 'Scholar', 'Complete 3 research projects', 'research', 5, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 5, 'DARK_MATTER', 25, 'Researcher', 'Complete 5 research projects', 'research', 6, FALSE),
('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 8, 'DARK_MATTER', 50, 'Scientist', 'Complete 8 research projects', 'research', 7, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 1, 'DARK_MATTER', 10, 'First Blood', 'Win your first battle', 'combat', 8, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 10, 'DARK_MATTER', 50, 'Warrior', 'Win 10 battles', 'combat', 9, FALSE),
('ACHIEVEMENT', 'COMBAT', 'BATTLE_WON', 50, 'DARK_MATTER', 200, 'Warlord', 'Win 50 battles', 'combat', 10, FALSE);

-- Daily quests (reset each day via last_reset_date)
INSERT INTO quest_definition (quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily) VALUES
('DAILY', 'BUILDING', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 2, 'Daily Construction', 'Complete 1 building upgrade today', 'daily_build', 1, TRUE),
('DAILY', 'RESEARCH', 'RESEARCH_COMPLETED', 1, 'DARK_MATTER', 2, 'Daily Research', 'Complete 1 research today', 'daily_research', 2, TRUE),
('DAILY', 'COMBAT', 'BATTLE_WON', 1, 'DARK_MATTER', 3, 'Daily Combat', 'Win 1 battle today', 'daily_combat', 3, TRUE),
('DAILY', 'GENERAL', 'SHIPS_BUILT', 5, 'DARK_MATTER', 2, 'Daily Fleet', 'Build 5 ships today', 'daily_fleet', 4, TRUE);
