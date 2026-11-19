package ctv.congtrinh;

import ctv.congtrinh.AttendanceDAO.Attendance;
import ctv.congtrinh.AttendanceDAO.Result;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Ui;
import ctv.util.BusinessException;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

/** Màn hình 2.5–2.6: chấm công hằng ngày, lịch sử, kiểm tra và báo cáo. */
public class AttendancePanel extends BorderPane {
    private record WorkerItem(int id, String code, String name, String status) {
        @Override public String toString() { return code + " - " + name + " (" + status + ")"; }
    }
    private record SiteItem(int id, String code, String name) {
        @Override public String toString() { return code + " - " + name; }
    }
    private record StatusItem(String code, String label) {
        @Override public String toString() { return label; }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final AttendanceService service = new AttendanceService();
    private final TextField keyword = Ui.searchPlain(24, "Tìm mã/tên công nhân hoặc công trình...");
    private final DatePicker from = Ui.datePicker(LocalDate.now().withDayOfMonth(1)), to = Ui.datePicker(LocalDate.now());
    private final ComboBox<SiteItem> siteFilter = new ComboBox<>();
    private final ComboBox<StatusItem> statusFilter = Ui.combo(List.of(new StatusItem(null, "Tất cả trạng thái"),
            new StatusItem("VALID", "Hợp lệ"), new StatusItem("WARNING", "Cảnh báo"), new StatusItem("INVALID", "Không hợp lệ")));
    private final DataTable table = Ui.table("#", "Mã CN", "Công nhân", "Công trình", "Ngày", "Check-in", "Check-out", "Ca thường",
            "Ca tăng ca", "Đơn giá thường", "Đơn giá tăng ca", "Trạng thái", "Ghi chú", "Thao tác");
    private List<Attendance> current = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();
    private Result last;

