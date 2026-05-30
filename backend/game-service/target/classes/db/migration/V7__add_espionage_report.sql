CREATE TABLE espionage_report (
    id BIGSERIAL PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    target_planet_id BIGINT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resources_json JSONB NOT NULL DEFAULT '{}',
    ships_json JSONB NOT NULL DEFAULT '{}',
    buildings_json JSONB NOT NULL DEFAULT '{}',
    technologies_json JSONB NOT NULL DEFAULT '{}',
    defenses_json JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_espionage_report_target_planet ON espionage_report(target_planet_id);
CREATE INDEX idx_espionage_report_defender ON espionage_report(defender_id);
