package ctv.kho;

import ctv.model.Models.*;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import ctv.util.Ownership;
import ctv.util.Session;
import static ctv.util.Db.exec;
import static ctv.util.Db.query;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Danh mục dùng cho ComboBox: kho, vị trí kho, công trình, loại thiết bị, thiết bị (+ CRUD vị trí kho). */
public class LookupDAO {

    public List<Warehouse> warehouses() {
        return query("SELECT warehouse_id, warehouse_code, warehouse_name FROM warehouses ORDER BY warehouse_code",
                rs -> new Warehouse(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    /** warehouseId = null → tất cả vị trí. */
    public List<Location> locations(Integer warehouseId) {
        String sql = """
            SELECT l.location_id, l.warehouse_id, w.warehouse_name, l.location_code, l.description
            FROM storage_locations l JOIN warehouses w ON w.warehouse_id = l.warehouse_id
            WHERE (? IS NULL OR l.warehouse_id = ?) ORDER BY w.warehouse_code, l.location_code""";
        return query(sql, rs -> new Location(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                warehouseId, warehouseId);
    }

    /** Vị trí kho kèm thiết bị đang lưu và số lượng (mỗi thiết bị một dòng; vị trí trống có một dòng equipmentName = null). */
    public List<LocationRow> locationStock(Integer warehouseId, String keyword) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String like = kw == null ? null : "%" + kw + "%";
        String sql = """
            SELECT l.location_id, l.warehouse_id, w.warehouse_name, l.location_code, l.description,
                   e.equipment_name, COALESCE(s.quantity, 0)
            FROM storage_locations l JOIN warehouses w ON w.warehouse_id = l.warehouse_id
            LEFT JOIN equipment_stock s ON s.location_id = l.location_id AND s.quantity > 0
            LEFT JOIN equipment e ON e.equipment_id = s.equipment_id
            WHERE (? IS NULL OR l.warehouse_id = ?)
              AND (? IS NULL OR l.location_code LIKE ? OR e.equipment_name LIKE ?)
            ORDER BY w.warehouse_code, l.location_code, e.equipment_code""";
        return query(sql, rs -> new LocationRow(
                new Location(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                rs.getString(6), rs.getInt(7)),
                warehouseId, warehouseId, kw, like, like);
    }

    public List<Worksite> worksites() {
        return query("SELECT worksite_id, worksite_code, worksite_name FROM worksites ORDER BY worksite_code",
                rs -> new Worksite(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    public List<EquipmentType> types() {
        return new EquipmentTypeDAO().search("");
    }

    public List<Equipment> equipmentList() {
        return query("""
            SELECT equipment_id, equipment_code, equipment_name, type_id, condition_status, usage_status, responsible_person
            FROM equipment ORDER BY equipment_code""",
                rs -> new Equipment(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)));
    }

    /**
     * Công nhân đã nhận thiết bị khi xuất cho một công trình và hiện còn đang giữ (đã nhận trừ đã trả > 0), chỉ lấy HỌ TÊN
     * (bỏ phần " – Chức vụ" ghi kèm trong phiếu). Dùng cho ô "Người trả" ở phiếu thu hồi.
     */
    public List<String> borrowers(int worksiteId) {
        return query("""
            SELECT nm FROM (
              SELECT nm, equipment_id, SUM(qty) held FROM (
                SELECT TRIM(SUBSTRING_INDEX(s.receiver_name, ' – ', 1)) nm, d.equipment_id, d.quantity qty
                  FROM issue_slips s JOIN issue_slip_details d ON d.issue_id = s.issue_id WHERE s.worksite_id = ?
                UNION ALL
                SELECT TRIM(SUBSTRING_INDEX(r.returner_name, ' – ', 1)), d.equipment_id, -d.quantity
                  FROM return_slips r JOIN return_slip_details d ON d.return_id = r.return_id WHERE r.worksite_id = ?
              ) x GROUP BY nm, equipment_id HAVING SUM(qty) > 0
            ) y WHERE nm <> '' GROUP BY nm ORDER BY nm""",
                rs -> rs.getString(1), worksiteId, worksiteId);
    }

    /** Thiết bị (mã thiết bị → số lượng) mà một người đang giữ tại công trình: đã nhận qua phiếu xuất trừ đã trả qua phiếu thu hồi. */
    public java.util.Map<Integer, Integer> heldBy(int worksiteId, String personName) {
        java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
        for (int[] r : query("""
            SELECT equipment_id, SUM(qty) FROM (
              SELECT d.equipment_id, d.quantity qty
                FROM issue_slips s JOIN issue_slip_details d ON d.issue_id = s.issue_id
               WHERE s.worksite_id = ? AND TRIM(SUBSTRING_INDEX(s.receiver_name, ' – ', 1)) = ?
              UNION ALL
              SELECT d.equipment_id, -d.quantity
                FROM return_slips r JOIN return_slip_details d ON d.return_id = r.return_id
               WHERE r.worksite_id = ? AND TRIM(SUBSTRING_INDEX(r.returner_name, ' – ', 1)) = ?
            ) x GROUP BY equipment_id HAVING SUM(qty) > 0""",
                rs -> new int[]{rs.getInt(1), rs.getInt(2)}, worksiteId, personName, worksiteId, personName)) m.put(r[0], r[1]);
        return m;
    }

    /** "Họ tên (Chức vụ)" của người đã thêm công trình, dùng cho ô chức vụ người nhận/trả ở phiếu xuất, thu hồi. null nếu chưa ghi nhận. */
    public String worksiteManager(int worksiteId) {
        List<String> r = query("""
            SELECT CONCAT(u.full_name, ' (', ro.role_name, ')') FROM worksites w
            JOIN users u ON u.user_id = w.created_by JOIN roles ro ON ro.role_id = u.role_id
            WHERE w.worksite_id = ?""", rs -> rs.getString(1), worksiteId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Công nhân đang được phân công (ACTIVE) tại công trình, dùng cho ô "Người nhận" ở phiếu xuất. */
    public List<String> workersAtWorksite(int worksiteId) {
        return query("""
            SELECT DISTINCT w.full_name FROM assignments a JOIN workers w ON w.worker_id = a.worker_id
            WHERE a.worksite_id = ? AND a.status = 'ACTIVE' ORDER BY w.full_name""",
                rs -> rs.getString(1), worksiteId);
    }

    /** Thiết bị đang có ở một công trình (dùng cho phiếu thu hồi). */
    public List<StockRow> stockAtWorksite(int worksiteId) {
        return new EquipmentDAO().findStock(null, null, null, "WORKSITE", worksiteId);
    }

    /** Số lượng tồn ở một vị trí kho. */
    public int quantityAt(int equipmentId, int locationId) {
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE equipment_id=? AND location_id=?")) {
            ps.setInt(1, equipmentId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi đọc tồn kho: " + e.getMessage(), e);
        }
    }

    /** Tổng số lượng thiết bị đang lưu tại một vị trí kho. */
    public int quantityInLocation(int locationId) {
        return query("SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE location_id=?",
                rs -> rs.getInt(1), locationId).get(0);
    }

    // ----- CRUD vị trí kho (frmViTriKho) -----
    public void insertLocation(int warehouseId, String code, String description) {
        exec("INSERT INTO storage_locations(warehouse_id, location_code, description, created_by) VALUES (?,?,?,?)",
                warehouseId, code, description, Session.userId());
    }

    public void updateLocation(int id, String code, String description) {
        Ownership.require(Ownership.Kind.LOCATION, id, "sửa");
        exec("UPDATE storage_locations SET location_code=?, description=? WHERE location_id=?", code, description, id);
    }

    public void deleteLocation(int id) {
        Ownership.require(Ownership.Kind.LOCATION, id, "xóa");
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE location_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) throw new BusinessException("Không xóa được vị trí còn thiết bị.");
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi kiểm tra vị trí: " + e.getMessage(), e);
        }
        exec("DELETE FROM equipment_stock WHERE location_id=?", id); // dòng tồn = 0
        exec("DELETE FROM storage_locations WHERE location_id=?", id);
    }
}
