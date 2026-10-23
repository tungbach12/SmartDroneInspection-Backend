--
-- PostgreSQL database dump
--


-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '${baseline_schema}', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA ${baseline_extension_schema};

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA ${baseline_extension_schema};
    ELSE
        RAISE NOTICE 'pgvector extension is not installed; skipping.';
    END IF;
END
$$;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: asset_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.asset_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(2000),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    organization_id uuid,
    CONSTRAINT ck_asset_categories_code_normalized CHECK (((code)::text = upper(btrim((code)::text))))
);


--
-- Name: asset_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.asset_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    document_type character varying(32) NOT NULL,
    file_name character varying(500) NOT NULL,
    content_type character varying(160) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character(64) NOT NULL,
    object_key character varying(1000) NOT NULL,
    document_date date,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    document_version character varying(64),
    status character varying(24),
    updated_at timestamp with time zone,
    CONSTRAINT ck_asset_documents_size CHECK ((size_bytes > 0))
);


--
-- Name: asset_pair_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.asset_pair_assignments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    inspector_user_id uuid NOT NULL,
    drone_id uuid NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_until timestamp with time zone,
    status character varying(24) NOT NULL,
    reason character varying(2000),
    assigned_by_user_id uuid NOT NULL,
    assigned_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    assignment_response character varying(24),
    responded_at timestamp with time zone,
    CONSTRAINT ck_asset_pair_assignments_response CHECK ((((assignment_response IS NULL) AND (responded_at IS NULL)) OR ((assignment_response IS NOT NULL) AND (responded_at IS NOT NULL)))),
    CONSTRAINT ck_asset_pair_assignments_response_vocabulary CHECK (((assignment_response IS NULL) OR ((assignment_response)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'REJECTED'::character varying])::text[])))),
    CONSTRAINT ck_asset_pair_assignments_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'SUPERSEDED'::character varying, 'SUSPENDED'::character varying])::text[]))),
    CONSTRAINT ck_asset_pair_assignments_window CHECK (((valid_until IS NULL) OR (valid_until > valid_from)))
);


--
-- Name: COLUMN asset_pair_assignments.assignment_response; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN ${baseline_schema}.asset_pair_assignments.assignment_response IS 'Inspector answer to the assignment: ACCEPTED or REJECTED (MF2-01). NULL until the assigned inspector responds. Independent of status, which tracks the pair''s own validity window rather than the answer.';


--
-- Name: COLUMN asset_pair_assignments.responded_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN ${baseline_schema}.asset_pair_assignments.responded_at IS 'Instant the assigned inspector answered. Records when the response arrived so a missed or late answer is distinguishable from no answer at all.';


--
-- Name: assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.assets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(2000),
    latitude numeric(9,6),
    longitude numeric(9,6),
    status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    asset_code character varying(64) NOT NULL,
    asset_type character varying(96),
    location jsonb,
    technical_profile jsonb,
    CONSTRAINT ck_assets_latitude CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT ck_assets_longitude CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT ck_assets_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid,
    actor_user_id uuid,
    action character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id uuid NOT NULL,
    before_status character varying(64),
    after_status character varying(64),
    aggregate_version bigint,
    reason character varying(2000),
    trace_id character varying(128),
    safe_metadata jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.auth_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    client_type character varying(16) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(128),
    CONSTRAINT ck_auth_sessions_client CHECK (((client_type)::text = ANY ((ARRAY['WEB'::character varying, 'MOBILE'::character varying])::text[])))
);


--
-- Name: checklist_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.checklist_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    template_id uuid NOT NULL,
    item_code character varying(64) NOT NULL,
    section_name character varying(160),
    prompt character varying(1000) NOT NULL,
    response_type character varying(24) NOT NULL,
    required boolean DEFAULT true NOT NULL,
    display_order integer NOT NULL,
    guidance character varying(2000),
    section_name_vi character varying(160),
    prompt_vi character varying(1000),
    guidance_vi character varying(2000),
    validation_config jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    evidence_requirement jsonb,
    CONSTRAINT ck_checklist_item_code_normalized CHECK (((item_code)::text = upper(btrim((item_code)::text)))),
    CONSTRAINT ck_checklist_item_display_order CHECK ((display_order >= 0)),
    CONSTRAINT ck_checklist_item_response_type CHECK (((response_type)::text = ANY ((ARRAY['PASS_FAIL'::character varying, 'TEXT'::character varying, 'NUMBER'::character varying, 'BOOLEAN'::character varying, 'CHOICE'::character varying])::text[])))
);


--
-- Name: checklist_responses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.checklist_responses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    checklist_item_id uuid NOT NULL,
    response_value jsonb NOT NULL,
    notes character varying(4000),
    completed_by_user_id uuid NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    context character varying(32),
    template_version integer,
    item_version integer,
    responder_user_id uuid,
    answer jsonb,
    evidence_id uuid,
    field_session_id uuid
);


--
-- Name: checklist_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.checklist_templates (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    template_key character varying(64) NOT NULL,
    version_number integer NOT NULL,
    asset_category_id uuid,
    name character varying(200) NOT NULL,
    description character varying(2000),
    name_vi character varying(200),
    description_vi character varying(2000),
    status character varying(24) NOT NULL,
    created_by_user_id uuid NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    context character varying(32),
    active_from timestamp with time zone,
    active_until timestamp with time zone,
    CONSTRAINT ck_checklist_template_key_normalized CHECK (((template_key)::text = upper(btrim((template_key)::text)))),
    CONSTRAINT ck_checklist_template_publication CHECK (((((status)::text = 'DRAFT'::text) AND (published_at IS NULL)) OR (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'RETIRED'::character varying])::text[])) AND (published_at IS NOT NULL)))),
    CONSTRAINT ck_checklist_template_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_checklist_template_version CHECK ((version_number > 0))
);


--
-- Name: drone_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.drone_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    drone_id uuid NOT NULL,
    document_type character varying(48) NOT NULL,
    issuer character varying(200),
    document_reference character varying(200),
    valid_from timestamp with time zone,
    valid_until timestamp with time zone,
    status character varying(24) NOT NULL,
    object_key character varying(1000) NOT NULL,
    checksum_sha256 character(64) NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_drone_documents_status CHECK (((status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying, 'REVOKED'::character varying])::text[]))),
    CONSTRAINT ck_drone_documents_validity CHECK (((valid_until IS NULL) OR (valid_from IS NULL) OR (valid_until > valid_from)))
);


