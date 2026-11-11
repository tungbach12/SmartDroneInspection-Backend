-- =============================================================================
-- V35: Configurable Drone Features and Safety Checklist
-- =============================================================================

CREATE TABLE drone_feature_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name_vi VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    description_vi VARCHAR(500),
    description_en VARCHAR(500),
    category VARCHAR(32) NOT NULL DEFAULT 'SAFETY',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_drone_feature_configs_active_order ON drone_feature_configs (is_active, display_order);

INSERT INTO drone_feature_configs (code, name_vi, name_en, description_vi, description_en, category, is_active, display_order)
VALUES
('RTK_POSITIONING', 'Định vị chính xác RTK', 'RTK GNSS Positioning', 'Cố định vị trí sai số centimet, chống nhiễu từ trường sát tòa nhà', 'Centimeter-level precision positioning resistant to magnetic interference', 'CAPABILITY', TRUE, 1),
('THERMAL_CAMERA', 'Camera nhiệt (Thermal)', 'Thermal Camera', 'Phát hiện thấm dột, ẩm mốc và rò rỉ nhiệt', 'Detect moisture intrusion, leaks, and thermal anomalies', 'CAPABILITY', TRUE, 2),
('LIDAR_SCANNER', 'Cảm biến quét LiDAR', 'LiDAR Scanner', 'Quét mô hình 3D đám mây điểm công trình', '3D point-cloud structural laser scanning', 'CAPABILITY', TRUE, 3),
('NIGHT_VISION', 'Nhìn đêm / Đèn trợ sáng', 'Night Vision / Spotlight', 'Hỗ trợ kiểm tra khu vực thiếu sáng hoặc ban đêm', 'Low-light sensor or high-lumen spotlight payload', 'CAPABILITY', TRUE, 4),
('OBSTACLE_AVOIDANCE', 'Tránh va chạm đa hướng', 'Omnidirectional Obstacle Sensing', 'Cảm biến phát hiện và phanh tự động tránh vật cản', 'Multi-directional obstacle detection and auto-braking', 'SAFETY', TRUE, 5),
('FAILSAFE_RTH', 'Tự quay về khi mất sóng (RTH)', 'Failsafe Return-to-Home (RTH)', 'Tự động quay về điểm cất cánh khi pin yếu hoặc mất tín hiệu', 'Automatic return when link is lost or battery is low', 'SAFETY', TRUE, 6),
('WEATHERPROOF_IP', 'Kháng nước & bụi (IP54/IP55)', 'Weatherproof (IP54/IP55)', 'Hoạt động ổn định trong điều kiện mưa nhẹ và bụi công trường', 'Ingress protection for dusty sites and light rain', 'SAFETY', TRUE, 7),
('PARACHUTE_SAFETY', 'Dù hạ cánh khẩn cấp', 'Emergency Parachute', 'Hệ thống bung dù giảm chấn khi xảy ra sự cố động cơ', 'Ballistic parachute recovery system for populated areas', 'SAFETY', TRUE, 8);

ALTER TABLE drone_registration_applications
    ADD COLUMN IF NOT EXISTS features JSONB;

ALTER TABLE drones
    ADD COLUMN IF NOT EXISTS features JSONB;
