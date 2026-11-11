-- =============================================================================
-- V33: Drone Document Configurations and Outdoor Flight Capabilities
-- =============================================================================

CREATE TABLE drone_document_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    title_vi VARCHAR(255) NOT NULL,
    title_en VARCHAR(255) NOT NULL,
    description_vi VARCHAR(1000),
    description_en VARCHAR(1000),
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_drone_document_configs_active_order ON drone_document_configs (is_active, display_order);

INSERT INTO drone_document_configs (code, title_vi, title_en, description_vi, description_en, is_required, is_active, display_order)
VALUES
('REGISTRATION', 'Đăng ký drone', 'Drone Registration', 'Giấy chứng nhận đăng ký hoặc đăng ký tạm thời do cơ quan chức năng cấp', 'Official registration certificate issued by competent authority', TRUE, TRUE, 1),
('OWNERSHIP', 'Chứng từ sở hữu', 'Proof of Ownership', 'Hóa đơn tài chính, hợp đồng chuyển nhượng hoặc tờ khai hải quan', 'Commercial invoice, sales contract, or customs declaration', TRUE, TRUE, 2),
('INSPECTION', 'Kiểm định an toàn', 'Safety Inspection', 'Chứng nhận an toàn kỹ thuật, biên bản kiểm định hoặc CoC (Tùy chọn)', 'Airworthiness cert, inspection report, or Certificate of Conformity (Optional)', FALSE, FALSE, 3);

ALTER TABLE drone_registration_applications
    ALTER COLUMN inspection_file_key DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS document_files JSONB,
    ADD COLUMN IF NOT EXISTS max_wind_resistance_mps NUMERIC(4, 1),
    ADD COLUMN IF NOT EXISTS optical_zoom_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS has_thermal_camera BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_rtk BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS document_files JSONB,
    ADD COLUMN IF NOT EXISTS max_wind_resistance_mps NUMERIC(4, 1),
    ADD COLUMN IF NOT EXISTS optical_zoom_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS has_thermal_camera BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_rtk BOOLEAN NOT NULL DEFAULT FALSE;