--
-- Name: drones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.drones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    serial_number character varying(128) NOT NULL,
    model character varying(200),
    manufacturer character varying(200),
    payload_metadata jsonb,
    serviceability character varying(24) NOT NULL,
    last_maintenance_at timestamp with time zone,
    next_maintenance_at timestamp with time zone,
    notes character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_drones_serviceability CHECK (((serviceability)::text = ANY ((ARRAY['ACTIVE'::character varying, 'MAINTENANCE'::character varying, 'SUSPENDED'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: event_publication; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.event_publication (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    status text,
    completion_attempts integer,
    last_resubmission_date timestamp with time zone
);


--
-- Name: evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.evidence (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid,
    maintenance_work_log_id uuid,
    evidence_kind character varying(32) NOT NULL,
    file_name character varying(500) NOT NULL,
    content_type character varying(160) NOT NULL,
    size_bytes bigint NOT NULL,
    object_key character varying(1000) NOT NULL,
    capture_time timestamp with time zone,
    latitude numeric(9,6),
    longitude numeric(9,6),
    upload_status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    organization_id uuid,
    field_session_id uuid,
    maintenance_work_order_id uuid,
    maintenance_task_id uuid,
    retired_at timestamp with time zone,
    location_description character varying(1000),
    CONSTRAINT ck_evidence_content_type CHECK ((btrim((content_type)::text) <> ''::text)),
    CONSTRAINT ck_evidence_file_name CHECK ((btrim((file_name)::text) <> ''::text)),
    CONSTRAINT ck_evidence_kind CHECK (((evidence_kind)::text = ANY ((ARRAY['INSPECTION'::character varying, 'BEFORE_MAINTENANCE'::character varying, 'AFTER_MAINTENANCE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_evidence_latitude CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT ck_evidence_longitude CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT ck_evidence_maintenance_pair CHECK ((((evidence_kind)::text <> ALL ((ARRAY['BEFORE_MAINTENANCE'::character varying, 'AFTER_MAINTENANCE'::character varying])::text[])) OR (maintenance_work_log_id IS NOT NULL))),
    CONSTRAINT ck_evidence_object_key CHECK ((btrim((object_key)::text) <> ''::text)),
    CONSTRAINT ck_evidence_size CHECK ((size_bytes > 0)),
    CONSTRAINT ck_evidence_target_parent CHECK (((inspection_id IS NOT NULL) OR (field_session_id IS NOT NULL) OR (maintenance_work_order_id IS NOT NULL) OR (maintenance_task_id IS NOT NULL))),
    CONSTRAINT ck_evidence_upload_status CHECK (((upload_status)::text = ANY ((ARRAY['UPLOADING'::character varying, 'AVAILABLE'::character varying, 'FAILED'::character varying, 'QUARANTINED'::character varying])::text[])))
);


--
-- Name: field_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.field_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    inspector_user_id uuid NOT NULL,
    drone_id uuid,
    readiness_decision_id uuid,
    status character varying(24) NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    postponement_reason character varying(2000),
    abort_reason character varying(2000),
    checklist_template_id uuid,
    readiness_version integer,
    limitations jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    pre_flight_checklist_note character varying(2000),
    outcome_notes character varying(4000),
    CONSTRAINT ck_field_sessions_status CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'IN_PROGRESS'::character varying, 'FIELD_COMPLETED'::character varying, 'POSTPONED'::character varying, 'ABORTED'::character varying])::text[]))),
    CONSTRAINT ck_field_sessions_time_order CHECK (((ended_at IS NULL) OR (started_at IS NULL) OR (ended_at >= started_at)))
);


--
-- Name: findings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.findings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    evidence_id uuid,
    finding_code character varying(64) NOT NULL,
    defect_label character varying(160) NOT NULL,
    severity character varying(16) NOT NULL,
    bounding_box jsonb,
    technical_notes character varying(4000) NOT NULL,
    recommended_action character varying(4000),
    status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    inspector_confirmed_at timestamp with time zone,
    model_name character varying(160),
    model_version character varying(80),
    model_provider character varying(96),
    confidence numeric(6,5),
    forwarded_to_maintenance_at timestamp with time zone,
    CONSTRAINT ck_findings_confidence CHECK (((confidence IS NULL) OR ((confidence >= (0)::numeric) AND (confidence <= (1)::numeric)))),
    CONSTRAINT ck_findings_confirmation CHECK (((((status)::text = 'CONFIRMED'::text) AND (inspector_confirmed_at IS NOT NULL)) OR (((status)::text = 'PENDING'::text) AND (inspector_confirmed_at IS NULL)))),
    CONSTRAINT ck_findings_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying])::text[]))),
    CONSTRAINT ck_verified_finding_severity CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: flight_permits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.flight_permits (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid,
    area_reference character varying(200),
    permit_type character varying(64) NOT NULL,
    issuing_authority character varying(200),
    permit_reference character varying(200),
    geographic_scope jsonb,
    valid_from timestamp with time zone,
    valid_until timestamp with time zone,
    conditions jsonb,
    status character varying(24) NOT NULL,
    source_evidence_id uuid,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    review_reason character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_flight_permits_status CHECK (((status)::text = ANY ((ARRAY['APPLICATION'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying, 'REVOKED'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
    CONSTRAINT ck_flight_permits_validity CHECK (((valid_until IS NULL) OR (valid_from IS NULL) OR (valid_until > valid_from)))
);


--
-- Name: inspection_preparations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspection_preparations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    inspector_user_id uuid NOT NULL,
    preparation_version integer NOT NULL,
    shot_list jsonb DEFAULT '[]'::jsonb NOT NULL,
    evidence_types jsonb,
    access_constraints jsonb,
    safety_observations character varying(4000),
    permit_document_references jsonb,
    submitted_at timestamp with time zone,
    status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_inspection_preparations_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'RETURNED'::character varying, 'READY'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_inspection_preparations_version CHECK ((preparation_version > 0))
);


--
-- Name: TABLE inspection_preparations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ${baseline_schema}.inspection_preparations IS 'Storage contract only; MF1-MF4 workflow execution is outside this cutover.';


--
-- Name: inspection_readiness_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspection_readiness_decisions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    preparation_id uuid,
    preparation_version integer,
    decision character varying(24) NOT NULL,
    reviewed_by_user_id uuid NOT NULL,
    decided_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason character varying(2000),
    permit_snapshot jsonb,
    credential_snapshot jsonb,
    drone_document_snapshot jsonb,
    source_hash character varying(128) NOT NULL,
    CONSTRAINT ck_inspection_readiness_decision CHECK (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'RETURNED'::character varying, 'INVALIDATED'::character varying])::text[])))
);


--
-- Name: inspection_report_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspection_report_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_report_id uuid NOT NULL,
    version_no integer NOT NULL,
    source_version_id uuid,
    status character varying(32) NOT NULL,
    reviewer_user_id uuid,
    reviewed_at timestamp with time zone,
    return_reason character varying(2000),
    content_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    rendered_object_key character varying(1000),
    rendered_checksum_sha256 character(64),
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    withdrawn_at timestamp with time zone,
    withdrawal_reason character varying(2000),
    submitted_at timestamp with time zone,
    CONSTRAINT ck_inspection_report_versions_number CHECK ((version_no > 0)),
    CONSTRAINT ck_inspection_report_versions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AUTHOR_VERIFIED'::character varying, 'SUBMITTED'::character varying, 'RETURNED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: inspection_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspection_reports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    author_user_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    current_version_number integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_inspection_reports_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AUTHOR_VERIFIED'::character varying, 'SUBMITTED'::character varying, 'RETURNED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_inspection_reports_version CHECK ((current_version_number >= 0))
);


--
-- Name: TABLE inspection_reports; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ${baseline_schema}.inspection_reports IS 'MF3 report aggregate. The human gates live on inspection_report_versions; see Report 3 sections 3.7.1 and 3.7.3.';


