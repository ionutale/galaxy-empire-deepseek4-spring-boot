CREATE TABLE building (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL REFERENCES planet(id),
    building_type VARCHAR(32) NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,
    grid_position SMALLINT NOT NULL,
    UNIQUE(planet_id, grid_position)
);

CREATE INDEX idx_building_planet ON building(planet_id);
