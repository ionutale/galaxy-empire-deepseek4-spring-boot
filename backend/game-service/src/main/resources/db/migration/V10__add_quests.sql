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