--
-- Name: inspection_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspection_schedules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    next_due_at timestamp with time zone NOT NULL,
    status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    cadence_unit character varying(16) NOT NULL,
    cadence_interval integer NOT NULL,
    scope_defaults jsonb,
    CONSTRAINT ck_inspection_schedules_cadence_interval CHECK ((cadence_interval > 0)),
    CONSTRAINT ck_inspection_schedules_cadence_unit CHECK (((cadence_unit)::text = ANY ((ARRAY['DAY'::character varying, 'WEEK'::character varying, 'MONTH'::character varying, 'YEAR'::character varying])::text[]))),
    CONSTRAINT ck_inspection_schedules_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PAUSED'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: inspections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.inspections (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    organization_id uuid NOT NULL,
    schedule_id uuid,
    due_cycle_key character varying(64),
    objective character varying(1000),
    scope jsonb,
    component_scope jsonb,
    acceptance_criteria jsonb,
    planned_start_at timestamp with time zone,
    planned_end_at timestamp with time zone,
    asset_pair_assignment_id uuid,
    inspector_id uuid,
    drone_id uuid,
    findings_confirmed_at timestamp with time zone,
    findings_snapshot_hash character varying(64),
    CONSTRAINT ck_inspections_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ASSIGNED'::character varying, 'PREPARING'::character varying, 'READY_FOR_FLIGHT'::character varying, 'IN_PROGRESS'::character varying, 'FIELD_COMPLETED'::character varying, 'REPORT_DRAFT'::character varying, 'REPORT_PUBLISHED'::character varying, 'REPAIR_PENDING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: maintenance_acceptance_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_acceptance_decisions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    report_version_id uuid NOT NULL,
    reviewer_user_id uuid NOT NULL,
    decision character varying(32) NOT NULL,
    technical_comments character varying(4000),
    acceptance_checklist jsonb,
    test_result jsonb,
    decided_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    signature_reference character varying(1000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_maintenance_acceptance_decision CHECK (((decision)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'REWORK_REQUIRED'::character varying, 'REINSPECTION_REQUIRED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: maintenance_change_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_change_orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    change_number integer NOT NULL,
    reason character varying(2000) NOT NULL,
    affected_tasks jsonb,
    proposed_delta numeric(18,2),
    supporting_evidence jsonb,
    proposed_start_at timestamp with time zone,
    proposed_end_at timestamp with time zone,
    status character varying(24) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    decision_reason character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_maintenance_change_orders_number CHECK ((change_number > 0)),
    CONSTRAINT ck_maintenance_change_orders_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AWAITING_APPROVAL'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'RETURNED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: maintenance_cost_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_cost_lines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    estimate_version_id uuid,
    change_order_id uuid,
    task_id uuid,
    line_kind character varying(24) NOT NULL,
    state character varying(24) NOT NULL,
    description character varying(1000) NOT NULL,
    quantity numeric(18,6) DEFAULT 1 NOT NULL,
    unit character varying(48),
    unit_rate numeric(18,6) NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency character(3) NOT NULL,
    tax_treatment jsonb,
    evidence_reference character varying(1000),
    entered_by_user_id uuid NOT NULL,
    entered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_maintenance_cost_line_kind CHECK (((line_kind)::text = ANY ((ARRAY['LABOR'::character varying, 'MATERIAL'::character varying, 'EQUIPMENT'::character varying, 'EXTERNAL_SERVICE'::character varying, 'OTHER'::character varying, 'CONTINGENCY'::character varying])::text[]))),
    CONSTRAINT ck_maintenance_cost_line_state CHECK (((state)::text = ANY ((ARRAY['ESTIMATE'::character varying, 'CHANGE'::character varying, 'ACTUAL'::character varying])::text[]))),
    CONSTRAINT ck_maintenance_cost_line_values CHECK (((quantity >= (0)::numeric) AND (unit_rate >= (0)::numeric) AND (amount >= (0)::numeric)))
);


--
-- Name: maintenance_estimate_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_estimate_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    version_no integer NOT NULL,
    status character varying(24) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    currency character(3) NOT NULL,
    tax_basis jsonb,
    baseline_total numeric(18,2) DEFAULT 0 NOT NULL,
    assumptions jsonb,
    snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_maintenance_estimate_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUPERSEDED'::character varying])::text[]))),
    CONSTRAINT ck_maintenance_estimate_total CHECK ((baseline_total >= (0)::numeric)),
    CONSTRAINT ck_maintenance_estimate_version_number CHECK ((version_no > 0))
);


--
-- Name: maintenance_report_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_report_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    version_no integer NOT NULL,
    status character varying(32) NOT NULL,
    author_user_id uuid NOT NULL,
    author_verified_at timestamp with time zone,
    llm_provider character varying(96),
    llm_model character varying(160),
    prompt_version character varying(96),
    generated_at timestamp with time zone,
    approved_scope_hash character varying(128),
    change_snapshot_hash character varying(128),
    work_log_snapshot_hash character varying(128),
    actual_cost_snapshot_hash character varying(128),
    content_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    rendered_object_key character varying(1000),
    rendered_checksum_sha256 character(64),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_maintenance_report_versions_number CHECK ((version_no > 0)),
    CONSTRAINT ck_maintenance_report_versions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AUTHOR_VERIFIED'::character varying, 'SUBMITTED'::character varying, 'RETURNED'::character varying, 'APPROVED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


--
-- Name: maintenance_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_tasks (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    task_number integer NOT NULL,
    name character varying(300) NOT NULL,
    method character varying(4000),
    assigned_engineer_user_id uuid,
    planned_start_at timestamp with time zone,
    planned_end_at timestamp with time zone,
    acceptance_criteria jsonb,
    status character varying(24) NOT NULL,
    completion_notes character varying(4000),
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_maintenance_tasks_order CHECK (((task_number > 0) AND (display_order >= 0))),
    CONSTRAINT ck_maintenance_tasks_status CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'READY'::character varying, 'IN_PROGRESS'::character varying, 'WORK_COMPLETED'::character varying, 'REWORK_REQUIRED'::character varying, 'ACCEPTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: maintenance_team_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_team_members (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    work_order_id uuid NOT NULL,
    engineer_user_id uuid NOT NULL,
    member_role character varying(24) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    assigned_by_user_id uuid NOT NULL,
    reason character varying(2000),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_maintenance_team_member_role CHECK (((member_role)::text = ANY ((ARRAY['LEAD'::character varying, 'REPORT_AUTHOR'::character varying, 'MEMBER'::character varying])::text[]))),
    CONSTRAINT ck_maintenance_team_member_window CHECK (((effective_until IS NULL) OR (effective_until > effective_from)))
);


--
-- Name: maintenance_work_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_work_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    engineer_user_id uuid NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone,
    status character varying(24) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    work_order_id uuid NOT NULL,
    task_id uuid NOT NULL,
    hours numeric(18,6) NOT NULL,
    actual_cost_references jsonb,
    as_left_condition character varying(4000),
    test_readings jsonb,
    CONSTRAINT ck_work_logs_status CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'PAUSED_FOR_CHANGE'::character varying, 'SUBMITTED'::character varying, 'VERIFIED'::character varying])::text[]))),
    CONSTRAINT ck_work_logs_time_order CHECK (((ended_at IS NULL) OR (ended_at >= started_at)))
);


--
-- Name: maintenance_work_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.maintenance_work_orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    source_report_version_id uuid NOT NULL,
    source_finding_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    priority character varying(24),
    due_at timestamp with time zone,
    corrective_scope jsonb,
    acceptance_criteria jsonb,
    owner_user_id uuid,
    budget_approver_user_id uuid,
    team_lead_user_id uuid,
    report_author_user_id uuid,
    accepting_reviewer_user_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_maintenance_work_orders_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AWAITING_APPROVAL'::character varying, 'APPROVED'::character varying, 'READY'::character varying, 'IN_PROGRESS'::character varying, 'WORK_COMPLETED'::character varying, 'SUBMITTED_FOR_ACCEPTANCE'::character varying, 'ACCEPTED'::character varying, 'COST_RECONCILED'::character varying, 'CLOSED'::character varying, 'REWORK_REQUIRED'::character varying, 'REINSPECTION_REQUIRED'::character varying])::text[])))
);


--
-- Name: TABLE maintenance_work_orders; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE ${baseline_schema}.maintenance_work_orders IS 'Storage contract only; MF1-MF4 workflow execution is outside this cutover.';


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    recipient_user_id uuid NOT NULL,
    organization_id uuid,
    event_type character varying(96) NOT NULL,
    channel character varying(16) NOT NULL,
    title character varying(300) NOT NULL,
    body character varying(4000) NOT NULL,
    target_path character varying(1000),
    status character varying(24) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error_code character varying(96),
    sent_at timestamp with time zone,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    aggregate_type character varying(128),
    aggregate_id uuid,
    CONSTRAINT ck_notifications_attempts CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_notifications_body CHECK ((btrim((body)::text) <> ''::text)),
    CONSTRAINT ck_notifications_channel CHECK (((channel)::text = ANY ((ARRAY['IN_APP'::character varying, 'EMAIL'::character varying])::text[]))),
    CONSTRAINT ck_notifications_event_type CHECK ((btrim((event_type)::text) <> ''::text)),
    CONSTRAINT ck_notifications_read_state CHECK (((((status)::text = 'READ'::text) AND (sent_at IS NOT NULL) AND (read_at IS NOT NULL)) OR (((status)::text <> 'READ'::text) AND (read_at IS NULL)))),
    CONSTRAINT ck_notifications_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'READ'::character varying])::text[]))),
    CONSTRAINT ck_notifications_target_path CHECK (((target_path IS NULL) OR (btrim((target_path)::text) <> ''::text))),
    CONSTRAINT ck_notifications_title CHECK ((btrim((title)::text) <> ''::text))
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.organizations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    legal_name character varying(250) NOT NULL,
    display_name character varying(200) NOT NULL,
    registration_code character varying(64) NOT NULL,
    timezone character varying(64) NOT NULL,
    status character varying(24) NOT NULL,
    created_by_user_id uuid,
    row_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    session_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoke_reason character varying(128),
    CONSTRAINT ck_refresh_tokens_expiry CHECK ((expires_at > issued_at))
);


