-- Structure height/footprint on assets and the manufacturer-declared maximum altitude on drones.
-- IF NOT EXISTS keeps this idempotent for databases that already received the columns.
ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS structure_height_meters NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS footprint_area_square_meters NUMERIC(12, 2);

ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS max_altitude_meters NUMERIC(8, 1);

ALTER TABLE drone_registration_applications
    ADD COLUMN IF NOT EXISTS max_altitude_meters NUMERIC(8, 1);

ALTER TABLE prospective_customer_registrations
    ADD COLUMN IF NOT EXISTS structure_height_meters NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS footprint_area_square_meters NUMERIC(12, 2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assets_structure_dimensions_nonnegative') THEN
        ALTER TABLE assets ADD CONSTRAINT ck_assets_structure_dimensions_nonnegative
            CHECK ((structure_height_meters IS NULL OR structure_height_meters >= 0)
               AND (footprint_area_square_meters IS NULL OR footprint_area_square_meters >= 0));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_prospective_customer_dimensions_nonnegative') THEN
        ALTER TABLE prospective_customer_registrations ADD CONSTRAINT ck_prospective_customer_dimensions_nonnegative
            CHECK ((structure_height_meters IS NULL OR structure_height_meters >= 0)
               AND (footprint_area_square_meters IS NULL OR footprint_area_square_meters >= 0));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drones_max_altitude_nonnegative') THEN
        ALTER TABLE drones ADD CONSTRAINT ck_drones_max_altitude_nonnegative
            CHECK (max_altitude_meters IS NULL OR max_altitude_meters >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_drone_registration_applications_max_altitude_nonnegative') THEN
        ALTER TABLE drone_registration_applications ADD CONSTRAINT ck_drone_registration_applications_max_altitude_nonnegative
            CHECK (max_altitude_meters IS NULL OR max_altitude_meters >= 0);
    END IF;
END $$;

-- Capability requirements must be versioned with the quote and preserved at acceptance.
ALTER TABLE service_request_quotes
    ADD COLUMN IF NOT EXISTS required_drone_feature_codes JSONB NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_service_request_quotes_required_feature_codes_array') THEN
        ALTER TABLE service_request_quotes ADD CONSTRAINT ck_service_request_quotes_required_feature_codes_array
            CHECK (jsonb_typeof(required_drone_feature_codes) = 'array');
    END IF;
END $$;

INSERT INTO drone_feature_configs
    (code, name_vi, name_en, description_vi, description_en, category, is_active, display_order)
VALUES
    ('UPWARD_GIMBAL', 'Gimbal/camera hướng lên', 'Upward-facing gimbal/camera',
     'Khả năng thu hình hướng lên khi được lắp và xác minh trên drone cụ thể',
     'Capability to capture upward-facing imagery when verified for the specific drone',
     'CAPABILITY', TRUE, 9),
    ('GNSS_DENIED_POSITIONING', 'Định vị trong môi trường mất GNSS', 'GNSS-denied positioning',
     'Khả năng định vị trong môi trường mất hoặc suy giảm GNSS khi được xác minh trên drone cụ thể',
     'Positioning capability in GNSS-denied environments when verified for the specific drone',
     'CAPABILITY', TRUE, 10)
ON CONFLICT (code) DO NOTHING;
