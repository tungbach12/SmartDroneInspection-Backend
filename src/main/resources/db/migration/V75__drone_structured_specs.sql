-- Split structured technical specifications out of the free-text `model` column
-- on `drone_registration_applications` and `drones`, and backfill legacy rows that
-- stored `[MTOW: ... | Thời gian bay: ... | Camera: ...]` inside `model`.

ALTER TABLE drone_registration_applications
    ADD COLUMN IF NOT EXISTS mtow_kg NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS max_flight_time_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS camera_sensor VARCHAR(128);

ALTER TABLE drone_registration_applications
    DROP CONSTRAINT IF EXISTS ck_drone_reg_app_mtow_positive,
    DROP CONSTRAINT IF EXISTS ck_drone_reg_app_flight_time_positive;

ALTER TABLE drone_registration_applications
    ADD CONSTRAINT ck_drone_reg_app_mtow_positive
        CHECK (mtow_kg IS NULL OR mtow_kg > 0),
    ADD CONSTRAINT ck_drone_reg_app_flight_time_positive
        CHECK (max_flight_time_minutes IS NULL OR max_flight_time_minutes > 0);

ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS mtow_kg NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS max_flight_time_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS camera_sensor VARCHAR(128);

ALTER TABLE drones
    DROP CONSTRAINT IF EXISTS ck_drones_mtow_positive,
    DROP CONSTRAINT IF EXISTS ck_drones_flight_time_positive;

ALTER TABLE drones
    ADD CONSTRAINT ck_drones_mtow_positive
        CHECK (mtow_kg IS NULL OR mtow_kg > 0),
    ADD CONSTRAINT ck_drones_flight_time_positive
        CHECK (max_flight_time_minutes IS NULL OR max_flight_time_minutes > 0);

-- Backfill structured columns from legacy bracketed specs in `drone_registration_applications.model`
UPDATE drone_registration_applications
SET
    mtow_kg = COALESCE(
        mtow_kg,
        CASE
            WHEN NULLIF(REPLACE((regexp_match(model, 'MTOW:\s*([0-9]+(?:[.,][0-9]+)?)', 'i'))[1], ',', '.'), '')::NUMERIC(6, 2) > 0
            THEN NULLIF(REPLACE((regexp_match(model, 'MTOW:\s*([0-9]+(?:[.,][0-9]+)?)', 'i'))[1], ',', '.'), '')::NUMERIC(6, 2)
            ELSE NULL
        END
    ),
    max_flight_time_minutes = COALESCE(
        max_flight_time_minutes,
        CASE
            WHEN NULLIF((regexp_match(model, '(?:Thời gian bay|Flight time):\s*([0-9]+)', 'i'))[1], '')::INTEGER > 0
            THEN NULLIF((regexp_match(model, '(?:Thời gian bay|Flight time):\s*([0-9]+)', 'i'))[1], '')::INTEGER
            ELSE NULL
        END
    ),
    camera_sensor = COALESCE(
        camera_sensor,
        LEFT(
            NULLIF(
                BTRIM((regexp_match(model, 'Camera(?:\s*/\s*Sensor)?:\s*([^|\]]+)', 'i'))[1]),
                ''
            ),
            128
        )
    ),
    model = BTRIM(
        regexp_replace(
            model,
            '\s*\[[^\]]*(?:MTOW:|Thời gian bay:|Flight time:|Camera(?:\s*/\s*Sensor)?:)[^\]]*\]\s*$',
            '',
            'i'
        )
    )
WHERE model ~* '\[[^\]]*(?:MTOW:|Thời gian bay:|Flight time:|Camera(?:\s*/\s*Sensor)?:)[^\]]*\]';

-- Backfill structured columns from legacy bracketed specs in `drones.model`
UPDATE drones
SET
    mtow_kg = COALESCE(
        mtow_kg,
        CASE
            WHEN NULLIF(REPLACE((regexp_match(model, 'MTOW:\s*([0-9]+(?:[.,][0-9]+)?)', 'i'))[1], ',', '.'), '')::NUMERIC(6, 2) > 0
            THEN NULLIF(REPLACE((regexp_match(model, 'MTOW:\s*([0-9]+(?:[.,][0-9]+)?)', 'i'))[1], ',', '.'), '')::NUMERIC(6, 2)
            ELSE NULL
        END
    ),
    max_flight_time_minutes = COALESCE(
        max_flight_time_minutes,
        CASE
            WHEN NULLIF((regexp_match(model, '(?:Thời gian bay|Flight time):\s*([0-9]+)', 'i'))[1], '')::INTEGER > 0
            THEN NULLIF((regexp_match(model, '(?:Thời gian bay|Flight time):\s*([0-9]+)', 'i'))[1], '')::INTEGER
            ELSE NULL
        END
    ),
    camera_sensor = COALESCE(
        camera_sensor,
        LEFT(
            NULLIF(
                BTRIM((regexp_match(model, 'Camera(?:\s*/\s*Sensor)?:\s*([^|\]]+)', 'i'))[1]),
                ''
            ),
            128
        )
    ),
    model = BTRIM(
        regexp_replace(
            model,
            '\s*\[[^\]]*(?:MTOW:|Thời gian bay:|Flight time:|Camera(?:\s*/\s*Sensor)?:)[^\]]*\]\s*$',
            '',
            'i'
        )
    )
WHERE model ~* '\[[^\]]*(?:MTOW:|Thời gian bay:|Flight time:|Camera(?:\s*/\s*Sensor)?:)[^\]]*\]';
