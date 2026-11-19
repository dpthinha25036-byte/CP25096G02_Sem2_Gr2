package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Ui;
import ctv.model.Models.Equipment;
import ctv.model.Models.EquipmentType;
import ctv.model.Models.Location;
import ctv.model.Models.Option;
import ctv.model.Models.SlipLine;
import ctv.model.Models.StockRow;
import ctv.model.Models.Worksite;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import ctv.util.I18n;
import ctv.util.Labels;
import ctv.util.Session;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/**
 * Một màn hình dùng chung cho 3 loại phiếu (Form 05 – 5.18, 5.19, 5.20: thông tin phiếu + bảng chi tiết + Lưu/Hủy):
 *  RECEIPT = frmNhapKho, ISSUE = frmXuatThietBi, RETURN = frmThuHoiThietBi.
 */
public class SlipPanel extends BorderPane {
    public enum Mode { RECEIPT, ISSUE, RETURN }

    private final Mode mode;
    private final InventoryService service = new InventoryService();
    private final LookupDAO lookup = new LookupDAO();

    private final TextField txtSoPhieu = Ui.text(22);
    private final TextField txtNguoiLap = Ui.text(22);
    private final DatePicker dtpNgay = Ui.datePicker(LocalDate.now());
    private final TextField txtNguon = Ui.text(22);      // nguồn nhập / người nhận / người trả
    private final TextField txtChucVu = Ui.text(22);
    private static final String DEFAULT_POSITION = "Quản lý công trình";   // dùng khi công trình cũ chưa ghi nhận người thêm
    private final ComboBox<String> cboNguoiNhan = new ComboBox<>();   // xuất: công nhân của công trình (gõ tay được)
    private final ComboBox<String> cboNguoiTra = new ComboBox<>();    // thu hồi: người đã mượn thiết bị
    private final ComboBox<Worksite> cboCongTrinh = Ui.combo(new ArrayList<>());
    private final ComboBox<Equipment> cboThietBi = Ui.combo(new ArrayList<>());
    private final ComboBox<Location> cboViTri = Ui.combo(new ArrayList<>());
    private final ComboBox<Option> cboTinhTrangSau = Ui.combo(Labels.CONDITIONS);
    private final Spinner<Integer> spnSoLuong = Ui.intSpinner(1, 1, 100000);
    private final Label lblNguoiNhanHienThi = Ui.caption(" ");   // xuất: "Họ tên – Quản lý công trình" như sẽ ghi trên phiếu
    private final Label lblTon = Ui.caption(" ");
    private final DataTable table;
    private final List<SlipLine> lines = new ArrayList<>();
    private final Map<Integer, String> typeNames = new HashMap<>();
    private boolean quiet;                 // true khi đang nạp lại danh sách: bỏ qua sự kiện chọn
    private boolean loadingBorrowers;