    public AttendancePanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);
        Ui.translateCells(siteFilter);
        Ui.size(siteFilter, 210, Ui.TOOLBAR_H);
        Ui.size(from, 140, Ui.TOOLBAR_H);
        Ui.size(to, 140, Ui.TOOLBAR_H);
        Button search = Ui.primary("Tìm kiếm", "search"), refresh = Ui.secondary("Làm mới", "refresh-cw"),
                add = Ui.primary("Thêm chấm công", "plus"), check = Ui.secondary("Kiểm tra dữ liệu"),
                report = Ui.secondary("Báo cáo tuần / công trình"), excel = Ui.secondary("Xuất Excel"), pdf = Ui.secondary("Xuất PDF");
        Node filters = Ui.searchBar(keyword, siteFilter, from, to, statusFilter, search);
        root.north(Ui.stack(Ui.header("calendar-check", "Chấm công hằng ngày", "Ghi nhận giờ làm, số ca và đơn giá tại ngày làm việc",
                refresh, add, check, report, excel, pdf), filters));
        root.center(Ui.scroll(table));
        table.installBadge(11);
        table.installActions(13, false, () -> { Attendance a = selected(); if (a != null) edit(a); }, this::delete,
                idx -> idx < current.size() && mine.contains(current.get(idx).id()));   // chỉ người chấm công mới được sửa / xóa
        loadSites();
        search.setOnAction(e -> reload());
        keyword.setOnAction(e -> reload());
        statusFilter.setOnAction(e -> reload());
        siteFilter.setOnAction(e -> reload());
        refresh.setOnAction(e -> {
            keyword.setText("");
            Ui.setDate(from, LocalDate.now().withDayOfMonth(1));
            Ui.setDate(to, LocalDate.now());
            statusFilter.getSelectionModel().selectFirst();
            siteFilter.getSelectionModel().selectFirst();
            reload();
        });
        add.setOnAction(e -> edit(null));
        check.setOnAction(e -> validateData());
        report.setOnAction(e -> showReport());
        excel.setOnAction(e -> export(false));
        pdf.setOnAction(e -> export(true));
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) showHistory(); });
        reload();
    }

    private void loadSites() {
        Ui.safe(this, () -> {
            List<SiteItem> items = new ArrayList<>();
            items.add(new SiteItem(0, "", "Tất cả công trình"));
            for (Object[] r : service.sites()) items.add(new SiteItem((Integer) r[0], (String) r[1], (String) r[2]));
            siteFilter.getItems().setAll(items);
            siteFilter.getSelectionModel().selectFirst();
        });
    }

    private Integer selectedSite() {
        SiteItem s = siteFilter.getValue();
        return s == null || s.id() == 0 ? null : s.id();
    }

    private void reload() {
        Ui.safe(this, () -> {
            StatusItem st = statusFilter.getValue();
            current = service.search(keyword.getText(), selectedSite(), Ui.dateOf(from), Ui.dateOf(to), st == null ? null : st.code());
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.ATTENDANCE, current, Attendance::id);
            table.setRowCount(0);
            int n = 1;
            for (Attendance a : current)
                table.addRow(n++, a.workerCode(), a.workerName(), a.worksiteCode() + " - " + a.worksiteName(), Ui.fmt(a.workDate()),
                        fmt(a.checkIn()), fmt(a.checkOut()), a.regular(), a.overtime(), Ui.money(a.regularRate()),
                        Ui.money(a.overtimeRate()), status(a.status()), a.note(), "");
        });
    }

    private Attendance selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn bản ghi chấm công."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private static String fmt(LocalTime t) { return t == null ? "" : t.format(TIME); }

    private static String status(String s) {
        return switch (s) {
            case "VALID" -> "Hợp lệ";
            case "WARNING" -> "Cảnh báo";
            case "INVALID" -> "Không hợp lệ";
            default -> s;
        };
    }

    private void edit(Attendance a) {
        List<WorkerItem> workers = new ArrayList<>();
        for (Object[] r : service.workers()) workers.add(new WorkerItem((Integer) r[0], (String) r[1], (String) r[2], (String) r[3]));
        List<SiteItem> sites = new ArrayList<>();
        for (Object[] r : service.sites()) sites.add(new SiteItem((Integer) r[0], (String) r[1], (String) r[2]));
        ComboBox<WorkerItem> w = Ui.combo(workers);
        ComboBox<SiteItem> s = Ui.combo(sites);
        DatePicker day = DatePopup.field(a == null ? LocalDate.now() : a.workDate(), false);
        TextField in = Ui.text(8), out = Ui.text(8), reg = Ui.text(8), ot = Ui.text(8);
        if (a != null) {
            for (WorkerItem x : workers) if (x.id() == a.workerId()) w.setValue(x);
            for (SiteItem x : sites) if (x.id() == a.worksiteId()) s.setValue(x);
            in.setText(fmt(a.checkIn()));
            out.setText(fmt(a.checkOut()));
            reg.setText(a.regular().toPlainString());
            ot.setText(a.overtime().toPlainString());
        } else {
            reg.setText("1");
            ot.setText("0");
        }
        GridPane f = Ui.form(2, 130, new Node[]{Ui.required("Công nhân"), Ui.required("Công trình"), Ui.required("Ngày làm việc"),
                Ui.caption("Giờ check-in (HH:mm)"), Ui.caption("Giờ check-out (HH:mm)"), Ui.required("Số ca thường"), Ui.required("Số ca tăng ca")},
                new Node[]{w, s, day, in, out, reg, ot});
        Ui.dialog(this, a == null ? "Thêm bản ghi chấm công" : "Sửa bản ghi chấm công", f, () -> {
            service.save(a == null ? null : a.id(), w.getValue().id(), s.getValue().id(), Ui.parseDate(day, "Ngày làm việc", true),
                    parseTime(in.getText()), parseTime(out.getText()),
                    new BigDecimal(reg.getText().trim().replace(',', '.')), new BigDecimal(ot.getText().trim().replace(',', '.')));
            reload();
        });
    }

    private static LocalTime parseTime(String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalTime.parse(v.trim(), TIME); } catch (Exception e) { throw new BusinessException("Giờ phải đúng định dạng HH:mm."); }
    }

    private void delete() {
        Attendance a = selected();
        if (a == null || !Ui.confirm(this, "Xóa chấm công " + a.workerCode() + " ngày " + Ui.fmt(a.workDate()) + "?")) return;
        Ui.safe(this, () -> { service.delete(a.id()); reload(); });
    }

    private void validateData() {
        Ui.safe(this, () -> {
            int n = service.validate(Ui.dateOf(from), Ui.dateOf(to));
            Ui.info(this, "Đã kiểm tra " + n + " bản ghi. Trạng thái đã được cập nhật.");
            reload();
        });
    }

    private void showHistory() {
        Attendance a = selected();
        if (a == null) return;
        DataTable t = Ui.table("Thời điểm", "Thao tác", "Giá trị cũ", "Giá trị mới");
        for (Object[] r : new AttendanceDAO().auditHistory(a.id())) t.addRow(r);
        Ui.showContent(this, "Lịch sử chỉnh sửa chấm công", Ui.scroll(t), 700, 300);
    }

    private void showReport() {
        ComboBox<String> kind = Ui.combo(List.of("Tổng hợp chấm công theo tuần", "Báo cáo theo công trình", "Lịch sử chấm công"));
        DatePicker fDate = Ui.datePicker(Ui.dateOf(from)), tDate = Ui.datePicker(Ui.dateOf(to));
        List<SiteItem> sites = new ArrayList<>();
        sites.add(new SiteItem(0, "", "Tất cả công trình"));
        for (Object[] x : service.sites()) sites.add(new SiteItem((Integer) x[0], (String) x[1], (String) x[2]));
        ComboBox<SiteItem> reportSite = Ui.combo(sites);
        List<WorkerItem> workers = new ArrayList<>();
        workers.add(new WorkerItem(0, "", "Tất cả công nhân", ""));
        for (Object[] x : service.workers()) workers.add(new WorkerItem((Integer) x[0], (String) x[1], (String) x[2], (String) x[3]));
        ComboBox<WorkerItem> reportWorker = Ui.combo(workers);
        GridPane f = Ui.form(2, new Node[]{Ui.required("Loại báo cáo"), Ui.required("Từ ngày"), Ui.required("Đến ngày"),
                Ui.caption("Công trình (nếu cần)"), Ui.caption("Công nhân (nếu cần)")},
                new Node[]{kind, fDate, tDate, reportSite, reportWorker});
        Ui.dialog(this, "Chọn báo cáo chấm công", f, () -> {
            SiteItem si = reportSite.getValue();
            Integer sid = si == null || si.id() == 0 ? null : si.id();
            WorkerItem wi = reportWorker.getValue();
            Integer wid = wi == null || wi.id() == 0 ? null : wi.id();
            LocalDate a = Ui.dateOf(fDate), b = Ui.dateOf(tDate);
            last = switch (kind.getSelectionModel().getSelectedIndex()) {
                case 0 -> service.weekly(a, b, wid);
                case 1 -> service.byWorksite(a, b, sid);
                default -> service.history(a, b, wid, sid);
            };
            showResult(last);
        });
    }

    private void showResult(Result r) {
        DataTable t = Ui.table(r.headers().toArray(new String[0]));
        for (var row : r.rows()) t.addRow(row.toArray());
        Ui.showContent(this, r.title(), Ui.scroll(t), 900, 420);
    }

    private void export(boolean pdf) {
        if (last == null) { Ui.error(this, "Hãy mở báo cáo trước khi xuất."); return; }
        String ext = pdf ? ".pdf" : ".xlsx";
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bao-cao-cham-cong" + ext);
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getPath() + ext);
        File out = f;
        Ui.safe(this, () -> { service.export(last, out, pdf); Ui.info(this, "Đã xuất: " + out.getAbsolutePath()); });
    }
}
