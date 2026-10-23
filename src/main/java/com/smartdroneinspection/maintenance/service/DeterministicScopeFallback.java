package com.smartdroneinspection.maintenance.service;

import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedScope;
import com.smartdroneinspection.maintenance.spi.MaintenanceScopeAdvisorPort.SuggestedTask;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministic rule-based fallback generator for repair scope and acceptance criteria. Ensures
 * zero downtime when external AI inference is disabled or unreachable.
 */
@Component
public class DeterministicScopeFallback {

  public SuggestedScope fallback(
      String defectLabel, String description, String component, String measurement) {
    String textToAnalyze =
        ((defectLabel != null ? defectLabel : "") + " " + (description != null ? description : ""))
            .toLowerCase(Locale.ROOT);

    if (textToAnalyze.contains("crack")
        || textToAnalyze.contains("nứt")
        || textToAnalyze.contains("nut")) {
      return concreteCrackFallback(component, measurement);
    } else if (textToAnalyze.contains("spall")
        || textToAnalyze.contains("bong tróc")
        || textToAnalyze.contains("bong troc")
        || textToAnalyze.contains("vỡ")
        || textToAnalyze.contains("vo")
        || textToAnalyze.contains("rỗ")
        || textToAnalyze.contains("ro to ong")) {
      return spallingFallback(component);
    } else if (textToAnalyze.contains("leak")
        || textToAnalyze.contains("thấm")
        || textToAnalyze.contains("tham")
        || textToAnalyze.contains("rò rỉ")
        || textToAnalyze.contains("ro ri")
        || textToAnalyze.contains("ẩm")
        || textToAnalyze.contains("water")) {
      return waterLeakageFallback(component);
    } else if (textToAnalyze.contains("corrosion")
        || textToAnalyze.contains("gỉ")
        || textToAnalyze.contains("gi set")
        || textToAnalyze.contains("rỉ sét")
        || textToAnalyze.contains("ri set")
        || textToAnalyze.contains("hoen rỉ")
        || textToAnalyze.contains("ăn mòn")
        || textToAnalyze.contains("rust")) {
      return steelCorrosionFallback(component);
    } else {
      return generalFallback(defectLabel, component);
    }
  }

  private SuggestedScope concreteCrackFallback(String component, String measurement) {
    String target = component != null && !component.isBlank() ? component : "kết cấu bê tông";
    String scope =
        "Vệ sinh bề mặt vết nứt tại "
            + target
            + "; gắn bát bơm epoxy chuyên dụng cách nhau 150-200mm; trám kín khe nứt bằng keo trám epoxy hai thành phần; bơm keo epoxy độ nhớt thấp áp lực thấp (0.15-0.35 MPa) từ dưới lên trên cho đến khi điền đầy hoàn toàn; bảo dưỡng tối thiểu 48 giờ trước khi mài phẳng hoàn thiện.";
    String criteria =
        "Kiểm tra siêu âm xung (UPV) theo TCVN 9357:2012 đạt độ phục hồi vận tốc sóng âm >= 90%; Thử kéo đứt (pull-off test) theo TCVN 9349 đạt cường độ bám dính >= 1.5 MPa; Bề mặt phẳng, không xuất hiện vết nứt thứ cấp sau 14 ngày.";
    List<String> standards = List.of("TCVN 9343:2012", "TCVN 9357:2012", "TCVN 9349");
    List<SuggestedTask> tasks =
        List.of(
            new SuggestedTask(
                1,
                "Khảo sát và vệ sinh khe nứt",
                "Làm sạch bằng chổi kim loại và thổi khí nén",
                "Khe nứt sạch bụi và dầu mỡ"),
            new SuggestedTask(
                2,
                "Gắn bát bơm và trám bịt khe nứt",
                "Gắn bát khoảng cách 150-200mm, trám keo epoxy cường độ cao",
                "Keo trám kín kín khít, chịu được áp lực bơm 0.4 MPa"),
            new SuggestedTask(
                3,
                "Bơm keo epoxy áp lực thấp",
                "Bơm keo epoxy độ nhớt thấp từ bát dưới lên bát trên",
                "Keo trào đều qua bát kế tiếp, không bị tắc"),
            new SuggestedTask(
                4,
                "Nghiệm thu và hoàn thiện bề mặt",
                "Mài phẳng bát bơm, siêu âm UPV và thử kéo đứt",
                "Cường độ bám dính >= 1.5 MPa, độ điền đầy >= 90%"));
    return new SuggestedScope(scope, criteria, standards, tasks);
  }