    public SlipPanel(Mode mode) {
        this.mode = mode;
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        String title = switch (mode) {
            case RECEIPT -> "Nhập kho thiết bị";
            case ISSUE -> "Xuất thiết bị";
            case RETURN -> "Thu hồi thiết bị";
        };
        String sub = switch (mode) {
            case RECEIPT -> "Lập phiếu nhập và cập nhật tồn kho";
            case ISSUE -> "Lập phiếu xuất thiết bị từ kho đến công trình";
            case RETURN -> "Lập phiếu thu hồi thiết bị từ công trình về kho";
        };
        String ico = switch (mode) {
            case RECEIPT -> "arrow-down-to-line";
            case ISSUE -> "arrow-up-from-line";
            case RETURN -> "undo-2";
        };
        String newText = switch (mode) {
            case RECEIPT -> "Lập phiếu nhập";
            case ISSUE -> "Lập phiếu xuất";
            case RETURN -> "Lập phiếu thu hồi";
        };
        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnLapPhieu = Ui.primary(newText, "plus");

        // ----- thông tin phiếu: lưới 2 x 2 theo Form 05 -----
        cboNguoiNhan.setEditable(true);  // chọn công nhân trong công trình, vẫn gõ tay được nếu người nhận chưa có trong danh sách
        cboNguoiNhan.getStyleClass().add("ctv-input");
        cboNguoiTra.getStyleClass().add("ctv-input");
        if (mode != Mode.RECEIPT) txtChucVu.setEditable(false);   // tự điền: tên + chức vụ người thêm công trình
        txtSoPhieu.setEditable(false);
        txtNguoiLap.setEditable(false);
        txtNguoiLap.setText(Session.fullName());
        Node[] labels, fields;
        switch (mode) {
            case RECEIPT -> {
                labels = new Node[]{Ui.captionBold("Số phiếu nhập"), Ui.captionBold("Ngày nhập"),
                        Ui.captionBold("Nguồn nhập"), Ui.captionBold("Người lập phiếu")};
                fields = new Node[]{txtSoPhieu, dtpNgay, txtNguon, txtNguoiLap};
            }
            case ISSUE -> {
                labels = new Node[]{Ui.captionBold("Số phiếu xuất"), Ui.captionBold("Ngày xuất"),
                        Ui.requiredBold("Công trình nhận"), Ui.requiredBold("Người nhận"), Ui.requiredBold("Chức vụ người nhận"),
                        Ui.captionBold("Ghi trên phiếu")};
                fields = new Node[]{txtSoPhieu, dtpNgay, cboCongTrinh, cboNguoiNhan, txtChucVu, lblNguoiNhanHienThi};
            }
            default -> {
                labels = new Node[]{Ui.captionBold("Số phiếu thu hồi"), Ui.captionBold("Ngày thu hồi"),
                        Ui.requiredBold("Công trình trả"), Ui.requiredBold("Người trả"), Ui.requiredBold("Chức vụ người trả")};
                fields = new Node[]{txtSoPhieu, dtpNgay, cboCongTrinh, cboNguoiTra, txtChucVu};
            }
        }
        GridPane info = Ui.form(2, 140, labels, fields);
        info.setStyle("-fx-background-color:transparent;");

        table = switch (mode) {
            case RECEIPT -> Ui.table("#", "Mã thiết bị", "Tên thiết bị", "Loại", "Kho", "Vị trí", "Số lượng nhập");
            case ISSUE -> Ui.table("#", "Mã thiết bị", "Tên thiết bị", "Kho", "Vị trí", "Tồn kho", "Số lượng xuất");
            case RETURN -> Ui.table("#", "Mã thiết bị", "Tên thiết bị", "Đang ở CT", "Số lượng thu hồi",
                    "Kho nhận", "Vị trí", "Tình trạng sau thu hồi");
        };
        if (mode == Mode.RETURN) table.installBadge(7);

        root.north(Ui.stack(Ui.header(ico, title, sub, btnLamMoi, btnLapPhieu), Ui.card(info)));
        root.center(Ui.scroll(table));

        // ----- chân trang: Xóa dòng bên trái, Lưu phiếu / Hủy bên phải -----
        Button btnXoaDong = Ui.cancel("Xóa dòng");
        Button btnLuu = Ui.primary("Lưu phiếu"), btnHuy = Ui.cancel("Hủy");
        BorderPane south = new BorderPane();
        south.setLeft(Ui.flow(Pos.CENTER_LEFT, btnXoaDong));
        south.setRight(Ui.flow(Pos.CENTER_RIGHT, btnLuu, btnHuy));
        root.south(south);

        loadCombos();
        cboThietBi.setOnAction(e -> { if (!quiet) showStock(); });
        cboViTri.setOnAction(e -> { if (!quiet) showStock(); });
        if (mode == Mode.RETURN) {
            cboCongTrinh.setOnAction(e -> { if (!quiet) loadEquipmentAtWorksite(); });
            cboNguoiTra.setOnAction(e -> {   // đổi người trả → chỉ hiện thiết bị người đó đang giữ
                if (quiet || loadingBorrowers) return;
                if (!lines.isEmpty()) {
                    lines.clear();
                    table.setRowCount(0);
                    Ui.info(this, "Đã xóa các dòng thiết bị đã thêm vì đổi người trả.");
                }
                reloadReturnEquipment();
            });
        }
        if (mode == Mode.ISSUE) {
            cboCongTrinh.setOnAction(e -> { if (!quiet) loadReceivers(); });
            cboNguoiNhan.setOnAction(e -> updateReceiverPreview());
            cboNguoiNhan.getEditor().textProperty().addListener((o, a, b) -> updateReceiverPreview());
            updateReceiverPreview();
        }
        btnLamMoi.setOnAction(e -> refresh());
        btnLapPhieu.setOnAction(e -> addLineDialog());
        btnXoaDong.setOnAction(e -> removeLine());
        btnLuu.setOnAction(e -> save());
        btnHuy.setOnAction(e -> reset());
        // Gắn vào cửa sổ (mở màn hình) → nạp lại dữ liệu mới nhất.
        sceneProperty().addListener((o, was, now) -> { if (now != null) loadCombos(); });
    }

