-- V43: Storage and vector index for RAG repair standards and specifications

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        PERFORM set_config('search_path', current_schema() || ', public', true);
        CREATE TABLE IF NOT EXISTS repair_knowledge_chunks (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            standard_code VARCHAR(64) NOT NULL,
            title VARCHAR(255) NOT NULL,
            category VARCHAR(64) NOT NULL,
            defect_patterns VARCHAR(255) NOT NULL,
            content TEXT NOT NULL,
            reference_url VARCHAR(500),
            embedding vector(1536),
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_repair_knowledge_chunks_category
            ON repair_knowledge_chunks(category);
        CREATE INDEX IF NOT EXISTS idx_repair_knowledge_chunks_hnsw
            ON repair_knowledge_chunks USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 64);
    ELSE
        CREATE TABLE IF NOT EXISTS repair_knowledge_chunks (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            standard_code VARCHAR(64) NOT NULL,
            title VARCHAR(255) NOT NULL,
            category VARCHAR(64) NOT NULL,
            defect_patterns VARCHAR(255) NOT NULL,
            content TEXT NOT NULL,
            reference_url VARCHAR(500),
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_repair_knowledge_chunks_category
            ON repair_knowledge_chunks(category);
    END IF;
END
$$;

-- Seed standard engineering guidelines for infrastructure repair
INSERT INTO repair_knowledge_chunks (standard_code, title, category, defect_patterns, content, reference_url)
VALUES
(
    'TCVN 9343:2012',
    'Bơm keo epoxy áp lực thấp xử lý nứt bê tông kết cấu',
    'CONCRETE_CRACK',
    'crack,nut,nut ket cau,nut doc,nut ngang,ve nut,crack_concrete',
    'Quy trình bơm keo epoxy xử lý nứt bê tông kết cấu (vết nứt >= 0.2mm đến 5.0mm): 1. Khảo sát xác định chiều sâu và hướng phát triển vết nứt. 2. Làm sạch bề mặt dọc vết nứt bằng bàn chải thép và thổi khí nén sạch dầu. 3. Gắn bát bơm (port) cách nhau 150mm - 200mm dọc theo vết nứt. 4. Trám kín khe nứt và cố định bát bơm bằng keo trám epoxy cường độ cao (Sikadur-731) với chiều dày 2-3mm, chờ đóng rắn tối thiểu 12 giờ. 5. Bơm keo epoxy độ nhớt thấp (Sikadur-752) áp lực thấp (0.15 - 0.35 MPa) từ bát thấp nhất lên cao cho đến khi keo trào ra ở bát kế tiếp, sau đó bịt lại và chuyển đầu bơm. 6. Bảo dưỡng tối thiểu 48 giờ trước khi mài phẳng bát bơm. Tiêu chuẩn nghiệm thu: Kiểm tra độ đồng nhất và điền đầy vết nứt bằng phương pháp siêu âm xung (UPV) theo TCVN 9357:2012 đạt phục hồi vận tốc sóng âm >= 90%; Thử kéo đứt (pull-off test) theo TCVN 9349 đạt cường độ bám dính >= 1.5 MPa, điểm đứt gãy nằm hoàn toàn trong nền bê tông cũ.',
    'https://vanbanphapluat.co/tcvn-9343-2012-ket-cau-be-tong-va-be-tong-cot-thep-huong-dan-cong-tac-bao-tri'
),
(
    'TCVN 9343:2012 / EN 1504-9',
    'Sửa chữa bong tróc bê tông và bảo vệ cốt thép bị ăn mòn',
    'SPALLING',
    'spalling,bong troc,vo be tong,lo cot thep,rebar_exposed,ro to ong',
    'Quy trình sửa chữa mảng vỡ, bong tróc bê tông và rỉ cốt thép: 1. Cắt viền vuông góc khu vực sửa chữa sâu tối thiểu 15-20mm bằng máy cắt đĩa để tránh mép vát mỏng. 2. Đục tẩy toàn bộ bê tông phong hóa, nứt vỡ ra xung quanh thanh thép và sâu vào phía sau cốt thép tối thiểu 20mm. 3. Làm sạch gỉ cốt thép đạt cấp độ Sa 2 hoặc St 3 (ISO 8501-1) bằng chổi kim loại hoặc phun cát. 4. Thi công 2 lớp chất quét bảo vệ cốt thép và kết nối gốc xi măng polyme chống ăn mòn giàu kẽm (Sika Monotop-910) độ dày mỗi lớp 1mm. 5. Thi công vữa sửa chữa polyme cải tiến bù co ngót (Sika Monotop R hoặc 412 NFG) từng lớp không quá 30mm, đầm chặt để tránh bọng khí. 6. Bảo dưỡng ẩm liên tục tối thiểu 3 ngày và quét lớp phủ chống cacbonat hóa bảo vệ. Tiêu chuẩn nghiệm thu: Gõ búa kiểm tra độ đặc chắc âm vang (sound test), 100% diện tích không có tiếng bộp rỗng; Cường độ bám dính kéo đứt >= 1.5 MPa; Sai lệch độ phẳng bề mặt không quá 2mm dưới thước 2m.',
    'https://vanbanphapluat.co/tcvn-9343-2012-ket-cau-be-tong-va-be-tong-cot-thep-huong-dan-cong-tac-bao-tri'
),
(
    'ISO 12944-5 / AASHTO',
    'Xử lý ăn mòn kim loại và sơn phủ bảo vệ kết cấu thép',
    'STEEL_CORROSION',
    'corrosion,gi set,an mon,rust,thep gi,ri set ket cau,mat tiet dien',
    'Quy trình phục hồi bề mặt và sơn bảo vệ kết cấu thép cầu/dầm: 1. Siêu âm đo chiều dày kim loại (UTM) đánh giá mức độ giảm tiết diện; nếu tiết diện suy giảm > 15% phải thiết kế tấm ốp gia cường (doubler plate). 2. Tẩy dầu mỡ và tạp chất bằng dung môi sạch. 3. Làm sạch bề mặt bằng phun hạt mài đạt cấp độ Sa 2.5 theo ISO 8501-1, độ nhám biên dạng đạt 45-75 micromet. 4. Thi công hệ sơn 3 lớp bảo vệ chống ăn mòn môi trường C4/C5: Lớp lót sơn lót epoxy giàu kẽm (Zinc-rich epoxy primer) chiều dày màng sơn khô (DFT) 60-80 um; Lớp trung gian sơn epoxy oxit sắt micaceous (MIO) DFT 100-120 um; Lớp phủ ngoài Polyurethane aliphatic kháng tia UV DFT 50-60 um. Tiêu chuẩn nghiệm thu: Tổng chiều dày màng sơn khô đạt tối thiểu 225 um (quy tắc 80/20); Thử độ bám dính bằng phương pháp cắt ô vuông (cross-cut test theo ISO 2409) đạt cấp 0 hoặc cấp 1; Thử dò khuyết tật màng sơn bằng tia lửa điện (holiday detector) đạt 0 điểm rò rỉ.',
    'https://www.iso.org/standard/64833.html'
),
(
    'TCVN 9343:2012 / ASTM C1583',
    'Bơm keo Polyurethane trương nở chặn rò rỉ và thấm nước',
    'WATER_LEAKAGE',
    'water,leak,tham nuoc,ro ri nuoc,chay nuoc,seepage,am uot',
    'Quy trình bơm keo PU trương nở chặn rò rỉ nước qua vết nứt và mạch ngừng bê tông: 1. Làm sạch bùn đất, rong rêu khu vực thấm nước để định vị chính xác vị trí rò rỉ. 2. Khoan các lỗ nghiêng 45 độ so với bề mặt, cắt qua vết nứt tại chiều sâu bằng 1/2 chiều dày kết cấu, khoảng cách các lỗ khoan từ 150-250mm so le nhau. 3. Lắp đặt kim bơm áp lực chuyên dụng (mechanical packer) và siết chặt. 4. Thổi bụi lỗ khoan bằng máy bơm nước áp lực hoặc khí nén. 5. Bơm keo gốc Polyurethane 2 thành phần phản ứng trương nở nhanh khi gặp nước (áp lực bơm 15-30 bar) từ dưới lên trên đến khi keo trương nở bọt tràn ra khỏi vết nứt. 6. Sau khi keo đóng rắn hoàn toàn, tháo kim bơm và trám bịt miệng lỗ khoan bằng vữa đông kết nhanh. Tiêu chuẩn nghiệm thu: Quan sát liên tục trong 72 giờ dưới áp lực nước làm việc bình thường, bề mặt khô ráo hoàn toàn 100%, không còn hiện tượng ẩm ướt hoặc đọng sương rỉ nước.',
    'https://vanbanphapluat.co/tcvn-9343-2012-ket-cau-be-tong-va-be-tong-cot-thep-huong-dan-cong-tac-bao-tri'
),
(
    'TCVN 9361:2012 / AASHTO',
    'Sửa chữa khe co giãn và khắc phục lún nứt chuyển vị',
    'STRUCTURAL_DEFORMATION',
    'joint,khe co gian,lun,chuyen vi,bien dang,settlement,lech nhip',
    'Quy trình sửa chữa khe co giãn kết cấu và khắc phục lún chuyển vị: 1. Đục bỏ lớp chèn khe, trám vá bị lão hóa hoặc hư hỏng. 2. Làm sạch lòng khe co giãn bằng máy thổi hơi áp lực cao và chổi sắt, tẩy sạch rêu mốc và bụi bẩn. 3. Kiểm tra mép khe co giãn, sửa chữa phần mép vát hoặc sứt vỡ bằng vữa epoxy chịu lực không co ngót. 4. Lắp đặt vật liệu đệm đáy khe (backer rod) bằng bọt Polyethylene kín khí có đường kính lớn hơn khe 25% ở độ sâu bằng 1/2 bề rộng khe. 5. Quét lớp lót kết nối (primer) chuyên dụng lên 2 thành khe. 6. Bơm chất trám khe đàn hồi gốc Polyurethane lưu hóa nguội (Sikaflex PRO-3) đều tay, tránh bọt khí và miết phẳng bề mặt hơi lõm 2mm so với mặt bê tông. Tiêu chuẩn nghiệm thu: Chất trám khe dính kết tuyệt đối với thành bê tông, không bong tách hay nứt chân chim; Độ giãn dài đứt gãy >= 400%; Bề mặt nhẵn mịn, đàn hồi tốt khi nén ép kiểm tra.',
    'https://vanbanphapluat.co/tcvn-9361-2012-khao-sat-danh-gia-tinh-trang-nha-va-cong-trinh-xay-dung'
);
