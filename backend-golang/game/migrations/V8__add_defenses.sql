CREATE TABLE planet_defense (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    defense_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE(planet_id, defense_type)
);
CREATE INDEX idx_planet_defense_planet ON planet_defense(planet_id);

ALTER TABLE shipyard_queue ADD COLUMN defense_type VARCHAR(32);
ALTER TABLE shipyard_queue ALTER COLUMN ship_type DROP NOT NULL;
