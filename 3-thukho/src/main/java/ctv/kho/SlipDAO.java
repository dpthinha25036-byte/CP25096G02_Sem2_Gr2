package ctv.kho;

import ctv.util.BusinessException;
import java.sql.*;
import java.time.LocalDate;

/** Ghi phiếu nhập / xuất / thu hồi. Mọi phương thức chạy trên Connection của giao dịch do service mở. */
public final class SlipDAO {
    private SlipDAO() {}

    /** Sinh số phiếu tự động, ví dụ PN00001 (nhập), PX00001 (xuất), PT00001 (thu hồi). */
    public static String nextCode(Connection c, String prefix, String table, String idCol) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(" + idCol + "),0)+1 FROM " + table + " FOR UPDATE")) {   // khóa để hai người lưu cùng lúc không trùng số phiếu
            rs.next();
            return String.format("%s%05d", prefix, rs.getInt(1));
        }
    }

    public static int insertReceipt(Connection c, String code, LocalDate date, String source, int userId) throws SQLException {
        return insertHeader(c, "INSERT INTO goods_receipts(receipt_code, receipt_date, source, created_by) VALUES (?,?,?,?)",
                code, Date.valueOf(date), source, userId);
    }

    /** Chỉ lấy họ tên từ chuỗi "Họ tên – Chức vụ" ghi trên phiếu. */
    public static String plainName(String stored) {
        if (stored == null) return "";
        int i = stored.indexOf(" – ");
        return (i < 0 ? stored : stored.substring(0, i)).trim();
    }

    /** Số lượng một thiết bị mà người này đang giữ tại công trình = đã nhận (phiếu xuất) − đã trả (phiếu thu hồi). */
    public static int heldQty(Connection c, int worksiteId, String person, int equipmentId) throws SQLException {
        return sumQty(c, "SELECT COALESCE(SUM(d.quantity),0) FROM issue_slips s JOIN issue_slip_details d ON d.issue_id=s.issue_id "
                + "WHERE s.worksite_id=? AND d.equipment_id=? AND TRIM(SUBSTRING_INDEX(s.receiver_name,' – ',1))=?", worksiteId, equipmentId, person)
             - sumQty(c, "SELECT COALESCE(SUM(d.quantity),0) FROM return_slips r JOIN return_slip_details d ON d.return_id=r.return_id "
                + "WHERE r.worksite_id=? AND d.equipment_id=? AND TRIM(SUBSTRING_INDEX(r.returner_name,' – ',1))=?", worksiteId, equipmentId, person);
    }

    /** Người này đã từng nhận thiết bị cho công trình qua phiếu xuất chưa. */
    public static boolean hasReceived(Connection c, int worksiteId, String person) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM issue_slips WHERE worksite_id=? AND TRIM(SUBSTRING_INDEX(receiver_name,' – ',1))=?")) {
            ps.setInt(1, worksiteId);
            ps.setString(2, person);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        }
    }

    private static int sumQty(Connection c, String sql, int worksiteId, int equipmentId, String person) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, worksiteId);
            ps.setInt(2, equipmentId);
            ps.setString(3, person);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    /** Form 06 chỉ có receiver_name / returner_name (VARCHAR 100): chức vụ được ghi kèm dạng "Họ tên – Chức vụ". */
    private static String withPosition(String name, String position) {
        String n = name == null ? "" : name.trim(), p = position == null ? "" : position.trim();
        String v = p.isEmpty() ? n : n + " – " + p;
        if (v.length() > 100) throw new ctv.util.BusinessException("Họ tên kèm chức vụ tối đa 100 ký tự (hiện " + v.length() + ").");
        return v;
    }

    public static int insertIssue(Connection c, String code, LocalDate date, int worksiteId, String receiver, String receiverPosition, int userId) throws SQLException {
        return insertHeader(c, "INSERT INTO issue_slips(issue_code, issue_date, worksite_id, receiver_name, created_by) VALUES (?,?,?,?,?)",
                code, Date.valueOf(date), worksiteId, withPosition(receiver, receiverPosition), userId);
    }

    public static int insertReturn(Connection c, String code, LocalDate date, int worksiteId, String returner, String returnerPosition, int userId) throws SQLException {
        return insertHeader(c, "INSERT INTO return_slips(return_code, return_date, worksite_id, returner_name, created_by) VALUES (?,?,?,?,?)",
                code, Date.valueOf(date), worksiteId, withPosition(returner, returnerPosition), userId);
    }

    public static void insertReceiptDetail(Connection c, int receiptId, int equipmentId, int locationId, int qty) throws SQLException {
        detail(c, "INSERT INTO goods_receipt_details(receipt_id, equipment_id, location_id, quantity) VALUES (?,?,?,?)",
                receiptId, equipmentId, locationId, qty);
    }

    public static void insertIssueDetail(Connection c, int issueId, int equipmentId, int locationId, int qty) throws SQLException {
        detail(c, "INSERT INTO issue_slip_details(issue_id, equipment_id, location_id, quantity) VALUES (?,?,?,?)",
                issueId, equipmentId, locationId, qty);
    }

    public static void insertReturnDetail(Connection c, int returnId, int equipmentId, int locationId, int qty, String conditionAfter) throws SQLException {
        detail(c, "INSERT INTO return_slip_details(return_id, equipment_id, location_id, quantity, condition_after) VALUES (?,?,?,?,?)",
                returnId, equipmentId, locationId, qty, conditionAfter);
    }

    public static void setCondition(Connection c, int equipmentId, String condition) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE equipment SET condition_status=? WHERE equipment_id=?")) {
            ps.setString(1, condition);
            ps.setInt(2, equipmentId);
            ps.executeUpdate();
        }
    }

    private static int insertHeader(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static void detail(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }
}