  private SuggestedScope spallingFallback(String component) {
    String target = component != null && !component.isBlank() ? component : "bề mặt kết cấu";
    String scope =
        "Cắt viền vuông góc khu vực sửa chữa tại "
            + target
            + " sâu tối thiểu 15mm; đục tẩy toàn bộ bê tông phong hóa ra sau cốt thép tối thiểu 20mm; làm sạch gỉ cốt thép đạt cấp Sa 2; quét 2 lớp chất bảo vệ cốt thép gốc kẽm; trám trét vữa sửa chữa polyme bù co ngót từng lớp <= 30mm; bảo dưỡng ẩm liên tục 3 ngày.";
    String criteria =
        "Gõ búa kiểm tra độ đặc chắc (sound test) 100% diện tích không có tiếng bộp rỗng; Cường độ bám dính kéo đứt theo TCVN 9349 >= 1.5 MPa; Độ sai lệch phẳng không quá 2mm dưới thước 2m.";
    List<String> standards = List.of("TCVN 9343:2012", "EN 1504-9", "TCVN 9349");
    List<SuggestedTask> tasks =
        List.of(
            new SuggestedTask(
                1,
                "Cắt viền và đục tẩy bê tông hỏng",
                "Cắt đĩa viền sâu 15mm và đục tẩy sau thanh thép 20mm",
                "Bề mặt bê tông lộ đá gốc đặc chắc"),
            new SuggestedTask(
                2,
                "Làm sạch gỉ cốt thép và quét chống gỉ",
                "Đánh gỉ đạt Sa 2, quét 2 lớp chất chống ăn mòn giàu kẽm",
                "Thép sạch gỉ và phủ kín chất bảo vệ"),
            new SuggestedTask(
                3,
                "Trám vữa polyme bù co ngót",
                "Đắp vữa polyme từng lớp <= 30mm, đầm chặt",
                "Bề mặt phẳng đều, không có bọng rỗng khí"),
            new SuggestedTask(
                4,
                "Bảo dưỡng và nghiệm thu độ bám dính",
                "Bảo dưỡng ẩm 72h, gõ búa âm vang và thử kéo đứt",
                "Âm vang đặc chắc 100%, bám dính >= 1.5 MPa"));
    return new SuggestedScope(scope, criteria, standards, tasks);
  }

  private SuggestedScope steelCorrosionFallback(String component) {
    String target = component != null && !component.isBlank() ? component : "kết cấu thép";
    String scope =
        "Tẩy dầu mỡ và tạp chất tại "
            + target
            + "; siêu âm đo chiều dày kim loại xác định mức giảm tiết diện; phun hạt mài làm sạch bề mặt đạt tiêu chuẩn ISO 8501-1 Sa 2.5; thi công hệ sơn 3 lớp chống ăn mòn (sơn lót epoxy giàu kẽm DFT 70 um, sơn trung gian epoxy MIO DFT 110 um, sơn phủ polyurethane DFT 50 um).";
    String criteria =
        "Tổng chiều dày màng sơn khô (DFT) đạt tối thiểu 225 um theo quy tắc 80/20; Thử bám dính cắt ô vuông theo ISO 2409 đạt cấp 0 hoặc cấp 1; Không có điểm khuyết tật rò điện khi dò tia lửa.";
    List<String> standards = List.of("ISO 12944-5", "ISO 8501-1", "ISO 2409");
    List<SuggestedTask> tasks =
        List.of(
            new SuggestedTask(
                1,
                "Khảo sát tiết diện và làm sạch sơ bộ",
                "Siêu âm UTM đo độ mòn và tẩy dầu mỡ bề mặt",
                "Xác định chính xác độ suy giảm tiết diện"),
            new SuggestedTask(
                2,
                "Phun cát làm sạch bề mặt",
                "Phun hạt mài đạt chuẩn Sa 2.5, độ nhám 45-75 um",
                "Bề mặt thép sáng ánh kim loại"),
            new SuggestedTask(
                3,
                "Thi công hệ sơn bảo vệ 3 lớp",
                "Sơn lót epoxy kẽm, trung gian MIO, phủ ngoài Polyurethane",
                "Tổng chiều dày khô DFT >= 225 um"),
            new SuggestedTask(
                4,
                "Kiểm tra độ bám dính và khuyết tật sơn",
                "Đo DFT, cắt ô vuông ISO 2409 và dò tia lửa điện",
                "Đạt độ bám dính cấp 0-1, không có bọt khí/rỗ châm kim"));
    return new SuggestedScope(scope, criteria, standards, tasks);
  }

