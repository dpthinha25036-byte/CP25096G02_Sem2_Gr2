package ctv.kho;

import ctv.util.Db;
import ctv.model.Models.Equipment;
import ctv.model.Models.StockRow;
import ctv.util.BusinessException;
import ctv.util.Session;
import ctv.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 3.2 – thiết bị và tồn kho (equipment, equipment_stock). */
public class EquipmentDAO {

    private static final String STOCK_SELECT = """
        SELECT e.equipment_id, e.equipment_code, e.equipment_name, t.type_id, t.type_name,
               e.condition_status, e.usage_status, e.responsible_person,
               s.location_id, w.warehouse_name, l.location_code,
               s.worksite_id, ws.worksite_name, COALESCE(s.quantity, 0)
        FROM equipment e
        JOIN equipment_types t ON t.type_id = e.type_id
        LEFT JOIN equipment_stock s ON s.equipment_id = e.equipment_id
        LEFT JOIN storage_locations l ON l.location_id = s.location_id
        LEFT JOIN warehouses w ON w.warehouse_id = l.warehouse_id
        LEFT JOIN worksites ws ON ws.worksite_id = s.worksite_id
        """;

    /**
     * Tìm / lọc thiết bị kèm vị trí (3.2.3 – 3.2.7, 3.2.10).
     * @param keyword   mã hoặc tên (null = bỏ qua)
     * @param typeId    lọc theo loại (null = tất cả)
     * @param condition lọc theo tình trạng GOOD / NEED_REPAIR / BROKEN (null = tất cả)
     * @param placeKind "WAREHOUSE" | "LOCATION" | "WORKSITE" | "ANY_WORKSITE" | null (tất cả)
     * @param placeId   mã kho / vị trí / công trình tương ứng
     */
    public List<StockRow> findStock(String keyword, Integer typeId, String condition, String placeKind, Integer placeId) {
        StringBuilder sql = new StringBuilder(STOCK_SELECT).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.equipment_code LIKE ? OR e.equipment_name LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        if (typeId != null) { sql.append(" AND e.type_id = ?"); params.add(typeId); }
        if (condition != null) { sql.append(" AND e.condition_status = ?"); params.add(condition); }
        if (placeKind != null) {
            switch (placeKind) {
                case "WAREHOUSE" -> { sql.append(" AND l.warehouse_id = ?"); params.add(placeId); }
                case "LOCATION" -> { sql.append(" AND s.location_id = ?"); params.add(placeId); }
                case "WORKSITE" -> { sql.append(" AND s.worksite_id = ? AND s.quantity > 0"); params.add(placeId); }
                case "ANY_WORKSITE" -> sql.append(" AND s.worksite_id IS NOT NULL AND s.quantity > 0");
                default -> { }
            }
        }
        sql.append(" ORDER BY e.equipment_code, s.location_id, s.worksite_id");
        return Db.query(sql.toString(), rs -> new StockRow(
                rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8),
                (Integer) rs.getObject(9), rs.getString(10), rs.getString(11),
                (Integer) rs.getObject(12), rs.getString(13), rs.getInt(14)), params.toArray());
    }

    public Equipment findByCode(String code) {
        List<Equipment> l = Db.query("""
            SELECT equipment_id, equipment_code, equipment_name, type_id, condition_status, usage_status, responsible_person
            FROM equipment WHERE equipment_code = ?""",
                rs -> new Equipment(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)), code);
        return l.isEmpty() ? null : l.get(0);
    }

    public boolean codeExists(String code, int excludeId) {
        return !Db.query("SELECT 1 FROM equipment WHERE equipment_code=? AND equipment_id<>?",
                rs -> 1, code, excludeId).isEmpty();
    }

    /** Thêm thiết bị + dòng tồn kho ban đầu trong MỘT giao dịch (3.2.1). */
    public int insertWithStock(String code, String name, int typeId, String condition, String usage,
                               String responsible, int locationId, int quantity) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO equipment(equipment_code, equipment_name, type_id, condition_status, usage_status, responsible_person, created_by)
                    VALUES (?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code); ps.setString(2, name); ps.setInt(3, typeId);
                    ps.setString(4, condition); ps.setString(5, usage); ps.setString(6, responsible);
                    ps.setInt(7, Session.userId());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); id = rs.getInt(1); }
                }
                if (quantity > 0) StockDAO.add(c, id, locationId, null, quantity);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi thêm thiết bị: " + e.getMessage(), e);
        }
    }

    /** Sửa thông tin thiết bị (3.2.2, 3.2.8, 3.2.9, 3.2.11). */
    public void update(int id, String code, String name, int typeId, String condition, String usage, String responsible) {
        Db.exec("""
            UPDATE equipment SET equipment_code=?, equipment_name=?, type_id=?, condition_status=?, usage_status=?, responsible_person=?
            WHERE equipment_id=?""", code, name, typeId, condition, usage, responsible, id);
    }

    /** Xóa thiết bị: chỉ khi không còn tồn kho và chưa có phiếu liên quan. */
    public void delete(int id) {
        int qty = Db.query("SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE equipment_id=?",
                rs -> rs.getInt(1), id).get(0);
        if (qty > 0) throw new BusinessException("Thiết bị còn tồn kho / đang ở công trình, không thể xóa.");
        try {
            Db.exec("DELETE FROM equipment_stock WHERE equipment_id=?", id);
            Db.exec("DELETE FROM equipment WHERE equipment_id=?", id);
        } catch (BusinessException e) {
            throw new BusinessException("Thiết bị đã phát sinh phiếu nhập/xuất/thu hồi, không thể xóa.", e);
        }
    }
}
