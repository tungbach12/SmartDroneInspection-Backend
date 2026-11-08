-- Create dedicated checklist_templates table for Pre-flight, Post-flight,
-- and per-category Standard Inspection Items, plus seed standardized building categories.

CREATE TABLE checklist_templates (
    id UUID PRIMARY KEY,
    template_type VARCHAR(32) NOT NULL,
    category_id UUID,
    label VARCHAR(300) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_checklist_templates_category
        FOREIGN KEY (category_id) REFERENCES asset_categories(id) ON DELETE CASCADE,
    CONSTRAINT ck_checklist_templates_type
        CHECK (template_type IN ('CATEGORY_INSPECTION_ITEM', 'PRE_FLIGHT', 'POST_FLIGHT')),
    CONSTRAINT ck_checklist_templates_category_scope
        CHECK (
            (template_type = 'CATEGORY_INSPECTION_ITEM' AND category_id IS NOT NULL)
            OR (template_type IN ('PRE_FLIGHT', 'POST_FLIGHT') AND category_id IS NULL)
        )
);

CREATE INDEX ix_checklist_templates_type_order
    ON checklist_templates (template_type, sort_order, created_at);

CREATE INDEX ix_checklist_templates_category_order
    ON checklist_templates (category_id, sort_order, created_at)
    WHERE category_id IS NOT NULL;

-- Seed Pre-flight Safety Checklist templates (Admin-managed)
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'PRE_FLIGHT', NULL, 'Drone airframe, propellers & camera gimbal inspected and intact', 1, TRUE),
    ('10000000-0000-0000-0000-000000000002', 'PRE_FLIGHT', NULL, 'Battery level sufficient, GPS lock acquired & Return-to-Home (RTH) altitude set', 2, TRUE),
    ('10000000-0000-0000-0000-000000000003', 'PRE_FLIGHT', NULL, 'Weather, wind speed & visual line-of-sight within safe operating limits', 3, TRUE),
    ('10000000-0000-0000-0000-000000000004', 'PRE_FLIGHT', NULL, 'Takeoff/landing zone secured and clear of bystanders', 4, TRUE),
    ('10000000-0000-0000-0000-000000000005', 'PRE_FLIGHT', NULL, 'Flight permit verified & surrounding airspace clear for takeoff', 5, TRUE);

-- Seed Post-flight Completion Checklist templates (Admin-managed)
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'POST_FLIGHT', NULL, 'Drone landed safely, motors disarmed & hardware free of post-flight damage', 1, TRUE),
    ('20000000-0000-0000-0000-000000000002', 'POST_FLIGHT', NULL, 'All planned structural inspection checkpoints captured', 2, TRUE),
    ('20000000-0000-0000-0000-000000000003', 'POST_FLIGHT', NULL, 'Captured photos, videos & flight telemetry logs saved intact', 3, TRUE),
    ('20000000-0000-0000-0000-000000000004', 'POST_FLIGHT', NULL, 'Equipment packed, safety barriers removed & site restored', 4, TRUE);

-- Ensure standardized Building & Tower categories exist
INSERT INTO asset_categories (id, code, name, description, active, row_version)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'HIGH_RISE_OFFICE', 'Tòa tháp Văn phòng & Kính mặt dựng (High-Rise Office)', 'Cao ốc văn phòng, vách kính mặt dựng (Curtain Wall), lam chắn nắng và hệ thống MEP đỉnh mái', TRUE, 0),
    ('30000000-0000-0000-0000-000000000002', 'RESIDENTIAL_TOWER', 'Tòa chung cư cao tầng (Residential Apartment Tower)', 'Tòa nhà căn hộ cao tầng, ban công, lô-gia, tường ngoài chống thấm và lan can an toàn', TRUE, 0),
    ('30000000-0000-0000-0000-000000000003', 'COMMERCIAL_MALL', 'Trung tâm thương mại & Khối đế (Commercial Mall & Podium)', 'Tổ hợp thương mại, mái kính giếng trời (Skylight), mặt dựng nhôm Alu và biển hiệu khổ lớn', TRUE, 0),
    ('30000000-0000-0000-0000-000000000004', 'INDUSTRIAL_FACTORY', 'Nhà xưởng công nghiệp & Kho vận (Industrial Factory)', 'Nhà máy kết cấu thép, mái tôn công nghiệp, máng xối thoát nước, ống khói và điện mặt trời áp mái', TRUE, 0),
    ('30000000-0000-0000-0000-000000000005', 'HOTEL_HOSPITAL_COMPLEX', 'Tổ hợp Khách sạn, Bệnh viện & Công trình công cộng', 'Khách sạn, bệnh viện, trường đại học, mái vòm kiến trúc, khu kỹ thuật Chiller và bãi đáp trực thăng', TRUE, 0),
    ('30000000-0000-0000-0000-000000000006', 'TELECOM_ROOFTOP_TOWER', 'Tháp viễn thông & Cột Anten trên mái tòa nhà', 'Cột thu phát sóng BTS trên mái cao ốc, chân đế neo, khung giằng thép, cáp馈 và kim thu sét', TRUE, 0)
ON CONFLICT (code) DO NOTHING;

-- Seed Standard Inspection Items for HIGH_RISE_OFFICE
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
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

-- Seed Standard Inspection Items for RESIDENTIAL_TOWER
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
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

-- Seed Standard Inspection Items for COMMERCIAL_MALL
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
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

-- Seed Standard Inspection Items for INDUSTRIAL_FACTORY
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
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

-- Seed Standard Inspection Items for HOTEL_HOSPITAL_COMPLEX
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mặt dựng kiến trúc, Mái vòm sảnh chính & Ban công phòng nghỉ', 1),
        ('Hệ thống xử lý khí thải y tế / ống thông hơi & Cụm bơm nhiệt trên mái', 2),
        ('Bãi đáp trực thăng sân thượng (Helipad), Đèn hiệu & Lưới bảo vệ', 3),
        ('Hồ bơi vô cực trên mái / Sân vườn thượng uyển & Chống thấm cổ ống', 4)
) AS item(label, ord)
WHERE c.code = 'HOTEL_HOSPITAL_COMPLEX';

-- Seed Standard Inspection Items for TELECOM_ROOFTOP_TOWER
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
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

-- Also seed items for existing general BUILDING, BRIDGE, ENERGY categories if present
INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mặt dựng kính & Tường bao ngoài tòa nhà (Exterior Facade & Curtain Wall)', 1),
        ('Kết cấu sàn mái, Tường chắn mái & Hệ thống thoát nước mưa', 2),
        ('Ban công, Lan can bảo vệ & Khe co giãn kết cấu', 3),
        ('Hệ thống cơ điện trên mái (Chiller, Cooling Tower, Thông gió & Chống sét)', 4)
) AS item(label, ord)
WHERE c.code = 'BUILDING';

INSERT INTO checklist_templates (id, template_type, category_id, label, sort_order, active)
SELECT gen_random_uuid(), 'CATEGORY_INSPECTION_ITEM', c.id, item.label, item.ord, TRUE
FROM asset_categories c
CROSS JOIN (
    VALUES
        ('Mố cầu & Trụ đỡ (Abutments & Piers)', 1),
        ('Dầm chính & Dầm biên (Girders & Beams)', 2),
        ('Khe co giãn & Khớp nối (Expansion Joints)', 3),
        ('Hệ cáp văng / Cáp treo (Stay Cables)', 4),
        ('Mặt đường & Lan can hộ lan (Deck Surface & Railings)', 5)
) AS item(label, ord)
WHERE c.code = 'BRIDGE';
