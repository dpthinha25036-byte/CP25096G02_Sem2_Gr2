package ctv.kho;

import ctv.kho.EquipmentTypeDAO;
import ctv.model.Models.EquipmentType;
import ctv.util.BusinessException;
import ctv.util.Ownership;
import java.util.List;

/** 3.1 – nghiệp vụ danh mục loại thiết bị. */
public class EquipmentTypeService {
    private final EquipmentTypeDAO dao = new EquipmentTypeDAO();

    public List<EquipmentType> search(String keyword) { return dao.search(keyword); }

    public void add(String code, String name, String desc) {
        validate(code, name, 0);
        dao.insert(code.trim(), name.trim(), blankToNull(desc));
    }

    public void edit(int id, String code, String name, String desc) {
        Ownership.require(Ownership.Kind.EQUIPMENT_TYPE, id, "sửa");
        validate(code, name, id);
        dao.update(id, code.trim(), name.trim(), blankToNull(desc));
    }

    public void delete(int id) {
        Ownership.require(Ownership.Kind.EQUIPMENT_TYPE, id, "xóa");
        if (dao.countEquipment(id) > 0)
            throw new BusinessException("Không thể xóa: đã có thiết bị thuộc loại này.");
        dao.delete(id);
    }

    private void validate(String code, String name, int excludeId) {
        if (code == null || code.isBlank()) throw new BusinessException("Mã loại thiết bị không được để trống.");
        if (name == null || name.isBlank()) throw new BusinessException("Tên loại thiết bị không được để trống.");
        if (name.trim().length() > 100) throw new BusinessException("Tên loại thiết bị tối đa 100 ký tự.");
        if (code.trim().length() > 20) throw new BusinessException("Mã loại tối đa 20 ký tự.");
        if (dao.codeExists(code.trim(), excludeId)) throw new BusinessException("Mã loại thiết bị đã tồn tại.");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
