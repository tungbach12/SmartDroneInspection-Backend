-- Seed Vietnam official UAV pilot license classes according to Decree 288/2025/ND-CP (Điều 27)
UPDATE pilot_license_classes SET active = FALSE;

INSERT INTO pilot_license_classes (name, description, active)
VALUES
    ('Hạng A', 'Giấy phép điều khiển phương tiện bay Hạng A (Điều khiển bay bằng trực quan - VLOS đối với phương tiện bay có trọng lượng cất cánh tối đa từ 0,25 kg đến dưới 02 kg) theo Nghị định 288/2025/NĐ-CP.', TRUE),
    ('Hạng B', 'Giấy phép điều khiển phương tiện bay Hạng B (Điều khiển bay bằng thiết bị - BVLOS hoặc lập trình tự động; bắt buộc đối với phương tiện bay từ 02 kg trở lên) theo Nghị định 288/2025/NĐ-CP.', TRUE)
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description,
    active = TRUE;
