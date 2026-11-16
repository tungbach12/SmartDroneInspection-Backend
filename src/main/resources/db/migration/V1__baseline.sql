-- Consolidated V1 Baseline Migration (schema + catalog seeds)
-- Includes standalone Re-inspection workflow and cleaned schema after dropping legacy/redundant columns.

--
-- PostgreSQL database dump
--




--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END $$;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: prevent_accepted_service_quote_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION prevent_accepted_service_quote_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM service_requests request
        WHERE request.accepted_quote_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'Accepted service quote terms are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;




--
-- Name: enforce_service_request_message_body_or_attachment(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION enforce_service_request_message_body_or_attachment() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_message_id UUID;
    target_body TEXT;
BEGIN
    IF TG_TABLE_NAME = 'service_request_messages' THEN
        target_message_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_message_id := COALESCE(NEW.message_id, OLD.message_id);
    END IF;
    SELECT body INTO target_body
      FROM service_request_messages
      WHERE id = target_message_id;

    IF FOUND
       AND length(btrim(target_body)) = 0
       AND NOT EXISTS (
           SELECT 1
             FROM service_request_message_attachments
             WHERE message_id = target_message_id
       ) THEN
        RAISE EXCEPTION 'A service request message requires text or at least one attachment'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_service_request_messages_body_or_attachment';
    END IF;
    RETURN NULL;
END;
$$;




--
-- Name: asset_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE asset_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(2000),
    active boolean DEFAULT true NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_asset_categories_code_normalized CHECK (((code)::text = upper(btrim((code)::text))))
);


--
-- Name: asset_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE asset_documents (
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
    CONSTRAINT ck_asset_documents_size CHECK ((size_bytes > 0))
);


--
-- Name: assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE assets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
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
    location jsonb,
    technical_profile jsonb,
    inspection_items jsonb DEFAULT '[]'::jsonb NOT NULL,
    structure_height_meters numeric(8,2),
    footprint_area_square_meters numeric(12,2),
    owner_customer_id uuid NOT NULL,
    CONSTRAINT ck_assets_footprint_area_nonnegative CHECK (((footprint_area_square_meters IS NULL) OR (footprint_area_square_meters >= (0)::numeric))),
    CONSTRAINT ck_assets_latitude CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT ck_assets_longitude CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT ck_assets_status CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('INACTIVE'::character varying)::text, ('RETIRED'::character varying)::text]))),
    CONSTRAINT ck_assets_structure_dimensions_nonnegative CHECK ((((structure_height_meters IS NULL) OR (structure_height_meters >= (0)::numeric)) AND ((footprint_area_square_meters IS NULL) OR (footprint_area_square_meters >= (0)::numeric)))),
    CONSTRAINT ck_assets_structure_height_nonnegative CHECK (((structure_height_meters IS NULL) OR (structure_height_meters >= (0)::numeric)))
);


--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id uuid,
    action character varying(128) NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id uuid NOT NULL,
    before_status character varying(64),
    after_status character varying(64),
    aggregate_version bigint,
    trace_id character varying(128),
    safe_metadata jsonb,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE auth_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    client_type character varying(16) NOT NULL,
    token_hash character varying(64),
    previous_token_hash character varying(64),
    issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(128),
    CONSTRAINT ck_auth_sessions_client CHECK (((client_type)::text = ANY (ARRAY[('WEB'::character varying)::text, ('MOBILE'::character varying)::text])))
);


--
-- Name: checklist_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE checklist_templates (
    id uuid NOT NULL,
    template_type character varying(32) NOT NULL,
    label character varying(300) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    required boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_checklist_templates_type CHECK (((template_type)::text = ANY ((ARRAY['PRE_FLIGHT'::character varying, 'POST_FLIGHT'::character varying])::text[])))
);


--
-- Name: drone_document_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE drone_document_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(64) NOT NULL,
    title_vi character varying(255) NOT NULL,
    title_en character varying(255) NOT NULL,
    description_vi character varying(1000),
    description_en character varying(1000),
    is_required boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: drone_feature_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE drone_feature_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(64) NOT NULL,
    name_vi character varying(255) NOT NULL,
    name_en character varying(255) NOT NULL,
    description_vi character varying(500),
    description_en character varying(500),
    category character varying(32) DEFAULT 'SAFETY'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: drone_registration_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE drone_registration_applications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspector_user_id uuid NOT NULL,
    serial_number character varying(128) NOT NULL,
    model character varying(1000),
    manufacturer character varying(200),
    registration_number character varying(100) NOT NULL,
    registration_file_key character varying(1000) NOT NULL,
    registration_expires_at timestamp with time zone NOT NULL,
    status character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    review_note character varying(2000),
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    drone_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    document_files jsonb,
    max_wind_resistance_mps numeric(4,1),
    optical_zoom_level character varying(32),
    image_file_keys jsonb,
    features jsonb,
    max_altitude_meters numeric(8,1),
    CONSTRAINT ck_drone_registration_applications_max_altitude_nonnegative CHECK (((max_altitude_meters IS NULL) OR (max_altitude_meters >= (0)::numeric))),
    CONSTRAINT drone_registration_applications_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: drones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE drones (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    serial_number character varying(128) NOT NULL,
    model character varying(1000),
    manufacturer character varying(200),
    payload_metadata jsonb,
    serviceability character varying(24) NOT NULL,
    notes character varying(2000),
    registration_number character varying(100),
    registration_file_key character varying(1000),
    registration_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    inspector_owner_id uuid,
    document_files jsonb,
    max_wind_resistance_mps numeric(4,1),
    optical_zoom_level character varying(32),
    image_file_keys jsonb,
    features jsonb,
    max_altitude_meters numeric(8,1),
    owner_customer_id uuid,
    CONSTRAINT ck_drones_max_altitude_nonnegative CHECK (((max_altitude_meters IS NULL) OR (max_altitude_meters >= (0)::numeric))),
    CONSTRAINT ck_drones_owner_scope CHECK ((((owner_customer_id IS NOT NULL) AND (inspector_owner_id IS NULL)) OR ((owner_customer_id IS NULL) AND (inspector_owner_id IS NOT NULL)))),
    CONSTRAINT ck_drones_serviceability CHECK (((serviceability)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('MAINTENANCE'::character varying)::text, ('SUSPENDED'::character varying)::text, ('RETIRED'::character varying)::text])))
);


--
-- Name: event_publication; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE event_publication (
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

CREATE TABLE evidence (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    field_session_id uuid,
    file_name character varying(500) NOT NULL,
    content_type character varying(160) NOT NULL,
    size_bytes bigint NOT NULL,
    object_key character varying(1000) NOT NULL,
    capture_time timestamp with time zone,
    latitude numeric(9,6),
    longitude numeric(9,6),
    location_description character varying(1000),
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    original_size_bytes bigint,
    processing_status character varying(32) DEFAULT 'READY'::character varying NOT NULL,
    processing_error text,
    raw_object_key character varying(1000),
    thumbnail_object_key character varying(1000),
    thumbnail_size_bytes bigint,
    media_width integer,
    media_height integer,
    duration_seconds double precision,
    processed_at timestamp with time zone,
    processing_attempts integer DEFAULT 0 NOT NULL,
    processing_locked_at timestamp with time zone,
    source_video_evidence_id uuid,
    source_timestamp_seconds double precision,
    CONSTRAINT ck_evidence_content_type CHECK ((btrim((content_type)::text) <> ''::text)),
    CONSTRAINT ck_evidence_file_name CHECK ((btrim((file_name)::text) <> ''::text)),
    CONSTRAINT ck_evidence_latitude CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT ck_evidence_longitude CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT ck_evidence_object_key CHECK ((btrim((object_key)::text) <> ''::text)),
    CONSTRAINT ck_evidence_processing_status CHECK (((processing_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_evidence_size CHECK ((size_bytes > 0))
);


--
-- Name: evidence_upload_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE evidence_upload_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    field_session_id uuid,
    created_by_user_id uuid NOT NULL,
    file_name character varying(500) NOT NULL,
    content_type character varying(160) NOT NULL,
    declared_size_bytes bigint NOT NULL,
    chunk_size_bytes integer NOT NULL,
    total_parts integer NOT NULL,
    status character varying(32) DEFAULT 'OPEN'::character varying NOT NULL,
    storage_upload_id character varying(500),
    raw_object_key character varying(1000) NOT NULL,
    evidence_id uuid,
    capture_time timestamp with time zone,
    latitude numeric(9,6),
    longitude numeric(9,6),
    location_description character varying(1000),
    uploaded_parts_json text DEFAULT '[]'::text NOT NULL,
    error_message text,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_evidence_upload_sessions_lat CHECK (((latitude IS NULL) OR ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)))),
    CONSTRAINT ck_evidence_upload_sessions_lon CHECK (((longitude IS NULL) OR ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric)))),
    CONSTRAINT ck_evidence_upload_sessions_sizes CHECK (((declared_size_bytes > 0) AND (chunk_size_bytes > 0) AND (total_parts > 0))),
    CONSTRAINT ck_evidence_upload_sessions_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'COMPLETING'::character varying, 'PROCESSING'::character varying, 'READY'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: field_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE field_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    status character varying(24) NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    abort_reason character varying(2000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    pre_flight_checklist_note character varying(2000),
    outcome_notes character varying(4000),
    telemetry_source character varying(64),
    flight_duration_seconds integer,
    distance_flown_meters numeric(10,2),
    max_altitude_meters numeric(8,2),
    start_battery_percent integer,
    end_battery_percent integer,
    flight_final_state character varying(32),
    flight_report_note character varying(1000),
    flight_trace_json text,
    flight_video_evidence_id uuid,
    hung_notified_at timestamp with time zone,
    aborted_by_user_id uuid,
    CONSTRAINT ck_field_sessions_status CHECK (((status)::text = ANY (ARRAY[('PLANNED'::character varying)::text, ('IN_PROGRESS'::character varying)::text, ('FIELD_COMPLETED'::character varying)::text, ('POSTPONED'::character varying)::text, ('ABORTED'::character varying)::text]))),
    CONSTRAINT ck_field_sessions_time_order CHECK (((ended_at IS NULL) OR (started_at IS NULL) OR (ended_at >= started_at)))
);