    private static String t(String s) { return I18n.t(s); }

    /** Làm mới: xóa các dòng đã thêm, đặt lại ô nhập và nạp lại dữ liệu mới nhất. */
    private void refresh() {
        reset();
        cboTinhTrangSau.getSelectionModel().selectFirst();
        Ui.setDate(dtpNgay, LocalDate.now());
        loadCombos();
    }

    private void loadCombos() {
        Ui.safe(this, () -> {
            setModel(cboViTri, lookup.locations(null));
            typeNames.clear();
            for (EquipmentType tp : lookup.types()) typeNames.put(tp.id(), tp.name());
            if (mode != Mode.RECEIPT) setModel(cboCongTrinh, lookup.worksites());
            if (mode == Mode.RETURN) loadEquipmentAtWorksite();
            else setModel(cboThietBi, lookup.equipmentList());
            if (mode == Mode.ISSUE) loadReceivers();
            showStock();
            txtSoPhieu.setText(previewCode());
            txtNguoiLap.setText(Session.fullName());
        });
    }

    /** Số phiếu kế tiếp (đúng định dạng service sẽ cấp khi lưu). */
    private String previewCode() {
        try (Connection c = DBConnection.get()) {
            return switch (mode) {
                case RECEIPT -> SlipDAO.nextCode(c, "PN", "goods_receipts", "receipt_id");
                case ISSUE -> SlipDAO.nextCode(c, "PX", "issue_slips", "issue_id");
                case RETURN -> SlipDAO.nextCode(c, "PT", "return_slips", "return_id");
            };
        } catch (Exception e) {
            return t("(tự sinh khi lưu)");
        }
    }

    /** Thay danh sách của ComboBox và chọn phần tử đầu, không phát sự kiện chọn (giống setModel của Swing). */
    private <T> void setModel(ComboBox<T> c, List<T> items) {
        boolean was = quiet;
        quiet = true;
        try {
            c.getItems().setAll(items);
            if (items.isEmpty()) c.setValue(null); else c.getSelectionModel().selectFirst();
        } finally {
            quiet = was;
        }
    }

    /** Thu hồi: chỉ liệt kê thiết bị đang ở công trình đã chọn. */
    private void loadEquipmentAtWorksite() {
        Worksite w = cboCongTrinh.getValue();
        loadingBorrowers = true;
        try { loadBorrowers(w); } finally { loadingBorrowers = false; }
        updateManager();
        reloadReturnEquipment();
    }

    /** Danh sách thiết bị trong hộp thoại thêm dòng: chỉ những thiết bị người trả (người đã nhận khi xuất) đang giữ tại công trình. */
    private void reloadReturnEquipment() {
        Worksite w = cboCongTrinh.getValue();
        List<Equipment> list = new ArrayList<>();
        if (w != null) {
            Map<Integer, Integer> held = heldByReturner(w);
            for (StockRow r : lookup.stockAtWorksite(w.id())) {
                if (!held.containsKey(r.equipmentId())) continue;
                list.add(new Equipment(r.equipmentId(), r.code(), r.name(), r.typeId(), r.condition(), r.usage(), r.responsible()));
            }
        }
        setModel(cboThietBi, list);
        showStock();
    }

    private Map<Integer, Integer> heldByReturner(Worksite w) {
        String n = returnerName().trim();
        return n.isEmpty() ? Map.of() : lookup.heldBy(w.id(), n);
    }

    /** Số lượng tối đa có thể thu hồi một thiết bị: số người trả đang giữ, không vượt số đang ở công trình. */
    private int availableToReturn(Equipment eq, Worksite w) {
        return Math.min(atSite(eq, w), heldByReturner(w).getOrDefault(eq.id(), 0));
    }

