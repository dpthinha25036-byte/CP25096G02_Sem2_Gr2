package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.EquipmentType;
import ctv.model.Models.Location;
import ctv.model.Models.StockRow;
import ctv.model.Models.Warehouse;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** frmThietBi (Form 05 – 5.16) – chức năng 3.2.1 – 3.2.13: danh sách, lọc, tìm kiếm, thêm/sửa/xóa, điều chỉnh tồn, thẻ thống kê. */
public class EquipmentPanel extends BorderPane {
    /** Mục trong ComboBox "kho": loại (WAREHOUSE / ANY_WORKSITE) + mã. */
    private record Place(String kind, Integer id, String label) {
        @Override public String toString() { return label; }
    }
    private record TypeItem(Integer id, String label) {
        @Override public String toString() { return label; }
    }
    /** Tình trạng hiển thị (gộp tình trạng + trạng thái sử dụng trong DB thành 5 nhãn như Form 05). */
    private record Status(String code, String label) {
        @Override public String toString() { return label; }
    }

    private static final List<Status> STATUSES = List.of(
            new Status("READY", "Sẵn sàng"), new Status("IN_USE", "Đang sử dụng"), new Status("MAINT", "Bảo trì"),
            new Status("REPAIR", "Cần sửa chữa"), new Status("BROKEN", "Hỏng"));

    /**
     * Tình trạng hiển thị theo TỪNG DÒNG tồn kho:
     * hỏng / cần sửa chữa / bảo trì giữ nguyên; ngược lại, thiết bị nằm trong kho → "Sẵn sàng",
     * thiết bị đang ở công trình → "Đang sử dụng".
     */
    private static String statusOf(StockRow r) {
        if ("BROKEN".equals(r.condition())) return "BROKEN";
        if ("NEED_REPAIR".equals(r.condition())) return "REPAIR";
        if ("MAINTENANCE".equals(r.usage())) return "MAINT";
        if (r.worksiteId() != null) return "IN_USE";
        return "READY";
    }

    private static String statusLabel(StockRow r) {
        String code = statusOf(r);
        for (Status st : STATUSES) if (st.code().equals(code)) return st.label();
        return code;
    }

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"));

    private final EquipmentService service = new EquipmentService();
    private final LookupDAO lookup = new LookupDAO();

    private final TextField txtTimKiem = Ui.search(24, "Tìm kiếm theo mã hoặc tên thiết bị...");
    private final ComboBox<TypeItem> cboLoai = new ComboBox<>();
    private final ComboBox<Object> cboTinhTrang = new ComboBox<>();
    private final ComboBox<Place> cboKho = new ComboBox<>();
    private final DataTable table = Ui.table(
            "#", "Mã thiết bị", "Tên thiết bị", "Loại thiết bị", "Kho", "Vị trí", "Tình trạng", "Số lượng", "Người phụ trách", "Thao tác");
    private final Label lblTong = new Label("0"), lblDangDung = new Label("0"),
            lblTrongKho = new Label("0"), lblBaoTri = new Label("0");
    private List<StockRow> all = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();

    public EquipmentPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnLamMoi = Ui.secondary("Làm mới", "refresh-cw");
        Button btnDieuChinh = Ui.secondary("Điều chỉnh tồn kho", "layers");
        Button btnThem = Ui.primary("Thêm thiết bị", "plus");

        Ui.translateCells(cboLoai);
        Ui.translateCells(cboTinhTrang);
        Ui.translateCells(cboKho);
        Button btnTim = Ui.primary("Tìm kiếm", "search");
        // Form 05: ô tìm kiếm rộng | Tất cả loại | Tất cả tình trạng | Tất cả kho | Tìm kiếm
        Ui.size(cboLoai, 190, Ui.TOOLBAR_H);
        Ui.size(cboTinhTrang, 190, Ui.TOOLBAR_H);
        Ui.size(cboKho, 190, Ui.TOOLBAR_H);
        Node bar = Ui.searchBar(Ui.withSearchIcon(txtTimKiem), cboLoai, cboTinhTrang, cboKho, btnTim);

        Responsive.Grid cards = new Responsive.Grid(4, 12, 215, 0);
        cards.add(Ui.statCard("box", Ui.CARD_ACCENTS[0], "Tổng thiết bị", lblTong, Ui.CARD_COLORS[0]));
        cards.add(Ui.statCard("circle-check", Ui.CARD_ACCENTS[1], "Đang sử dụng", lblDangDung, Ui.CARD_COLORS[1]));
        cards.add(Ui.statCard("warehouse", Ui.CARD_ACCENTS[2], "Trong kho", lblTrongKho, Ui.CARD_COLORS[2]));
        cards.add(Ui.statCard("wrench", Ui.CARD_ACCENTS[3], "Cần bảo trì", lblBaoTri, Ui.CARD_COLORS[3]));