--
-- Name: report_version_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.report_version_evidence (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    report_version_id uuid NOT NULL,
    evidence_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: report_version_findings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.report_version_findings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    report_version_id uuid NOT NULL,
    finding_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.subscriptions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    plan_code character varying(32) NOT NULL,
    billing_period_months integer NOT NULL,
    agreed_amount_usd numeric(12,2),
    status character varying(24) NOT NULL,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    external_reference character varying(128),
    terms_snapshot jsonb,
    confirmed_by_user_id uuid,
    confirmed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_subscriptions_billing_period CHECK ((billing_period_months >= 1 AND billing_period_months <= 120)),
    CONSTRAINT ck_subscriptions_plan CHECK (((plan_code)::text = 'ENTERPRISE'::text)),
    CONSTRAINT ck_subscriptions_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'PAST_DUE'::character varying, 'SUSPENDED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_subscriptions_window CHECK (((ends_at IS NULL) OR (starts_at IS NULL) OR (ends_at > starts_at)))
);

--
-- Name: payment_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.payment_transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    subscription_id uuid NOT NULL,
    paypal_order_id character varying(64) NOT NULL,
    paypal_capture_id character varying(64),
    amount numeric(12,2) NOT NULL,
    currency character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    status character varying(24) NOT NULL,
    payer_email character varying(255),
    captured_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_payment_transactions_status CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'PENDING'::character varying, 'DECLINED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.user_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    role character varying(64) NOT NULL,
    CONSTRAINT ck_user_roles_role CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'ORG_ADMIN'::character varying, 'INSPECTOR'::character varying, 'MAINTENANCE_ENGINEER'::character varying])::text[])))
);


--
-- Name: CONSTRAINT ck_user_roles_role ON user_roles; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_user_roles_role ON ${baseline_schema}.user_roles IS 'Target role vocabulary: ADMIN, ORG_ADMIN, INSPECTOR, MAINTENANCE_ENGINEER.';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(320) NOT NULL,
    normalized_email character varying(320) NOT NULL,
    full_name character varying(200) NOT NULL,
    password_hash character varying(1024),
    status character varying(32) NOT NULL,
    actor_zone character varying(32) NOT NULL,
    organization_id uuid,
    auth_version integer DEFAULT 0 NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    lockout_until timestamp with time zone,
    must_change_password boolean DEFAULT false NOT NULL,
    last_login_at timestamp with time zone,
    last_login_ip character varying(45),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    vision_fallback_enabled boolean DEFAULT true NOT NULL,
    phone_number character varying(32),
    avatar_url character varying(500),
    CONSTRAINT ck_users_actor_zone CHECK (((actor_zone)::text = ANY ((ARRAY['PLATFORM'::character varying, 'CUSTOMER_ORGANIZATION'::character varying])::text[]))),
    CONSTRAINT ck_users_failed_login_count CHECK ((failed_login_count >= 0)),
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_users_zone_organization CHECK (((((actor_zone)::text = 'CUSTOMER_ORGANIZATION'::text) AND (organization_id IS NOT NULL)) OR (((actor_zone)::text <> 'CUSTOMER_ORGANIZATION'::text) AND (organization_id IS NULL))))
);


--
-- Name: workforce_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ${baseline_schema}.workforce_credentials (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL,
    credential_type character varying(64) NOT NULL,
    issuer character varying(200),
    credential_reference character varying(200),
    issued_at timestamp with time zone,
    expires_at timestamp with time zone,
    status character varying(24) NOT NULL,
    evidence_id uuid,
    verified_by_user_id uuid,
    verified_at timestamp with time zone,
    verification_reason character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_workforce_credentials_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'ACTIVE'::character varying, 'EXPIRING_SOON'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: asset_categories asset_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_categories
    ADD CONSTRAINT asset_categories_pkey PRIMARY KEY (id);


--
-- Name: asset_documents asset_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_documents
    ADD CONSTRAINT asset_documents_pkey PRIMARY KEY (id);


--
-- Name: asset_pair_assignments asset_pair_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_pkey PRIMARY KEY (id);


--
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: auth_sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);


--
-- Name: checklist_items checklist_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_items
    ADD CONSTRAINT checklist_items_pkey PRIMARY KEY (id);


--
-- Name: checklist_responses checklist_responses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_pkey PRIMARY KEY (id);


--
-- Name: checklist_templates checklist_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_templates
    ADD CONSTRAINT checklist_templates_pkey PRIMARY KEY (id);


--
-- Name: drone_documents drone_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drone_documents
    ADD CONSTRAINT drone_documents_pkey PRIMARY KEY (id);


--
-- Name: drones drones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drones
    ADD CONSTRAINT drones_pkey PRIMARY KEY (id);


--
-- Name: event_publication event_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);


--
-- Name: evidence evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT evidence_pkey PRIMARY KEY (id);


--
-- Name: field_sessions field_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_pkey PRIMARY KEY (id);


--
-- Name: flight_permits flight_permits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT flight_permits_pkey PRIMARY KEY (id);


--
-- Name: inspection_preparations inspection_preparations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_preparations
    ADD CONSTRAINT inspection_preparations_pkey PRIMARY KEY (id);


--
-- Name: inspection_readiness_decisions inspection_readiness_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_readiness_decisions
    ADD CONSTRAINT inspection_readiness_decisions_pkey PRIMARY KEY (id);


--
-- Name: inspection_report_versions inspection_report_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_report_versions
    ADD CONSTRAINT inspection_report_versions_pkey PRIMARY KEY (id);


--
-- Name: inspection_reports inspection_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_reports
    ADD CONSTRAINT inspection_reports_pkey PRIMARY KEY (id);


--
-- Name: inspection_schedules inspection_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_schedules
    ADD CONSTRAINT inspection_schedules_pkey PRIMARY KEY (id);


--
-- Name: inspections inspections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_pkey PRIMARY KEY (id);


--
-- Name: maintenance_acceptance_decisions maintenance_acceptance_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_acceptance_decisions
    ADD CONSTRAINT maintenance_acceptance_decisions_pkey PRIMARY KEY (id);


--
-- Name: maintenance_change_orders maintenance_change_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_change_orders
    ADD CONSTRAINT maintenance_change_orders_pkey PRIMARY KEY (id);


--
-- Name: maintenance_cost_lines maintenance_cost_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_pkey PRIMARY KEY (id);


--
-- Name: maintenance_estimate_versions maintenance_estimate_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_estimate_versions
    ADD CONSTRAINT maintenance_estimate_versions_pkey PRIMARY KEY (id);


--
-- Name: maintenance_report_versions maintenance_report_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_report_versions
    ADD CONSTRAINT maintenance_report_versions_pkey PRIMARY KEY (id);


--
-- Name: maintenance_tasks maintenance_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_tasks
    ADD CONSTRAINT maintenance_tasks_pkey PRIMARY KEY (id);


--
-- Name: maintenance_team_members maintenance_team_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_team_members
    ADD CONSTRAINT maintenance_team_members_pkey PRIMARY KEY (id);


--
-- Name: maintenance_work_logs maintenance_work_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_logs
    ADD CONSTRAINT maintenance_work_logs_pkey PRIMARY KEY (id);


--
-- Name: maintenance_work_orders maintenance_work_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: report_version_evidence report_version_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_evidence
    ADD CONSTRAINT report_version_evidence_pkey PRIMARY KEY (id);


--
-- Name: report_version_findings report_version_findings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_findings
    ADD CONSTRAINT report_version_findings_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ${baseline_schema}.payment_transactions
    ADD CONSTRAINT payment_transactions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ${baseline_schema}.payment_transactions
    ADD CONSTRAINT uq_payment_transactions_capture UNIQUE (paypal_capture_id);


--
-- Name: asset_categories uq_asset_categories_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_categories
    ADD CONSTRAINT uq_asset_categories_code UNIQUE (code);


--
-- Name: asset_documents uq_asset_documents_checksum; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_documents
    ADD CONSTRAINT uq_asset_documents_checksum UNIQUE (asset_id, checksum_sha256);


