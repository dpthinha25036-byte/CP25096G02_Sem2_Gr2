package ctv.kho;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.kho.ReportDAO.Result;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

/** frmLichSuGiaoDich (Form 05 – 5.22) – tra cứu phiếu nhập, xuất, thu hồi (3.4.5 – 3.4.7). */
public class HistoryPanel extends BorderPane {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportService service = new ReportService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo số phiếu hoặc tên thiết bị...");
    private final ComboBox<String> cboLoaiPhieu = Ui.combo(List.of("Tất cả loại phiếu", "Nhập kho", "Xuất", "Thu hồi"));
    private final TextField txtKhoangNgay = Ui.text(22);
    private final DataTable table = Ui.table(
            "#", "Số phiếu", "Loại", "Ngày", "Thiết bị", "Số lượng", "Công trình / Nguồn", "Người lập");
    private LocalDate from = LocalDate.now().withDayOfMonth(1), to = LocalDate.now();
    private Result last;

    public HistoryPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnExcel = Ui.primary("Xuất Excel");
        Button btnTim = Ui.primary("Tìm kiếm");

        txtKhoangNgay.setEditable(false);
        txtKhoangNgay.setCursor(Cursor.HAND);
        showRange();
        Ui.size(cboLoaiPhieu, 170, Ui.TOOLBAR_H);
        Ui.size(txtKhoangNgay, 300, Ui.TOOLBAR_H);

        // Form 05: ô tìm kiếm | Tất cả loại phiếu | Từ … đến … | Tìm kiếm
        Node bar = Ui.searchBar(txtTimKiem, cboLoaiPhieu, txtKhoangNgay, btnTim);

        root.north(Ui.stack(
                Ui.header("history", "Lịch sử giao dịch", "Tra cứu phiếu nhập, xuất và thu hồi thiết bị", btnLamMoi, btnExcel),
                bar));
        root.center(Ui.scroll(table));
        table.installBadge(2);

        txtKhoangNgay.setOnMouseClicked(e -> chooseRange());
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showDetail(); });
        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboLoaiPhieu.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> {
            txtTimKiem.setText("");
            cboLoaiPhieu.getSelectionModel().selectFirst();
            from = LocalDate.now().withDayOfMonth(1);
            to = LocalDate.now();
            showRange();
            reload();
        });
        btnExcel.setOnAction(e -> export());
        visibleProperty().addListener((o, was, now) -> { if (now) reload(); });   // mở lại màn hình → cập nhật phiếu mới
        reload();
    }

    private void showRange() {
        txtKhoangNgay.setText(ctv.util.I18n.t("Từ") + " " + from.format(FMT) + " " + ctv.util.I18n.t("đến") + " " + to.format(FMT));
    }

    private void chooseRange() {
        DatePicker f = Ui.datePicker(from), t = Ui.datePicker(to);
        GridPane p = Ui.form(new Node[]{Ui.required("Từ ngày"), Ui.required("Đến ngày")}, new Node[]{f, t});
        Ui.dialog(this, "Khoảng thời gian", p, () -> {
            from = Ui.dateOf(f);
            to = Ui.dateOf(t);
            showRange();
            reload();
        });
    }

    private void reload() {
        Ui.safe(this, () -> {
            String type = cboLoaiPhieu.getSelectionModel().getSelectedIndex() <= 0 ? null : cboLoaiPhieu.getValue();
            last = service.history(from, to, type, txtTimKiem.getText());
            table.setRowCount(0);
            int i = 1;
            for (List<Object> row : last.rows()) {
                List<Object> r = new ArrayList<>();
                r.add(i++);
                r.addAll(row);
                table.addRow(r.toArray());
            }
        });
    }

    /** Nhấp đúp một dòng: liệt kê toàn bộ thiết bị của phiếu đó. */
    private void showDetail() {
        int r = table.getSelectedRow();
        if (r < 0 || last == null) return;
        Object code = table.getValueAt(table.convertRowIndexToModel(r), 1);
        StringBuilder sb = new StringBuilder("Phiếu " + code + "\n\n");
        for (List<Object> row : last.rows())
            if (code.equals(row.get(0))) sb.append("• ").append(row.get(3)).append(" – SL ").append(row.get(4)).append("\n");
        Ui.info(this, sb.toString());
    }

    private void export() {
        if (last == null || last.rows().isEmpty()) { Ui.error(this, "Chưa có dữ liệu để xuất."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("lich-su-giao-dich.xlsx");
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        File out = f.getName().toLowerCase().endsWith(".xlsx") ? f : new File(f.getPath() + ".xlsx");
        Ui.safe(this, () -> {
            service.export(last, out, false);
            Ui.info(this, "Đã xuất danh sách: " + out.getAbsolutePath());
        });
    }
}