--
-- Name: findings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE findings (
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
    model_name character varying(160),
    confidence numeric(6,5),
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_findings_confidence CHECK (((confidence IS NULL) OR ((confidence >= (0)::numeric) AND (confidence <= (1)::numeric)))),
    CONSTRAINT ck_findings_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('CONFIRMED'::character varying)::text]))),
    CONSTRAINT ck_verified_finding_severity CHECK (((severity)::text = ANY (ARRAY[('LOW'::character varying)::text, ('MEDIUM'::character varying)::text, ('HIGH'::character varying)::text, ('CRITICAL'::character varying)::text])))
);


--
-- Name: inspection_finance_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspection_finance_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    commission_rate numeric(5,2),
    vat_rate numeric(5,2),
    tncn_rate numeric(5,2),
    updated_by_user_id uuid,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_inspection_finance_settings_commission CHECK (((commission_rate IS NULL) OR ((commission_rate >= (0)::numeric) AND (commission_rate <= (100)::numeric)))),
    CONSTRAINT ck_inspection_finance_settings_tncn CHECK (((tncn_rate IS NULL) OR ((tncn_rate >= (0)::numeric) AND (tncn_rate <= (100)::numeric)))),
    CONSTRAINT ck_inspection_finance_settings_vat CHECK (((vat_rate IS NULL) OR ((vat_rate >= (0)::numeric) AND (vat_rate <= (100)::numeric))))
);


--
-- Name: inspection_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspection_items (
    id uuid NOT NULL,
    category_id uuid NOT NULL,
    label character varying(300) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: inspection_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspection_reports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspection_id uuid NOT NULL,
    version_no integer NOT NULL,
    status character varying(32) NOT NULL,
    submitted_at timestamp with time zone,
    reviewer_user_id uuid,
    reviewed_at timestamp with time zone,
    return_reason character varying(2000),
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    summary text,
    CONSTRAINT ck_inspection_reports_number CHECK ((version_no > 0)),
    CONSTRAINT ck_inspection_reports_status CHECK (((status)::text = ANY (ARRAY[('SUBMITTED'::character varying)::text, ('RETURNED'::character varying)::text, ('PUBLISHED'::character varying)::text, ('SUPERSEDED'::character varying)::text])))
);


--
-- Name: TABLE inspection_reports; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE inspection_reports IS 'MF3 versioned inspection reports and human review gates; see Report 3 sections 3.7.1 and 3.7.3.';


