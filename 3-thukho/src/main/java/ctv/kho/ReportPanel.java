package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.kho.ReportDAO.Result;
import ctv.model.Models.Option;
import ctv.util.Labels;
import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** frmBaoCaoTonKho (Form 05 – 5.21) – chức năng 3.4.1 – 3.4.9. */
public class ReportPanel extends BorderPane {
    private enum Kind {
        BY_TYPE("Thống kê theo loại"), WAREHOUSE("Thiết bị trong kho"), WORKSITE("Thiết bị tại công trình"),
        BY_CONDITION("Thống kê theo tình trạng"), RECEIPT("Báo cáo nhập kho"), ISSUE("Báo cáo xuất thiết bị"),
        RETURN("Báo cáo thu hồi thiết bị"), SUMMARY("Tổng hợp tồn kho hiện tại");
        final String label;
        Kind(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private record Item<T>(T value, String label) { @Override public String toString() { return label; } }

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"));

    private final ReportService service = new ReportService();
    private final LookupDAO lookup = new LookupDAO();
    private final ComboBox<Kind> cboLoaiBaoCao = Ui.combo(List.of(Kind.values()));
    private final ComboBox<Item<String>> cboTinhTrang = new ComboBox<>();
    private final ComboBox<Item<Integer>> cboKho = new ComboBox<>();
    private final ComboBox<Item<Integer>> cboLoaiThietBi = new ComboBox<>();
    private final ComboBox<Item<Integer>> cboCongTrinh = new ComboBox<>();
    private final DatePicker dtpTuNgay = Ui.datePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker dtpDenNgay = Ui.datePicker(LocalDate.now());
    private final FlowPane extraRow = Ui.flow(Pos.CENTER_LEFT);
    private final Label lblCongTrinh = Ui.caption("Công trình"), lblTu = Ui.caption("Từ ngày"), lblDen = Ui.caption("Đến ngày");
    private final DataTable table = Ui.table();
    private final Label lblTong = new Label("0"), lblKho = new Label("0"), lblSite = new Label("0"), lblBaoTri = new Label("0");
    private Result last;

    public ReportPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnXem = Ui.primary("Xem báo cáo"), btnExcel = Ui.secondary("Xuất Excel"), btnPdf = Ui.primary("Xuất PDF");
        Ui.translateCells(cboTinhTrang);
        Ui.translateCells(cboKho);
        Ui.translateCells(cboLoaiThietBi);
        Ui.translateCells(cboCongTrinh);

        // Form 05: Thống kê theo loại | Tất cả tình trạng | Tất cả kho | Xem báo cáo
        FlowPane row1 = Ui.flow(Pos.CENTER_LEFT, cboLoaiBaoCao, cboLoaiThietBi, cboTinhTrang, cboKho, btnXem);
        for (ComboBox<?> c : List.of(cboLoaiBaoCao, cboLoaiThietBi, cboTinhTrang, cboKho)) Ui.size(c, 190, 34);
        // Các ô lọc phụ chỉ hiện khi loại báo cáo cần đến (công trình, khoảng ngày)
        Ui.size(dtpTuNgay, 140, 34);
        Ui.size(dtpDenNgay, 140, 34);
        Ui.size(cboCongTrinh, 220, 34);
        extraRow.getChildren().addAll(lblCongTrinh, cboCongTrinh, lblTu, dtpTuNgay, lblDen, dtpDenNgay);
        VBox rows = new VBox(8, row1, extraRow);

        Responsive.Grid cards = new Responsive.Grid(4, 12, 215, 0);
        cards.add(Ui.statCard("Tổng thiết bị", lblTong, Ui.CARD_COLORS[0]));
        cards.add(Ui.statCard("Trong kho", lblKho, Ui.CARD_COLORS[2]));
        cards.add(Ui.statCard("Tại công trình", lblSite, Ui.CARD_COLORS[1]));
        cards.add(Ui.statCard("Cần bảo trì", lblBaoTri, Ui.CARD_COLORS[3]));

        root.north(Ui.stack(
                Ui.header("bar-chart-3", "Báo cáo tồn kho", "Thống kê thiết bị trong kho và tại công trình", btnExcel, btnPdf),
                cards, Ui.card(rows)));
        root.center(Ui.scroll(table));

        loadFilters();
        cboLoaiBaoCao.setOnAction(e -> { updateFilters(); view(); });
        btnXem.setOnAction(e -> view());
        btnExcel.setOnAction(e -> export(false));
        btnPdf.setOnAction(e -> export(true));
        updateFilters();
        view();
    }

    private static void show(Node n, boolean on) { n.setVisible(on); n.setManaged(on); }

