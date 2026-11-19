package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.EquipmentType;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** frmLoaiThietBi (Form 05 – 5.15) – chức năng 3.1.1 – 3.1.4. */
public class EquipmentTypePanel extends BorderPane {
    private final EquipmentTypeService service = new EquipmentTypeService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã hoặc tên loại thiết bị...");
    private final DataTable table = Ui.table("#", "Mã loại", "Tên loại thiết bị", "Mô tả", "Số thiết bị", "Thao tác");
    private List<EquipmentType> current = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();

    public EquipmentTypePanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnThem = Ui.primary("Thêm loại", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");

        Responsive.Row bar = new Responsive.Row(10);
        bar.center(txtTimKiem).east(btnTim);

        root.north(Ui.stack(
                Ui.header("tag", "Danh mục loại thiết bị", "Quản lý danh mục loại thiết bị trong kho", btnLamMoi, btnThem),
                Ui.card(bar)));
        root.center(Ui.scroll(table));

        table.installActions(5, false,
                () -> { EquipmentType t = selected(); if (t != null) showDialog(t); },
                this::delete,
                idx -> idx < current.size() && mine.contains(current.get(idx).id()));   // chỉ người thêm loại thiết bị mới được sửa / xóa

        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> { txtTimKiem.setText(""); reload(); });
        btnThem.setOnAction(e -> showDialog(null));
        reload();
    }

    private void reload() {
        Ui.safe(this, () -> {
            current = service.search(txtTimKiem.getText());
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.EQUIPMENT_TYPE, current, EquipmentType::id);
            table.setRowCount(0);
            int i = 1;
            for (EquipmentType t : current)
                table.addRow(i++, t.code(), t.name(), t.description(), t.equipmentCount(), "");
        });
    }

    private EquipmentType selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một loại thiết bị trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private void showDialog(EquipmentType t) {
        TextField code = Ui.text(14), name = Ui.text(14), desc = Ui.text(14);
        if (t != null) { code.setText(t.code()); name.setText(t.name()); desc.setText(t.description()); }
        // Form 05: hai cột – Mã loại | Tên loại thiết bị, Mô tả
        GridPane f = Ui.form(2,
                new Node[]{Ui.required("Mã loại"), Ui.required("Tên loại thiết bị"), Ui.caption("Mô tả")},
                new Node[]{code, name, desc});
        Ui.dialog(this, t == null ? "Thêm loại thiết bị" : "Sửa loại thiết bị", f, () -> {
            if (t == null) service.add(code.getText(), name.getText(), desc.getText());
            else service.edit(t.id(), code.getText(), name.getText(), desc.getText());
            reload();
        });
    }

    private void delete() {
        EquipmentType t = selected();
        if (t == null || !Ui.confirm(this, "Xóa loại thiết bị " + t.name() + "?")) return;
        Ui.safe(this, () -> { service.delete(t.id()); reload(); });
    }
}
