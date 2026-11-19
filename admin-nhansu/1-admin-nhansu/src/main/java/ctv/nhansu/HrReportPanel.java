package ctv.nhansu;

import ctv.congtrinh.SiteService;
import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.util.Exporter;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

/** Báo cáo tổng hợp nhân lực (chức năng 1.4): thống kê theo kỹ năng / bậc thợ / trạng thái / loại, thiếu hụt theo công trình, xuất Excel / PDF. */
public class HrReportPanel extends BorderPane {
    private static final String[] DIMENSIONS = {"Kỹ năng", "Bậc thợ", "Trạng thái", "Loại công nhân"};

    private final HrService service = new HrService();
    private final SiteService siteService = new SiteService();
    private final ComboBox<String> cboTieuChi = Ui.combo(List.of(DIMENSIONS));
    private final Label lblTong = new Label("0"), lblDangLam = new Label("0"),
            lblTamNghi = new Label("0"), lblNghiViec = new Label("0");
    private final DataTable table = Ui.table("Nhóm thống kê", "Số công nhân");

    // Dữ liệu của báo cáo đang hiển thị (để xuất file đúng như đang xem).
    private String title = "";
    private List<String> headers = List.of();
    private List<List<Object>> rows = List.of();

    public HrReportPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Button btnXem = Ui.primary("Xem báo cáo");
        Button btnThieu = Ui.secondary("Thiếu hụt theo công trình");
        Button btnThieuKyNang = Ui.secondary("Thiếu hụt theo kỹ năng");
        Button btnExcel = Ui.primary("Xuất Excel");
        Button btnPdf = Ui.secondary("Xuất PDF");
        Ui.size(cboTieuChi, 200, 34);
        cboTieuChi.setMinWidth(200);
        cboTieuChi.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox.setHgrow(cboTieuChi, javafx.scene.layout.Priority.ALWAYS);

        // Ô chọn tiêu chí giãn hết chỗ trống, các nút nằm sát bên phải
        var bar = new javafx.scene.layout.HBox(8, Ui.captionBold("Tiêu chí thống kê:"), cboTieuChi, btnXem, btnThieu, btnThieuKyNang);
        bar.setAlignment(Pos.CENTER_LEFT);
        Responsive.Grid cards = new Responsive.Grid(4, 12, 215, 0);
        cards.add(Ui.statCard("users", Ui.CARD_ACCENTS[0], "Tổng công nhân", lblTong, Ui.CARD_COLORS[0]));
        cards.add(Ui.statCard("circle-check", Ui.CARD_ACCENTS[2], "Đang làm việc", lblDangLam, Ui.CARD_COLORS[2]));
        cards.add(Ui.statCard("calendar", Ui.CARD_ACCENTS[3], "Tạm nghỉ", lblTamNghi, Ui.CARD_COLORS[3]));
        cards.add(Ui.statCard("x", Ui.CARD_ACCENTS[1], "Đã nghỉ việc", lblNghiViec, Ui.CARD_COLORS[1]));

        root.north(Ui.stack(
                Ui.header("bar-chart-3", "Báo cáo nhân lực", "Thống kê, phân tích nhân lực và xuất báo cáo", btnExcel, btnPdf),
                cards, Ui.card(bar)));
        root.center(Ui.scroll(table));