    /** Phiếu xuất: nạp công nhân đang được phân công tại công trình đã chọn vào ô "Người nhận". */
    private void loadReceivers() {
        Worksite w = cboCongTrinh.getValue();
        List<String> names = w == null ? new ArrayList<>() : lookup.workersAtWorksite(w.id());
        boolean was = quiet;
        quiet = true;
        cboNguoiNhan.getItems().setAll(names);
        cboNguoiNhan.setValue(names.isEmpty() ? "" : names.get(0));
        quiet = was;
        updateManager();
        updateReceiverPreview();
    }

    /** Ô chức vụ: ghi rõ tên và chức vụ của người đã thêm công trình đang chọn. */
    private void updateManager() {
        if (mode == Mode.RECEIPT) return;
        Worksite w = cboCongTrinh.getValue();
        String m = w == null ? null : lookup.worksiteManager(w.id());
        txtChucVu.setText(t(m == null ? DEFAULT_POSITION : m));
    }

    /** Hiển thị rõ tên người nhận kèm chức vụ, đúng như sẽ lưu trên phiếu. */
    private void updateReceiverPreview() {
        String n = receiverName().trim();
        lblNguoiNhanHienThi.setText(n.isEmpty() ? " " : n + " – " + txtChucVu.getText());
    }

    private String receiverName() {
        String s = cboNguoiNhan.getEditor().getText();
        return s == null ? "" : s;
    }

    /** Nạp vào ô "Người trả" các công nhân đã nhận thiết bị khi xuất cho công trình và còn đang giữ (chỉ họ tên). */
    private void loadBorrowers(Worksite w) {
        String keep = cboNguoiTra.getValue();
        List<String> names = w == null ? new ArrayList<>() : lookup.borrowers(w.id());
        setModel(cboNguoiTra, names);
        if (keep != null && names.contains(keep)) cboNguoiTra.setValue(keep);
    }

    private void showStock() {
        Equipment eq = cboThietBi.getValue();
        if (eq == null) { lblTon.setText(" "); return; }
        try {
            if (mode == Mode.ISSUE) {
                Location l = cboViTri.getValue();
                if (l != null) lblTon.setText(t("Tồn tại") + " " + l + ": " + lookup.quantityAt(eq.id(), l.id()));
            } else if (mode == Mode.RETURN) {
                Worksite w = cboCongTrinh.getValue();
                if (w != null) lblTon.setText(t("Người trả đang giữ:") + " " + (availableToReturn(eq, w) - alreadyReturned(eq)));
            } else lblTon.setText(" ");
        } catch (BusinessException ex) { lblTon.setText(" "); }
    }

    /** Số lượng thiết bị này đã được thêm vào phiếu thu hồi hiện tại. */
    private int alreadyReturned(Equipment eq) {
        return lines.stream().filter(l -> l.equipmentId() == eq.id()).mapToInt(SlipLine::quantity).sum();
    }

    private int atSite(Equipment eq, Worksite w) {
        return lookup.stockAtWorksite(w.id()).stream().filter(r -> r.equipmentId() == eq.id())
                .mapToInt(StockRow::quantity).sum();
    }

    /** Hộp thoại nhập một dòng thiết bị cho phiếu (các ô nhập không nằm trên màn hình chính để giống thiết kế Form 05). */
    private void addLineDialog() {
        List<Node> labels = new ArrayList<>(List.of(Ui.required("Thiết bị"),
                Ui.required(mode == Mode.RETURN ? "Nhận lại tại" : mode == Mode.ISSUE ? "Xuất từ" : "Lưu tại"),
                Ui.required("Số lượng")));
        List<Node> fields = new ArrayList<>(List.of(cboThietBi, cboViTri, spnSoLuong));
        if (mode == Mode.RETURN) { labels.add(Ui.caption("Tình trạng sau")); fields.add(cboTinhTrangSau); }
        if (mode != Mode.RECEIPT) { labels.add(Ui.caption("Thông tin")); fields.add(lblTon); }
        GridPane f = Ui.form(labels.toArray(new Node[0]), fields.toArray(new Node[0]));
        spnSoLuong.getValueFactory().setValue(1);
        showStock();
        Ui.dialog(this, "Thêm thiết bị vào phiếu", f, this::addLine);
    }

