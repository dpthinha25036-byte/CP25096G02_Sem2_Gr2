package ctv.kho;

import java.sql.*;

/**
 * Cập nhật tồn kho (equipment_stock) – LUÔN chạy trong giao dịch do tầng service mở,
 * nên mọi phương thức nhận Connection thay vì tự mở kết nối.
 */
public final class StockDAO {
    private StockDAO() {}

    /** Số lượng hiện có tại vị trí (khóa dòng để tránh hai người xuất cùng lúc). */
    public static int lockedQty(Connection c, int equipmentId, Integer locationId, Integer worksiteId) throws SQLException {
        String col = locationId != null ? "location_id" : "worksite_id";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT quantity FROM equipment_stock WHERE equipment_id=? AND " + col + "=? FOR UPDATE")) {
            ps.setInt(1, equipmentId);
            ps.setInt(2, locationId != null ? locationId : worksiteId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    /** Cộng (delta > 0) hoặc trừ (delta < 0) tồn tại vị trí; tạo dòng mới nếu chưa có. */
    public static void add(Connection c, int equipmentId, Integer locationId, Integer worksiteId, int delta) throws SQLException {
        String col = locationId != null ? "location_id" : "worksite_id";
        int placeId = locationId != null ? locationId : worksiteId;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE equipment_stock SET quantity = quantity + ? WHERE equipment_id=? AND " + col + "=? AND quantity + ? >= 0")) {
            ps.setInt(1, delta); ps.setInt(2, equipmentId); ps.setInt(3, placeId); ps.setInt(4, delta);
            if (ps.executeUpdate() > 0) return;
        }
        if (delta < 0) throw new SQLException("Tồn kho không đủ để trừ " + (-delta));
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO equipment_stock(equipment_id, location_id, worksite_id, quantity) VALUES (?,?,?,?)")) {
            ps.setInt(1, equipmentId);
            ps.setObject(2, locationId);
            ps.setObject(3, worksiteId);
            ps.setInt(4, delta);
            ps.executeUpdate();
        }
    }

    /** Đặt lại trạng thái sử dụng: có hàng ở công trình → IN_USE, ngược lại IN_STOCK (giữ nguyên MAINTENANCE). */
    public static void refreshUsage(Connection c, int equipmentId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
            UPDATE equipment e SET usage_status = CASE
                WHEN (SELECT COALESCE(SUM(quantity),0) FROM equipment_stock s
                      WHERE s.equipment_id = e.equipment_id AND s.worksite_id IS NOT NULL) > 0 THEN 'IN_USE'
                ELSE 'IN_STOCK' END
            WHERE e.equipment_id = ? AND e.usage_status <> 'MAINTENANCE'""")) {
            ps.setInt(1, equipmentId);
            ps.executeUpdate();
        }
    }
}