--
-- Name: inspections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspections (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    planned_start_at timestamp with time zone,
    planned_end_at timestamp with time zone,
    inspector_id uuid,
    drone_id uuid,
    flight_permit_file_key character varying(1000),
    flight_permit_file_name character varying(500),
    findings_confirmed_at timestamp with time zone,
    partner_assignment boolean DEFAULT false NOT NULL,
    inspection_type character varying(32) DEFAULT 'INITIAL'::character varying NOT NULL,
    owner_customer_id uuid NOT NULL,
    decline_reason character varying(2000),
    declined_at timestamp with time zone,
    declined_by_inspector_id uuid,
    cancellation_reason character varying(2000),
    overdue_notified_at timestamp with time zone,
    clarification_count integer DEFAULT 0 NOT NULL,
    assignment_status character varying(32) DEFAULT 'ACCEPTED'::character varying NOT NULL,
    assignment_accepted_at timestamp with time zone,
    flight_permit_number character varying(120),
    flight_permit_authority character varying(200),
    flight_permit_valid_from timestamp with time zone,
    flight_permit_valid_to timestamp with time zone,
    flight_permit_drone_serial character varying(120),
    flight_permit_max_altitude_m integer,
    flight_permit_file_sha256 character varying(64),
    flight_permit_file_size_bytes bigint,
    flight_permit_content_type character varying(120),
    flight_permit_uploaded_at timestamp with time zone,
    flight_permit_uploaded_by uuid,
    flight_permit_verification_status character varying(32) DEFAULT 'NOT_UPLOADED'::character varying NOT NULL,
    flight_permit_verified_by uuid,
    flight_permit_verified_at timestamp with time zone,
    flight_permit_rejection_reason character varying(1000),
    flight_permit_review_notes character varying(1000),
    source_inspection_id uuid,
    CONSTRAINT ck_inspections_assignment_status CHECK (((assignment_status)::text = ANY ((ARRAY['PENDING_ACCEPTANCE'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying])::text[]))),
    CONSTRAINT ck_inspections_flight_permit_altitude CHECK (((flight_permit_max_altitude_m IS NULL) OR ((flight_permit_max_altitude_m > 0) AND (flight_permit_max_altitude_m <= 2000)))),
    CONSTRAINT ck_inspections_flight_permit_size CHECK (((flight_permit_file_size_bytes IS NULL) OR (flight_permit_file_size_bytes > 0))),
    CONSTRAINT ck_inspections_flight_permit_verification_status CHECK (((flight_permit_verification_status)::text = ANY ((ARRAY['NOT_UPLOADED'::character varying, 'PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_inspections_flight_permit_window CHECK (((flight_permit_valid_from IS NULL) OR (flight_permit_valid_to IS NULL) OR (flight_permit_valid_to > flight_permit_valid_from))),
    CONSTRAINT ck_inspections_status CHECK (((status)::text = ANY (ARRAY[('SCHEDULED'::character varying)::text, ('IN_PROGRESS'::character varying)::text, ('FIELD_COMPLETED'::character varying)::text, ('UNDER_REVIEW'::character varying)::text, ('COMPLETED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT ck_inspections_type CHECK (((inspection_type)::text = ANY ((ARRAY['INITIAL'::character varying, 'RE_INSPECTION'::character varying])::text[])))
);


--
-- Name: inspector_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_applications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    normalized_email character varying(320) NOT NULL,
    email character varying(320) NOT NULL,
    full_name character varying(200) NOT NULL,
    phone_number character varying(32),
    citizen_id character varying(32) NOT NULL,
    address character varying(500) NOT NULL,
    pilot_license_number character varying(128) NOT NULL,
    pilot_license_class character varying(64) NOT NULL,
    pilot_license_expires_at timestamp with time zone,
    license_scan_file_key character varying(1000) NOT NULL,
    password_hash character varying(1024) NOT NULL,
    status character varying(32) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    rejection_reason character varying(1000),
    notification_state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    cleanup_attempts integer DEFAULT 0 NOT NULL,
    correlation_id character varying(128),
    approved_user_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    signed_contract_file_key character varying(1000),
    signed_contract_sha256 character varying(128),
    CONSTRAINT ck_inspector_applications_notification CHECK (((notification_state)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying])::text[]))),
    CONSTRAINT ck_inspector_applications_status CHECK (((status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: inspector_flight_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_flight_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspector_user_id uuid NOT NULL,
    flight_code character varying(64) NOT NULL,
    flight_date date NOT NULL,
    mission_title character varying(255) NOT NULL,
    drone_model character varying(255) NOT NULL,
    drone_serial_number character varying(128),
    location character varying(500) NOT NULL,
    flight_duration_minutes integer NOT NULL,
    inspection_type character varying(128) NOT NULL,
    safety_incident boolean DEFAULT false NOT NULL,
    notes text,
    recorded_by_user_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT inspector_flight_logs_flight_duration_minutes_check CHECK ((flight_duration_minutes > 0))
);


--
-- Name: inspector_payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_payouts (
    occurrence_id uuid NOT NULL,
    inspector_id uuid,
    payout_status character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    payout_reference character varying(255),
    payout_recorded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    payout_held_for_refund boolean DEFAULT false NOT NULL,
    payout_recorded_by_user_id uuid,
    final_payout_amount numeric(15,2),
    CONSTRAINT ck_inspector_payouts_status CHECK (((payout_status)::text = ANY ((ARRAY['PENDING'::character varying, 'ELIGIBLE'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT inspector_payouts_final_payout_amount_check CHECK ((final_payout_amount >= (0)::numeric))
);


--
-- Name: inspector_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_profiles (
    user_id uuid NOT NULL,
    pilot_license_number character varying(128),
    pilot_license_class character varying(64),
    pilot_license_expires_at timestamp with time zone,
    pilot_license_file_key character varying(1000),
    pilot_license_issued_date date,
    pilot_license_authority character varying(200),
    citizen_id character varying(32),
    date_of_birth date,
    address character varying(500),
    prior_flight_hours numeric(8,2) DEFAULT 0.00 NOT NULL,
    certified_drone_models character varying(500),
    specializations character varying(500),
    experience_summary text,
    notes text,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    onboarding_status character varying(32) DEFAULT 'APPROVED'::character varying NOT NULL,
    partner_contract_file_key character varying(1000),
    partner_contract_sha256 character varying(64),
    partner_contract_uploaded_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    rejection_reason text
);


--
-- Name: inspector_receivable_recoveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_receivable_recoveries (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    receivable_id uuid NOT NULL,
    amount numeric(15,2) NOT NULL,
    external_reference character varying(255) NOT NULL,
    note character varying(2000) NOT NULL,
    recorded_by_user_id uuid NOT NULL,
    recorded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT inspector_receivable_recoveries_amount_check CHECK ((amount > (0)::numeric))
);


--
-- Name: inspector_receivables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_receivables (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    refund_id uuid NOT NULL,
    occurrence_id uuid NOT NULL,
    inspector_id uuid NOT NULL,
    assessed_amount numeric(15,2) NOT NULL,
    source_reference character varying(255) NOT NULL,
    reason character varying(2000) NOT NULL,
    recorded_by_user_id uuid NOT NULL,
    recorded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT inspector_receivables_assessed_amount_check CHECK ((assessed_amount > (0)::numeric))
);


--
-- Name: inspector_reliability_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE inspector_reliability_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inspector_id uuid NOT NULL,
    inspection_id uuid NOT NULL,
    field_session_id uuid,
    event_type character varying(24) NOT NULL,
    reason character varying(2000),
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_inspector_reliability_event_type CHECK (((event_type)::text = ANY ((ARRAY['DECLINE'::character varying, 'NO_SHOW'::character varying, 'ABORT'::character varying, 'FORCE_ABORT'::character varying])::text[])))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    recipient_user_id uuid NOT NULL,
    event_type character varying(96) NOT NULL,
    channel character varying(16) NOT NULL,
    title character varying(300) NOT NULL,
    body character varying(4000) NOT NULL,
    target_path character varying(1000),
    status character varying(24) NOT NULL,
    sent_at timestamp with time zone,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deduplication_key character varying(192),
    CONSTRAINT ck_notifications_body CHECK ((btrim((body)::text) <> ''::text)),
    CONSTRAINT ck_notifications_deduplication_key CHECK (((deduplication_key IS NULL) OR (btrim((deduplication_key)::text) <> ''::text))),
    CONSTRAINT ck_notifications_event_type CHECK ((btrim((event_type)::text) <> ''::text)),
    CONSTRAINT ck_notifications_read_state CHECK (((((status)::text = 'READ'::text) AND (sent_at IS NOT NULL) AND (read_at IS NOT NULL)) OR (((status)::text <> 'READ'::text) AND (read_at IS NULL)))),
    CONSTRAINT ck_notifications_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'READ'::character varying])::text[]))),
    CONSTRAINT ck_notifications_target_path CHECK (((target_path IS NULL) OR (btrim((target_path)::text) <> ''::text))),
    CONSTRAINT ck_notifications_title CHECK ((btrim((title)::text) <> ''::text))
);


--
-- Name: otp_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE otp_challenges (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    purpose character varying(32) NOT NULL,
    challenge_hash character varying(64) NOT NULL,
    user_id uuid,
    destination character varying(255),
    channel character varying(16),
    client_ip character varying(45),
    code_hash character varying(64),
    code_expires_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    failed_attempts integer DEFAULT 0 NOT NULL,
    send_count integer DEFAULT 0 NOT NULL,
    last_sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_otp_challenges_purpose CHECK (((purpose)::text = ANY ((ARRAY['LOGIN'::character varying, 'REGISTRATION'::character varying, 'PASSWORD_RESET'::character varying])::text[])))
);


--
-- Name: pilot_license_classes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE pilot_license_classes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(500),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: platform_ai_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE platform_ai_settings (
    id integer DEFAULT 1 NOT NULL,
    auto_confirm_enabled boolean DEFAULT true NOT NULL,
    auto_confirm_threshold numeric(5,2) DEFAULT 85.00 NOT NULL,
    rejected_pending_count bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by uuid,
    CONSTRAINT ck_platform_ai_settings_singleton CHECK ((id = 1)),
    CONSTRAINT ck_platform_ai_settings_threshold CHECK (((auto_confirm_threshold >= (0)::numeric) AND (auto_confirm_threshold <= (100)::numeric)))
);


--
-- Name: prospective_customer_registrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE prospective_customer_registrations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    organization_name character varying(200) NOT NULL,
    organization_code character varying(64) NOT NULL,
    asset_category_id uuid NOT NULL,
    asset_code character varying(64) NOT NULL,
    asset_name character varying(200) NOT NULL,
    asset_description character varying(2000),
    asset_location character varying(500) NOT NULL,
    latitude numeric(9,6) NOT NULL,
    longitude numeric(9,6) NOT NULL,
    ownership_information character varying(1000),
    status character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    airspace_classification character varying(32) NOT NULL,
    review_note character varying(2000),
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    structure_height_meters numeric(8,2),
    footprint_area_square_meters numeric(12,2),
    CONSTRAINT ck_prospective_customer_dimensions_nonnegative CHECK ((((structure_height_meters IS NULL) OR (structure_height_meters >= (0)::numeric)) AND ((footprint_area_square_meters IS NULL) OR (footprint_area_square_meters >= (0)::numeric)))),
    CONSTRAINT prospective_customer_registrations_latitude_check CHECK (((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric))),
    CONSTRAINT prospective_customer_registrations_longitude_check CHECK (((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))),
    CONSTRAINT prospective_customer_registrations_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: reinspection_defect_verifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE reinspection_defect_verifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reinspection_id uuid NOT NULL,
    source_finding_id uuid NOT NULL,
    new_evidence_id uuid,
    resolution_status character varying(32) DEFAULT 'UNRESOLVED'::character varying NOT NULL,
    inspector_notes character varying(4000),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_reinspection_resolution_status CHECK (((resolution_status)::text = ANY ((ARRAY['RESOLVED'::character varying, 'UNRESOLVED'::character varying, 'INCONCLUSIVE'::character varying])::text[])))
);


--
-- Name: service_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_invoices (
    occurrence_id uuid NOT NULL,
    total_price numeric(15,2),
    vat_rate numeric(5,2),
    commission_rate numeric(5,2),
    tncn_rate numeric(5,2),
    report_accepted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: service_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_payments (
    occurrence_id uuid NOT NULL,
    payment_status character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    payos_order_code bigint,
    payment_method character varying(30),
    payment_reference character varying(255),
    payment_confirmed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    refunded_amount numeric(15,2) DEFAULT 0 NOT NULL,
    refund_status character varying(24) DEFAULT 'NONE'::character varying NOT NULL,
    CONSTRAINT ck_service_payments_refund_status CHECK (((refund_status)::text = ANY ((ARRAY['NONE'::character varying, 'PARTIAL'::character varying, 'FULL'::character varying])::text[]))),
    CONSTRAINT ck_service_payments_refunded_amount CHECK ((refunded_amount >= (0)::numeric)),
    CONSTRAINT ck_service_payments_status CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAYMENT_DUE'::character varying, 'PAID'::character varying])::text[])))
);


--
-- Name: service_refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_refunds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    occurrence_id uuid NOT NULL,
    amount numeric(15,2) NOT NULL,
    refund_reference character varying(255),
    reason character varying(2000) NOT NULL,
    inspector_payout_reduction numeric(15,2),
    platform_commission_adjustment numeric(15,2),
    vat_adjustment numeric(15,2),
    tncn_adjustment numeric(15,2),
    platform_absorbed_amount numeric(15,2),
    allocation_reconciled_by_user_id uuid,
    allocation_reconciliation_reference character varying(255),
    allocation_reconciliation_note character varying(2000),
    allocation_reconciled_at timestamp with time zone,
    request_reference character varying(255),
    inspector_receivable_amount numeric(15,2) DEFAULT 0 NOT NULL,
    platform_absorbed_plan_amount numeric(15,2) DEFAULT 0 NOT NULL,
    refund_status character varying(24) DEFAULT 'COMPLETED'::character varying NOT NULL,
    initiated_by_user_id uuid NOT NULL,
    initiated_at timestamp with time zone NOT NULL,
    completed_by_user_id uuid,
    completed_at timestamp with time zone,
    cancelled_by_user_id uuid,
    cancelled_at timestamp with time zone,
    cancellation_reason character varying(2000),
    CONSTRAINT ck_service_refunds_allocation_sum CHECK ((((inspector_payout_reduction IS NULL) AND (platform_commission_adjustment IS NULL) AND (vat_adjustment IS NULL) AND (tncn_adjustment IS NULL) AND (platform_absorbed_amount IS NULL) AND (allocation_reconciled_by_user_id IS NULL) AND (allocation_reconciliation_reference IS NULL) AND (allocation_reconciliation_note IS NULL) AND (allocation_reconciled_at IS NULL)) OR ((inspector_payout_reduction IS NOT NULL) AND (platform_commission_adjustment IS NOT NULL) AND (vat_adjustment IS NOT NULL) AND (tncn_adjustment IS NOT NULL) AND (platform_absorbed_amount IS NOT NULL) AND (((((inspector_payout_reduction + platform_commission_adjustment) + vat_adjustment) + tncn_adjustment) + platform_absorbed_amount) = amount) AND (((allocation_reconciled_by_user_id IS NULL) AND (allocation_reconciliation_reference IS NULL) AND (allocation_reconciliation_note IS NULL) AND (allocation_reconciled_at IS NULL)) OR ((allocation_reconciled_by_user_id IS NOT NULL) AND (allocation_reconciliation_reference IS NOT NULL) AND (allocation_reconciliation_note IS NOT NULL) AND (allocation_reconciled_at IS NOT NULL)))))),
    CONSTRAINT ck_service_refunds_initiated_allocation_plan CHECK (((inspector_receivable_amount >= (0)::numeric) AND (platform_absorbed_plan_amount >= (0)::numeric) AND ((inspector_receivable_amount + platform_absorbed_plan_amount) <= amount))),
    CONSTRAINT ck_service_refunds_lifecycle CHECK (((((refund_status)::text = 'INITIATED'::text) AND (initiated_by_user_id IS NOT NULL) AND (initiated_at IS NOT NULL) AND (refund_reference IS NULL) AND (completed_at IS NULL) AND (cancelled_at IS NULL)) OR (((refund_status)::text = 'COMPLETED'::text) AND (refund_reference IS NOT NULL) AND (completed_by_user_id IS NOT NULL) AND (completed_at IS NOT NULL) AND (cancelled_at IS NULL)) OR (((refund_status)::text = 'CANCELLED'::text) AND (initiated_by_user_id IS NOT NULL) AND (initiated_at IS NOT NULL) AND (cancelled_by_user_id IS NOT NULL) AND (cancelled_at IS NOT NULL) AND (cancellation_reason IS NOT NULL)))),
    CONSTRAINT ck_service_refunds_status CHECK (((refund_status)::text = ANY ((ARRAY['INITIATED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT service_refunds_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT service_refunds_inspector_payout_reduction_check CHECK ((inspector_payout_reduction >= (0)::numeric)),
    CONSTRAINT service_refunds_platform_absorbed_amount_check CHECK ((platform_absorbed_amount >= (0)::numeric)),
    CONSTRAINT service_refunds_platform_commission_adjustment_check CHECK ((platform_commission_adjustment >= (0)::numeric)),
    CONSTRAINT service_refunds_tncn_adjustment_check CHECK ((tncn_adjustment >= (0)::numeric)),
    CONSTRAINT service_refunds_vat_adjustment_check CHECK ((vat_adjustment >= (0)::numeric))
);


--
-- Name: service_request_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_request_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    author_user_id uuid NOT NULL,
    body character varying(4000) DEFAULT ''::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    read_at timestamp with time zone,
    CONSTRAINT ck_service_request_messages_body CHECK ((length(btrim((body)::text)) <= 4000))
);


--
-- Name: service_request_message_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_request_message_attachments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    message_id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    content_type character varying(128) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    object_key character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_service_request_message_attachments_checksum CHECK ((checksum_sha256::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_service_request_message_attachments_content_type CHECK (((content_type)::text = ANY ((ARRAY['image/png'::character varying, 'image/jpeg'::character varying, 'image/webp'::character varying, 'application/pdf'::character varying, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'::character varying])::text[]))),
    CONSTRAINT ck_service_request_message_attachments_size CHECK (((size_bytes > 0) AND (size_bytes <= 20971520)))
);


--
-- Name: service_request_occurrences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_request_occurrences (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    occurrence_index integer NOT NULL,
    planned_start_at timestamp with time zone NOT NULL,
    planned_end_at timestamp with time zone NOT NULL,
    status character varying(24) DEFAULT 'PENDING_ASSIGNMENT'::character varying NOT NULL,
    inspector_id uuid,
    drone_id uuid,
    inspection_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_service_request_occurrences_assignment CHECK (((((status)::text = 'PENDING_ASSIGNMENT'::text) AND (inspector_id IS NULL) AND (drone_id IS NULL) AND (inspection_id IS NULL)) OR (((status)::text = 'ASSIGNED'::text) AND (inspector_id IS NOT NULL) AND (drone_id IS NOT NULL) AND (inspection_id IS NOT NULL)) OR ((status)::text = 'CANCELLED'::text))),
    CONSTRAINT ck_service_request_occurrences_index CHECK ((occurrence_index > 0)),
    CONSTRAINT ck_service_request_occurrences_status CHECK (((status)::text = ANY ((ARRAY['PENDING_ASSIGNMENT'::character varying, 'ASSIGNED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_service_request_occurrences_time CHECK ((planned_end_at > planned_start_at))
);


--
-- Name: service_request_quotes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_request_quotes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id uuid NOT NULL,
    scope_snapshot character varying(4000) NOT NULL,
    first_occurrence_start_at timestamp with time zone NOT NULL,
    first_occurrence_end_at timestamp with time zone NOT NULL,
    inspector_id uuid NOT NULL,
    drone_id uuid NOT NULL,
    issued_by_user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    price_per_occurrence_vnd numeric(15,2) NOT NULL,
    required_drone_feature_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by_user_id uuid NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_service_request_quotes_price CHECK ((price_per_occurrence_vnd > (0)::numeric)),
    CONSTRAINT ck_service_request_quotes_required_feature_codes_array CHECK ((jsonb_typeof(required_drone_feature_codes) = 'array'::text)),
    CONSTRAINT ck_service_request_quotes_time CHECK ((first_occurrence_end_at > first_occurrence_start_at))
);


--
-- Name: service_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    description character varying(4000) NOT NULL,
    requested_from date NOT NULL,
    requested_until date NOT NULL,
    timezone character varying(64) NOT NULL,
    window_start time without time zone DEFAULT '08:00:00'::time without time zone NOT NULL,
    window_end time without time zone DEFAULT '17:00:00'::time without time zone NOT NULL,
    request_type character varying(16) NOT NULL,
    recurrence_unit character varying(16),
    recurrence_interval integer,
    occurrence_count integer,
    recurrence_end_date date,
    status character varying(24) DEFAULT 'OPEN'::character varying NOT NULL,
    contract_object_key character varying(1000),
    contract_uploaded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    accepted_quote_id uuid,
    accepted_at timestamp with time zone,
    accepted_by_user_id uuid,
    accepted_terms_snapshot jsonb,
    owner_customer_id uuid NOT NULL,
    source_inspection_id uuid,
    assigned_manager_id uuid,
    manager_assigned_at timestamp with time zone,
    manager_assigned_by_user_id uuid,
    CONSTRAINT ck_service_requests_acceptance_v33 CHECK (((((status)::text = ANY ((ARRAY['OPEN'::character varying, 'QUOTED'::character varying, 'CANCELLED'::character varying])::text[])) AND (accepted_quote_id IS NULL) AND (accepted_at IS NULL) AND (accepted_terms_snapshot IS NULL)) OR (((status)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'CONTRACT_SIGNED'::character varying, 'ASSIGNED'::character varying])::text[])) AND (accepted_quote_id IS NOT NULL) AND (accepted_at IS NOT NULL) AND (accepted_terms_snapshot IS NOT NULL)))),
    CONSTRAINT ck_service_requests_contract_scan CHECK ((((contract_object_key IS NULL) AND (contract_uploaded_at IS NULL)) OR ((contract_object_key IS NOT NULL) AND (contract_uploaded_at IS NOT NULL)))),
    CONSTRAINT ck_service_requests_date_range CHECK ((requested_until >= requested_from)),
    CONSTRAINT ck_service_requests_recurrence CHECK (((((request_type)::text = 'ONE_TIME'::text) AND (recurrence_unit IS NULL) AND (recurrence_interval IS NULL) AND (occurrence_count IS NULL) AND (recurrence_end_date IS NULL)) OR (((request_type)::text = 'RECURRING'::text) AND ((recurrence_unit)::text = ANY ((ARRAY['DAY'::character varying, 'WEEK'::character varying, 'MONTH'::character varying])::text[])) AND ((((recurrence_unit)::text = 'MONTH'::text) AND ((recurrence_interval >= 1) AND (recurrence_interval <= 120))) OR (((recurrence_unit)::text = ANY ((ARRAY['DAY'::character varying, 'WEEK'::character varying])::text[])) AND ((recurrence_interval >= 1) AND (recurrence_interval <= 52)))) AND ((((recurrence_unit)::text = 'MONTH'::text) AND ((((occurrence_count >= 2) AND (occurrence_count <= 100)) AND (recurrence_end_date IS NULL)) OR ((occurrence_count IS NULL) AND (recurrence_end_date > requested_from)))) OR (((recurrence_unit)::text = ANY ((ARRAY['DAY'::character varying, 'WEEK'::character varying])::text[])) AND ((((occurrence_count >= 1) AND (occurrence_count <= 100)) AND (recurrence_end_date IS NULL)) OR ((occurrence_count IS NULL) AND (recurrence_end_date >= requested_from)))))))),
    CONSTRAINT ck_service_requests_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'QUOTED'::character varying, 'ACCEPTED'::character varying, 'CONTRACT_SIGNED'::character varying, 'ASSIGNED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_service_requests_time_window CHECK ((window_end > window_start)),
    CONSTRAINT ck_service_requests_type CHECK (((request_type)::text = ANY ((ARRAY['ONE_TIME'::character varying, 'RECURRING'::character varying])::text[])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(320) NOT NULL,
    normalized_email character varying(320) NOT NULL,
    full_name character varying(200) NOT NULL,
    password_hash character varying(1024),
    status character varying(32) NOT NULL,
    auth_version integer DEFAULT 0 NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    lockout_until timestamp with time zone,
    must_change_password boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    vision_fallback_enabled boolean DEFAULT true NOT NULL,
    phone_number character varying(32),
    avatar_url text,
    roles character varying(255) DEFAULT ''::character varying NOT NULL,
    otp_verified boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_users_failed_login_count CHECK ((failed_login_count >= 0)),
    CONSTRAINT ck_users_roles CHECK ((((roles)::text = ''::text) OR ((roles)::text ~ '^(ADMIN|MANAGER|CUSTOMER|INSPECTOR)(,(ADMIN|MANAGER|CUSTOMER|INSPECTOR))*$'::text))),
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('SUSPENDED'::character varying)::text, ('DISABLED'::character varying)::text])))
);


--
-- Name: asset_categories asset_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_categories
    ADD CONSTRAINT asset_categories_pkey PRIMARY KEY (id);


--
-- Name: asset_documents asset_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_documents
    ADD CONSTRAINT asset_documents_pkey PRIMARY KEY (id);


--
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: auth_sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);


--
-- Name: checklist_templates checklist_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY checklist_templates
    ADD CONSTRAINT checklist_templates_pkey PRIMARY KEY (id);


--
-- Name: drone_document_configs drone_document_configs_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_document_configs
    ADD CONSTRAINT drone_document_configs_code_key UNIQUE (code);


--
-- Name: drone_document_configs drone_document_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_document_configs
    ADD CONSTRAINT drone_document_configs_pkey PRIMARY KEY (id);


--
-- Name: drone_feature_configs drone_feature_configs_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_feature_configs
    ADD CONSTRAINT drone_feature_configs_code_key UNIQUE (code);


--
-- Name: drone_feature_configs drone_feature_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_feature_configs
    ADD CONSTRAINT drone_feature_configs_pkey PRIMARY KEY (id);


--
-- Name: drone_registration_applications drone_registration_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_registration_applications
    ADD CONSTRAINT drone_registration_applications_pkey PRIMARY KEY (id);


--
-- Name: drones drones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drones
    ADD CONSTRAINT drones_pkey PRIMARY KEY (id);


--
-- Name: event_publication event_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);


--
-- Name: evidence evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence
    ADD CONSTRAINT evidence_pkey PRIMARY KEY (id);


--
-- Name: evidence_upload_sessions evidence_upload_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence_upload_sessions
    ADD CONSTRAINT evidence_upload_sessions_pkey PRIMARY KEY (id);


--
-- Name: field_sessions field_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_sessions
    ADD CONSTRAINT field_sessions_pkey PRIMARY KEY (id);


--
-- Name: inspection_finance_settings inspection_finance_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_finance_settings
    ADD CONSTRAINT inspection_finance_settings_pkey PRIMARY KEY (id);


--
-- Name: inspection_items inspection_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_items
    ADD CONSTRAINT inspection_items_pkey PRIMARY KEY (id);


--
-- Name: inspection_reports inspection_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_reports
    ADD CONSTRAINT inspection_reports_pkey PRIMARY KEY (id);


--
-- Name: inspections inspections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_pkey PRIMARY KEY (id);


--
-- Name: inspector_applications inspector_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_applications
    ADD CONSTRAINT inspector_applications_pkey PRIMARY KEY (id);


--
-- Name: inspector_flight_logs inspector_flight_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_flight_logs
    ADD CONSTRAINT inspector_flight_logs_pkey PRIMARY KEY (id);


--
-- Name: inspector_payouts inspector_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_payouts
    ADD CONSTRAINT inspector_payouts_pkey PRIMARY KEY (occurrence_id);


--
-- Name: inspector_profiles inspector_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_profiles
    ADD CONSTRAINT inspector_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: inspector_receivable_recoveries inspector_receivable_recoveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivable_recoveries
    ADD CONSTRAINT inspector_receivable_recoveries_pkey PRIMARY KEY (id);


--
-- Name: inspector_receivables inspector_receivables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_pkey PRIMARY KEY (id);


--
-- Name: inspector_receivables inspector_receivables_refund_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_refund_id_key UNIQUE (refund_id);


--
-- Name: inspector_reliability_events inspector_reliability_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_reliability_events
    ADD CONSTRAINT inspector_reliability_events_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: otp_challenges otp_challenges_challenge_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY otp_challenges
    ADD CONSTRAINT otp_challenges_challenge_hash_key UNIQUE (challenge_hash);


--
-- Name: otp_challenges otp_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY otp_challenges
    ADD CONSTRAINT otp_challenges_pkey PRIMARY KEY (id);


--
-- Name: pilot_license_classes pilot_license_classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY pilot_license_classes
    ADD CONSTRAINT pilot_license_classes_pkey PRIMARY KEY (id);


--
-- Name: platform_ai_settings platform_ai_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY platform_ai_settings
    ADD CONSTRAINT platform_ai_settings_pkey PRIMARY KEY (id);


--
-- Name: prospective_customer_registrations prospective_customer_registrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY prospective_customer_registrations
    ADD CONSTRAINT prospective_customer_registrations_pkey PRIMARY KEY (id);


--
-- Name: prospective_customer_registrations prospective_customer_registrations_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY prospective_customer_registrations
    ADD CONSTRAINT prospective_customer_registrations_user_id_key UNIQUE (user_id);


--
-- Name: reinspection_defect_verifications reinspection_defect_verifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reinspection_defect_verifications
    ADD CONSTRAINT reinspection_defect_verifications_pkey PRIMARY KEY (id);


--
-- Name: service_invoices service_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_invoices
    ADD CONSTRAINT service_invoices_pkey PRIMARY KEY (occurrence_id);


--
-- Name: service_payments service_payments_payos_order_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_payments
    ADD CONSTRAINT service_payments_payos_order_code_key UNIQUE (payos_order_code);


--
-- Name: service_payments service_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_payments
    ADD CONSTRAINT service_payments_pkey PRIMARY KEY (occurrence_id);


--
-- Name: service_refunds service_refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_pkey PRIMARY KEY (id);


--
-- Name: service_request_messages service_request_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_messages
    ADD CONSTRAINT service_request_messages_pkey PRIMARY KEY (id);


--
-- Name: service_request_message_attachments service_request_message_attachments_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_message_attachments
    ADD CONSTRAINT service_request_message_attachments_object_key_key UNIQUE (object_key);


--
-- Name: service_request_message_attachments service_request_message_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_message_attachments
    ADD CONSTRAINT service_request_message_attachments_pkey PRIMARY KEY (id);


--
-- Name: service_request_occurrences service_request_occurrences_inspection_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT service_request_occurrences_inspection_id_key UNIQUE (inspection_id);


--
-- Name: service_request_occurrences service_request_occurrences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT service_request_occurrences_pkey PRIMARY KEY (id);


--
-- Name: service_request_quotes service_request_quotes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT service_request_quotes_pkey PRIMARY KEY (id);


--
-- Name: service_requests service_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_pkey PRIMARY KEY (id);


--
-- Name: asset_categories uq_asset_categories_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_categories
    ADD CONSTRAINT uq_asset_categories_code UNIQUE (code);


--
-- Name: asset_documents uq_asset_documents_checksum; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_documents
    ADD CONSTRAINT uq_asset_documents_checksum UNIQUE (asset_id, checksum_sha256);


--
-- Name: asset_documents uq_asset_documents_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_documents
    ADD CONSTRAINT uq_asset_documents_object_key UNIQUE (object_key);


--
-- Name: auth_sessions uq_auth_sessions_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT uq_auth_sessions_token_hash UNIQUE (token_hash);


--
-- Name: evidence uq_evidence_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence
    ADD CONSTRAINT uq_evidence_object_key UNIQUE (object_key);


--
-- Name: findings uq_findings_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY findings
    ADD CONSTRAINT uq_findings_code UNIQUE (inspection_id, finding_code);


--
-- Name: inspection_reports uq_inspection_reports_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_reports
    ADD CONSTRAINT uq_inspection_reports_number UNIQUE (inspection_id, version_no);


--
-- Name: notifications uq_notifications_deduplication_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT uq_notifications_deduplication_key UNIQUE (deduplication_key);


--
-- Name: pilot_license_classes uq_pilot_license_classes_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY pilot_license_classes
    ADD CONSTRAINT uq_pilot_license_classes_name UNIQUE (name);


--
-- Name: reinspection_defect_verifications uq_reinspection_defect; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reinspection_defect_verifications
    ADD CONSTRAINT uq_reinspection_defect UNIQUE (reinspection_id, source_finding_id);


--
-- Name: service_request_occurrences uq_service_request_occurrences_index; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT uq_service_request_occurrences_index UNIQUE (request_id, occurrence_index);


--
-- Name: service_request_quotes uq_service_request_quotes_id_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT uq_service_request_quotes_id_request UNIQUE (id, request_id);


--
-- Name: service_request_quotes uq_service_request_quotes_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT uq_service_request_quotes_request UNIQUE (request_id);


--
-- Name: users uq_users_normalized_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: findings verified_findings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY findings
    ADD CONSTRAINT verified_findings_pkey PRIMARY KEY (id);


--
-- Name: event_publication_by_completion_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_publication_by_completion_date_idx ON event_publication USING btree (completion_date);


--
-- Name: event_publication_serialized_event_hash_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);


--
-- Name: idx_evidence_source_video_evidence_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evidence_source_video_evidence_id ON evidence USING btree (source_video_evidence_id) WHERE (source_video_evidence_id IS NOT NULL);


--
-- Name: ix_asset_documents_asset_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_asset_documents_asset_time ON asset_documents USING btree (asset_id, created_at DESC);


--
-- Name: ix_assets_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_assets_category ON assets USING btree (category_id);


--
-- Name: ix_assets_owner_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_assets_owner_customer ON assets USING btree (owner_customer_id);


--
-- Name: ix_assets_owner_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_assets_owner_status ON assets USING btree (owner_customer_id, status, name);


--
-- Name: ix_auth_sessions_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_active ON auth_sessions USING btree (user_id, revoked_at);


--
-- Name: ix_auth_sessions_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_auth_sessions_user ON auth_sessions USING btree (user_id);


--
-- Name: ix_checklist_templates_type_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_checklist_templates_type_order ON checklist_templates USING btree (template_type, sort_order, created_at);


--
-- Name: ix_drone_document_configs_active_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drone_document_configs_active_order ON drone_document_configs USING btree (is_active, display_order);


--
-- Name: ix_drone_feature_configs_active_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drone_feature_configs_active_order ON drone_feature_configs USING btree (is_active, display_order);


--
-- Name: ix_drone_registration_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drone_registration_status_created ON drone_registration_applications USING btree (status, created_at);


--
-- Name: ix_drones_inspector_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drones_inspector_owner ON drones USING btree (inspector_owner_id);


--
-- Name: ix_drones_owner_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drones_owner_customer ON drones USING btree (owner_customer_id);


--
-- Name: ix_drones_owner_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_drones_owner_status ON drones USING btree (owner_customer_id, serviceability);


--
-- Name: ix_evidence_inspection_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_inspection_time ON evidence USING btree (inspection_id, created_at DESC);


--
-- Name: ix_evidence_processing_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_processing_queue ON evidence USING btree (processing_status, processing_locked_at, created_at);


--
-- Name: ix_evidence_upload_sessions_evidence_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_upload_sessions_evidence_id ON evidence_upload_sessions USING btree (evidence_id);


--
-- Name: ix_evidence_upload_sessions_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_upload_sessions_expires_at ON evidence_upload_sessions USING btree (status, expires_at);


--
-- Name: ix_evidence_upload_sessions_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_evidence_upload_sessions_inspection_status ON evidence_upload_sessions USING btree (inspection_id, status);


--
-- Name: ix_field_sessions_inspection_status_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_field_sessions_inspection_status_started ON field_sessions USING btree (inspection_id, status, started_at);


--
-- Name: ix_findings_evidence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_findings_evidence ON findings USING btree (evidence_id);


--
-- Name: ix_findings_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_findings_inspection_status ON findings USING btree (inspection_id, status, severity);


--
-- Name: ix_inspection_items_category_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_items_category_order ON inspection_items USING btree (category_id, sort_order, created_at);


--
-- Name: ix_inspection_reports_inspection_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspection_reports_inspection_status ON inspection_reports USING btree (inspection_id, status, version_no DESC);


--
-- Name: ix_inspections_inspector_assignment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_inspector_assignment_status ON inspections USING btree (inspector_id, assignment_status) WHERE ((status)::text = 'SCHEDULED'::text);


--
-- Name: ix_inspections_owner_asset_status_planned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_owner_asset_status_planned ON inspections USING btree (owner_customer_id, asset_id, status, planned_start_at);


--
-- Name: ix_inspections_owner_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_owner_customer ON inspections USING btree (owner_customer_id);


--
-- Name: ix_inspections_permit_pending_review; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_permit_pending_review ON inspections USING btree (flight_permit_verification_status) WHERE ((flight_permit_verification_status)::text = 'PENDING_REVIEW'::text);


--
-- Name: ix_inspections_scheduled_overdue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_scheduled_overdue ON inspections USING btree (status, planned_end_at) WHERE (((status)::text = 'SCHEDULED'::text) AND ((assignment_status)::text <> 'DECLINED'::text));


--
-- Name: ix_inspections_source_inspection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspections_source_inspection ON inspections USING btree (source_inspection_id) WHERE (source_inspection_id IS NOT NULL);


--
-- Name: ix_inspector_applications_email_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_applications_email_status ON inspector_applications USING btree (normalized_email, status);


--
-- Name: ix_inspector_applications_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_applications_status_created ON inspector_applications USING btree (status, created_at);


--
-- Name: ix_inspector_flight_logs_inspector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_flight_logs_inspector ON inspector_flight_logs USING btree (inspector_user_id, flight_date DESC);


--
-- Name: ix_inspector_payouts_payout_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_payouts_payout_status ON inspector_payouts USING btree (payout_status);


--
-- Name: ix_inspector_receivable_recoveries_receivable; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_receivable_recoveries_receivable ON inspector_receivable_recoveries USING btree (receivable_id, recorded_at);


--
-- Name: ix_inspector_receivables_inspector_recorded; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inspector_receivables_inspector_recorded ON inspector_receivables USING btree (inspector_id, recorded_at);


--
-- Name: ix_notifications_inbox; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_inbox ON notifications USING btree (recipient_user_id, created_at DESC);


--
-- Name: ix_notifications_recipient_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notifications_recipient_status ON notifications USING btree (recipient_user_id, status, created_at DESC);


--
-- Name: ix_otp_challenges_client_ip_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_otp_challenges_client_ip_created ON otp_challenges USING btree (client_ip, created_at DESC) WHERE (client_ip IS NOT NULL);


--
-- Name: ix_otp_challenges_dest_purpose_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_otp_challenges_dest_purpose_created ON otp_challenges USING btree (destination, purpose, created_at DESC) WHERE (destination IS NOT NULL);


--
-- Name: ix_otp_challenges_user_purpose_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_otp_challenges_user_purpose_created ON otp_challenges USING btree (user_id, purpose, created_at DESC) WHERE (user_id IS NOT NULL);


--
-- Name: ix_prospective_customer_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_prospective_customer_status_created ON prospective_customer_registrations USING btree (status, created_at);


--
-- Name: ix_reinspection_verif_reinspection; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_reinspection_verif_reinspection ON reinspection_defect_verifications USING btree (reinspection_id);


--
-- Name: ix_reinspection_verif_source_finding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_reinspection_verif_source_finding ON reinspection_defect_verifications USING btree (source_finding_id);


--
-- Name: ix_reliability_inspector_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_reliability_inspector_type ON inspector_reliability_events USING btree (inspector_id, event_type);


--
-- Name: ix_service_payments_payment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_payments_payment_status ON service_payments USING btree (payment_status);


--
-- Name: ix_service_refunds_occurrence_initiated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_refunds_occurrence_initiated_at ON service_refunds USING btree (occurrence_id, initiated_at);


--
-- Name: ix_service_request_messages_request_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_request_messages_request_created ON service_request_messages USING btree (request_id, created_at, id);


--
-- Name: ix_service_request_message_attachments_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_request_message_attachments_message ON service_request_message_attachments USING btree (message_id, created_at, id);


--
-- Name: ix_service_request_messages_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_request_messages_unread ON service_request_messages USING btree (request_id, read_at) WHERE (read_at IS NULL);


--
-- Name: ix_service_request_occurrences_request_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_request_occurrences_request_status ON service_request_occurrences USING btree (request_id, status, occurrence_index);


--
-- Name: ix_service_requests_assigned_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_requests_assigned_manager ON service_requests USING btree (assigned_manager_id, created_at DESC) WHERE (assigned_manager_id IS NOT NULL);


--
-- Name: ix_service_requests_owner_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_requests_owner_created ON service_requests USING btree (owner_customer_id, created_at DESC);


--
-- Name: ix_service_requests_owner_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_requests_owner_customer ON service_requests USING btree (owner_customer_id);


--
-- Name: ix_service_requests_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_requests_status_created ON service_requests USING btree (status, created_at DESC);


--
-- Name: ix_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_status ON users USING btree (status);


--
-- Name: uq_assets_owner_customer_asset_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_assets_owner_customer_asset_code ON assets USING btree (owner_customer_id, asset_code) WHERE (asset_code IS NOT NULL);


--
-- Name: uq_drone_registration_pending_serial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drone_registration_pending_serial ON drone_registration_applications USING btree (serial_number) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_drones_id_inspector_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drones_id_inspector_owner ON drones USING btree (id, inspector_owner_id);


--
-- Name: uq_drones_inspector_serial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drones_inspector_serial ON drones USING btree (inspector_owner_id, serial_number) WHERE (inspector_owner_id IS NOT NULL);


--
-- Name: uq_drones_owner_customer_serial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drones_owner_customer_serial ON drones USING btree (owner_customer_id, serial_number) WHERE (owner_customer_id IS NOT NULL);


--
-- Name: uq_drones_serial_number_global; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_drones_serial_number_global ON drones USING btree (serial_number);


--
-- Name: uq_inspector_receivable_recovery_reference_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_inspector_receivable_recovery_reference_ci ON inspector_receivable_recoveries USING btree (lower((external_reference)::text));


--
-- Name: uq_prospective_customer_pending_asset_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_prospective_customer_pending_asset_code ON prospective_customer_registrations USING btree (organization_code, asset_code) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_prospective_customer_pending_org_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_prospective_customer_pending_org_code ON prospective_customer_registrations USING btree (organization_code) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_reliability_no_show; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reliability_no_show ON inspector_reliability_events USING btree (inspection_id, inspector_id) WHERE ((event_type)::text = 'NO_SHOW'::text);


--
-- Name: uq_service_refunds_reference_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_service_refunds_reference_ci ON service_refunds USING btree (lower((refund_reference)::text));


--
-- Name: uq_service_refunds_request_reference_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_service_refunds_request_reference_ci ON service_refunds USING btree (lower((request_reference)::text)) WHERE (request_reference IS NOT NULL);


--
-- Name: service_request_quotes trg_service_request_quote_accepted_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_service_request_quote_accepted_immutable BEFORE DELETE OR UPDATE ON service_request_quotes FOR EACH ROW EXECUTE FUNCTION prevent_accepted_service_quote_mutation();


--
-- Name: service_request_messages ck_service_request_messages_body_or_attachment; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER ck_service_request_messages_body_or_attachment AFTER INSERT OR UPDATE ON service_request_messages DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_service_request_message_body_or_attachment();


--
-- Name: service_request_message_attachments ck_service_request_message_attachments_body_or_text; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER ck_service_request_message_attachments_body_or_text AFTER INSERT OR DELETE ON service_request_message_attachments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_service_request_message_body_or_attachment();


--
-- Name: asset_documents asset_documents_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_documents
    ADD CONSTRAINT asset_documents_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE RESTRICT;


--
-- Name: asset_documents asset_documents_uploaded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY asset_documents
    ADD CONSTRAINT asset_documents_uploaded_by_user_id_fkey FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: assets assets_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY assets
    ADD CONSTRAINT assets_category_id_fkey FOREIGN KEY (category_id) REFERENCES asset_categories(id) ON DELETE RESTRICT;


--
-- Name: assets assets_owner_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY assets
    ADD CONSTRAINT assets_owner_customer_id_fkey FOREIGN KEY (owner_customer_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: audit_events audit_events_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_events
    ADD CONSTRAINT audit_events_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: auth_sessions auth_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: drone_registration_applications drone_registration_applications_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_registration_applications
    ADD CONSTRAINT drone_registration_applications_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT;


--
-- Name: drone_registration_applications drone_registration_applications_inspector_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_registration_applications
    ADD CONSTRAINT drone_registration_applications_inspector_user_id_fkey FOREIGN KEY (inspector_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: drone_registration_applications drone_registration_applications_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drone_registration_applications
    ADD CONSTRAINT drone_registration_applications_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: drones drones_inspector_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drones
    ADD CONSTRAINT drones_inspector_owner_id_fkey FOREIGN KEY (inspector_owner_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: drones drones_owner_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY drones
    ADD CONSTRAINT drones_owner_customer_id_fkey FOREIGN KEY (owner_customer_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: evidence evidence_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence
    ADD CONSTRAINT evidence_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES field_sessions(id) ON DELETE RESTRICT;


--
-- Name: evidence evidence_source_video_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence
    ADD CONSTRAINT evidence_source_video_evidence_id_fkey FOREIGN KEY (source_video_evidence_id) REFERENCES evidence(id) ON DELETE SET NULL;


--
-- Name: evidence_upload_sessions evidence_upload_sessions_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence_upload_sessions
    ADD CONSTRAINT evidence_upload_sessions_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: evidence_upload_sessions evidence_upload_sessions_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence_upload_sessions
    ADD CONSTRAINT evidence_upload_sessions_evidence_id_fkey FOREIGN KEY (evidence_id) REFERENCES evidence(id) ON DELETE SET NULL;


--
-- Name: evidence_upload_sessions evidence_upload_sessions_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence_upload_sessions
    ADD CONSTRAINT evidence_upload_sessions_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES field_sessions(id) ON DELETE RESTRICT;


--
-- Name: evidence_upload_sessions evidence_upload_sessions_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence_upload_sessions
    ADD CONSTRAINT evidence_upload_sessions_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE CASCADE;


--
-- Name: field_sessions field_sessions_aborted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_sessions
    ADD CONSTRAINT field_sessions_aborted_by_user_id_fkey FOREIGN KEY (aborted_by_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: field_sessions field_sessions_flight_video_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_sessions
    ADD CONSTRAINT field_sessions_flight_video_evidence_id_fkey FOREIGN KEY (flight_video_evidence_id) REFERENCES evidence(id) ON DELETE SET NULL;


--
-- Name: field_sessions field_sessions_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY field_sessions
    ADD CONSTRAINT field_sessions_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT;


--
-- Name: evidence fk_evidence_inspection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY evidence
    ADD CONSTRAINT fk_evidence_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT;


--
-- Name: inspection_finance_settings fk_inspection_finance_settings_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_finance_settings
    ADD CONSTRAINT fk_inspection_finance_settings_user FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspection_items fk_inspection_items_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_items
    ADD CONSTRAINT fk_inspection_items_category FOREIGN KEY (category_id) REFERENCES asset_categories(id) ON DELETE CASCADE;


--
-- Name: inspector_payouts fk_inspector_payouts_inspector; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_payouts
    ADD CONSTRAINT fk_inspector_payouts_inspector FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspector_payouts fk_inspector_payouts_invoice; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_payouts
    ADD CONSTRAINT fk_inspector_payouts_invoice FOREIGN KEY (occurrence_id) REFERENCES service_invoices(occurrence_id) ON DELETE CASCADE;


--
-- Name: service_invoices fk_service_invoices_occurrence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_invoices
    ADD CONSTRAINT fk_service_invoices_occurrence FOREIGN KEY (occurrence_id) REFERENCES service_request_occurrences(id) ON DELETE CASCADE;


--
-- Name: service_payments fk_service_payments_invoice; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_payments
    ADD CONSTRAINT fk_service_payments_invoice FOREIGN KEY (occurrence_id) REFERENCES service_invoices(occurrence_id) ON DELETE CASCADE;


--
-- Name: service_request_messages fk_service_request_messages_author; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_messages
    ADD CONSTRAINT fk_service_request_messages_author FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_request_messages fk_service_request_messages_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_messages
    ADD CONSTRAINT fk_service_request_messages_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE;


--
-- Name: service_request_message_attachments fk_service_request_message_attachments_message; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_message_attachments
    ADD CONSTRAINT fk_service_request_message_attachments_message FOREIGN KEY (message_id) REFERENCES service_request_messages(id) ON DELETE CASCADE;


--
-- Name: service_request_occurrences fk_service_request_occurrences_drone; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT fk_service_request_occurrences_drone FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT;


--
-- Name: service_request_occurrences fk_service_request_occurrences_inspection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT fk_service_request_occurrences_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT;


--
-- Name: service_request_occurrences fk_service_request_occurrences_inspector; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT fk_service_request_occurrences_inspector FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_request_occurrences fk_service_request_occurrences_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_occurrences
    ADD CONSTRAINT fk_service_request_occurrences_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE;


--
-- Name: service_request_quotes fk_service_request_quotes_drone; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT fk_service_request_quotes_drone FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT;


--
-- Name: service_request_quotes fk_service_request_quotes_inspector; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT fk_service_request_quotes_inspector FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_request_quotes fk_service_request_quotes_issuer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT fk_service_request_quotes_issuer FOREIGN KEY (issued_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_request_quotes fk_service_request_quotes_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT fk_service_request_quotes_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON DELETE CASCADE;


--
-- Name: service_requests fk_service_requests_accepted_quote_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT fk_service_requests_accepted_quote_id FOREIGN KEY (accepted_quote_id, id) REFERENCES service_request_quotes(id, request_id) ON DELETE RESTRICT;


--
-- Name: findings fk_verified_finding_evidence; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY findings
    ADD CONSTRAINT fk_verified_finding_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id) ON DELETE RESTRICT;


--
-- Name: inspection_reports inspection_reports_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_reports
    ADD CONSTRAINT inspection_reports_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT;


--
-- Name: inspection_reports inspection_reports_reviewer_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspection_reports
    ADD CONSTRAINT inspection_reports_reviewer_user_id_fkey FOREIGN KEY (reviewer_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_declined_by_inspector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_declined_by_inspector_id_fkey FOREIGN KEY (declined_by_inspector_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspections inspections_drone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_drone_id_fkey FOREIGN KEY (drone_id) REFERENCES drones(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_flight_permit_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_flight_permit_uploaded_by_fkey FOREIGN KEY (flight_permit_uploaded_by) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspections inspections_flight_permit_verified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_flight_permit_verified_by_fkey FOREIGN KEY (flight_permit_verified_by) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspections inspections_inspector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_inspector_id_fkey FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_owner_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_owner_customer_id_fkey FOREIGN KEY (owner_customer_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspections inspections_source_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspections
    ADD CONSTRAINT inspections_source_inspection_id_fkey FOREIGN KEY (source_inspection_id) REFERENCES inspections(id) ON DELETE SET NULL;


--
-- Name: inspector_applications inspector_applications_approved_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_applications
    ADD CONSTRAINT inspector_applications_approved_user_id_fkey FOREIGN KEY (approved_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspector_applications inspector_applications_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_applications
    ADD CONSTRAINT inspector_applications_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspector_flight_logs inspector_flight_logs_inspector_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_flight_logs
    ADD CONSTRAINT inspector_flight_logs_inspector_user_id_fkey FOREIGN KEY (inspector_user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: inspector_flight_logs inspector_flight_logs_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_flight_logs
    ADD CONSTRAINT inspector_flight_logs_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspector_payouts inspector_payouts_payout_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_payouts
    ADD CONSTRAINT inspector_payouts_payout_recorded_by_user_id_fkey FOREIGN KEY (payout_recorded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspector_profiles inspector_profiles_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_profiles
    ADD CONSTRAINT inspector_profiles_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL;


--
-- Name: inspector_profiles inspector_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_profiles
    ADD CONSTRAINT inspector_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: inspector_receivable_recoveries inspector_receivable_recoveries_receivable_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivable_recoveries
    ADD CONSTRAINT inspector_receivable_recoveries_receivable_id_fkey FOREIGN KEY (receivable_id) REFERENCES inspector_receivables(id) ON DELETE RESTRICT;


--
-- Name: inspector_receivable_recoveries inspector_receivable_recoveries_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivable_recoveries
    ADD CONSTRAINT inspector_receivable_recoveries_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspector_receivables inspector_receivables_inspector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_inspector_id_fkey FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspector_receivables inspector_receivables_occurrence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_occurrence_id_fkey FOREIGN KEY (occurrence_id) REFERENCES service_request_occurrences(id) ON DELETE RESTRICT;


--
-- Name: inspector_receivables inspector_receivables_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: inspector_receivables inspector_receivables_refund_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_receivables
    ADD CONSTRAINT inspector_receivables_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES service_refunds(id) ON DELETE RESTRICT;


--
-- Name: inspector_reliability_events inspector_reliability_events_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_reliability_events
    ADD CONSTRAINT inspector_reliability_events_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES field_sessions(id) ON DELETE SET NULL;


--
-- Name: inspector_reliability_events inspector_reliability_events_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_reliability_events
    ADD CONSTRAINT inspector_reliability_events_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE CASCADE;


--
-- Name: inspector_reliability_events inspector_reliability_events_inspector_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY inspector_reliability_events
    ADD CONSTRAINT inspector_reliability_events_inspector_id_fkey FOREIGN KEY (inspector_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: notifications notifications_recipient_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_recipient_user_id_fkey FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: otp_challenges otp_challenges_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY otp_challenges
    ADD CONSTRAINT otp_challenges_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;


--
-- Name: prospective_customer_registrations prospective_customer_registrations_asset_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY prospective_customer_registrations
    ADD CONSTRAINT prospective_customer_registrations_asset_category_id_fkey FOREIGN KEY (asset_category_id) REFERENCES asset_categories(id) ON DELETE RESTRICT;


--
-- Name: prospective_customer_registrations prospective_customer_registrations_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY prospective_customer_registrations
    ADD CONSTRAINT prospective_customer_registrations_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: prospective_customer_registrations prospective_customer_registrations_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY prospective_customer_registrations
    ADD CONSTRAINT prospective_customer_registrations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: reinspection_defect_verifications reinspection_defect_verifications_new_evidence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reinspection_defect_verifications
    ADD CONSTRAINT reinspection_defect_verifications_new_evidence_id_fkey FOREIGN KEY (new_evidence_id) REFERENCES evidence(id) ON DELETE SET NULL;


--
-- Name: reinspection_defect_verifications reinspection_defect_verifications_reinspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reinspection_defect_verifications
    ADD CONSTRAINT reinspection_defect_verifications_reinspection_id_fkey FOREIGN KEY (reinspection_id) REFERENCES inspections(id) ON DELETE CASCADE;


--
-- Name: reinspection_defect_verifications reinspection_defect_verifications_source_finding_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY reinspection_defect_verifications
    ADD CONSTRAINT reinspection_defect_verifications_source_finding_id_fkey FOREIGN KEY (source_finding_id) REFERENCES findings(id) ON DELETE RESTRICT;


--
-- Name: service_refunds service_refunds_allocation_reconciled_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_allocation_reconciled_by_user_id_fkey FOREIGN KEY (allocation_reconciled_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_refunds service_refunds_cancelled_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_cancelled_by_user_id_fkey FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_refunds service_refunds_completed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_completed_by_user_id_fkey FOREIGN KEY (completed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_refunds service_refunds_initiated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_initiated_by_user_id_fkey FOREIGN KEY (initiated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_refunds service_refunds_occurrence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_refunds
    ADD CONSTRAINT service_refunds_occurrence_id_fkey FOREIGN KEY (occurrence_id) REFERENCES service_payments(occurrence_id) ON DELETE RESTRICT;


--
-- Name: service_request_quotes service_request_quotes_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_request_quotes
    ADD CONSTRAINT service_request_quotes_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_requests service_requests_accepted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_accepted_by_user_id_fkey FOREIGN KEY (accepted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_requests service_requests_assigned_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_assigned_manager_id_fkey FOREIGN KEY (assigned_manager_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_requests service_requests_manager_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_manager_assigned_by_user_id_fkey FOREIGN KEY (manager_assigned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_requests service_requests_owner_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_owner_customer_id_fkey FOREIGN KEY (owner_customer_id) REFERENCES users(id) ON DELETE RESTRICT;


--
-- Name: service_requests service_requests_source_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_requests
    ADD CONSTRAINT service_requests_source_inspection_id_fkey FOREIGN KEY (source_inspection_id) REFERENCES inspections(id) ON DELETE SET NULL;


--
-- Name: findings verified_findings_inspection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY findings
    ADD CONSTRAINT verified_findings_inspection_id_fkey FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--
-- =============================================================================
-- Catalog & Configuration Seeds (preserved from V30-V57)
-- =============================================================================

-- 1. Standardized Asset Categories
INSERT INTO asset_categories (id, code, name, description, active, row_version)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'HIGH_RISE_OFFICE', 'Tòa tháp Văn phòng & Kính mặt dựng (High-Rise Office)', 'Cao ốc văn phòng, vách kính mặt dựng (Curtain Wall), lam chắn nắng và hệ thống MEP đỉnh mái', TRUE, 0),
    ('30000000-0000-0000-0000-000000000002', 'RESIDENTIAL_TOWER', 'Tòa chung cư cao tầng (Residential Apartment Tower)', 'Tòa nhà căn hộ cao tầng, ban công, lô-gia, tường ngoài chống thấm và lan can an toàn', TRUE, 0),
    ('30000000-0000-0000-0000-000000000003', 'COMMERCIAL_MALL', 'Trung tâm thương mại & Khối đế (Commercial Mall & Podium)', 'Tổ hợp thương mại, mái kính giếng trời (Skylight), mặt dựng nhôm Alu và biển hiệu khổ lớn', TRUE, 0),
    ('30000000-0000-0000-0000-000000000004', 'INDUSTRIAL_FACTORY', 'Nhà xưởng công nghiệp & Kho vận (Industrial Factory)', 'Nhà máy kết cấu thép, mái tôn công nghiệp, máng xối thoát nước, ống khói và điện mặt trời áp mái', TRUE, 0),
    ('30000000-0000-0000-0000-000000000005', 'HOTEL_HOSPITAL_COMPLEX', 'Tổ hợp Khách sạn, Bệnh viện & Công trình công cộng', 'Khách sạn, bệnh viện, trường đại học, mái vòm kiến trúc, khu kỹ thuật Chiller và bãi đáp trực thăng', TRUE, 0),
    ('30000000-0000-0000-0000-000000000006', 'TELECOM_ROOFTOP_TOWER', 'Tháp viễn thông & Cột Anten trên mái tòa nhà', 'Cột thu phát sóng BTS trên mái cao ốc, chân đế neo, khung giằng thép, cáp馈 và kim thu sét', TRUE, 0)
ON CONFLICT (code) DO NOTHING;

-- 2. Flight Safety Checklist Templates (PRE_FLIGHT & POST_FLIGHT)
INSERT INTO checklist_templates (id, template_type, label, sort_order, active, required)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'PRE_FLIGHT', 'Airframe, propellers & mounting secure', 1, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000002', 'PRE_FLIGHT', 'Battery levels sufficient for planned flight', 2, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000003', 'PRE_FLIGHT', 'Weather & visibility within safe operating limits', 3, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000004', 'PRE_FLIGHT', 'Takeoff/landing zone clear of people & hazards', 4, TRUE, TRUE),
    ('10000000-0000-0000-0000-000000000005', 'PRE_FLIGHT', 'Navigation & failsafe (RTH) settings verified', 5, TRUE, FALSE),
    ('10000000-0000-0000-0000-000000000006', 'PRE_FLIGHT', 'Camera / sensor payload & storage ready', 6, TRUE, FALSE),
    ('10000000-0000-0000-0000-000000000007', 'PRE_FLIGHT', 'Surrounding airspace hazards & emergency landing spot noted', 7, TRUE, FALSE),
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

-- 3. Standard Inspection Items per Asset Category
INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Hệ vách kính mặt dựng (Curtain Wall) & Kính cường lực ngoài trời', 1),
        ('Đường keo Silicone chống thấm & Gioăng cao su liên kết kính', 2),
        ('Khung nhôm định hình (Mullion / Transom) & Lam chắn nắng (Louvers)', 3),
        ('Sàn mái thượng, Tường chắn mái (Parapet) & Hệ thống chống sét', 4),
        ('Cụm tháp giải nhiệt (Cooling Tower), Chiller & Dàn nóng VRV/VRF trên mái', 5),
        ('Hệ thống ray tời Gondola bảo trì tòa nhà & Móc neo an toàn', 6)
) AS item(label, ord)
WHERE c.code = 'HIGH_RISE_OFFICE';

INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Tường ngoài bê tông, Lớp sơn chống thấm & Vết nứt chân chim mặt đứng', 1),
        ('Hệ thống ban công, Lan can sắt/kính & Lưới an toàn các tầng căn hộ', 2),
        ('Khu vực Lô-gia, Dàn nóng điều hòa treo tường & Ống thoát nước ngoài trời', 3),
        ('Khe co giãn nhiệt giữa các nguyên đơn (Block) tòa chung cư', 4),
        ('Mái Bê tông cốt thép, Bồn nước mái, Quạt tăng áp cầu thang & Chống sét', 5)
) AS item(label, ord)
WHERE c.code = 'RESIDENTIAL_TOWER';

INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mái kính giếng trời (Skylight / Canopy) & Khung thép chịu lực mái', 1),
        ('Tấm ốp nhôm Aluminium (ACP) mặt tiền & Mối ghép chống thấm', 2),
        ('Hệ thống khung biển quảng cáo, Màn hình LED & Logo khổ lớn mặt ngoài', 3),
        ('Máng thu nước mưa mái khối đế (Podium) & Phễu thoát nước Siphonic', 4),
        ('Hệ thống ống gió xả khí thải & Cụm AHU/Chiller trên mái', 5)
) AS item(label, ord)
WHERE c.code = 'COMMERCIAL_MALL';

INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mái tôn công nghiệp, Đinh vít lợp mái & Các điểm rỉ sét / dột nước', 1),
        ('Hệ thống máng xối thu nước mưa (Gutter) & Ống thoát nước đứng', 2),
        ('Khung vì kèo thép tiền chế, Cột biên & Quạt thông gió mái (Louvers)', 3),
        ('Tấm Pin năng lượng mặt trời áp mái (Rooftop Solar Panels) & Điểm nóng', 4),
        ('Ống khói công nghiệp, Silo chứa & Đường ống kỹ thuật ngoài trời', 5)
) AS item(label, ord)
WHERE c.code = 'INDUSTRIAL_FACTORY';

INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mặt dựng kiến trúc, Mái vòm sảnh chính & Ban công phòng nghỉ', 1),
        ('Hệ thống xử lý khí thải y tế / ống thông hơi & Cụm bơm nhiệt trên mái', 2),
        ('Bãi đáp trực thăng sân thượng (Helipad), Đèn hiệu & Lưới bảo vệ', 3),
        ('Hồ bơi vô cực trên mái / Sân vườn thượng uyển & Chống thấm cổ ống', 4)
) AS item(label, ord)
WHERE c.code = 'HOTEL_HOSPITAL_COMPLEX';

INSERT INTO inspection_items (id, category_id, label, sort_order, active)
SELECT gen_random_uuid(), c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Bệ móng bê tông & Bản mã bu-lông neo chân cột Anten trên mái tòa nhà', 1),
        ('Thân tháp thép tự đứng / Dây co cáp giằng (Guy Wires) & Tăng-đơ', 2),
        ('Cụm Anten phát sóng RRU, Chảo viba & Giá đỡ (Mounting Brackets)', 3),
        ('Thang leo cáp, Máng cáp Feeder & Hộp tiếp địa chống sét lan truyền', 4),
        ('Kim thu sét đỉnh tháp & Đèn báo không chướng ngại vật ban đêm (Aviation Light)', 5)
) AS item(label, ord)
WHERE c.code = 'TELECOM_ROOFTOP_TOWER';

-- 4. Platform AI Settings Singleton
INSERT INTO platform_ai_settings (id, auto_confirm_enabled, auto_confirm_threshold, rejected_pending_count)
VALUES (1, TRUE, 85.00, 0)
ON CONFLICT (id) DO NOTHING;

-- 5. Drone Document Configurations
INSERT INTO drone_document_configs (code, title_vi, title_en, description_vi, description_en, is_required, is_active, display_order)
VALUES
    ('REGISTRATION', 'Đăng ký drone', 'Drone Registration', 'Giấy chứng nhận đăng ký hoặc đăng ký tạm thời do cơ quan chức năng cấp', 'Official registration certificate issued by competent authority', TRUE, TRUE, 1),
    ('OWNERSHIP', 'Chứng từ sở hữu', 'Proof of Ownership', 'Hóa đơn tài chính, hợp đồng chuyển nhượng hoặc tờ khai hải quan', 'Commercial invoice, sales contract, or customs declaration', TRUE, TRUE, 2),
    ('INSPECTION', 'Kiểm định an toàn', 'Safety Inspection', 'Chứng nhận an toàn kỹ thuật, biên bản kiểm định hoặc CoC (Tùy chọn)', 'Airworthiness cert, inspection report, or Certificate of Conformity (Optional)', FALSE, FALSE, 3)
ON CONFLICT (code) DO NOTHING;

-- 6. Drone Feature Configurations
INSERT INTO drone_feature_configs (code, name_vi, name_en, description_vi, description_en, category, is_active, display_order)
VALUES
    ('RTK_POSITIONING', 'Định vị chính xác RTK', 'RTK GNSS Positioning', 'Cố định vị trí sai số centimet, chống nhiễu từ trường sát tòa nhà', 'Centimeter-level precision positioning resistant to magnetic interference', 'CAPABILITY', TRUE, 1),
    ('THERMAL_CAMERA', 'Camera nhiệt (Thermal)', 'Thermal Camera', 'Phát hiện thấm dột, ẩm mốc và rò rỉ nhiệt', 'Detect moisture intrusion, leaks, and thermal anomalies', 'CAPABILITY', TRUE, 2),
    ('LIDAR_SCANNER', 'Cảm biến quét LiDAR', 'LiDAR Scanner', 'Quét mô hình 3D đám mây điểm công trình', '3D point-cloud structural laser scanning', 'CAPABILITY', TRUE, 3),
    ('NIGHT_VISION', 'Nhìn đêm / Đèn trợ sáng', 'Night Vision / Spotlight', 'Hỗ trợ kiểm tra khu vực thiếu sáng hoặc ban đêm', 'Low-light sensor or high-lumen spotlight payload', 'CAPABILITY', TRUE, 4),
    ('OBSTACLE_AVOIDANCE', 'Tránh va chạm đa hướng', 'Omnidirectional Obstacle Sensing', 'Cảm biến phát hiện và phanh tự động tránh vật cản', 'Multi-directional obstacle detection and auto-braking', 'SAFETY', TRUE, 5),
    ('FAILSAFE_RTH', 'Tự quay về khi mất sóng (RTH)', 'Failsafe Return-to-Home (RTH)', 'Tự động quay về điểm cất cánh khi pin yếu hoặc mất tín hiệu', 'Automatic return when link is lost or battery is low', 'SAFETY', TRUE, 6),
    ('WEATHERPROOF_IP', 'Kháng nước & bụi (IP54/IP55)', 'Weatherproof (IP54/IP55)', 'Hoạt động ổn định trong điều kiện mưa nhẹ và bụi công trường', 'Ingress protection for dusty sites and light rain', 'SAFETY', TRUE, 7),
    ('PARACHUTE_SAFETY', 'Dù hạ cánh khẩn cấp', 'Emergency Parachute', 'Hệ thống bung dù giảm chấn khi xảy ra sự cố động cơ', 'Ballistic parachute recovery system for populated areas', 'SAFETY', TRUE, 8),
    ('UPWARD_GIMBAL', 'Gimbal/camera hướng lên', 'Upward-facing gimbal/camera', 'Khả năng thu hình hướng lên khi được lắp và xác minh trên drone cụ thể', 'Capability to capture upward-facing imagery when verified for the specific drone', 'CAPABILITY', TRUE, 9),
    ('GNSS_DENIED_POSITIONING', 'Định vị trong môi trường mất GNSS', 'GNSS-denied positioning', 'Khả năng định vị trong môi trường mất hoặc suy giảm GNSS khi được xác minh trên drone cụ thể', 'Positioning capability in GNSS-denied environments when verified for the specific drone', 'CAPABILITY', TRUE, 10)
ON CONFLICT (code) DO NOTHING;

-- 7. Pilot License Classes (Decree 288/2025/ND-CP)
INSERT INTO pilot_license_classes (name, description, active)
VALUES
    ('Hạng A', 'Giấy phép điều khiển phương tiện bay Hạng A (Điều khiển bay bằng trực quan - VLOS đối với phương tiện bay có trọng lượng cất cánh tối đa từ 0,25 kg đến dưới 02 kg) theo Nghị định 288/2025/NĐ-CP.', TRUE),
    ('Hạng B', 'Giấy phép điều khiển phương tiện bay Hạng B (Điều khiển bay bằng thiết bị - BVLOS hoặc lập trình tự động; bắt buộc đối với phương tiện bay từ 02 kg trở lên) theo Nghị định 288/2025/NĐ-CP.', TRUE)
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description,
    active = TRUE;