    private void addLine() {
        Equipment eq = cboThietBi.getValue();
        Location loc = cboViTri.getValue();
        if (eq == null || loc == null) throw new BusinessException("Chưa chọn thiết bị hoặc vị trí.");
        int qty = spnSoLuong.getValue();
        String after = mode == Mode.RETURN ? cboTinhTrangSau.getValue().code() : null;
        int have = 0;
        if (mode == Mode.RETURN) {
            Worksite w = site();
            int left = availableToReturn(eq, w) - alreadyReturned(eq);
            if (qty > left)
                throw new BusinessException("Số lượng thu hồi vượt số thiết bị " + returnerName().trim() + " đang giữ: " + eq.code()
                        + " chỉ còn " + Math.max(left, 0) + ".");
        }
        // Cảnh báo sớm khi xuất vượt tồn (service sẽ kiểm tra lại lần cuối khi lưu)
        if (mode == Mode.ISSUE) {
            int already = lines.stream().filter(l -> l.equipmentId() == eq.id() && l.locationId() == loc.id())
                    .mapToInt(SlipLine::quantity).sum();
            have = lookup.quantityAt(eq.id(), loc.id());
            if (already + qty > have)
                throw new BusinessException("Thiếu tồn kho: " + eq.code() + " tại " + loc + " chỉ còn " + (have - already) + ".");
        }
        int n = lines.size() + 1;
        SlipLine line = new SlipLine(eq.id(), eq.code(), eq.name(), loc.id(), loc.toString(), qty, after);
        lines.add(line);
        switch (mode) {
            case RECEIPT -> table.addRow(n, eq.code(), eq.name(), typeNames.getOrDefault(eq.typeId(), ""),
                    loc.warehouseName(), loc.code(), qty);
            case ISSUE -> table.addRow(n, eq.code(), eq.name(), loc.warehouseName(), loc.code(), have, qty);
            case RETURN -> {
                Worksite w = cboCongTrinh.getValue();
                table.addRow(n, eq.code(), eq.name(), w == null ? "" : atSite(eq, w), qty,
                        loc.warehouseName(), loc.code(), Labels.condition(after));
            }
        }
    }

    private void removeLine() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn dòng cần xóa."); return; }
        lines.remove(r);
        table.removeRow(r);
        for (int i = 0; i < table.getRowCount(); i++) table.setValueAt(i + 1, i, 0);
        showStock();
    }

    private void save() {
        Ui.safe(this, () -> {
            LocalDate date = Ui.dateOf(dtpNgay);
            if (mode == Mode.RETURN && returnerName().isBlank())
                throw new BusinessException("Chưa có người trả: công trình này chưa có công nhân nào đang giữ thiết bị đã nhận khi xuất.");
            int uid = Session.userId();
            String code;
            switch (mode) {
                case RECEIPT -> code = service.createReceipt(date, txtNguon.getText(), lines, uid);
                case ISSUE -> code = service.createIssue(date, site().id(), receiverName(), txtChucVu.getText(), lines, uid);
                default -> code = service.createReturn(date, site().id(), returnerName(), txtChucVu.getText(), lines, uid);
            }
            Ui.info(this, "Đã lưu phiếu " + code + (mode == Mode.ISSUE ? " (người nhận: " + receiverName().trim() + " – " + txtChucVu.getText() + ")" : "")
                    + " và cập nhật tồn kho.");
            reset();
            loadCombos();
        });
    }

    private String returnerName() {
        String o = cboNguoiTra.getValue();
        return o == null ? "" : o;
    }

    private Worksite site() {
        Worksite w = cboCongTrinh.getValue();
        if (w == null) throw new BusinessException("Chưa chọn công trình.");
        return w;
    }

    private void reset() {
        lines.clear();
        table.setRowCount(0);
        txtNguon.setText("");
        if (mode == Mode.RECEIPT) txtChucVu.setText("");
        else updateManager();
        if (mode == Mode.ISSUE && !cboNguoiNhan.getItems().isEmpty()) cboNguoiNhan.getSelectionModel().selectFirst();
        if (mode == Mode.RETURN && !cboNguoiTra.getItems().isEmpty()) cboNguoiTra.getSelectionModel().selectFirst();
        spnSoLuong.getValueFactory().setValue(1);
    }
}
