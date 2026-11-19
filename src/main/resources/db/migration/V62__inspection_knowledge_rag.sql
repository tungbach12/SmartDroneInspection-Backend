-- Historical case recommendation (DroneKnowledgeAI) schema.
--
-- Two read-mostly stores:
--   * inspection_knowledge_chunks - anonymized standard/guideline chunks used to ground an AI
--     summary in published engineering references rather than invented facts.
--   * historical_finding_cases - confirmed findings of published reports (tenant-private) plus
--     explicitly anonymized benchmark cases (shared, zero customer PII).
--
-- The 1536-dimension embedding column is read and written with parameterized JDBC using the
-- pgvector literal '[v1,v2,...]'; it is deliberately not mapped as a JPA attribute.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE inspection_knowledge_chunks (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    standard_code varchar(64) NOT NULL,
    title varchar(255) NOT NULL,
    asset_category_code varchar(64) NOT NULL,
    defect_patterns varchar(255) NOT NULL,
    severity varchar(16) NOT NULL,
    guidance text NOT NULL,
    reference_url varchar(500),
    embedding vector(1536),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_inspection_knowledge_chunks_cat
    ON inspection_knowledge_chunks (asset_category_code);

CREATE INDEX ix_inspection_knowledge_chunks_hnsw
    ON inspection_knowledge_chunks USING hnsw (embedding vector_cosine_ops);

INSERT INTO inspection_knowledge_chunks
    (standard_code, title, asset_category_code, defect_patterns, severity, guidance, reference_url)
VALUES
    ('TCVN 9343:2012', 'Concrete surface crack assessment for office towers',
     'HIGH_RISE_OFFICE', 'Facade crack|Spalling|Curtain wall staining', 'MEDIUM',
     'Classify visible cracks by width and stability; record location and orientation before repair. Confirm movement with repeat observation before treating a crack as structural.',
     'https://www.tcvn.vn/'),
    ('TCVN 9361:2012', 'Balcony and facade waterproofing for residential towers',
     'RESIDENTIAL_TOWER', 'Balcony crack|Efflorescence|Water leakage|Railing corrosion', 'HIGH',
     'Trace leakage to its source, verify drainage slopes and seal continuity, and inspect embedded railing anchors where corrosion staining is visible.',
     'https://www.tcvn.vn/'),
    ('EN 1504-9', 'Glazed skylight and cladding repair principles',
     'COMMERCIAL_MALL', 'Skylight leak|Cladding delamination|Sealant failure', 'MEDIUM',
     'Select a repair principle that addresses the cause, not only the symptom; verify fixing and seal integrity after any glazing or cladding intervention.',
     'https://standards.iteh.ai/catalog/standards/cen'),
    ('ISO 12944-5', 'Protective coating systems for industrial steelwork',
     'INDUSTRIAL_FACTORY', 'Coating blister|Rust|Roof sheet corrosion|Gutter blockage', 'HIGH',
     'Assess coating breakdown against the specified durability class, clean and prepare the substrate, and restore drainage before recoating.',
     'https://www.iso.org/standard/'),
    ('ASTM C1583', 'Surface tensile strength verification before concrete repair',
     'HOTEL_HOSPITAL_COMPLEX', 'Map cracking|Spall|Exposed rebar|Delamination', 'CRITICAL',
     'Determine the extent of unsound concrete and verify substrate strength before patch repair; exposed reinforcement requires immediate protective treatment.',
     'https://www.astm.org/'),
    ('TCVN 9361:2012', 'Roof antenna mast anchorage and lightning protection',
     'TELECOM_ROOFTOP_TOWER', 'Anchor bolt corrosion|Brace deformation|Cable wear|Lightning rod', 'HIGH',
     'Inspect base plate anchors, brace connections and conductor continuity; treat section loss or loose fixings as a priority safety defect.',
     'https://www.tcvn.vn/'),
    ('TCVN 9343:2012', 'General crack width classification for all asset types',
     'ALL', 'Hairline crack|Structural crack|Differential displacement', 'MEDIUM',
     'Record width, length and pattern with a scale reference. Classify by observed width and trend; persistent or widening cracks require engineering assessment.',
     'https://www.tcvn.vn/'),
    ('EN 1504-9', 'General post-repair verification principles',
     'ALL', 'Confirmed defect|Post-repair inspection|Recurrence', 'LOW',
     'Define the repair objective, document the as-found condition, and plan a follow-up inspection so repair effectiveness can be verified rather than assumed.',
     'https://standards.iteh.ai/catalog/standards/cen');

CREATE TABLE historical_finding_cases (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    source_finding_id uuid UNIQUE,
    source_inspection_id uuid,
    organization_id uuid,
    asset_id uuid,
    asset_category_code varchar(64) NOT NULL,
    asset_label varchar(255) NOT NULL,
    defect_label varchar(160) NOT NULL,
    severity varchar(16) NOT NULL,
    technical_notes text NOT NULL,
    report_outcome varchar(64) NOT NULL,
    is_anonymized_benchmark boolean NOT NULL DEFAULT FALSE,
    embedding vector(1536),
    published_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_historical_finding_cases_scope
    ON historical_finding_cases (organization_id, is_anonymized_benchmark);

CREATE INDEX ix_historical_finding_cases_cat_defect
    ON historical_finding_cases (asset_category_code, defect_label);

CREATE INDEX ix_historical_finding_cases_hnsw
    ON historical_finding_cases USING hnsw (embedding vector_cosine_ops);

-- Anonymized industry benchmarks: no organization, no asset reference, no customer identity.
INSERT INTO historical_finding_cases
    (asset_category_code, asset_label, defect_label, severity, technical_notes,
     report_outcome, is_anonymized_benchmark, published_at)
VALUES
    ('HIGH_RISE_OFFICE', 'High-rise office tower - curtain wall facade',
     'Facade crack', 'HIGH',
     'Diagonal hairline cracking observed near a curtain wall anchor; width estimated below 1 mm with no active water ingress.',
     'MONITOR', TRUE, CURRENT_TIMESTAMP - INTERVAL '12 days'),
    ('RESIDENTIAL_TOWER', 'Residential apartment tower - balcony slab',
     'Water leakage', 'HIGH',
     'Efflorescence and damp patches below a balcony slab; drainage outlet partially blocked by debris.',
     'REMEDIATION_REQUIRED', TRUE, CURRENT_TIMESTAMP - INTERVAL '40 days'),
    ('COMMERCIAL_MALL', 'Commercial mall - atrium skylight',
     'Sealant failure', 'MEDIUM',
     'Localized sealant shrinkage along a skylight frame; no dripping recorded during the observation window.',
     'PUBLISHED', TRUE, CURRENT_TIMESTAMP - INTERVAL '75 days'),
    ('INDUSTRIAL_FACTORY', 'Industrial factory - roof steelwork',
     'Coating corrosion', 'HIGH',
     'Coating blistering and early rust at roof purlin laps; gutter silt observed upstream of the affected bay.',
     'REMEDIATION_REQUIRED', TRUE, CURRENT_TIMESTAMP - INTERVAL '95 days'),
    ('HOTEL_HOSPITAL_COMPLEX', 'Hotel complex - transfer slab soffit',
     'Spalling with exposed rebar', 'CRITICAL',
     'Localized spall with exposed reinforcement on a soffit; surrounding concrete sounded sound on hammer survey.',
     'PUBLISHED', TRUE, CURRENT_TIMESTAMP - INTERVAL '130 days'),
    ('TELECOM_ROOFTOP_TOWER', 'Rooftop telecom tower - base anchorage',
     'Anchor bolt corrosion', 'HIGH',
     'Surface corrosion on base plate anchor nuts with intact washers; brace geometry unchanged.',
     'MONITOR', TRUE, CURRENT_TIMESTAMP - INTERVAL '20 days');
