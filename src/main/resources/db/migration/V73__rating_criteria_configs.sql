-- Admin-configurable rating criteria per rater role (CUSTOMER / MANAGER) replacing hardcoded lists.

CREATE TABLE IF NOT EXISTS rating_criterion_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    rater_role character varying(16) NOT NULL,
    code character varying(64) NOT NULL,
    label_en character varying(120) NOT NULL,
    label_vi character varying(120) NOT NULL,
    description_en character varying(300),
    description_vi character varying(300),
    display_order integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_rating_criterion_role_code UNIQUE (rater_role, code),
    CONSTRAINT ck_rating_criterion_rater_role CHECK (rater_role IN ('CUSTOMER', 'MANAGER'))
);

CREATE INDEX IF NOT EXISTS ix_rating_criterion_configs_role_order
    ON rating_criterion_configs (rater_role, is_active, display_order, created_at);

INSERT INTO rating_criterion_configs (
    rater_role, code, label_en, label_vi, description_en, description_vi, display_order, is_active
)
VALUES
    ('CUSTOMER', 'communication', 'Communication', 'Giao tiếp & phối hợp',
     'Clarity and responsiveness before, during, and after the inspection.',
     'Mức độ trao đổi rõ ràng và phản hồi kịp thời trước, trong và sau buổi kiểm tra.', 1, true),
    ('CUSTOMER', 'professionalism', 'Professionalism', 'Tác phong chuyên nghiệp',
     'On-site conduct, punctuality, and respect for site rules.',
     'Tác phong tại hiện trường, đúng giờ và tuân thủ nội quy khu vực.', 2, true),
    ('CUSTOMER', 'evidenceQuality', 'Evidence & report', 'Chất lượng bằng chứng & báo cáo',
     'Clarity of inspection media, findings coverage, and report presentation.',
     'Độ rõ nét hình ảnh/video, mức độ bao phủ hạng mục và báo cáo dễ hiểu.', 3, true),
    ('MANAGER', 'timeliness', 'Timeliness', 'Đúng tiến độ',
     'Adherence to scheduled flight windows, SLA turnaround, and submission deadlines.',
     'Tuân thủ khung giờ bay đã lập lịch và thời hạn nộp kết quả/báo cáo.', 1, true),
    ('MANAGER', 'technicalAccuracy', 'Technical accuracy', 'Độ chính xác kỹ thuật',
     'Accuracy of defect identification, severity grading, and telemetry logging.',
     'Độ chính xác khi xác định khuyết tật, phân loại mức độ và ghi nhận telemetry.', 2, true),
    ('MANAGER', 'sopAndSafety', 'SOP & safety', 'Tuân thủ SOP & an toàn bay',
     'Compliance with flight permits, pre/post-flight safety checklists, and field SOP.',
     'Tuân thủ giấy phép bay, checklist an toàn trước/sau bay và quy trình SOP.', 3, true)
ON CONFLICT (rater_role, code) DO NOTHING;
