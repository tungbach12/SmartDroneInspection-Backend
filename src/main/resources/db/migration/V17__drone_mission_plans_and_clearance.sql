-- V17: MF2 drone mission plans, shot items, and airspace clearance state.
--
-- Forward-only: V1..V16 are applied and are not edited here. Both tables are brand new, plus one
-- nullable column on inspection_service_orders. Nothing existing is dropped, rewritten or made NOT NULL.
--
-- Authority: business-flows.md v3.3 MF2 (MF2-01..MF2-06) and database-design.md drone_mission_plans /
-- mission_shot_items. Design rules carried straight from the contract:
--   * A versioned plan belongs to ONE service order and ONE provider; a revision creates a new version
--     and supersedes the previous one (previous_version_id), it never edits an approved plan in place.
--   * GSD, overlap, AGL and camera inputs are mission-specific. No universal default is implied, so the
--     database constrains RANGES only, never a required value.
--   * Airspace check status is NOT_CHECKED / CLEARANCE_REQUIRED / MANUAL_REVIEW / CLEARED / BLOCKED.
--     A public map lookup (cambay.mod.gov.vn, Decision 18/2020/QĐ-TTg) is a pre-flight early warning
--     layer only - it is never a permit, which is why NOT_CHECKED can never reach APPROVED.
--   * Waypoints are OPTIONAL: a manual-flight plan carries shot items with NULL/NULL coordinates. A
--     latitude without a longitude, or an out-of-range coordinate, is refused by the CHECK.
--   * Approval by PROVIDER_MANAGER is what moves the parent order to READY_FOR_FLIGHT (MF2-06). That
--     transition crosses two tables, so it is an APPLICATION rule: the mission service sets the order
--     status in the same transaction that approves the plan. Everything the database can state about
--     approval itself is stated below as a CHECK on this table.
--
-- Pre-checks: neither table exists before this file runs, so no pre-existing row can violate any
-- constraint declared on them - stated, not assumed (a pre-check query would not compile). The single
-- statement touching a populated table is the flight_permit_no column addition, which is nullable and
-- therefore cannot fail on data.


-- 1. Flight permit reference on the order (MF2-05 dossier, recorded on the contract as well as on the
--    plan). Nullable, no backfill: pre-V17 orders simply never had one.
ALTER TABLE inspection_service_orders
    ADD COLUMN IF NOT EXISTS flight_permit_no VARCHAR(128);

COMMENT ON COLUMN inspection_service_orders.flight_permit_no IS
    'Authority-issued flight permit/approval reference for this order when the flight is permit-required under current regulations. The platform records a dossier; it never issues permits and a public airspace map lookup is not one.';


