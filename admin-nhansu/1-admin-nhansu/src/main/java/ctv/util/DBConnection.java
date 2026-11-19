
package ctv.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Tạo kết nối MySQL cho toàn hệ thống (CSDL ctv_labor). Đổi cấu hình bằng biến môi trường DB_URL, DB_USER, DB_PASS. */
public final class DBConnection {
    private static final String URL = env("DB_URL",
            "jdbc:mysql://localhost:3306/ctv_labor?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
    private static final String USER = env("DB_USER", "root");
    private static final String PASS = env("DB_PASS", "root");

    private DBConnection() {}

    private static boolean schemaChecked;

    public static Connection get() {
        try {
            Connection c = DriverManager.getConnection(URL, USER, PASS);
            ensureSchema(c);
            return c;
        } catch (SQLException e) {
            throw new BusinessException("Không kết nối được cơ sở dữ liệu: " + e.getMessage(), e);
        }
    }

    /**
     * Tự nâng cấp CSDL cũ (một lần mỗi lần chạy): thêm cột created_by (người thêm dữ liệu) cho các bảng dữ liệu nếu chưa có và gán chủ cho dữ liệu cũ. Các cột ngân hàng của workers có sẵn trong construction_labor.sql (CSDL cũ: chạy migration_worker_bank.sql).
     * Lỗi (ví dụ tài khoản không có quyền ALTER) bị bỏ qua; khi đó hãy chạy lại database/construction_labor.sql để tạo CSDL mới.
     */
    private static synchronized void ensureSchema(Connection c) {
        if (schemaChecked) return;
        schemaChecked = true;
        try (java.sql.Statement st = c.createStatement()) {
            // Mỗi dòng dữ liệu có chủ (created_by): chỉ người thêm được sửa / xóa, người khác chỉ xem (ctv.util.Ownership).
            for (Ownership.Kind k : Ownership.kinds()) {
                if (!hasColumn(st, k.table, "created_by")) {
                    st.executeUpdate("ALTER TABLE " + k.table + " ADD COLUMN created_by INT NULL");
                    try {
                        st.executeUpdate("ALTER TABLE " + k.table + " ADD CONSTRAINT fk_" + k.table + "_owner FOREIGN KEY (created_by) REFERENCES users(user_id)");
                    } catch (SQLException ignored) { /* khóa ngoại là phần bổ trợ, thiếu cũng không ảnh hưởng chức năng */ }
                    // Chỉ khi VỪA thêm cột (nâng cấp CSDL cũ): dữ liệu cũ chưa có người thêm được giao cho tài khoản đầu tiên của vai trò phụ trách.
                    // Các lần kết nối sau không gán lại, nên bản ghi không có chủ (created_by NULL) chỉ để xem theo Form 02A.
                    st.executeUpdate("UPDATE " + k.table + " SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = "
                            + k.defaultRole + ") WHERE created_by IS NULL");
                }
            }
            if (!hasColumn(st, "wage_payments", "work_date")) {   // trả lương hằng ngày cho công nhân thời vụ
                st.executeUpdate("ALTER TABLE wage_payments ADD COLUMN work_date DATE NULL");
            }
        } catch (SQLException e) {
            schemaChecked = false;   // thử lại lần kết nối sau
        }
    }

    private static boolean hasColumn(java.sql.Statement st, String table, String column) throws SQLException {
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
                        + table + "' AND COLUMN_NAME = '" + column + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