    /** Bật / tắt / hiện các ô lọc theo loại báo cáo đang chọn. */
    private void updateFilters() {
        Kind k = cboLoaiBaoCao.getValue();
        cboKho.setDisable(k != Kind.WAREHOUSE);
        cboLoaiThietBi.setDisable(k != Kind.BY_TYPE);
        cboTinhTrang.setDisable(k != Kind.BY_CONDITION);
        boolean site = k == Kind.WORKSITE || k == Kind.ISSUE || k == Kind.RETURN;
        boolean dates = k == Kind.RECEIPT || k == Kind.ISSUE || k == Kind.RETURN;
        show(lblCongTrinh, site); show(cboCongTrinh, site);
        show(lblTu, dates); show(dtpTuNgay, dates);
        show(lblDen, dates); show(dtpDenNgay, dates);
        show(extraRow, site || dates);
    }

    private void loadFilters() {
        Ui.safe(this, () -> {
            fill(cboKho, "Tất cả kho", lookup.warehouses().stream().map(w -> new Item<>(w.id(), w.name())).toList());
            fill(cboLoaiThietBi, "Tất cả loại thiết bị", lookup.types().stream().map(t -> new Item<>(t.id(), t.name())).toList());
            fill(cboCongTrinh, "Tất cả công trình", lookup.worksites().stream().map(w -> new Item<>(w.id(), w.toString())).toList());
            List<Item<String>> m = new ArrayList<>();
            m.add(new Item<>(null, "Tất cả tình trạng"));
            for (Option o : Labels.STATUSES) m.add(new Item<>(o.code(), o.label()));
            cboTinhTrang.getItems().setAll(m);
            cboTinhTrang.getSelectionModel().selectFirst();
        });
    }

    private static <T> void fill(ComboBox<Item<T>> c, String all, List<Item<T>> items) {
        List<Item<T>> m = new ArrayList<>();
        m.add(new Item<>(null, all));
        m.addAll(items);
        c.getItems().setAll(m);
        c.getSelectionModel().selectFirst();
    }

    private static <T> T valueOf(ComboBox<Item<T>> c) {
        return c.getValue() == null ? null : c.getValue().value();
    }

    private static Integer id(ComboBox<Item<Integer>> c) { return valueOf(c); }

    private void view() {
        Ui.safe(this, () -> {
            LocalDate from = Ui.dateOf(dtpTuNgay), to = Ui.dateOf(dtpDenNgay);
            Result r = switch (cboLoaiBaoCao.getValue()) {
                case BY_TYPE -> service.byType(id(cboLoaiThietBi));
                case WAREHOUSE -> service.stockInWarehouse(id(cboKho));
                case WORKSITE -> service.stockAtWorksite(id(cboCongTrinh));
                case BY_CONDITION -> service.byCondition(valueOf(cboTinhTrang));
                case RECEIPT -> service.receipts(from, to);
                case ISSUE -> service.issues(from, to, id(cboCongTrinh));
                case RETURN -> service.returns(from, to, id(cboCongTrinh));
                case SUMMARY -> service.currentSummary();
            };
            last = r;
            // thêm cột số thứ tự "#" ở đầu bảng (không đánh số dòng "Tổng cộng")
            List<String> headers = new ArrayList<>();
            headers.add("#");
            for (Object h : r.headers()) headers.add(String.valueOf(h));
            table.setColumns(headers.toArray(new String[0]));
            int n = 1;
            for (List<Object> row : r.rows()) {
                List<Object> withIndex = new ArrayList<>();
                withIndex.add("Tổng cộng".equals(row.get(0)) ? "" : (Object) n++);
                withIndex.addAll(row);
                table.addRow(withIndex.toArray());
            }
            refreshCards();
        });
    }

    /** Thẻ thống kê lấy từ bảng tổng hợp hiện tại, độc lập với báo cáo đang xem. */
    private void refreshCards() {
        Ui.safe(this, () -> {
            Result s = service.currentSummary();
            long inStore = 0, atSite = 0, total = 0;
            for (List<Object> row : s.rows()) {
                if ("Tổng cộng".equals(row.get(0))) continue;
                inStore += ((Number) row.get(3)).longValue();
                atSite += ((Number) row.get(4)).longValue();
                long q = ((Number) row.get(5)).longValue();
                total += q;
            }
            lblTong.setText(NUM.format(total));
            lblKho.setText(NUM.format(inStore));
            lblSite.setText(NUM.format(atSite));
            lblBaoTri.setText(NUM.format(service.maintenanceQuantity()));
        });
    }

    private void export(boolean pdf) {
        if (last == null) { Ui.error(this, "Hãy bấm \"Xem báo cáo\" trước khi xuất."); return; }
        String ext = pdf ? ".pdf" : ".xlsx";
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bao-cao-ton-kho" + ext);
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getPath() + ext);
        File out = f;
        Ui.safe(this, () -> {
            service.export(last, out, pdf);
            Ui.info(this, "Đã xuất báo cáo: " + out.getAbsolutePath());
        });
    }
}
