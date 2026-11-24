-- Remove unused detailed verification text fields and hardcoded universal numeric thresholds
-- from seeded checklists so checks apply cleanly across any assigned UAV model and spec.

UPDATE checklist_templates
SET
    label = 'Airframe, propellers & gimbal mounting lock verified',
    label_vi = 'Kiểm tra khung thân, cánh quạt và khóa gắn gimbal nguyên vẹn',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000001';

UPDATE checklist_templates
SET
    label = 'Flight & controller batteries healthy, balanced & sufficient for mission',
    label_vi = 'Pin bay & pin tay điều khiển ổn định, cân bằng cell và đủ cho lộ trình',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000002';

UPDATE checklist_templates
SET
    label = 'Surface wind, gusts & visibility within assigned drone operating limits',
    label_vi = 'Gió bề mặt, gió giật và tầm nhìn trong giới hạn vận hành của drone được gán',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000003';

UPDATE checklist_templates
SET
    label = 'Takeoff/landing pad & safety perimeter clear of obstacles and bystanders',
    label_vi = 'Bãi cất/hạ cánh sạch vật cản & vành đai an toàn không có người qua lại',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000004';

UPDATE checklist_templates
SET
    label = 'Flight permit ceiling & surrounding obstacle/EMF clearance checked',
    label_vi = 'Xác nhận trần bay theo giấy phép & rà soát vật cản / nhiễu điện từ',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000005';

UPDATE checklist_templates
SET
    label = 'GNSS lock, compass/IMU status & RTH failsafe set above obstacles',
    label_vi = 'Khóa GNSS ổn định, la bàn/IMU bình thường & độ cao RTH cao hơn vật cản',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000006';

UPDATE checklist_templates
SET
    label = 'Payload optics clean, storage sufficient & C2 telemetry link verified',
    label_vi = 'Ống kính sạch, bộ nhớ đủ cho các chuyến bay & kết nối điều khiển C2 ổn định',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000007';

UPDATE checklist_templates
SET
    label = 'Backup emergency landing zone & fire/first-aid kit ready on site',
    label_vi = 'Bãi hạ cánh khẩn cấp dự phòng & thiết bị PCCC/sơ cứu sẵn sàng tại hiện trường',
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '10000000-0000-0000-0000-000000000008';

UPDATE checklist_templates
SET
    details = NULL,
    details_vi = NULL,
    reference_threshold = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    '20000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000004'
);
