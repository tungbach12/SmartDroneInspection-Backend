-- Admin-managed UAV pilot license class catalog. Existing profile values are preserved and backfilled.
CREATE TABLE pilot_license_classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pilot_license_classes_name UNIQUE (name)
);

INSERT INTO pilot_license_classes (name)
SELECT BTRIM(pilot_license_class)
FROM (
    SELECT DISTINCT ON (LOWER(BTRIM(pilot_license_class))) pilot_license_class
    FROM inspector_profiles
    WHERE pilot_license_class IS NOT NULL
      AND BTRIM(pilot_license_class) <> ''
    ORDER BY LOWER(BTRIM(pilot_license_class)), BTRIM(pilot_license_class)
) existing_classes
ON CONFLICT (name) DO NOTHING;
