package ctv.kho;

import ctv.model.Models.EquipmentType;
import ctv.util.BusinessException;
import ctv.util.Session;
import ctv.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 3.1 – Truy vấn bảng equipment_types bằng JDBC PreparedStatement. */
public class EquipmentTypeDAO {

    /** Danh sách loại thiết bị kèm số thiết bị thuộc loại (3.1.4). keyword lọc theo mã/tên. */
    public List<EquipmentType> search(String keyword) {
        String sql = """
            SELECT t.type_id, t.type_code, t.type_name, t.description, COUNT(e.equipment_id) AS cnt
            FROM equipment_types t LEFT JOIN equipment e ON e.type_id = t.type_id
            WHERE t.type_code LIKE ? OR t.type_name LIKE ?
            GROUP BY t.type_id, t.type_code, t.type_name, t.description
            ORDER BY t.type_code""";
        String kw = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        List<EquipmentType> list = new ArrayList<>();
        try (Connection c = DBConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, kw);
            ps.setString(2, kw);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new EquipmentType(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi đọc loại thiết bị: " + e.getMessage(), e);
        }
        return list;
    }

    public boolean codeExists(String code, int excludeId) {
        String sql = "SELECT COUNT(*) FROM equipment_types WHERE type_code = ? AND type_id <> ?";
        try (Connection c = DBConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi kiểm tra mã loại: " + e.getMessage(), e);
        }
    }

    public void insert(String code, String name, String description) {
        run("INSERT INTO equipment_types(type_code, type_name, description, created_by) VALUES (?,?,?,?)",
                code, name, description, Session.userId());
    }

    public void update(int id, String code, String name, String description) {
        run("UPDATE equipment_types SET type_code=?, type_name=?, description=? WHERE type_id=?", code, name, description, id);
    }

    public void delete(int id) {
        run("DELETE FROM equipment_types WHERE type_id=?", id);
    }

    public int countEquipment(int typeId) {
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM equipment WHERE type_id=?")) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi đếm thiết bị: " + e.getMessage(), e);
        }
    }

    private void run(String sql, Object... params) {
        try (Connection c = DBConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new BusinessException("Lỗi ghi dữ liệu: " + e.getMessage(), e);
        }
    }
}
