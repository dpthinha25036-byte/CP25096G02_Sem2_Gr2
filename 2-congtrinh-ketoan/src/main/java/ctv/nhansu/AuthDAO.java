package ctv.nhansu;

import ctv.util.Db;
import ctv.nhansu.AuthModels.Role;
import ctv.nhansu.AuthModels.UserAccount;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Truy cập dữ liệu tài khoản, phân quyền, nhật ký đăng nhập và nhật ký thay đổi dữ liệu (chức năng 1.1). */
public class AuthDAO {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String USER_SELECT = """
        SELECT u.user_id, u.username, u.full_name, u.role_id, r.role_name, u.status, u.created_at, u.password_hash
        FROM users u JOIN roles r ON r.role_id = u.role_id""" + "\n";

    private static UserAccount map(ResultSet rs) throws SQLException {
        Timestamp t = rs.getTimestamp(7);
        return new UserAccount(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                rs.getString(6), t == null ? null : t.toLocalDateTime(), rs.getString(8));
    }

    private static String fmt(Timestamp t) { return t == null ? "" : t.toLocalDateTime().format(TS); }

    public List<Role> roles() {
        return Db.query("SELECT role_id, role_name FROM roles ORDER BY role_id",
                rs -> new Role(rs.getInt(1), rs.getString(2)));
    }

    public UserAccount findByUsername(String username) {
        List<UserAccount> l = Db.query(USER_SELECT + "WHERE u.username = ?", AuthDAO::map, username);
        return l.isEmpty() ? null : l.get(0);
    }

    public UserAccount findById(int id) {
        List<UserAccount> l = Db.query(USER_SELECT + "WHERE u.user_id = ?", AuthDAO::map, id);
        return l.isEmpty() ? null : l.get(0);
    }

    public List<UserAccount> search(String keyword, Integer roleId, String status) {
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        return Db.query(USER_SELECT + """
            WHERE (? IS NULL OR u.username LIKE ? OR u.full_name LIKE ?)
              AND (? IS NULL OR u.role_id = ?) AND (? IS NULL OR u.status = ?)
            ORDER BY u.user_id""", AuthDAO::map, kw, kw, kw, roleId, roleId, status, status);
    }

    public int insertUser(String username, String passwordHash, String fullName, int roleId) {
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username, password_hash, full_name, role_id) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setInt(4, roleId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new BusinessException(e.getErrorCode() == 1062 ? "Tên đăng nhập đã tồn tại."
                    : "Lỗi ghi dữ liệu: " + e.getMessage(), e);
        }
    }

    /** passwordHash = null → giữ nguyên mật khẩu cũ. */
    public void updateUser(int id, String fullName, int roleId, String passwordHash) {
        if (passwordHash == null)
            Db.exec("UPDATE users SET full_name=?, role_id=? WHERE user_id=?", fullName, roleId, id);
        else
            Db.exec("UPDATE users SET full_name=?, role_id=?, password_hash=? WHERE user_id=?",
                    fullName, roleId, passwordHash, id);
    }

    public void setStatus(int id, String status) {
        Db.exec("UPDATE users SET status=? WHERE user_id=?", status, id);
    }

    public int countActiveAdmins(int adminRoleId) {
        return Db.query("SELECT COUNT(*) FROM users WHERE role_id=? AND status='ACTIVE'",
                rs -> rs.getInt(1), adminRoleId).get(0);
    }

    // ---------- phân quyền ----------
    /** Các function_code được phép của tài khoản; null nếu tài khoản chưa có dòng phân quyền nào (dùng quyền mặc định của vai trò). */
    public Set<String> allowedCodes(int userId) {
        List<String[]> rows = Db.query("SELECT function_code, is_allowed FROM user_permissions WHERE user_id=?",
                rs -> new String[]{rs.getString(1), rs.getString(2)}, userId);
        if (rows.isEmpty()) return null;
        Set<String> s = new HashSet<>();
        for (String[] r : rows) if ("1".equals(r[1])) s.add(r[0]);
        return s;
    }

    /** Ghi lại toàn bộ phân quyền của tài khoản (xóa cũ, thêm mới) trong một giao dịch. */
    public void savePermissions(int userId, Map<String, Boolean> perms) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM user_permissions WHERE user_id=?");
                 PreparedStatement ins = c.prepareStatement(
                         "INSERT INTO user_permissions(user_id, function_code, is_allowed) VALUES (?,?,?)")) {
                del.setInt(1, userId);
                del.executeUpdate();
                for (Map.Entry<String, Boolean> e : perms.entrySet()) {
                    ins.setInt(1, userId);
                    ins.setString(2, e.getKey());
                    ins.setInt(3, e.getValue() ? 1 : 0);
                    ins.addBatch();
                }
                ins.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi lưu phân quyền: " + e.getMessage(), e);
        }
    }

    // ---------- nhật ký ----------
    public void logLogin(Integer userId, String username, String actionType, String result) {
        Db.exec("INSERT INTO login_logs(user_id, username, action_time, action_type, result) VALUES (?,?,NOW(),?,?)",
                userId, username, actionType, result);
    }

    public void audit(int userId, String table, int recordId, String actionType, String oldValue, String newValue) {
        Db.exec("""
            INSERT INTO audit_logs(user_id, action_time, table_name, record_id, action_type, old_value, new_value)
            VALUES (?,NOW(),?,?,?,?,?)""", userId, table, recordId, actionType, oldValue, newValue);
    }

    public List<Object[]> loginLogs(int limit) {
        return Db.query("SELECT action_time, username, action_type, result FROM login_logs ORDER BY log_id DESC LIMIT ?",
                rs -> new Object[]{fmt(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), rs.getString(4)}, limit);
    }

    public List<Object[]> auditLogs(int limit) {
        return Db.query("""
            SELECT a.action_time, COALESCE(u.username, ''), a.table_name, a.record_id, a.action_type, a.old_value, a.new_value
            FROM audit_logs a LEFT JOIN users u ON u.user_id = a.user_id ORDER BY a.audit_id DESC LIMIT ?""",
                rs -> new Object[]{fmt(rs.getTimestamp(1)), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6) == null ? "" : rs.getString(6),
                        rs.getString(7) == null ? "" : rs.getString(7)}, limit);
    }
}
