package ctv.kho;

import ctv.kho.EquipmentDAO;
import ctv.kho.LookupDAO;
import ctv.model.Models.StockRow;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** 3.2 – nghiệp vụ thiết bị và tồn kho. */
public class EquipmentService {
    private final EquipmentDAO dao = new EquipmentDAO();

    public List<StockRow> find(String keyword, Integer typeId, String condition, String placeKind, Integer placeId) {
        return dao.findStock(keyword, typeId, condition, placeKind, placeId);
    }

    public void add(String code, String name, int typeId, String condition, String usage,
                    String responsible, int locationId, int quantity) {
        check(code, name, 0);
        if (quantity < 0) throw new BusinessException("Số lượng không được âm.");
        validateState(condition, usage);
        if (typeId <= 0 || locationId <= 0) throw new BusinessException("Hãy chọn loại thiết bị và vị trí kho.");
        dao.insertWithStock(code.trim(), name.trim(), typeId, condition, usage, blankToNull(responsible), locationId, quantity);
    }

    /** Sửa thông tin, tình trạng, trạng thái, người chịu trách nhiệm (3.2.2, 3.2.8, 3.2.9, 3.2.11). */
    public void edit(int id, String code, String name, int typeId, String condition, String usage, String responsible) {
        ctv.util.Ownership.require(ctv.util.Ownership.Kind.EQUIPMENT, id, "sửa");
        check(code, name, id);
        validateState(condition, usage);
        if (typeId <= 0) throw new BusinessException("Hãy chọn loại thiết bị.");
        dao.update(id, code.trim(), name.trim(), typeId, condition, usage, blankToNull(responsible));
    }

    public void delete(int id) {
        ctv.util.Ownership.require(ctv.util.Ownership.Kind.EQUIPMENT, id, "xóa");
        dao.delete(id);
    }

    /** 3.2.13 – điều chỉnh tồn kho tại một vị trí (delta có thể âm). */
    public void adjustStock(int equipmentId, int locationId, int delta) {
        if (delta == 0) throw new BusinessException("Số lượng thay đổi phải khác 0.");
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                ctv.kho.StockDAO.add(c, equipmentId, locationId, null, delta);
                ctv.kho.StockDAO.refreshUsage(c, equipmentId);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw new BusinessException("Không điều chỉnh được tồn kho: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi giao dịch: " + e.getMessage(), e);
        }
    }

    private void validateState(String condition,String usage) {
        if (!List.of("GOOD","NEED_REPAIR","BROKEN").contains(condition)) throw new BusinessException("Tình trạng thiết bị không hợp lệ.");
        if (!List.of("IN_STOCK","IN_USE","MAINTENANCE").contains(usage)) throw new BusinessException("Trạng thái sử dụng không hợp lệ.");
    }

    private void check(String code, String name, int excludeId) {
        if (code == null || code.isBlank()) throw new BusinessException("Mã thiết bị không được để trống.");
        if (name == null || name.isBlank()) throw new BusinessException("Tên thiết bị không được để trống.");
        if (dao.codeExists(code.trim(), excludeId)) throw new BusinessException("Mã thiết bị đã tồn tại.");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
