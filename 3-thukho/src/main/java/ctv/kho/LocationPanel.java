package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Location;
import ctv.model.Models.LocationRow;
import ctv.model.Models.Warehouse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** frmViTriKho (Form 05 – 5.17) – quản lý vị trí lưu trữ trong kho (3.2.6, 3.2.10). */
public class LocationPanel extends BorderPane {
    private final LookupDAO dao = new LookupDAO();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã vị trí hoặc tên thiết bị...");
    private final ComboBox<Object> cboKho = new ComboBox<>();
    private final DataTable table = Ui.table("#", "Kho", "Mã vị trí", "Mô tả", "Thiết bị lưu trữ", "Số lượng", "Thao tác");
    private final Label lblKho = new Label("0"), lblViTri = new Label("0"), lblDung = new Label("0"), lblTrong = new Label("0");
    private List<LocationRow> current = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();

    public LocationPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnThem = Ui.primary("Thêm vị trí", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");
        Ui.translateCells(cboKho);
        Ui.size(cboKho, 170, Ui.TOOLBAR_H);

        Node bar = Ui.searchBar(txtTimKiem, cboKho, btnTim);

        Responsive.Grid cards = new Responsive.Grid(4, 12, 215, 0);
        cards.add(Ui.statCard("Số kho", lblKho, Ui.CARD_COLORS[0]));
        cards.add(Ui.statCard("Số vị trí", lblViTri, Ui.CARD_COLORS[1]));
        cards.add(Ui.statCard("Vị trí đang dùng", lblDung, Ui.CARD_COLORS[2]));
        cards.add(Ui.statCard("Vị trí trống", lblTrong, Ui.CARD_COLORS[3]));

        root.north(Ui.stack(
                Ui.header("map-pin", "Quản lý vị trí kho", "Danh sách kho, vị trí và số lượng thiết bị tại từng vị trí", btnLamMoi, btnThem),
                bar, cards));
        root.center(Ui.scroll(table));
        table.installActions(6, false,
                () -> { Location l = selected(); if (l != null) showDialog(l); },
                this::deleteSelected,
                idx -> idx < current.size() && mine.contains(current.get(idx).location().id()));   // chỉ người thêm vị trí mới được sửa / xóa

        loadWarehouses();
        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboKho.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> { txtTimKiem.setText(""); loadWarehouses(); reload(); });
        btnThem.setOnAction(e -> showDialog(null));
        reload();
    }

    private void loadWarehouses() {
        Ui.safe(this, () -> {
            List<Object> items = new ArrayList<>();
            items.add("Tất cả kho");
            items.addAll(dao.warehouses());
            cboKho.getItems().setAll(items);
            cboKho.getSelectionModel().selectFirst();
        });
    }

    private Integer warehouseId() {
        return cboKho.getValue() instanceof Warehouse w ? w.id() : null;
    }

    private void reload() {
        Ui.safe(this, () -> {
            current = dao.locationStock(warehouseId(), txtTimKiem.getText());
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.LOCATION, current, lr -> lr.location().id());
            table.setRowCount(0);
            int i = 1;
            for (LocationRow r : current) {
                Location l = r.location();
                table.addRow(i++, l.warehouseName(), l.code(), l.description(),
                        r.equipmentName() == null ? "-" : r.equipmentName(), r.quantity(), "");
            }
            refreshCards();
        });
    }

    /** Thẻ thống kê tính trên toàn bộ kho, không phụ thuộc ô tìm kiếm. */
    private void refreshCards() {
        List<LocationRow> everything = dao.locationStock(null, null);
        Set<Integer> all = new HashSet<>(), used = new HashSet<>();
        for (LocationRow r : everything) {
            all.add(r.location().id());
            if (r.quantity() > 0) used.add(r.location().id());
        }
        lblKho.setText(String.valueOf(dao.warehouses().size()));
        lblViTri.setText(String.valueOf(all.size()));
        lblDung.setText(String.valueOf(used.size()));
        lblTrong.setText(String.valueOf(all.size() - used.size()));
    }

    private void deleteSelected() {
        Location l = selected();
        if (l != null && Ui.confirm(this, "Xóa vị trí " + l + "?")) Ui.safe(this, () -> { dao.deleteLocation(l.id()); reload(); });
    }

    private Location selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một vị trí trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r)).location();
    }

    private void showDialog(Location l) {
        List<Warehouse> whs = dao.warehouses();
        ComboBox<Warehouse> kho = Ui.combo(whs);
        TextField code = Ui.text(14), desc = Ui.text(14);
        if (l != null) {
            code.setText(l.code()); desc.setText(l.description());
            for (Warehouse w : whs) if (w.id() == l.warehouseId()) kho.setValue(w);
            kho.setDisable(true);
        }
        GridPane f = Ui.form(2, new Node[]{Ui.required("Kho"), Ui.required("Mã vị trí"), Ui.caption("Mô tả")},
                new Node[]{kho, code, desc});
        Ui.dialog(this, l == null ? "Thêm vị trí" : "Sửa vị trí", f, () -> {
            if (code.getText().isBlank()) throw new ctv.util.BusinessException("Mã vị trí không được để trống.");
            if (l == null) dao.insertLocation(kho.getValue().id(), code.getText().trim(), desc.getText().trim());
            else dao.updateLocation(l.id(), code.getText().trim(), desc.getText().trim());
            loadWarehouses();
            reload();
        });
    }
}