        btnXem.setOnAction(e -> viewSummary());
        cboTieuChi.setOnAction(e -> viewSummary());
        btnThieu.setOnAction(e -> viewShortage());
        btnThieuKyNang.setOnAction(e -> viewShortageBySkill());
        btnExcel.setOnAction(e -> export(false));
        btnPdf.setOnAction(e -> export(true));
        viewSummary();
    }

    private void show(String reportTitle, List<String> cols, List<Object[]> data) {
        title = reportTitle;
        headers = cols;
        List<List<Object>> out = new ArrayList<>();
        table.setColumns(cols.toArray(new String[0]));
        for (Object[] r : data) {
            table.addRow(r);
            out.add(java.util.Arrays.asList(r));
        }
        rows = out;
    }

    private void viewSummary() {
        Ui.safe(this, () -> {
            String dim = cboTieuChi.getValue();
            show("BÁO CÁO NHÂN LỰC THEO " + dim.toUpperCase(), List.of("Nhóm thống kê", "Số công nhân"), service.countBy(dim));
            Map<String, Integer> c = service.statusCounts();
            int working = c.getOrDefault("WORKING", 0), leave = c.getOrDefault("ON_LEAVE", 0), resigned = c.getOrDefault("RESIGNED", 0);
            lblTong.setText(String.valueOf(working + leave + resigned));
            lblDangLam.setText(String.valueOf(working));
            lblTamNghi.setText(String.valueOf(leave));
            lblNghiViec.setText(String.valueOf(resigned));
        });
    }

    private void viewShortage() {
        Ui.safe(this, () -> {
            List<Object[]> data = new ArrayList<>();
            for (Object[] r : siteService.shortage(null))
                data.add(new Object[]{r[0], r[1], Ui.fmt((LocalDate) r[2]) + " - " + Ui.fmt((LocalDate) r[3]), r[4], r[5], r[6], r[7], r[8], r[9]});
            show("BÁO CÁO THIẾU HỤT NHÂN LỰC THEO CÔNG TRÌNH",
                    List.of("Mã CT", "Công trình", "Thời gian", "Giai đoạn", "Kỹ năng", "Bậc tối thiểu", "Cần", "Đã phân công", "Thiếu"), data);
        });
    }

    /**
     * Thiếu hụt gộp theo từng kỹ năng, cộng từ các dòng nhu cầu của mọi công trình (cùng nguồn với báo cáo theo công trình).
     * "Thiếu" của mỗi dòng nhu cầu đã được tính riêng (không âm) rồi mới cộng, nên dòng dư người không bù cho dòng thiếu.
     */
    private void viewShortageBySkill() {
        Ui.safe(this, () -> {
            class Acc { int lines, need, assigned, missing; java.util.Set<Object> sites = new java.util.HashSet<>(); }
            Map<String, Acc> bySkill = new java.util.TreeMap<>();
            for (Object[] r : siteService.shortage(null)) {
                Acc a = bySkill.computeIfAbsent(String.valueOf(r[5]), k -> new Acc());
                int need = (Integer) r[7], assigned = (Integer) r[8], missing = (Integer) r[9];
                a.lines++; a.need += need; a.assigned += assigned; a.missing += missing;
                if (missing > 0) a.sites.add(r[0]);
            }
            List<Map.Entry<String, Acc>> sorted = new ArrayList<>(bySkill.entrySet());
            sorted.sort((x, y) -> y.getValue().missing != x.getValue().missing
                    ? Integer.compare(y.getValue().missing, x.getValue().missing) : x.getKey().compareTo(y.getKey()));
            List<Object[]> data = new ArrayList<>();
            int tl = 0, tn = 0, ta = 0, tm = 0;
            for (Map.Entry<String, Acc> e : sorted) {
                Acc a = e.getValue();
                data.add(new Object[]{e.getKey(), a.lines, a.sites.size(), a.need, a.assigned, a.missing});
                tl += a.lines; tn += a.need; ta += a.assigned; tm += a.missing;
            }
            if (!data.isEmpty()) data.add(new Object[]{"Tổng cộng", tl, "", tn, ta, tm});
            show("BÁO CÁO THIẾU HỤT NHÂN LỰC THEO KỸ NĂNG",
                    List.of("Kỹ năng", "Số dòng nhu cầu", "Số công trình thiếu", "Tổng cần", "Đã phân công", "Thiếu"), data);
        });
    }

    private void export(boolean pdf) {
        if (rows.isEmpty()) { Ui.error(this, "Chưa có dữ liệu để xuất. Hãy bấm \"Xem báo cáo\" trước."); return; }
        String ext = pdf ? ".pdf" : ".xlsx";
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bao-cao-nhan-luc" + ext);
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getPath() + ext);
        File out = f;
        Ui.safe(this, () -> {
            if (pdf) Exporter.toPdf(out, title, headers, rows);
            else Exporter.toExcel(out, title, headers, rows);
            Ui.info(this, "Đã xuất báo cáo: " + out.getAbsolutePath());
        });
    }
}
