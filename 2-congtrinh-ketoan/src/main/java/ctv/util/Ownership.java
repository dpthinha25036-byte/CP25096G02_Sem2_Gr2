package ctv.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quyền sở hữu dữ liệu: dữ liệu do tài khoản nào thêm (cột created_by) thì chỉ tài khoản đó được sửa / xóa;
 * các tài khoản khác chỉ được xem. Áp dụng cho mọi vai trò, kể cả Admin.
 * Dữ liệu cũ chưa có người thêm (created_by = NULL) chỉ để xem; DBConnection tự gán chủ cho dữ liệu cũ khi nâng cấp CSDL.
 */
public final class Ownership {
    private Ownership() {}

    /** Loại dữ liệu có chủ sở hữu: tên bảng, cột khóa chính và tên gọi dùng trong thông báo lỗi. */
    public enum Kind {
        WORKER("workers", "worker_id", "công nhân", Permissions.ROLE_HR),
        SKILL("skills", "skill_id", "kỹ năng", Permissions.ROLE_HR),
        LEVEL("skill_levels", "level_id", "bậc thợ", Permissions.ROLE_HR),
        WORKER_SKILL("worker_skills", "worker_skill_id", "kỹ năng của công nhân", Permissions.ROLE_HR),
        RATE("shift_rates", "rate_id", "đơn giá", Permissions.ROLE_HR),
        SITE("worksites", "worksite_id", "công trình", Permissions.ROLE_SITE),
        DEMAND("manpower_demands", "demand_id", "nhu cầu nhân lực", Permissions.ROLE_SITE),
        ASSIGNMENT("assignments", "assignment_id", "phân công", Permissions.ROLE_SITE),
        ATTENDANCE("attendances", "attendance_id", "bản ghi chấm công", Permissions.ROLE_ACCOUNTANT),
        EQUIPMENT_TYPE("equipment_types", "type_id", "loại thiết bị", Permissions.ROLE_WAREHOUSE),
        EQUIPMENT("equipment", "equipment_id", "thiết bị", Permissions.ROLE_WAREHOUSE),
        LOCATION("storage_locations", "location_id", "vị trí kho", Permissions.ROLE_WAREHOUSE);

        public final String table, idColumn, label;
        /** Vai trò được gán làm chủ của dữ liệu cũ chưa có người thêm (tài khoản đầu tiên của vai trò đó). */
        public final int defaultRole;

        Kind(String table, String idColumn, String label, int defaultRole) {
            this.table = table; this.idColumn = idColumn; this.label = label; this.defaultRole = defaultRole;
        }
    }

    /** Mọi bảng có cột created_by do chức năng này quản lý (dùng khi nâng cấp CSDL cũ). */
    public static List<Kind> kinds() { return List.of(Kind.values()); }

    /** Người đang đăng nhập có phải chủ của dòng có người thêm là owner hay không. */
    public static boolean isMine(Integer owner) {
        return owner != null && Session.isLoggedIn() && owner == Session.userId();
    }

    /** Mã người thêm của một dòng; null nếu dòng không tồn tại hoặc chưa có người thêm. */
    public static Integer ownerOf(Kind k, int id) {
        List<Integer> r = Db.query("SELECT created_by FROM " + k.table + " WHERE " + k.idColumn + "=?",
                rs -> { int v = rs.getInt(1); return rs.wasNull() ? null : v; }, id);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Như ownerOf nhưng dùng kết nối (giao dịch) có sẵn của người gọi. */
    public static Integer ownerIn(java.sql.Connection c, Kind k, int id) throws java.sql.SQLException {
        try (java.sql.PreparedStatement ps = c.prepareStatement(
                "SELECT created_by FROM " + k.table + " WHERE " + k.idColumn + "=?")) {
            ps.setInt(1, id);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    /** Người đang đăng nhập có được sửa / xóa dòng này không. */
    public static boolean canModify(Kind k, int id) { return isMine(ownerOf(k, id)); }

    /** Ném BusinessException nếu dòng không do người đang đăng nhập thêm. action: "sửa" hoặc "xóa". */
    public static void require(Kind k, int id, String action) {
        if (!canModify(k, id))
            throw new BusinessException("Bạn chỉ được " + action + " " + k.label + " do chính mình thêm.");
    }

    /** Như mine(k, ids) nhưng lấy mã từ danh sách dòng đang hiển thị: mine(Kind.WORKER, current, Worker::id). */
    public static <T> Set<Integer> mine(Kind k, List<T> rows, java.util.function.ToIntFunction<T> idOf) {
        List<Integer> ids = new java.util.ArrayList<>();
        for (T r : rows) ids.add(idOf.applyAsInt(r));
        return mine(k, ids);
    }

    /** Trong các dòng ids, tập các dòng do người đang đăng nhập thêm (để bảng chỉ hiện nút Sửa / Xóa cho dòng của mình). */
    public static Set<Integer> mine(Kind k, Collection<Integer> ids) {
        Set<Integer> out = new HashSet<>();
        if (ids.isEmpty() || !Session.isLoggedIn()) return out;
        Map<Integer, Integer> owners = new HashMap<>();
        String in = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Db.query("SELECT " + k.idColumn + ", created_by FROM " + k.table + " WHERE " + k.idColumn + " IN (" + in + ")",
                rs -> { int id = rs.getInt(1); int o = rs.getInt(2); owners.put(id, rs.wasNull() ? null : o); return null; },
                ids.toArray());
        for (Map.Entry<Integer, Integer> e : owners.entrySet()) if (isMine(e.getValue())) out.add(e.getKey());
        return out;
    }
}
