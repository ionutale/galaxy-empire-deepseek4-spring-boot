CREATE TABLE fleet (
    id BIGSERIAL PRIMARY KEY,
    origin_planet_id BIGINT NOT NULL REFERENCES planet(id),
    target_planet_id BIGINT NOT NULL REFERENCES planet(id),
    player_id BIGINT NOT NULL,
    mission VARCHAR(16) NOT NULL,
    departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    return_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(16) NOT NULL DEFAULT 'EN_ROUTE',
    metal_loot DOUBLE PRECISION NOT NULL DEFAULT 0,
    crystal_loot DOUBLE PRECISION NOT NULL DEFAULT 0,
    gas_loot DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE fleet_ship (
    id BIGSERIAL PRIMARY KEY,
    fleet_id BIGINT NOT NULL REFERENCES fleet(id),
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    UNIQUE (fleet_id, ship_type)
);

CREATE TABLE combat_report (
    id BIGSERIAL PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    attacker_planet_id BIGINT NOT NULL,
    defender_planet_id BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    result VARCHAR(16) NOT NULL,
    attacker_ships_before JSONB NOT NULL DEFAULT '{}',
    defender_ships_before JSONB NOT NULL DEFAULT '{}',
    attacker_ships_lost JSONB NOT NULL DEFAULT '{}',
    defender_ships_lost JSONB NOT NULL DEFAULT '{}',
    debris_metal DOUBLE PRECISION NOT NULL DEFAULT 0,
    debris_crystal DOUBLE PRECISION NOT NULL DEFAULT 0,
    resources_looted JSONB NOT NULL DEFAULT '{}',
    rounds JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE debris_field (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL UNIQUE REFERENCES planet(id),
    metal DOUBLE PRECISION NOT NULL DEFAULT 0,
    crystal DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE INDEX idx_fleet_origin_planet ON fleet(origin_planet_id);
CREATE INDEX idx_fleet_target_planet ON fleet(target_planet_id);
CREATE INDEX idx_fleet_player ON fleet(player_id);
CREATE INDEX idx_fleet_status_arrival ON fleet(status, arrival_time);
CREATE INDEX idx_fleet_status_return ON fleet(status, return_time);
CREATE INDEX idx_fleet_ship_fleet ON fleet_ship(fleet_id);
CREATE INDEX idx_combat_report_attacker_planet ON combat_report(attacker_planet_id);
CREATE INDEX idx_combat_report_defender_planet ON combat_report(defender_planet_id);
CREATE INDEX idx_combat_report_timestamp ON combat_report(timestamp);
CREATE INDEX idx_debris_field_planet ON debris_field(planet_id);
