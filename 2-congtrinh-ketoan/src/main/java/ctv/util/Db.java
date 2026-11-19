package ctv.util;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Tiện ích JDBC dùng chung cho các DAO của mọi nhóm (query / exec với PreparedStatement). */
public final class Db {
    private Db() {}

    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = new ArrayList<>();
        try (Connection c = DBConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(mapper.map(rs)); }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi truy vấn: " + e.getMessage(), e);
        }
        return list;
    }

    public static void exec(String sql, Object... params) {
        try (Connection c = DBConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new BusinessException("Lỗi ghi dữ liệu: " + e.getMessage(), e);
        }
    }
}