  private SuggestedScope waterLeakageFallback(String component) {
    String target = component != null && !component.isBlank() ? component : "vị trí rò rỉ";
    String scope =
        "Khoan lỗ nghiêng 45 độ cắt qua mạch nứt rò rỉ tại "
            + target
            + ", khoảng cách lỗ 200mm so le; lắp đặt kim bơm cơ học áp lực cao; bơm keo Polyurethane trương nở kỵ nước (áp lực 15-30 bar) chặn dòng thấm; tháo kim và trám lỗ khoan bằng vữa đông kết nhanh.";
    String criteria =
        "Bề mặt khô ráo hoàn toàn 100% trong 72 giờ quan sát dưới áp lực nước làm việc bình thường; Không có hiện tượng rò rỉ lan sang khu vực lân cận.";
    List<String> standards = List.of("TCVN 9343:2012", "ASTM C1583");
    List<SuggestedTask> tasks =
        List.of(
            new SuggestedTask(
                1,
                "Khoan lỗ và lắp kim bơm áp lực",
                "Khoan nghiêng 45 độ sâu 1/2 bản kết cấu, lắp kim bơm cơ học",
                "Kim bơm gắn chặt, không bị lỏng"),
            new SuggestedTask(
                2,
                "Bơm keo PU trương nở ngăn nước",
                "Bơm keo PU 2 thành phần áp lực cao đến khi bọt trào ra",
                "Keo phản ứng bọt kín toàn bộ mạch nứt"),
            new SuggestedTask(
                3,
                "Tháo kim và trám hoàn thiện lỗ khoan",
                "Tháo kim sau 24h, trám vữa xi măng đóng rắn nhanh",
                "Lỗ khoan phẳng kín, không nứt"),
            new SuggestedTask(
                4,
                "Theo dõi và nghiệm thu chống thấm",
                "Quan sát 72h dưới điều kiện tải trọng nước",
                "Bề mặt hoàn toàn khô ráo, không tái rò rỉ"));
    return new SuggestedScope(scope, criteria, standards, tasks);
  }

  private SuggestedScope generalFallback(String defectLabel, String component) {
    String target = component != null && !component.isBlank() ? component : "cấu kiện";
    String defect = defectLabel != null && !defectLabel.isBlank() ? defectLabel : "khuyết tật";
    String scope =
        "Khoanh vùng khu vực bị "
            + defect
            + " tại "
            + target
            + "; xử lý bề mặt loại bỏ phần vật liệu suy giảm chất lượng; trám vá/gia cố bằng vật liệu kỹ thuật tương thích; kiểm tra độ phẳng và phủ bảo vệ bề mặt sau khi hoàn thiện.";
    String criteria =
        "Kết cấu được gia cố phục hồi nguyên trạng; kiểm tra bằng phương pháp gõ thử và quan sát thị giác đạt độ đồng nhất, không có nứt vỡ hay khuyết tật phát sinh.";
    List<String> standards = List.of("TCVN 9343:2012");
    List<SuggestedTask> tasks =
        List.of(
            new SuggestedTask(
                1,
                "Khoanh vùng và chuẩn bị mặt bằng",
                "Khảo sát ranh giới khuyết tật và làm sạch bề mặt",
                "Bề mặt sạch sẽ, sẵn sàng thi công"),
            new SuggestedTask(
                2,
                "Thi công sửa chữa và gia cố",
                "Trám vá vật liệu chuyên dụng theo khuyến cáo nhà sản xuất",
                "Vật liệu trám đặc chắc, bám dính tốt"),
            new SuggestedTask(
                3,
                "Nghiệm thu chất lượng sửa chữa",
                "Kiểm tra thị giác, độ phẳng và độ cứng bề mặt",
                "Đạt yêu cầu kỹ thuật và thẩm mỹ công trình"));
    return new SuggestedScope(scope, criteria, standards, tasks);
  }
}