        root.north(Ui.stack(
                Ui.header("hammer", "Quản lý thiết bị", "Quản lý danh sách thiết bị trong kho", btnLamMoi, btnDieuChinh, btnThem),
                bar, cards));
        root.center(Ui.scroll(table));

        table.installBadge(6);   // Tình trạng
        table.installActions(9, true,
                () -> { StockRow r = selected(); if (r != null) editDialog(r); },
                this::delete,
                idx -> idx < all.size() && mine.contains(all.get(idx).equipmentId()));   // chỉ người thêm thiết bị mới được sửa / xóa

        loadFilters();
        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> { txtTimKiem.setText(""); loadFilters(); reload(); });
        btnThem.setOnAction(e -> addDialog());
        btnDieuChinh.setOnAction(e -> adjust());
        reload();
    }

    /** Nạp lại các ComboBox lọc (loại, tình trạng, kho). */
    private void loadFilters() {
        Ui.safe(this, () -> {
            List<TypeItem> types = new ArrayList<>();
            types.add(new TypeItem(null, "Tất cả loại"));
            for (EquipmentType t : lookup.types()) types.add(new TypeItem(t.id(), t.name()));
            cboLoai.getItems().setAll(types);
            cboLoai.getSelectionModel().selectFirst();

            List<Object> cond = new ArrayList<>();
            cond.add("Tất cả tình trạng");
            cond.addAll(STATUSES);
            cboTinhTrang.getItems().setAll(cond);
            cboTinhTrang.getSelectionModel().selectFirst();

            List<Place> places = new ArrayList<>();
            places.add(new Place(null, null, "Tất cả kho"));
            for (Warehouse w : lookup.warehouses()) places.add(new Place("WAREHOUSE", w.id(), w.name()));
            places.add(new Place("ANY_WORKSITE", null, "Tại công trình"));
            cboKho.getItems().setAll(places);
            cboKho.getSelectionModel().selectFirst();
        });
    }

    private void reload() {
        Ui.safe(this, () -> {
            TypeItem type = cboLoai.getValue();
            Place place = cboKho.getValue();
            Status st = cboTinhTrang.getValue() instanceof Status x ? x : null;
            all = service.find(txtTimKiem.getText(), type == null ? null : type.id(), null,
                    place == null ? null : place.kind(), place == null ? null : place.id())
                    .stream()
                    .filter(r -> st == null || st.code().equals(statusOf(r)))
                    .toList();
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.EQUIPMENT, all, StockRow::equipmentId);
            render();
        });
    }

    private void render() {
        table.setRowCount(0);
        for (int i = 0; i < all.size(); i++) {
            StockRow r = all.get(i);
            String kho = r.worksiteId() != null ? "Công trình" : r.warehouseName() == null ? "-" : r.warehouseName();
            String viTri = r.worksiteId() != null ? r.worksiteName() : r.locationCode() == null ? "-" : r.locationCode();
            table.addRow(i + 1, r.code(), r.name(), r.typeName(), kho, viTri,
                    statusLabel(r), r.quantity(), r.responsible(), "");
        }

        Set<Integer> ids = new HashSet<>();
        int atSite = 0, inStore = 0;
        long maintain = 0;   // cùng định nghĩa với màn hình báo cáo: số lượng ở tình trạng Bảo trì / Cần sửa chữa / Hỏng
        for (StockRow r : all) {
            ids.add(r.equipmentId());
            if (r.worksiteId() != null) atSite += r.quantity(); else if (r.locationId() != null) inStore += r.quantity();
            if (!"GOOD".equals(r.condition()) || "MAINTENANCE".equals(r.usage())) maintain += r.quantity();
        }
        lblTong.setText(NUM.format(ids.size()));
        lblDangDung.setText(NUM.format(atSite));
        lblTrongKho.setText(NUM.format(inStore));
        lblBaoTri.setText(NUM.format(maintain));
    }

    private StockRow selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một dòng thiết bị trong bảng."); return null; }
        return all.get(table.convertRowIndexToModel(r));
    }

    // ----- hộp thoại -----
    /** Đổi nhãn "Tình trạng" trong hộp thoại thành (tình trạng, trạng thái) lưu DB; giữ nguyên giá trị cũ nếu không bị nhãn mới ghi đè. */
    private static String[] toDb(Status st, String oldCond, String oldUsage) {
        return switch (st.code()) {
            case "IN_USE" -> new String[]{"GOOD", "IN_USE"};
            case "MAINT" -> new String[]{oldCond, "MAINTENANCE"};
            case "REPAIR" -> new String[]{"NEED_REPAIR", oldUsage.equals("MAINTENANCE") ? "IN_STOCK" : oldUsage};
            case "BROKEN" -> new String[]{"BROKEN", oldUsage.equals("MAINTENANCE") ? "IN_STOCK" : oldUsage};
            default -> new String[]{"GOOD", "IN_STOCK"};
        };
    }

    private void addDialog() {
        List<EquipmentType> types = lookup.types();
        List<Warehouse> warehouses = lookup.warehouses();
        if (types.isEmpty() || warehouses.isEmpty()) {
            Ui.error(this, "Cần có ít nhất một loại thiết bị và một kho trước khi thêm thiết bị.");
            return;
        }
        TextField code = Ui.text(14), name = Ui.text(14), resp = Ui.text(14);
        ComboBox<EquipmentType> type = Ui.combo(types);
        ComboBox<Warehouse> kho = Ui.combo(warehouses);
        ComboBox<Location> loc = Ui.combo(lookup.locations(warehouses.get(0).id()));
        kho.setOnAction(e -> {
            Warehouse w = kho.getValue();
            loc.getItems().setAll(lookup.locations(w == null ? null : w.id()));
            loc.getSelectionModel().selectFirst();
        });
        Spinner<Integer> qty = Ui.intSpinner(1, 0, 100000);
        ComboBox<Status> status = Ui.combo(STATUSES);
        // Form 05: trái = Mã, Tên, Loại, Kho; phải = Vị trí, Tình trạng, Số lượng, Người phụ trách
        GridPane f = Ui.form(2,
                new Node[]{Ui.required("Mã thiết bị"), Ui.caption("Vị trí"), Ui.required("Tên thiết bị"), Ui.caption("Tình trạng"),
                        Ui.required("Loại thiết bị"), Ui.required("Số lượng"), Ui.required("Kho"), Ui.caption("Người phụ trách")},
                new Node[]{code, loc, name, status, type, qty, kho, resp});
        Ui.dialog(this, "Thêm thiết bị", f, () -> {
            if (loc.getValue() == null) throw new ctv.util.BusinessException("Kho đã chọn chưa có vị trí lưu trữ.");
            String[] db = toDb(status.getValue(), "GOOD", "IN_STOCK");
            service.add(code.getText(), name.getText(), type.getValue().id(),
                    db[0], db[1], resp.getText(), loc.getValue().id(), qty.getValue());
            loadFilters();
            reload();
        });
    }

    private void editDialog(StockRow r) {
        TextField code = Ui.text(14), name = Ui.text(14), resp = Ui.text(14);
        code.setText(r.code()); name.setText(r.name()); resp.setText(r.responsible());
        List<EquipmentType> types = lookup.types();
        ComboBox<EquipmentType> type = Ui.combo(types);
        for (EquipmentType t : types) if (t.id() == r.typeId()) type.setValue(t);
        ComboBox<Status> status = Ui.combo(STATUSES);
        String cur = statusOf(r);
        for (Status s : STATUSES) if (s.code().equals(cur)) status.setValue(s);
        GridPane f = Ui.form(2,
                new Node[]{Ui.required("Mã thiết bị"), Ui.caption("Tình trạng"), Ui.required("Tên thiết bị"),
                        Ui.caption("Người phụ trách"), Ui.required("Loại thiết bị")},
                new Node[]{code, status, name, resp, type});
        Ui.dialog(this, "Sửa thiết bị", f, () -> {
            Status st = status.getValue();
            // không đổi nhãn → giữ nguyên tình trạng / trạng thái đang lưu trong DB
            String[] db = st.code().equals(cur) ? new String[]{r.condition(), r.usage()} : toDb(st, r.condition(), r.usage());
            service.edit(r.equipmentId(), code.getText(), name.getText(), type.getValue().id(),
                    db[0], db[1], resp.getText());
            reload();
        });
    }

    private void delete() {
        StockRow r = selected();
        if (r == null || !Ui.confirm(this, "Xóa thiết bị " + r.code() + " - " + r.name() + "?")) return;
        Ui.safe(this, () -> { service.delete(r.equipmentId()); reload(); });
    }

    private void adjust() {
        StockRow r = selected();
        if (r == null) return;
        if (r.locationId() == null) { Ui.error(this, "Chỉ điều chỉnh tồn tại vị trí trong kho. Hãy chọn một dòng ở kho."); return; }
        Spinner<Integer> delta = Ui.intSpinner(0, -100000, 100000);
        GridPane f = Ui.form(
                new Node[]{Ui.caption("Thiết bị"), Ui.caption("Vị trí / tồn hiện tại"), Ui.required("Số lượng thay đổi (+/-)")},
                new Node[]{Ui.caption(r.code() + " - " + r.name()), Ui.caption(r.place() + " / " + r.quantity()), delta});
        Ui.dialog(this, "Điều chỉnh tồn kho", f, () -> { service.adjustStock(r.equipmentId(), r.locationId(), delta.getValue()); reload(); });
    }
}
