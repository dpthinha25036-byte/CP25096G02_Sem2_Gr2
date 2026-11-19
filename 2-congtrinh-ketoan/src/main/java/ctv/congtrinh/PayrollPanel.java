package ctv.congtrinh;

import ctv.congtrinh.PayrollDAO.PayrollRow;
import ctv.congtrinh.PayrollDAO.Period;
import ctv.congtrinh.PayrollDAO.Result;
import ctv.congtrinh.PayrollDAO.Unlock;
import ctv.congtrinh.SiteModels.Site;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Ui;
import ctv.util.Session;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import javafx.stage.FileChooser;

/** 2.7–2.8: kỳ lương Thứ Năm–Thứ Tư, tính dự kiến, chốt/khóa sổ, yêu cầu và duyệt mở khóa. */
public class PayrollPanel extends BorderPane {
    private final PayrollService service = new PayrollService();
    private final SiteService siteService = new SiteService();
    private final ComboBox<Period> periods = Ui.combo(List.of());
    private final DataTable table = Ui.table("#", "Mã CN", "Họ tên", "Ca thường", "Ca tăng ca", "Tổng tiền công", "Đã trả", "Còn lại", "Trạng thái thanh toán");
    private List<PayrollRow> current = List.of();
    private Result last;

    public PayrollPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);
        Ui.size(periods, 330, 34);
        Button refresh = Ui.secondary("Làm mới"), create = Ui.primary("Tạo kỳ lương", "plus"), calc = Ui.secondary("Tính bảng lương"),
                close = Ui.primary("Chốt & khóa kỳ"), request = Ui.secondary("Yêu cầu mở khóa"), approve = Ui.secondary("Duyệt yêu cầu mở khóa"),
                reports = Ui.secondary("Báo cáo chi phí / thu nhập"), excel = Ui.secondary("Xuất Excel"), pdf = Ui.secondary("Xuất PDF");
        root.north(Ui.stack(Ui.header("lock", "Tổng hợp và chốt bảng lương",
                "Tính theo đơn giá đã lưu tại ngày chấm công; kỳ lương từ Thứ Năm đến Thứ Tư",
                refresh, create, calc, close, request, approve, reports, excel, pdf),
                Ui.card(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Kỳ lương:"), periods))));
        root.center(Ui.scroll(table));
        visible(create, Session.can("2.7"));
        visible(calc, Session.can("2.7"));
        visible(close, Session.can("2.8"));
        visible(request, Session.can("2.8"));
        visible(approve, Session.can("2.10"));
        periods.setOnAction(e -> reloadRows());
        refresh.setOnAction(e -> loadPeriods());
        create.setOnAction(e -> create());
        calc.setOnAction(e -> calculate());
        close.setOnAction(e -> close());
        request.setOnAction(e -> requestUnlock());
        approve.setOnAction(e -> approveUnlock());
        reports.setOnAction(e -> reports());
        excel.setOnAction(e -> export(false));
        pdf.setOnAction(e -> export(true));
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) detail(); });
        loadPeriods();
    }

    private static void visible(Node n, boolean on) { n.setVisible(on); n.setManaged(on); }

    private Period selected() { return periods.getValue(); }

    private void loadPeriods() {
        Ui.safe(this, () -> {
            Period keep = selected();
            List<Period> all = service.periods();
            periods.getItems().setAll(all);
            periods.getSelectionModel().selectFirst();
            if (keep != null) for (Period p : all) if (p.id() == keep.id()) periods.setValue(p);
            reloadRows();
        });
    }

    private void reloadRows() {
        Period p = selected();
        table.setRowCount(0);
        current = List.of();
        if (p == null) return;
        Ui.safe(this, () -> {
            current = service.rows(p.id());
            int i = 1;
            for (PayrollRow r : current)
                table.addRow(i++, r.code(), r.name(), r.regular(), r.overtime(), Ui.money(r.total()), Ui.money(r.paid()),
                        Ui.money(r.remaining()), paymentLabel(r.paymentStatus()));
        });
    }

    private static String paymentLabel(String s) {
        return switch (s) {
            case "UNPAID" -> "Chưa thanh toán";
            case "PARTIAL" -> "Thanh toán một phần";
            case "PAID" -> "Đã thanh toán";
            default -> s;
        };
    }

    private void create() {
        DatePicker day = DatePopup.field(LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.THURSDAY)), false);
        GridPane f = Ui.form(new Node[]{Ui.required("Ngày bắt đầu (phải là Thứ Năm)")}, new Node[]{day});
        Ui.dialog(this, "Tạo kỳ lương", f, () -> {
            LocalDate d = Ui.parseDate(day, "Ngày bắt đầu", true);
            int id = service.createPeriod(d);
            loadPeriods();
            for (Period p : periods.getItems()) if (p.id() == id) periods.setValue(p);
            Ui.info(this, "Đã tạo kỳ lương: " + d + " đến " + d.plusDays(6));
        });
    }

    private void calculate() {
        Period p = selected();
        if (p == null) { Ui.error(this, "Hãy tạo hoặc chọn kỳ lương."); return; }
        Ui.safe(this, () -> { service.calculate(p.id()); reloadRows(); Ui.info(this, "Đã tạo/cập nhật bảng lương dự kiến."); });
    }

    private void close() {
        Period p = selected();
        if (p == null) return;
        if (!Ui.confirm(this, "Chốt bảng lương và khóa dữ liệu chấm công từ " + p.from() + " đến " + p.to() + "?")) return;
        if (!backupBeforeClose()) return;
        Ui.safe(this, () -> { service.close(p.id()); loadPeriods(); Ui.info(this, "Đã chốt và khóa kỳ lương."); });
    }

    /** Form 03 VI.3: sao lưu CSDL trước khi chốt lương. Sao lưu lỗi thì hỏi có chốt tiếp không. */
    private boolean backupBeforeClose() {
        try {
            ctv.util.BackupService.backup();
            return true;
        } catch (RuntimeException e) {
            return Ui.confirm(this, "Không sao lưu được trước khi chốt lương (" + e.getMessage() + "). Vẫn chốt lương?");
        }
    }

    private void requestUnlock() {
        Period p = selected();
        if (p == null) return;
        TextField reason = Ui.text(30);
        GridPane f = Ui.form(new Node[]{Ui.required("Lý do mở khóa")}, new Node[]{reason});
        Ui.dialog(this, "Yêu cầu mở khóa kỳ lương", f, () -> {
            service.requestUnlock(p.id(), reason.getText());
            Ui.info(this, "Đã gửi yêu cầu mở khóa, đang chờ phê duyệt.");
        });
    }

    private void approveUnlock() {
        List<Unlock> all = service.unlocks();
        if (all.isEmpty()) { Ui.info(this, "Chưa có yêu cầu mở khóa."); return; }
        DataTable t = Ui.table("Mã YC", "Kỳ lương", "Người yêu cầu", "Lý do", "Thời điểm", "Trạng thái");
        for (Unlock u : all) t.addRow(u.id(), u.periodLabel(), u.requester(), u.reason(), u.requestedAt(), u.status());
        if (!Ui.chooseContent(this, "Chọn yêu cầu cần phê duyệt", Ui.scroll(t), 820, 300) || t.getSelectedRow() < 0) return;
        Unlock u = all.get(t.convertRowIndexToModel(t.getSelectedRow()));
        if (!"PENDING".equals(u.status())) { Ui.info(this, "Yêu cầu đã được xử lý."); return; }
        int c = Ui.options(this, "Xử lý yêu cầu #" + u.id() + "?", "Phê duyệt mở khóa", "Phê duyệt", "Từ chối", "Hủy");
        if (c < 0 || c == 2) return;
        boolean yes = c == 0;
        Ui.safe(this, () -> { service.decide(u.id(), yes); loadPeriods(); Ui.info(this, yes ? "Đã mở khóa kỳ lương." : "Đã từ chối yêu cầu."); });
    }

    private void detail() {
        Period p = selected();
        int r = table.getSelectedRow();
        if (p == null || r < 0) return;
        PayrollRow row = current.get(table.convertRowIndexToModel(r));
        showResult(service.detail(p.id(), row.workerId()));
    }

    private void reports() {
        Ui.safe(this, () -> {
            ComboBox<String> scope = Ui.combo(List.of("Theo kỳ lương đang chọn", "Theo tháng"));
            ComboBox<String> kind = Ui.combo(List.of("Chi phí theo công trình", "Thu nhập cao nhất", "Thu nhập thấp nhất"));
            LocalDate now = LocalDate.now();
            Spinner<Integer> month = Ui.intSpinner(now.getMonthValue(), 1, 12), year = Ui.intSpinner(now.getYear(), 2000, 2100),
                    count = Ui.intSpinner(10, 1, 100);
            List<Object> choices = new ArrayList<>();
            choices.add("Tất cả công trình");
            choices.addAll(siteService.allSites());
            ComboBox<Object> site = Ui.combo(choices);
            Label periodNote = Ui.caption(selected() == null ? "(chưa chọn kỳ lương)" : selected().toString());
            Runnable sync = () -> {
                boolean byMonth = scope.getSelectionModel().getSelectedIndex() == 1;
                month.setDisable(!byMonth);
                year.setDisable(!byMonth);
                site.setDisable(kind.getSelectionModel().getSelectedIndex() != 0);
                count.setDisable(kind.getSelectionModel().getSelectedIndex() == 0);
            };
            scope.setOnAction(e -> sync.run());
            kind.setOnAction(e -> sync.run());
            sync.run();
            GridPane f = Ui.form(new Node[]{Ui.required("Phạm vi báo cáo"), Ui.caption("Kỳ lương đang chọn"), Ui.required("Tháng (khi chọn theo tháng)"),
                    Ui.required("Năm (khi chọn theo tháng)"), Ui.required("Loại báo cáo"), Ui.caption("Công trình (báo cáo chi phí)"),
                    Ui.caption("Số công nhân cần liệt kê")},
                    new Node[]{scope, periodNote, month, year, kind, site, count});
            if (!Ui.dialog(this, "Báo cáo chi phí / thu nhập", f)) return;
            boolean byMonth = scope.getSelectionModel().getSelectedIndex() == 1;
            Period p = selected();
            if (!byMonth && p == null) { Ui.error(this, "Hãy chọn kỳ lương."); return; }
            java.time.YearMonth ym = byMonth ? java.time.YearMonth.of(year.getValue(), month.getValue()) : null;
            int c = kind.getSelectionModel().getSelectedIndex();
            if (c == 0) {
                Integer sid = site.getValue() instanceof Site s ? s.id() : null;
                last = byMonth ? service.laborCostMonth(ym, sid) : service.laborCost(p.id(), sid);
            } else {
                int n = count.getValue();
                last = byMonth ? service.topMonth(ym, n, c == 1) : service.top(p.id(), n, c == 1);
            }
            showResult(last);
        });
    }

    private void showResult(Result r) {
        DataTable t = Ui.table(r.headers().toArray(new String[0]));
        for (var row : r.rows()) t.addRow(row.toArray());
        Ui.showContent(this, r.title(), Ui.scroll(t), 850, 380);
    }

    private void export(boolean pdf) {
        if (last == null) { Ui.error(this, "Hãy mở báo cáo trước khi xuất."); return; }
        String ext = pdf ? ".pdf" : ".xlsx";
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bao-cao-tien-luong" + ext);
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getPath() + ext);
        File out = f;
        Ui.safe(this, () -> { service.export(last, out, pdf); Ui.info(this, "Đã xuất: " + out.getAbsolutePath()); });
    }
}
