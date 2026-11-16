-- =============================================================================
-- V57: Add required flag to checklist_templates & generalize safety checklists
-- =============================================================================

ALTER TABLE checklist_templates
    ADD COLUMN IF NOT EXISTS required BOOLEAN NOT NULL DEFAULT TRUE;

-- Replace V30 seeded Pre-flight checklist items with general, atomic checks:
-- 4 core flight-safety checks (required = TRUE) + 3 contextual/payload checks (required = FALSE).
INSERT INTO checklist_templates (id, template_type, label, sort_order, active, required)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'PRE_FLIGHT', 'Airframe, propellers & mounting secure', 1, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000002', 'PRE_FLIGHT', 'Battery levels sufficient for planned flight', 2, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000003', 'PRE_FLIGHT', 'Weather & visibility within safe operating limits', 3, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000004', 'PRE_FLIGHT', 'Takeoff/landing zone clear of people & hazards', 4, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000005', 'PRE_FLIGHT', 'Navigation & failsafe (RTH) settings verified', 5, TRUE, FALSE),
    ('10000000-0000-0000-0000-000000000006', 'PRE_FLIGHT', 'Camera / sensor payload & storage ready', 6, TRUE, FALSE),
    ('10000000-0000-0000-0000-000000000007', 'PRE_FLIGHT', 'Surrounding airspace hazards & emergency landing spot noted', 7, TRUE, FALSE)
ON CONFLICT (id) DO UPDATE
SET
    label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active,
    required = EXCLUDED.required,
    updated_at = CURRENT_TIMESTAMP;

-- Update seeded Post-flight checklist items to general advisory checks (required = FALSE).
-- Target Building Inspection Items are tracked in their own dedicated checklist.
INSERT INTO checklist_templates (id, template_type, label, sort_order, active, required)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'POST_FLIGHT', 'Aircraft landed safely & propulsion disarmed', 1, TRUE, FALSE),
    ('20000000-0000-0000-0000-000000000002', 'POST_FLIGHT', 'Hardware & batteries checked for post-flight damage or overheating', 2, TRUE, FALSE),
    ('20000000-0000-0000-0000-000000000003', 'POST_FLIGHT', 'Inspection media saved & accessible before departure', 3, TRUE, FALSE),
    ('20000000-0000-0000-0000-000000000004', 'POST_FLIGHT', 'Equipment secured & site area cleared', 4, TRUE, FALSE)
ON CONFLICT (id) DO UPDATE
SET
    label = EXCLUDED.label,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active,
    required = EXCLUDED.required,
    updated_at = CURRENT_TIMESTAMP;