--
-- Name: asset_documents uq_asset_documents_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_documents
    ADD CONSTRAINT uq_asset_documents_object_key UNIQUE (object_key);


--
-- Name: checklist_items uq_checklist_item_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_items
    ADD CONSTRAINT uq_checklist_item_code UNIQUE (template_id, item_code);


--
-- Name: checklist_items uq_checklist_item_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_items
    ADD CONSTRAINT uq_checklist_item_order UNIQUE (template_id, display_order);


--
-- Name: checklist_responses uq_checklist_responses_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT uq_checklist_responses_item UNIQUE (inspection_id, checklist_item_id);


--
-- Name: checklist_templates uq_checklist_template_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_templates
    ADD CONSTRAINT uq_checklist_template_version UNIQUE (template_key, version_number);


--
-- Name: drone_documents uq_drone_documents_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drone_documents
    ADD CONSTRAINT uq_drone_documents_object_key UNIQUE (object_key);


--
-- Name: drones uq_drones_organization_serial; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drones
    ADD CONSTRAINT uq_drones_organization_serial UNIQUE (organization_id, serial_number);


--
-- Name: evidence uq_evidence_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT uq_evidence_object_key UNIQUE (object_key);


--
-- Name: findings uq_findings_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.findings
    ADD CONSTRAINT uq_findings_code UNIQUE (inspection_id, finding_code);


--
-- Name: inspection_preparations uq_inspection_preparations_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_preparations
    ADD CONSTRAINT uq_inspection_preparations_version UNIQUE (inspection_id, preparation_version);


--
-- Name: inspection_report_versions uq_inspection_report_versions_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_report_versions
    ADD CONSTRAINT uq_inspection_report_versions_number UNIQUE (inspection_report_id, version_no);


--
-- Name: inspection_reports uq_inspection_reports_inspection; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_reports
    ADD CONSTRAINT uq_inspection_reports_inspection UNIQUE (inspection_id);


--
-- Name: maintenance_change_orders uq_maintenance_change_orders_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_change_orders
    ADD CONSTRAINT uq_maintenance_change_orders_number UNIQUE (work_order_id, change_number);


--
-- Name: maintenance_estimate_versions uq_maintenance_estimate_versions_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_estimate_versions
    ADD CONSTRAINT uq_maintenance_estimate_versions_number UNIQUE (work_order_id, version_no);


--
-- Name: maintenance_report_versions uq_maintenance_report_versions_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_report_versions
    ADD CONSTRAINT uq_maintenance_report_versions_number UNIQUE (work_order_id, version_no);


--
-- Name: maintenance_tasks uq_maintenance_tasks_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_tasks
    ADD CONSTRAINT uq_maintenance_tasks_number UNIQUE (work_order_id, task_number);


--
-- Name: refresh_tokens uq_refresh_tokens_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash);


--
-- Name: report_version_evidence uq_report_version_evidence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_evidence
    ADD CONSTRAINT uq_report_version_evidence UNIQUE (report_version_id, evidence_id);


--
-- Name: report_version_findings uq_report_version_findings; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_findings
    ADD CONSTRAINT uq_report_version_findings UNIQUE (report_version_id, finding_id);


--
-- Name: user_roles uq_user_roles; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.user_roles
    ADD CONSTRAINT uq_user_roles UNIQUE (user_id, role);


--
-- Name: users uq_users_normalized_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.users
    ADD CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: findings verified_findings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.findings
    ADD CONSTRAINT verified_findings_pkey PRIMARY KEY (id);


--
-- Name: workforce_credentials workforce_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.workforce_credentials
    ADD CONSTRAINT workforce_credentials_pkey PRIMARY KEY (id);


--
-- Name: event_publication_by_completion_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_publication_by_completion_date_idx ON ${baseline_schema}.event_publication USING btree (completion_date);


--
-- Name: event_publication_serialized_event_hash_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_publication_serialized_event_hash_idx ON ${baseline_schema}.event_publication USING hash (serialized_event);


--
-- Name: ix_asset_documents_asset_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_asset_documents_asset_time ON ${baseline_schema}.asset_documents USING btree (asset_id, created_at DESC);


--
-- Name: ix_asset_pair_assignments_asset_status_from; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_asset_pair_assignments_asset_status_from ON ${baseline_schema}.asset_pair_assignments USING btree (asset_id, status, valid_from);


--
-- Name: ix_assets_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_assets_category ON ${baseline_schema}.assets USING btree (category_id);


--
-- Name: ix_assets_organization_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_assets_organization_status ON ${baseline_schema}.assets USING btree (organization_id, status, name);


--
-- Name: ix_audit_events_org_aggregate_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_events_org_aggregate_time ON ${baseline_schema}.audit_events USING btree (organization_id, aggregate_type, aggregate_id, created_at);


--
-- Name: ix_auth_sessions_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_active ON ${baseline_schema}.auth_sessions USING btree (user_id, revoked_at);


--
-- Name: ix_auth_sessions_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_user ON ${baseline_schema}.auth_sessions USING btree (user_id);


--
-- Name: ix_checklist_items_template; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_checklist_items_template ON ${baseline_schema}.checklist_items USING btree (template_id);


--
-- Name: ix_checklist_responses_inspection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_checklist_responses_inspection ON ${baseline_schema}.checklist_responses USING btree (inspection_id, completed_at);


--
-- Name: ix_checklist_templates_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_checklist_templates_category ON ${baseline_schema}.checklist_templates USING btree (asset_category_id);


--
-- Name: ix_checklist_templates_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_checklist_templates_status ON ${baseline_schema}.checklist_templates USING btree (status);


--
-- Name: ix_drone_documents_drone_status_validity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drone_documents_drone_status_validity ON ${baseline_schema}.drone_documents USING btree (drone_id, status, valid_until);


--
-- Name: ix_drones_organization_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drones_organization_status ON ${baseline_schema}.drones USING btree (organization_id, serviceability);


--
-- Name: ix_evidence_inspection_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_inspection_time ON ${baseline_schema}.evidence USING btree (inspection_id, created_at DESC) WHERE (inspection_id IS NOT NULL);


--
-- Name: ix_evidence_work_log_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_work_log_time ON ${baseline_schema}.evidence USING btree (maintenance_work_log_id, created_at DESC) WHERE (maintenance_work_log_id IS NOT NULL);


--
-- Name: ix_field_sessions_inspection_status_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_field_sessions_inspection_status_started ON ${baseline_schema}.field_sessions USING btree (inspection_id, status, started_at);


--
-- Name: ix_findings_evidence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_findings_evidence ON ${baseline_schema}.findings USING btree (evidence_id);


--
-- Name: ix_findings_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_findings_inspection_status ON ${baseline_schema}.findings USING btree (inspection_id, status, severity);


--
-- Name: ix_flight_permits_org_status_validity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_flight_permits_org_status_validity ON ${baseline_schema}.flight_permits USING btree (organization_id, status, valid_from, valid_until);


--
-- Name: ix_inspection_preparations_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_preparations_inspection_status ON ${baseline_schema}.inspection_preparations USING btree (inspection_id, status);


--
-- Name: ix_inspection_report_versions_report_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_report_versions_report_status ON ${baseline_schema}.inspection_report_versions USING btree (inspection_report_id, status, version_no DESC);


--
-- Name: ix_inspection_reports_author_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_reports_author_status ON ${baseline_schema}.inspection_reports USING btree (author_user_id, status, updated_at DESC);


--
-- Name: ix_inspection_schedules_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_schedules_asset ON ${baseline_schema}.inspection_schedules USING btree (asset_id);


--
-- Name: ix_inspection_schedules_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_schedules_due ON ${baseline_schema}.inspection_schedules USING btree (status, next_due_at);


--
-- Name: ix_inspections_org_asset_status_planned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_org_asset_status_planned ON ${baseline_schema}.inspections USING btree (organization_id, asset_id, status, planned_start_at);


--
-- Name: ix_maintenance_acceptance_work_order_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_acceptance_work_order_time ON ${baseline_schema}.maintenance_acceptance_decisions USING btree (work_order_id, decided_at DESC);


