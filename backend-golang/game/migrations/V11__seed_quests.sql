-- Seed quest definitions. Idempotent: only seeds when the table is empty, so it
-- is safe whether or not an earlier (seedless) V10 was already applied, and it
-- will not duplicate rows if re-run.
INSERT INTO quest_definition
    (quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily)
SELECT v.quest_type, v.category, v.requirement_type, v.requirement_value, v.reward_type, v.reward_amount, v.title, v.description, v.icon, v.sort_order, v.daily
FROM (VALUES
    -- Achievements (one-time, cumulative count of completed actions)
    ('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 1,  'DARK_MATTER', 5,   'First Steps',        'Complete your first building upgrade', 'building',        1,  FALSE),
    ('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 5,  'DARK_MATTER', 10,  'Apprentice Builder', 'Complete 5 building upgrades',          'building',        2,  FALSE),
    ('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 10, 'DARK_MATTER', 25,  'Master Builder',     'Complete 10 building upgrades',         'building',        3,  FALSE),
    ('ACHIEVEMENT', 'BUILDING', 'BUILDING_UPGRADED', 15, 'DARK_MATTER', 50,  'Grand Architect',    'Complete 15 building upgrades',         'building',        4,  FALSE),
    ('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 3, 'DARK_MATTER', 10,  'Scholar',            'Complete 3 research projects',         'research',        5,  FALSE),
    ('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 5, 'DARK_MATTER', 25,  'Researcher',         'Complete 5 research projects',         'research',        6,  FALSE),
    ('ACHIEVEMENT', 'RESEARCH', 'RESEARCH_COMPLETED', 8, 'DARK_MATTER', 50,  'Scientist',          'Complete 8 research projects',         'research',        7,  FALSE),
    ('ACHIEVEMENT', 'COMBAT',   'BATTLE_WON', 1,  'DARK_MATTER', 10,  'First Blood',        'Win your first battle',                'combat',          8,  FALSE),
    ('ACHIEVEMENT', 'COMBAT',   'BATTLE_WON', 10, 'DARK_MATTER', 50,  'Warrior',            'Win 10 battles',                       'combat',          9,  FALSE),
    ('ACHIEVEMENT', 'COMBAT',   'BATTLE_WON', 50, 'DARK_MATTER', 200, 'Warlord',            'Win 50 battles',                       'combat',          10, FALSE),
    -- Daily quests (reset each day via last_reset_date)
    ('DAILY', 'BUILDING', 'BUILDING_UPGRADED', 1, 'DARK_MATTER', 2, 'Daily Construction', 'Complete 1 building upgrade today', 'daily_build',    1, TRUE),
    ('DAILY', 'RESEARCH', 'RESEARCH_COMPLETED', 1, 'DARK_MATTER', 2, 'Daily Research',     'Complete 1 research today',        'daily_research', 2, TRUE),
    ('DAILY', 'COMBAT',   'BATTLE_WON', 1, 'DARK_MATTER', 3, 'Daily Combat',       'Win 1 battle today',               'daily_combat',   3, TRUE),
    ('DAILY', 'GENERAL',  'SHIPS_BUILT', 5, 'DARK_MATTER', 2, 'Daily Fleet',        'Build 5 ships today',              'daily_fleet',    4, TRUE)
) AS v(quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily)
WHERE NOT EXISTS (SELECT 1 FROM quest_definition);
