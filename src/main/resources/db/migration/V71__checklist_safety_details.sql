-- Enrich safety checklist templates with category, bilingual instructions, pass thresholds,
-- safety criticality, and store structured checklist snapshots + pre-flight site readings.

ALTER TABLE checklist_templates
    ADD COLUMN IF NOT EXISTS category character varying(64) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS label_vi character varying(300),
    ADD COLUMN IF NOT EXISTS details character varying(1000),
    ADD COLUMN IF NOT EXISTS details_vi character varying(1000),
    ADD COLUMN IF NOT EXISTS reference_threshold character varying(120),
    ADD COLUMN IF NOT EXISTS safety_critical boolean NOT NULL DEFAULT false;

INSERT INTO checklist_templates (
    id, template_type, category, label, label_vi, details, details_vi,
    reference_threshold, safety_critical, sort_order, active, required
)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'PRE_FLIGHT', 'AIRFRAME_POWER',
     'Airframe, propellers & gimbal mounting lock verified',
     'Kiểm tra khung thân, cánh quạt và khóa gắn gimbal',
     'Inspect frame arms for cracks, twist-lock propellers securely without chips/nicks, confirm gimbal rubber dampers and SD door latched.',
     'Kiểm tra nứt gãy tay đòn, độ chặt khóa cánh quạt (không mẻ/móp), đệm chống rung gimbal và nắp khe thẻ nhớ.',
     'Zero cracks / play', true, 1, true, true),

    ('10000000-0000-0000-0000-000000000002', 'PRE_FLIGHT', 'AIRFRAME_POWER',
     'Flight & controller batteries healthy & balanced',
     'Pin bay & pin tay điều khiển đủ dung lượng và ổn định',
     'Confirm aircraft battery SOC, inspect housing for swelling/thermal marks, check cell voltage delta and RC battery charge.',
     'Kiểm tra % pin máy bay, vỏ pin không phồng/cháy xém, độ lệch áp giữa các cell và mức pin tay điều khiển.',
     'SOC >= 80% · Δ <= 0.1V', true, 2, true, true),

    ('10000000-0000-0000-0000-000000000003', 'PRE_FLIGHT', 'WEATHER_AIRSPACE',
     'Surface wind, gusts & visibility within UAV operating envelope',
     'Gió bề mặt, gió giật và tầm nhìn trong giới hạn an toàn',
     'Measure on-site wind speed/gusts, confirm dry conditions (no rain/dense fog), and check geomagnetic Kp index.',
     'Đo tốc độ gió/gió giật tại hiện trường, đảm bảo trời khô ráo (không mưa/sương mù dày) và chỉ số từ trường Kp an toàn.',
     'Wind <= 10 m/s · Vis >= 3 km', true, 3, true, true),

    ('10000000-0000-0000-0000-000000000004', 'PRE_FLIGHT', 'SITE_EMERGENCY',
     'Takeoff/landing pad & safety perimeter secured',
     'Khu vực cất/hạ cánh & vành đai an toàn đã được cô lập',
     'Establish flat FOD-free takeoff pad, deploy safety cones/tape around perimeter, brief ground observer and keep bystanders clear.',
     'Chọn bãi cất/hạ cánh phẳng sạch vật cản, đặt cọc/dây cảnh báo bán kính an toàn, phổ biến người hỗ trợ và giữ khoảng cách người không phận sự.',
     'Radius >= 15 m clear', true, 4, true, true),

    ('10000000-0000-0000-0000-000000000005', 'PRE_FLIGHT', 'WEATHER_AIRSPACE',
     'Flight permit ceiling & surrounding obstacle/EMF clearance checked',
     'Xác nhận trần bay theo phép & kiểm tra vật cản / nhiễu điện từ',
     'Verify mission stays inside approved permit altitude and boundary; identify cranes, power lines, RF antennas, and tallest building height.',
     'Đối chiếu độ cao trần bay trong giấy phép; xác định cần cẩu, đường dây điện, trạm phát sóng và chiều cao công trình cao nhất.',
     'Within permit ceiling', true, 5, true, true),

    ('10000000-0000-0000-0000-000000000006', 'PRE_FLIGHT', 'NAVIGATION_FAILSAFE',
     'GNSS lock, compass/IMU status & RTH failsafe configured',
     'Khóa vệ tinh GNSS, la bàn/IMU ổn định & cài đặt RTH an toàn',
     'Wait for strong GNSS lock, confirm zero compass/IMU calibration warnings, set Return-to-Home altitude above tallest obstacle + 15 m and verify signal-loss action.',
     'Đợi đủ vệ tinh GNSS, không có cảnh báo hiệu chuẩn la bàn/IMU, đặt độ cao RTH cao hơn vật cản cao nhất + 15 m và kiểm tra chế độ mất sóng.',
     '>= 12 Sats · RTH +15 m', true, 6, true, true),

    ('10000000-0000-0000-0000-000000000007', 'PRE_FLIGHT', 'PAYLOAD_COMMS',
     'Payload optics clean, storage free & C2 telemetry link verified',
     'Ống kính/cảm biến sạch, đủ dung lượng lưu trữ & kết nối C2 ổn định',
     'Clean RGB/thermal lenses, verify storage free space for planned sorties, confirm stable RC/telemetry signal and test hover control at 3 m for 10 s.',
     'Vệ sinh ống kính RGB/nhiệt, kiểm tra bộ nhớ trống cho các chuyến bay, test tín hiệu điều khiển & bay treo kiểm tra ở 3 m trong 10 giây.',
     'Storage >= 16 GB · 3m hover OK', false, 7, true, false),

    ('10000000-0000-0000-0000-000000000008', 'PRE_FLIGHT', 'SITE_EMERGENCY',
     'Emergency landing zone & fire/first-aid kit ready',
     'Xác định bãi hạ cánh khẩn cấp & sẵn sàng thiết bị PCCC/sơ cứu',
     'Designate secondary emergency landing spot downwind, ensure LiPo fire bag / extinguisher and first-aid kit are immediately accessible.',
     'Chọn sẵn vị trí hạ cánh khẩn cấp dự phòng cuối hướng gió, đặt túi chống cháy pin LiPo / bình chữa cháy và hộp sơ cứu trong tầm với.',
     'Backup LZ + LiPo bag ready', false, 8, true, false),

    ('20000000-0000-0000-0000-000000000001', 'POST_FLIGHT', 'POST_FLIGHT_HARDWARE',
     'Aircraft landed cleanly & propulsion/power completely disarmed',
     'Hạ cánh an toàn & ngắt hoàn toàn động cơ / nguồn pin',
     'Confirm all rotors fully stopped before approaching; power off aircraft before controller and inspect airframe for mid-flight strikes or loose fasteners.',
     'Đợi cánh quạt dừng hẳn trước khi tiếp cận; tắt máy bay trước tay điều khiển và kiểm tra khung vỏ, ốc vít sau bay.',
     'Disarmed · Zero strike marks', true, 1, true, true),

    ('20000000-0000-0000-0000-000000000002', 'POST_FLIGHT', 'POST_FLIGHT_HARDWARE',
     'Motors, ESCs & batteries checked for abnormal heat or swelling',
     'Kiểm tra nhiệt độ bất thường / phồng pin, motor & ESC',
     'Check motor bells, ESC housing, and battery packs for overheating or swelling; allow packs to cool to ambient before storing or recharging.',
     'Kiểm tra nhiệt độ motor/ESC và tình trạng pin sau bay; để pin nguội về nhiệt độ môi trường trước khi cất giữ hoặc sạc lại.',
     'Temp < 60°C · No swelling', true, 2, true, true),

    ('20000000-0000-0000-0000-000000000003', 'POST_FLIGHT', 'POST_FLIGHT_MEDIA',
     'All sortie photos, videos & flight logs verified & backed up',
     'Kiểm tra đầy đủ ảnh, video & nhật ký bay các chuyến trước khi rời hiện trường',
     'Confirm target asset checkpoints were captured clearly without motion blur, and verify telemetry/SRT/CSV logs are readable on field device.',
     'Rà soát ảnh/video từng hạng mục công trình không bị mờ nhòe và đảm bảo file log bay/telemetry đọc được đầy đủ trước khi thu quân.',
     'Media + logs verified', true, 3, true, true),

    ('20000000-0000-0000-0000-000000000004', 'POST_FLIGHT', 'POST_FLIGHT_MEDIA',
     'Safety cones removed, equipment packed & site restored',
     'Thu dọn thiết bị, tháo cọc cảnh báo & bàn giao mặt bằng sạch sẽ',
     'Recover takeoff pad, cones, tools, and spare batteries; confirm zero debris left on customer premises and log any incidents or site constraints.',
     'Thu hồi thảm bay, cọc tiêu, dụng cụ và pin dự phòng; đảm bảo hiện trường sạch sẽ và ghi nhận các hạn chế/sự cố phát sinh (nếu có).',
     'Site cleared', false, 4, true, false)
ON CONFLICT (id) DO UPDATE
SET
    category = EXCLUDED.category,
    label = EXCLUDED.label,
    label_vi = EXCLUDED.label_vi,
    details = EXCLUDED.details,
    details_vi = EXCLUDED.details_vi,
    reference_threshold = EXCLUDED.reference_threshold,
    safety_critical = EXCLUDED.safety_critical,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active,
    required = EXCLUDED.required,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE field_sessions
    ADD COLUMN IF NOT EXISTS session_date date,
    ADD COLUMN IF NOT EXISTS pre_flight_checklist_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS pre_flight_conditions jsonb,
    ADD COLUMN IF NOT EXISTS post_flight_checklist_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE field_sessions
SET session_date = COALESCE((started_at AT TIME ZONE 'UTC')::date, (created_at AT TIME ZONE 'UTC')::date)
WHERE session_date IS NULL;