--
-- Name: ix_maintenance_change_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_change_orders_status ON ${baseline_schema}.maintenance_change_orders USING btree (work_order_id, status);


--
-- Name: ix_maintenance_cost_lines_work_order_kind_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_cost_lines_work_order_kind_state ON ${baseline_schema}.maintenance_cost_lines USING btree (work_order_id, line_kind, state);


--
-- Name: ix_maintenance_estimate_versions_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_estimate_versions_status ON ${baseline_schema}.maintenance_estimate_versions USING btree (work_order_id, status);


--
-- Name: ix_maintenance_report_versions_work_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_report_versions_work_order ON ${baseline_schema}.maintenance_report_versions USING btree (work_order_id, version_no DESC);


--
-- Name: ix_maintenance_tasks_work_order_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_tasks_work_order_status ON ${baseline_schema}.maintenance_tasks USING btree (work_order_id, status);


--
-- Name: ix_maintenance_team_members_work_order_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_team_members_work_order_active ON ${baseline_schema}.maintenance_team_members USING btree (work_order_id, active);


--
-- Name: ix_maintenance_work_logs_engineer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_work_logs_engineer_status ON ${baseline_schema}.maintenance_work_logs USING btree (engineer_user_id, status, started_at DESC);


--
-- Name: ix_maintenance_work_orders_org_status_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_work_orders_org_status_due ON ${baseline_schema}.maintenance_work_orders USING btree (organization_id, status, due_at);


--
-- Name: ix_maintenance_work_orders_source_finding_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_maintenance_work_orders_source_finding_status ON ${baseline_schema}.maintenance_work_orders USING btree (source_finding_id, status);


--
-- Name: ix_notifications_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_delivery ON ${baseline_schema}.notifications USING btree (status, created_at);


--
-- Name: ix_notifications_recipient_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_recipient_status ON ${baseline_schema}.notifications USING btree (recipient_user_id, status, created_at DESC);


--
-- Name: ix_readiness_decisions_inspection_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_readiness_decisions_inspection_time ON ${baseline_schema}.inspection_readiness_decisions USING btree (inspection_id, decided_at DESC);


--
-- Name: ix_refresh_tokens_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_refresh_tokens_session ON ${baseline_schema}.refresh_tokens USING btree (session_id);


--
-- Name: ix_report_version_evidence_evidence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_report_version_evidence_evidence ON ${baseline_schema}.report_version_evidence USING btree (evidence_id);


--
-- Name: ix_report_version_findings_finding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_report_version_findings_finding ON ${baseline_schema}.report_version_findings USING btree (finding_id);


--
-- Name: ix_subscriptions_organization_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_subscriptions_organization_status ON ${baseline_schema}.subscriptions USING btree (organization_id, status);


--
-- Name: ix_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_roles_role ON ${baseline_schema}.user_roles USING btree (role);


--
-- Name: ix_user_roles_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_user_roles_user ON ${baseline_schema}.user_roles USING btree (user_id);


--
-- Name: ix_users_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_organization ON ${baseline_schema}.users USING btree (organization_id);


--
-- Name: ix_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_status ON ${baseline_schema}.users USING btree (status);


--
-- Name: ix_workforce_credentials_org_user_status_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_workforce_credentials_org_user_status_expiry ON ${baseline_schema}.workforce_credentials USING btree (organization_id, user_id, status, expires_at);


