package ctv.model;

import ctv.util.I18n;

/** Các lớp dữ liệu (Model) ánh xạ bảng trong Form 06. Dùng record cho gọn. */
public final class Models {
    private Models() {}

    public record EquipmentType(int id, String code, String name, String description, int equipmentCount) {
        @Override public String toString() { return name; }
    }

    public record Warehouse(int id, String code, String name) {
        @Override public String toString() { return name; }
    }

    public record Location(int id, int warehouseId, String warehouseName, String code, String description) {
        @Override public String toString() { return warehouseName + " / " + code; }
    }

    /** Một dòng ở màn hình Quản lý vị trí kho: vị trí + một thiết bị đang lưu tại đó (equipmentName = null nếu vị trí trống). */
    public record LocationRow(Location location, String equipmentName, int quantity) {}

    public record Worksite(int id, String code, String name) {
        @Override public String toString() { return code + " - " + name; }
    }

    public record Equipment(int id, String code, String name, int typeId,
                            String condition, String usage, String responsible) {
        @Override public String toString() { return code + " - " + name; }
    }

    /** Một dòng tồn kho: thiết bị + vị trí (kho hoặc công trình) + số lượng. */
    public record StockRow(int equipmentId, String code, String name, int typeId, String typeName,
                           String condition, String usage, String responsible,
                           Integer locationId, String warehouseName, String locationCode,
                           Integer worksiteId, String worksiteName, int quantity) {
        public String place() {
            if (worksiteId != null) return "Công trình: " + worksiteName;
            if (locationId != null) return warehouseName + " / " + locationCode;
            return "-";
        }
    }

    /** Dòng chi tiết của phiếu nhập / xuất / thu hồi. */
    public record SlipLine(int equipmentId, String equipmentCode, String equipmentName,
                           int locationId, String locationLabel, int quantity, String conditionAfter) {}

    /** Lựa chọn hiển thị tiếng Việt cho ComboBox (mã lưu DB, nhãn hiển thị). */
    public record Option(String code, String label) {
        @Override public String toString() { return ctv.util.I18n.t(label); }
    }
}