-- 2. The mission plan aggregate.
CREATE TABLE drone_mission_plans
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_order_id        UUID        NOT NULL,
    provider_id             UUID        NOT NULL,
    version_number          INTEGER     NOT NULL,
    previous_version_id     UUID,
    created_by_user_id      UUID        NOT NULL,
    drone_registration_id   VARCHAR(128),
    pilot_user_id           UUID,
    flight_permit_reference VARCHAR(128),
    camera_model            VARCHAR(200),
    sensor_width_mm         NUMERIC(10,4),
    focal_length_mm         NUMERIC(10,4),
    image_width_px          INTEGER,
    target_gsd_mm_per_pixel NUMERIC(12,6),
    planned_agl_m           NUMERIC(10,3),
    forward_overlap_percent NUMERIC(5,2),
    side_overlap_percent    NUMERIC(5,2),
    airspace_check_status   VARCHAR(32) NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    approved_by_user_id     UUID,
    approved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_drone_mission_plans_order
        FOREIGN KEY (service_order_id) REFERENCES inspection_service_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_drone_mission_plans_provider
        FOREIGN KEY (provider_id) REFERENCES provider_organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_drone_mission_plans_previous
        FOREIGN KEY (previous_version_id) REFERENCES drone_mission_plans (id) ON DELETE RESTRICT,
    CONSTRAINT fk_drone_mission_plans_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_drone_mission_plans_pilot
        FOREIGN KEY (pilot_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_drone_mission_plans_approver
        FOREIGN KEY (approved_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_drone_mission_plans_order_version UNIQUE (service_order_id, version_number),
    CONSTRAINT ck_drone_mission_plans_version CHECK (version_number > 0),
    CONSTRAINT ck_drone_mission_plans_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED', 'CANCELLED', 'SUPERSEDED')
    ),
    -- Five values only. NOT_CHECKED means nobody has intersected the coordinates with the restricted
    -- area dataset yet; it is not a clearance, so it cannot back an approved flight.
    CONSTRAINT ck_drone_mission_plans_airspace CHECK (
        airspace_check_status IN ('NOT_CHECKED', 'CLEARANCE_REQUIRED', 'MANUAL_REVIEW',
                                  'CLEARED', 'BLOCKED')
    ),
    -- An approval names its actor and its instant together, and a non-approved plan carries neither.
    CONSTRAINT ck_drone_mission_plans_approval CHECK (
        (status = 'APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
        OR (status <> 'APPROVED' AND approved_by_user_id IS NULL AND approved_at IS NULL)
    ),
    -- MF2-04 / MF2-05 gate. Approval requires a completed airspace determination that is not BLOCKED,
    -- and when that determination says clearance is required, the authority-issued permit reference
    -- must be attached: a public map lookup is not a permit.
    CONSTRAINT ck_drone_mission_plans_clearance_gate CHECK (
        status <> 'APPROVED'
        OR (airspace_check_status IN ('CLEARED', 'MANUAL_REVIEW', 'CLEARANCE_REQUIRED')
            AND (airspace_check_status <> 'CLEARANCE_REQUIRED'
                 OR (flight_permit_reference IS NOT NULL
                     AND BTRIM(flight_permit_reference) <> '')))
    ),
    -- MF2-05 dossier completeness at approval time: the registered airframe and the pilot in command
    -- are named before the plan is released. Whether a given licence or registration is VALID is an
    -- application rule - the database cannot know an expiry date an external authority owns.
    CONSTRAINT ck_drone_mission_plans_dossier CHECK (
        status <> 'APPROVED'
        OR (drone_registration_id IS NOT NULL AND BTRIM(drone_registration_id) <> ''
            AND pilot_user_id IS NOT NULL)
    ),
    -- Photogrammetric inputs are mission-specific: ranges only, never a required value.
    CONSTRAINT ck_drone_mission_plans_capture_inputs CHECK (
        (target_gsd_mm_per_pixel IS NULL OR target_gsd_mm_per_pixel > 0)
        AND (planned_agl_m IS NULL OR planned_agl_m > 0)
        AND (sensor_width_mm IS NULL OR sensor_width_mm > 0)
        AND (focal_length_mm IS NULL OR focal_length_mm > 0)
        AND (image_width_px IS NULL OR image_width_px > 0)
    ),
    CONSTRAINT ck_drone_mission_plans_overlap CHECK (
        (forward_overlap_percent IS NULL OR (forward_overlap_percent >= 0 AND forward_overlap_percent <= 100))
        AND (side_overlap_percent IS NULL OR (side_overlap_percent >= 0 AND side_overlap_percent <= 100))
    )
);

COMMENT ON TABLE drone_mission_plans IS
    'Versioned MF2 mission plan for one inspection service order: photogrammetric inputs (target GSD, overlap, AGL, camera/sensor), airspace determination, flight permit reference, pilot and airframe, and approval by PROVIDER_MANAGER. Approval moves the parent order to READY_FOR_FLIGHT (application rule).';

CREATE INDEX ix_drone_mission_plans_order
    ON drone_mission_plans (service_order_id, version_number DESC);
CREATE INDEX ix_drone_mission_plans_provider_status
    ON drone_mission_plans (provider_id, status);
CREATE INDEX ix_drone_mission_plans_clearance
    ON drone_mission_plans (airspace_check_status, status)
    WHERE status <> 'SUPERSEDED';

-- Exactly one approved plan version per service order; a revision supersedes rather than coexists.
-- The table is created in this file, so the index cannot fail on data that does not exist yet.
CREATE UNIQUE INDEX uq_drone_mission_plans_approved
    ON drone_mission_plans (service_order_id)
    WHERE status = 'APPROVED';


-- 3. Ordered shot items for one plan version.
CREATE TABLE mission_shot_items
(
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_plan_id         UUID        NOT NULL,
    sequence_number         INTEGER     NOT NULL,
    component_reference     VARCHAR(200) NOT NULL,
    waypoint_latitude       NUMERIC(10,7),
    waypoint_longitude      NUMERIC(10,7),
    waypoint_altitude_m     NUMERIC(10,3),
    camera_heading_degrees  NUMERIC(7,3),
    gimbal_pitch_degrees    NUMERIC(7,3),
    target_gsd_mm_per_pixel NUMERIC(12,6),
    capture_instructions    VARCHAR(2000),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_shot_items_plan
        FOREIGN KEY (mission_plan_id) REFERENCES drone_mission_plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_mission_shot_items_sequence UNIQUE (mission_plan_id, sequence_number),
    CONSTRAINT ck_mission_shot_items_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_mission_shot_items_component CHECK (BTRIM(component_reference) <> ''),
    -- Waypoints are optional (manual flight): NULL/NULL is a legal row. When a coordinate is supplied
    -- it must be supplied in a pair and fall inside the valid geographic range - latitude without
    -- longitude fails, longitude without latitude fails, and any out-of-range value fails.
    CONSTRAINT ck_mission_shot_items_waypoint CHECK (
        (waypoint_latitude IS NULL AND waypoint_longitude IS NULL)
        OR (waypoint_latitude IS NOT NULL
            AND waypoint_longitude IS NOT NULL
            AND waypoint_latitude BETWEEN -90 AND 90
            AND waypoint_longitude BETWEEN -180 AND 180
            AND (waypoint_altitude_m IS NULL OR waypoint_altitude_m > 0))
    ),
    CONSTRAINT ck_mission_shot_items_gsd CHECK (
        target_gsd_mm_per_pixel IS NULL OR target_gsd_mm_per_pixel > 0
    )
);

COMMENT ON TABLE mission_shot_items IS
    'Ordered MF2 capture guidance for one mission plan version. Waypoints are optional so a manual-flight shot list is a legal row; a supplied waypoint must be a complete, in-range coordinate pair.';

CREATE INDEX ix_mission_shot_items_plan
    ON mission_shot_items (mission_plan_id, sequence_number);