--
-- Name: uq_asset_pair_assignments_active_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asset_pair_assignments_active_asset ON ${baseline_schema}.asset_pair_assignments USING btree (asset_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_asset_pair_assignments_id_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asset_pair_assignments_id_scope ON ${baseline_schema}.asset_pair_assignments USING btree (id, organization_id, asset_id);


--
-- Name: uq_assets_id_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_assets_id_organization ON ${baseline_schema}.assets USING btree (id, organization_id);


--
-- Name: uq_assets_organization_asset_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_assets_organization_asset_code ON ${baseline_schema}.assets USING btree (organization_id, asset_code) WHERE (asset_code IS NOT NULL);


--
-- Name: uq_drones_id_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drones_id_organization ON ${baseline_schema}.drones USING btree (id, organization_id);


--
-- Name: uq_inspection_schedules_id_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inspection_schedules_id_asset ON ${baseline_schema}.inspection_schedules USING btree (id, asset_id);


--
-- Name: uq_inspections_id_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inspections_id_organization ON ${baseline_schema}.inspections USING btree (id, organization_id);


--
-- Name: uq_inspections_schedule_due_cycle; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inspections_schedule_due_cycle ON ${baseline_schema}.inspections USING btree (schedule_id, due_cycle_key) WHERE ((schedule_id IS NOT NULL) AND (due_cycle_key IS NOT NULL));


--
-- Name: uq_maintenance_team_lead_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_maintenance_team_lead_active ON ${baseline_schema}.maintenance_team_members USING btree (work_order_id) WHERE (active AND ((member_role)::text = 'LEAD'::text));


--
-- Name: uq_maintenance_team_report_author_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_maintenance_team_report_author_active ON ${baseline_schema}.maintenance_team_members USING btree (work_order_id) WHERE (active AND ((member_role)::text = 'REPORT_AUTHOR'::text));


--
-- Name: uq_organizations_registration_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_organizations_registration_code ON ${baseline_schema}.organizations USING btree (registration_code) WHERE (registration_code IS NOT NULL);


--
-- Name: uq_subscriptions_active_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_subscriptions_active_organization ON ${baseline_schema}.subscriptions USING btree (organization_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_users_id_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_id_organization ON ${baseline_schema}.users USING btree (id, organization_id);


--
-- Name: asset_categories asset_categories_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_categories
    ADD CONSTRAINT asset_categories_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: asset_documents asset_documents_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_documents
    ADD CONSTRAINT asset_documents_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: asset_documents asset_documents_uploaded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_documents
    ADD CONSTRAINT asset_documents_uploaded_by_user_id_fkey FOREIGN KEY (uploaded_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments asset_pair_assignments_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments asset_pair_assignments_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments asset_pair_assignments_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES ${baseline_schema}.drones(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments asset_pair_assignments_inspector_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_inspector_user_id_fkey FOREIGN KEY (inspector_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments asset_pair_assignments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT asset_pair_assignments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: assets assets_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.assets
    ADD CONSTRAINT assets_category_id_fkey FOREIGN KEY (category_id) REFERENCES ${baseline_schema}.asset_categories(id) ON DELETE RESTRICT;


--
-- Name: assets assets_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.assets
    ADD CONSTRAINT assets_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: audit_events audit_events_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.audit_events
    ADD CONSTRAINT audit_events_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE SET NULL;


--
-- Name: audit_events audit_events_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.audit_events
    ADD CONSTRAINT audit_events_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: auth_sessions auth_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE CASCADE;


--
-- Name: checklist_items checklist_items_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_items
    ADD CONSTRAINT checklist_items_template_id_fkey FOREIGN KEY (template_id) REFERENCES ${baseline_schema}.checklist_templates(id) ON DELETE CASCADE;


--
-- Name: checklist_responses checklist_responses_checklist_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_checklist_item_id_fkey FOREIGN KEY (checklist_item_id) REFERENCES ${baseline_schema}.checklist_items(id) ON DELETE RESTRICT;


--
-- Name: checklist_responses checklist_responses_completed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_completed_by_user_id_fkey FOREIGN KEY (completed_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: checklist_responses checklist_responses_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES ${baseline_schema}.field_sessions(id) ON DELETE RESTRICT;


--
-- Name: checklist_responses checklist_responses_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE CASCADE;


--
-- Name: checklist_responses checklist_responses_responder_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT checklist_responses_responder_user_id_fkey FOREIGN KEY (responder_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: checklist_templates checklist_templates_asset_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_templates
    ADD CONSTRAINT checklist_templates_asset_category_id_fkey FOREIGN KEY (asset_category_id) REFERENCES ${baseline_schema}.asset_categories(id) ON DELETE RESTRICT;


--
-- Name: checklist_templates checklist_templates_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_templates
    ADD CONSTRAINT checklist_templates_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: drone_documents drone_documents_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drone_documents
    ADD CONSTRAINT drone_documents_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES ${baseline_schema}.drones(id) ON DELETE RESTRICT;


--
-- Name: drone_documents drone_documents_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drone_documents
    ADD CONSTRAINT drone_documents_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: drone_documents drone_documents_uploaded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drone_documents
    ADD CONSTRAINT drone_documents_uploaded_by_user_id_fkey FOREIGN KEY (uploaded_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: drones drones_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.drones
    ADD CONSTRAINT drones_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: evidence evidence_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT evidence_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES ${baseline_schema}.field_sessions(id) ON DELETE RESTRICT;


--
-- Name: evidence evidence_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT evidence_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_checklist_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_checklist_template_id_fkey FOREIGN KEY (checklist_template_id) REFERENCES ${baseline_schema}.checklist_templates(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES ${baseline_schema}.drones(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_inspector_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_inspector_user_id_fkey FOREIGN KEY (inspector_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: field_sessions field_sessions_readiness_decision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.field_sessions
    ADD CONSTRAINT field_sessions_readiness_decision_id_fkey FOREIGN KEY (readiness_decision_id) REFERENCES ${baseline_schema}.inspection_readiness_decisions(id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments fk_asset_pair_assignments_asset_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT fk_asset_pair_assignments_asset_tenant FOREIGN KEY (asset_id, organization_id) REFERENCES ${baseline_schema}.assets(id, organization_id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments fk_asset_pair_assignments_drone_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT fk_asset_pair_assignments_drone_tenant FOREIGN KEY (drone_id, organization_id) REFERENCES ${baseline_schema}.drones(id, organization_id) ON DELETE RESTRICT;


--
-- Name: asset_pair_assignments fk_asset_pair_assignments_inspector_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.asset_pair_assignments
    ADD CONSTRAINT fk_asset_pair_assignments_inspector_tenant FOREIGN KEY (inspector_user_id, organization_id) REFERENCES ${baseline_schema}.users(id, organization_id) ON DELETE RESTRICT;


--
-- Name: checklist_responses fk_checklist_responses_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.checklist_responses
    ADD CONSTRAINT fk_checklist_responses_evidence FOREIGN KEY (evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: evidence fk_evidence_inspection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT fk_evidence_inspection FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: evidence fk_evidence_target_task; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT fk_evidence_target_task FOREIGN KEY (maintenance_task_id) REFERENCES ${baseline_schema}.maintenance_tasks(id) ON DELETE RESTRICT;


--
-- Name: evidence fk_evidence_target_work_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT fk_evidence_target_work_order FOREIGN KEY (maintenance_work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: evidence fk_evidence_work_log; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.evidence
    ADD CONSTRAINT fk_evidence_work_log FOREIGN KEY (maintenance_work_log_id) REFERENCES ${baseline_schema}.maintenance_work_logs(id) ON DELETE RESTRICT;


--
-- Name: flight_permits fk_flight_permits_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT fk_flight_permits_evidence FOREIGN KEY (source_evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: flight_permits fk_flight_permits_source_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT fk_flight_permits_source_evidence FOREIGN KEY (source_evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: inspections fk_inspections_asset_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT fk_inspections_asset_tenant FOREIGN KEY (asset_id, organization_id) REFERENCES ${baseline_schema}.assets(id, organization_id) ON DELETE RESTRICT;


--
-- Name: inspections fk_inspections_drone_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT fk_inspections_drone_tenant FOREIGN KEY (drone_id, organization_id) REFERENCES ${baseline_schema}.drones(id, organization_id) ON DELETE RESTRICT;


--
-- Name: inspections fk_inspections_inspector_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT fk_inspections_inspector_tenant FOREIGN KEY (inspector_id, organization_id) REFERENCES ${baseline_schema}.users(id, organization_id) ON DELETE RESTRICT;


--
-- Name: inspections fk_inspections_pair_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT fk_inspections_pair_tenant FOREIGN KEY (asset_pair_assignment_id, organization_id, asset_id) REFERENCES ${baseline_schema}.asset_pair_assignments(id, organization_id, asset_id) ON DELETE RESTRICT;


--
-- Name: inspections fk_inspections_schedule_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT fk_inspections_schedule_asset FOREIGN KEY (schedule_id, asset_id) REFERENCES ${baseline_schema}.inspection_schedules(id, asset_id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_logs fk_maintenance_work_logs_target_task; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_logs
    ADD CONSTRAINT fk_maintenance_work_logs_target_task FOREIGN KEY (task_id) REFERENCES ${baseline_schema}.maintenance_tasks(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders fk_maintenance_work_orders_asset_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT fk_maintenance_work_orders_asset_tenant FOREIGN KEY (asset_id, organization_id) REFERENCES ${baseline_schema}.assets(id, organization_id) ON DELETE RESTRICT;


--
-- Name: findings fk_verified_finding_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.findings
    ADD CONSTRAINT fk_verified_finding_evidence FOREIGN KEY (evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: workforce_credentials fk_workforce_credentials_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.workforce_credentials
    ADD CONSTRAINT fk_workforce_credentials_evidence FOREIGN KEY (evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: flight_permits flight_permits_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT flight_permits_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: flight_permits flight_permits_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT flight_permits_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: flight_permits flight_permits_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT flight_permits_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: flight_permits flight_permits_source_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.flight_permits
    ADD CONSTRAINT flight_permits_source_evidence_id_fkey FOREIGN KEY (source_evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: inspection_preparations inspection_preparations_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_preparations
    ADD CONSTRAINT inspection_preparations_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: inspection_preparations inspection_preparations_inspector_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_preparations
    ADD CONSTRAINT inspection_preparations_inspector_user_id_fkey FOREIGN KEY (inspector_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: inspection_readiness_decisions inspection_readiness_decisions_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_readiness_decisions
    ADD CONSTRAINT inspection_readiness_decisions_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: inspection_readiness_decisions inspection_readiness_decisions_preparation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_readiness_decisions
    ADD CONSTRAINT inspection_readiness_decisions_preparation_id_fkey FOREIGN KEY (preparation_id) REFERENCES ${baseline_schema}.inspection_preparations(id) ON DELETE RESTRICT;


--
-- Name: inspection_readiness_decisions inspection_readiness_decisions_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_readiness_decisions
    ADD CONSTRAINT inspection_readiness_decisions_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: inspection_report_versions inspection_report_versions_inspection_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_report_versions
    ADD CONSTRAINT inspection_report_versions_inspection_report_id_fkey FOREIGN KEY (inspection_report_id) REFERENCES ${baseline_schema}.inspection_reports(id) ON DELETE RESTRICT;


--
-- Name: inspection_report_versions inspection_report_versions_reviewer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_report_versions
    ADD CONSTRAINT inspection_report_versions_reviewer_user_id_fkey FOREIGN KEY (reviewer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: inspection_report_versions inspection_report_versions_source_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_report_versions
    ADD CONSTRAINT inspection_report_versions_source_version_id_fkey FOREIGN KEY (source_version_id) REFERENCES ${baseline_schema}.inspection_report_versions(id) ON DELETE RESTRICT;


--
-- Name: inspection_reports inspection_reports_author_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_reports
    ADD CONSTRAINT inspection_reports_author_user_id_fkey FOREIGN KEY (author_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: inspection_reports inspection_reports_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_reports
    ADD CONSTRAINT inspection_reports_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: inspection_schedules inspection_schedules_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspection_schedules
    ADD CONSTRAINT inspection_schedules_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_asset_pair_assignment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_asset_pair_assignment_id_fkey FOREIGN KEY (asset_pair_assignment_id) REFERENCES ${baseline_schema}.asset_pair_assignments(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES ${baseline_schema}.drones(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_inspector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_inspector_id_fkey FOREIGN KEY (inspector_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_schedule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.inspections
    ADD CONSTRAINT inspections_schedule_id_fkey FOREIGN KEY (schedule_id) REFERENCES ${baseline_schema}.inspection_schedules(id) ON DELETE RESTRICT;


--
-- Name: maintenance_acceptance_decisions maintenance_acceptance_decisions_report_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_acceptance_decisions
    ADD CONSTRAINT maintenance_acceptance_decisions_report_version_id_fkey FOREIGN KEY (report_version_id) REFERENCES ${baseline_schema}.maintenance_report_versions(id) ON DELETE RESTRICT;


--
-- Name: maintenance_acceptance_decisions maintenance_acceptance_decisions_reviewer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_acceptance_decisions
    ADD CONSTRAINT maintenance_acceptance_decisions_reviewer_user_id_fkey FOREIGN KEY (reviewer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_acceptance_decisions maintenance_acceptance_decisions_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_acceptance_decisions
    ADD CONSTRAINT maintenance_acceptance_decisions_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_change_orders maintenance_change_orders_decided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_change_orders
    ADD CONSTRAINT maintenance_change_orders_decided_by_user_id_fkey FOREIGN KEY (decided_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_change_orders maintenance_change_orders_requested_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_change_orders
    ADD CONSTRAINT maintenance_change_orders_requested_by_user_id_fkey FOREIGN KEY (requested_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_change_orders maintenance_change_orders_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_change_orders
    ADD CONSTRAINT maintenance_change_orders_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_cost_lines maintenance_cost_lines_change_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_change_order_id_fkey FOREIGN KEY (change_order_id) REFERENCES ${baseline_schema}.maintenance_change_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_cost_lines maintenance_cost_lines_entered_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_entered_by_user_id_fkey FOREIGN KEY (entered_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_cost_lines maintenance_cost_lines_estimate_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_estimate_version_id_fkey FOREIGN KEY (estimate_version_id) REFERENCES ${baseline_schema}.maintenance_estimate_versions(id) ON DELETE RESTRICT;


--
-- Name: maintenance_cost_lines maintenance_cost_lines_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_task_id_fkey FOREIGN KEY (task_id) REFERENCES ${baseline_schema}.maintenance_tasks(id) ON DELETE RESTRICT;


--
-- Name: maintenance_cost_lines maintenance_cost_lines_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_cost_lines
    ADD CONSTRAINT maintenance_cost_lines_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_estimate_versions maintenance_estimate_versions_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_estimate_versions
    ADD CONSTRAINT maintenance_estimate_versions_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_estimate_versions maintenance_estimate_versions_prepared_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_estimate_versions
    ADD CONSTRAINT maintenance_estimate_versions_prepared_by_user_id_fkey FOREIGN KEY (prepared_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_estimate_versions maintenance_estimate_versions_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_estimate_versions
    ADD CONSTRAINT maintenance_estimate_versions_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_report_versions maintenance_report_versions_author_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_report_versions
    ADD CONSTRAINT maintenance_report_versions_author_user_id_fkey FOREIGN KEY (author_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_report_versions maintenance_report_versions_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_report_versions
    ADD CONSTRAINT maintenance_report_versions_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_tasks maintenance_tasks_assigned_engineer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_tasks
    ADD CONSTRAINT maintenance_tasks_assigned_engineer_user_id_fkey FOREIGN KEY (assigned_engineer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_tasks maintenance_tasks_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_tasks
    ADD CONSTRAINT maintenance_tasks_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_team_members maintenance_team_members_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_team_members
    ADD CONSTRAINT maintenance_team_members_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_team_members maintenance_team_members_engineer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_team_members
    ADD CONSTRAINT maintenance_team_members_engineer_user_id_fkey FOREIGN KEY (engineer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_team_members maintenance_team_members_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_team_members
    ADD CONSTRAINT maintenance_team_members_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_logs maintenance_work_logs_engineer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_logs
    ADD CONSTRAINT maintenance_work_logs_engineer_user_id_fkey FOREIGN KEY (engineer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_logs maintenance_work_logs_work_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_logs
    ADD CONSTRAINT maintenance_work_logs_work_order_id_fkey FOREIGN KEY (work_order_id) REFERENCES ${baseline_schema}.maintenance_work_orders(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_accepting_reviewer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_accepting_reviewer_user_id_fkey FOREIGN KEY (accepting_reviewer_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES ${baseline_schema}.assets(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_budget_approver_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_budget_approver_user_id_fkey FOREIGN KEY (budget_approver_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_report_author_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_report_author_user_id_fkey FOREIGN KEY (report_author_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_source_finding_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_source_finding_id_fkey FOREIGN KEY (source_finding_id) REFERENCES ${baseline_schema}.findings(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_source_report_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_source_report_version_id_fkey FOREIGN KEY (source_report_version_id) REFERENCES ${baseline_schema}.inspection_report_versions(id) ON DELETE RESTRICT;


--
-- Name: maintenance_work_orders maintenance_work_orders_team_lead_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.maintenance_work_orders
    ADD CONSTRAINT maintenance_work_orders_team_lead_user_id_fkey FOREIGN KEY (team_lead_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: notifications notifications_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.notifications
    ADD CONSTRAINT notifications_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE SET NULL;


--
-- Name: notifications notifications_recipient_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.notifications
    ADD CONSTRAINT notifications_recipient_user_id_fkey FOREIGN KEY (recipient_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: organizations organizations_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.organizations
    ADD CONSTRAINT organizations_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: refresh_tokens refresh_tokens_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.refresh_tokens
    ADD CONSTRAINT refresh_tokens_session_id_fkey FOREIGN KEY (session_id) REFERENCES ${baseline_schema}.auth_sessions(id) ON DELETE CASCADE;


--
-- Name: report_version_evidence report_version_evidence_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_evidence
    ADD CONSTRAINT report_version_evidence_evidence_id_fkey FOREIGN KEY (evidence_id) REFERENCES ${baseline_schema}.evidence(id) ON DELETE RESTRICT;


--
-- Name: report_version_evidence report_version_evidence_report_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_evidence
    ADD CONSTRAINT report_version_evidence_report_version_id_fkey FOREIGN KEY (report_version_id) REFERENCES ${baseline_schema}.inspection_report_versions(id) ON DELETE RESTRICT;


--
-- Name: report_version_findings report_version_findings_finding_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_findings
    ADD CONSTRAINT report_version_findings_finding_id_fkey FOREIGN KEY (finding_id) REFERENCES ${baseline_schema}.findings(id) ON DELETE RESTRICT;


--
-- Name: report_version_findings report_version_findings_report_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.report_version_findings
    ADD CONSTRAINT report_version_findings_report_version_id_fkey FOREIGN KEY (report_version_id) REFERENCES ${baseline_schema}.inspection_report_versions(id) ON DELETE RESTRICT;


--
-- Name: subscriptions subscriptions_confirmed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.subscriptions
    ADD CONSTRAINT subscriptions_confirmed_by_user_id_fkey FOREIGN KEY (confirmed_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: subscriptions subscriptions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.subscriptions
    ADD CONSTRAINT subscriptions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;

ALTER TABLE ONLY ${baseline_schema}.payment_transactions
    ADD CONSTRAINT fk_payment_transactions_subscription FOREIGN KEY (subscription_id) REFERENCES ${baseline_schema}.subscriptions(id) ON DELETE RESTRICT;

CREATE INDEX ix_payment_transactions_subscription ON ${baseline_schema}.payment_transactions USING btree (subscription_id);
CREATE INDEX ix_payment_transactions_order ON ${baseline_schema}.payment_transactions USING btree (paypal_order_id);


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE CASCADE;


--
-- Name: users users_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.users
    ADD CONSTRAINT users_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE SET NULL;


--
-- Name: findings verified_findings_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.findings
    ADD CONSTRAINT verified_findings_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES ${baseline_schema}.inspections(id) ON DELETE RESTRICT;


--
-- Name: workforce_credentials workforce_credentials_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.workforce_credentials
    ADD CONSTRAINT workforce_credentials_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES ${baseline_schema}.organizations(id) ON DELETE RESTRICT;


--
-- Name: workforce_credentials workforce_credentials_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.workforce_credentials
    ADD CONSTRAINT workforce_credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- Name: workforce_credentials workforce_credentials_verified_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ${baseline_schema}.workforce_credentials
    ADD CONSTRAINT workforce_credentials_verified_by_user_id_fkey FOREIGN KEY (verified_by_user_id) REFERENCES ${baseline_schema}.users(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--
