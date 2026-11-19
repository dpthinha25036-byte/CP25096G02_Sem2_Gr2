package ctv.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Danh sách chức năng (function_code theo Form 02A) và quyền mặc định của từng vai trò (bảng roles). */
public final class Permissions {
    private Permissions() {}

    public static final int ROLE_ADMIN = 1, ROLE_HR = 2, ROLE_SITE = 3, ROLE_ACCOUNTANT = 4, ROLE_WAREHOUSE = 5;

    public record Function(String code, String name, String group) {}

    public static final List<Function> ALL = List.of(
            new Function("1.1", "Quản lý tài khoản và phân quyền", "Hệ thống"),
            new Function("1.2", "Quản lý hồ sơ công nhân", "Nhân sự"),
            new Function("1.3", "Danh mục kỹ năng và đơn giá", "Nhân sự"),
            new Function("1.4", "Báo cáo tổng hợp nhân lực", "Nhân sự"),
            new Function("2.1", "Quản lý thông tin công trình", "Công trình"),
            new Function("2.2", "Lập nhu cầu nhân lực", "Công trình"),
            new Function("2.3", "Phân công công nhân", "Công trình"),
            new Function("2.4", "Theo dõi và cảnh báo nhân lực", "Công trình"),
            new Function("2.5", "Chấm công hằng ngày", "Chấm công & lương"),
            new Function("2.6", "Kiểm tra và báo cáo chấm công", "Chấm công & lương"),
            new Function("2.7", "Tổng hợp tiền công theo kỳ", "Chấm công & lương"),
            new Function("2.8", "Chốt bảng lương, khóa sổ", "Chấm công & lương"),
            new Function("2.9", "Ghi nhận thanh toán lương", "Chấm công & lương"),
            new Function("2.10", "Duyệt yêu cầu mở khóa bảng lương", "Chấm công & lương"),
            new Function("3.1", "Danh mục loại thiết bị", "Kho thiết bị"),
            new Function("3.2", "Quản lý thiết bị, vị trí và tồn kho", "Kho thiết bị"),
            new Function("3.3", "Nhập kho, xuất và thu hồi thiết bị", "Kho thiết bị"),
            new Function("3.4", "Báo cáo tồn kho, lịch sử giao dịch", "Kho thiết bị"));

    /**
     * Phạm vi chức năng của từng vai trò: quyền mặc định khi tạo tài khoản hoặc bấm 'Đặt theo vai trò mặc định'.
     * Admin: tài khoản, phân quyền, nhật ký, sao lưu và phục hồi (1.1). HR: hồ sơ công nhân, kỹ năng, đơn giá, báo cáo nhân lực (1.2, 1.3, 1.4).
     * Công trình: công trình, nhu cầu, phân công, cảnh báo thiếu/đủ nhân lực (2.1–2.4).
     * Kế toán: chấm công, kiểm tra/báo cáo chấm công, tổng hợp tiền công, chốt lương, khóa sổ, thanh toán, duyệt mở khóa (2.5–2.10).
     * Thủ kho: loại thiết bị, thiết bị, vị trí, nhập/xuất/thu hồi, báo cáo kho (3.1–3.4).
     */
    private static final java.util.Map<Integer, List<String>> SCOPE = java.util.Map.of(
            ROLE_ADMIN, List.of("1.1"),
            ROLE_HR, List.of("1.2", "1.3", "1.4"),
            ROLE_SITE, List.of("2.1", "2.2", "2.3", "2.4"),
            ROLE_ACCOUNTANT, List.of("2.5", "2.6", "2.7", "2.8", "2.9", "2.10"),
            ROLE_WAREHOUSE, List.of("3.1", "3.2", "3.3", "3.4"));

    /**
     * Quyền mặc định của vai trò. Riêng 2.10 (duyệt mở khóa bảng lương) không cấp sẵn: Admin bật cho từng
     * tài khoản Kế toán, và người duyệt luôn phải khác người yêu cầu.
     */
    public static Set<String> defaultsFor(int roleId) {
        Set<String> s = new LinkedHashSet<>(SCOPE.getOrDefault(roleId, List.of()));
        s.remove("2.10");
        return s;
    }

    /**
     * Chức năng bị chặn, kể cả khi DB còn quyền cũ. Chỉ Admin bị cố định (chỉ có 1.1);
     * với các vai trò khác, SCOPE chỉ là quyền mặc định, Admin vẫn cấp thêm hoặc thu hồi từng chức năng trong "Phân quyền".
     */
    public static boolean isBlocked(int roleId, String code) {
        return roleId == ROLE_ADMIN && !"1.1".equals(code);
    }

    /** Loại các chức năng bị chặn khỏi tập quyền của vai trò. */
    public static Set<String> sanitize(int roleId, Set<String> codes) {
        Set<String> s = new LinkedHashSet<>(codes);
        s.removeIf(c -> isBlocked(roleId, c));
        return s;
    }
}
