package ctv.congtrinh;

import ctv.congtrinh.PayrollDAO.DailyRow;
import ctv.congtrinh.PayrollDAO.Payment;
import ctv.congtrinh.PayrollDAO.PayrollRow;
import ctv.congtrinh.PayrollDAO.Period;
import ctv.congtrinh.PayrollDAO.Receiver;
import ctv.congtrinh.PayrollDAO.Result;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Ui;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

/** 2.9: thanh toán tiền mặt/chuyển khoản, trạng thái, lịch sử và báo cáo thanh toán. */
public class PaymentPanel extends BorderPane {
    private final PayrollService service = new PayrollService();
    private final AttendanceService attendanceService = new AttendanceService();
    private final ComboBox<Period> periods = Ui.combo(List.of());
    private final ComboBox<String> status = Ui.combo(List.of("Tất cả trạng thái", "Chưa thanh toán", "Thanh toán một phần", "Đã thanh toán"));
    private final DataTable table = Ui.table("#", "Mã CN", "Họ tên", "Ca thường", "Ca tăng ca", "Tổng phải trả", "Đã trả", "Còn lại", "Trạng thái", "Chi tiết");
    private List<PayrollRow> rows = List.of();
    private Result last;

    public PaymentPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);
        Ui.size(periods, 320, 34);
        Ui.size(status, 190, 34);
        Button refresh = Ui.secondary("Làm mới"), pay = Ui.primary("Thanh toán lương", "plus"), daily = Ui.primary("Trả lương theo ngày", "banknote"),
                history = Ui.secondary("Lịch sử thanh toán"), summary = Ui.secondary("Báo cáo thanh toán"),
                excel = Ui.secondary("Xuất Excel"), pdf = Ui.secondary("Xuất PDF");
        root.north(Ui.stack(Ui.header("banknote", "Thanh toán lương",
                "Theo dõi chưa thanh toán, thanh toán một phần và đã thanh toán; công nhân thời vụ có thể trả hằng ngày",
                refresh, pay, daily, history, summary, excel, pdf),
                Ui.card(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Kỳ lương:"), periods, status))));
        root.center(Ui.scroll(table));
        periods.setOnAction(e -> reload());
        status.setOnAction(e -> reload());
        refresh.setOnAction(e -> loadPeriods());
        pay.setOnAction(e -> pay());
        daily.setOnAction(e -> dailyPay());
        history.setOnAction(e -> history());
        summary.setOnAction(e -> summary());
        excel.setOnAction(e -> export(false));
        pdf.setOnAction(e -> export(true));
        loadPeriods();
    }

    private Period selectedPeriod() { return periods.getValue(); }

    private void loadPeriods() {
        Ui.safe(this, () -> {
            Period keep = selectedPeriod();
            List<Period> list = service.periods();
            periods.getItems().setAll(list);
            periods.getSelectionModel().selectFirst();
            if (keep != null) for (Period p : list) if (p.id() == keep.id()) periods.setValue(p);
            reload();
        });
    }

    private void reload() {
        Period p = selectedPeriod();
        table.setRowCount(0);
        rows = List.of();
        if (p == null) return;
        Ui.safe(this, () -> {
            rows = service.rows(p.id());
            String st = switch (status.getSelectionModel().getSelectedIndex()) {
                case 1 -> "UNPAID";
                case 2 -> "PARTIAL";
                case 3 -> "PAID";
                default -> null;
            };
            int i = 1;
            for (PayrollRow r : rows) {
                if (st != null && !st.equals(r.paymentStatus())) continue;
                table.addRow(i++, r.code(), r.name(), r.regular(), r.overtime(), Ui.money(r.total()), Ui.money(r.paid()),
                        Ui.money(r.remaining()), label(r.paymentStatus()), "");
            }
        });
    }

    private PayrollRow selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn công nhân cần thanh toán."); return null; }
        String code = String.valueOf(table.getValueAt(r, 1));
        return rows.stream().filter(x -> x.code().equals(code)).findFirst().orElse(null);
    }

    private static String label(String s) {
        return switch (s) {
            case "UNPAID" -> "Chưa thanh toán";
            case "PARTIAL" -> "Thanh toán một phần";
            case "PAID" -> "Đã thanh toán";
            default -> s;
        };
    }

    /** Dữ liệu người dùng nhập ở hộp thoại thanh toán (đọc/kiểm tra bên trong Ui.safe của nơi gọi). */
    private record PayForm(boolean cash, TextField amount, DatePicker day, TextField ref, TextField account) {}

    /** Hộp thoại nhập thanh toán dùng chung cho lương kỳ và lương hằng ngày. Phần kiểm tra + lưu (onSave) chạy trong hộp thoại: lỗi thì báo và giữ form mở. */
    private void askPayment(Node parent, String title, int workerId, BigDecimal remaining, LocalDate defaultDay, java.util.function.Consumer<PayForm> onSave) {
        Receiver rc = service.receiver(workerId);
        Label info = Ui.caption(Ui.htmlToText(rc.html()));
        ComboBox<String> method = Ui.combo(List.of("Tiền mặt", "Chuyển khoản"));
        TextField amount = Ui.text(14), ref = Ui.text(14), account = Ui.text(14);
        DatePicker day = DatePopup.field(defaultDay, false);
        amount.setText(remaining.stripTrailingZeros().toPlainString());
        // Có tài khoản ngân hàng → mặc định chuyển khoản và điền sẵn tài khoản; không có → mặc định lãnh lương tay (tiền mặt).
        if (rc.hasAccount()) { method.getSelectionModel().select(1); account.setText(rc.accountText()); } else method.getSelectionModel().select(0);
        method.setOnAction(e -> {
            if (method.getSelectionModel().getSelectedIndex() == 1 && account.getText().isBlank() && rc.hasAccount()) account.setText(rc.accountText());
        });
        GridPane f = Ui.form(1, 150, new Node[]{Ui.caption("Thông tin nhận lương"), Ui.required("Phương thức"), Ui.required("Số tiền"),
                Ui.required("Ngày thanh toán"), Ui.caption("Mã tham chiếu"), Ui.caption("Tài khoản nhận (chuyển khoản)")},
                new Node[]{info, method, amount, day, ref, account});
        Ui.dialog(parent, title, f, () -> onSave.accept(new PayForm(method.getSelectionModel().getSelectedIndex() == 0, amount, day, ref, account)));
    }

    private static BigDecimal money(TextField t) {
        try { return new BigDecimal(t.getText().trim().replace(",", ".")); }
        catch (NumberFormatException e) { throw new ctv.util.BusinessException("Số tiền không hợp lệ."); }
    }

    private void pay() {
        PayrollRow r = selected();
        if (r == null) return;
        if (r.remaining().signum() <= 0) { Ui.info(this, "Khoản lương này đã thanh toán đủ."); return; }
        askPayment(this, "Thanh toán lương - " + r.name(), r.workerId(), r.remaining(), LocalDate.now(), p -> {
            service.pay(r.detailId(), p.cash() ? "CASH" : "TRANSFER", money(p.amount()), Ui.parseDate(p.day(), "Ngày thanh toán", true),
                    p.ref().getText(), p.cash() ? "" : p.account().getText());
            reload();
            Ui.info(this, "Đã thanh toán lương.");
        });
    }

    /** Trả lương hằng ngày cho công nhân loại "Thời vụ": chọn ngày công, chọn công nhân, trả ngay trong ngày không cần chờ chốt kỳ tuần. */
    private void dailyPay() {
        DatePicker day = DatePopup.field(LocalDate.now(), false);
        Button load = Ui.secondary("Xem");
        DataTable t = Ui.table("Mã CN", "Họ tên", "Ca thường", "Ca tăng ca", "Tiền công ngày", "Đã trả", "Còn lại", "Ghi chú");
        List<DailyRow> cur = new ArrayList<>();
        BorderPane panel = new BorderPane();
        Runnable reloadDaily = () -> Ui.safe(panel, () -> {
            cur.clear();
            t.setRowCount(0);
            cur.addAll(service.dailyRows(Ui.dateOf(day)));
            for (DailyRow r : cur)
                t.addRow(r.code(), r.name(), r.regular(), r.overtime(), Ui.money(r.wage()), Ui.money(r.paid()), Ui.money(r.remaining()),
                        !r.attended() ? "Chưa chấm công ngày này" : !r.valid() ? "Chấm công chưa hợp lệ" : r.remaining().signum() <= 0 ? "Đã trả đủ" : "");
        });
        load.setOnAction(e -> {
            reloadDaily.run();
            if (cur.isEmpty()) Ui.info(panel, "Chưa có công nhân thời vụ nào trong danh sách. Hãy kiểm tra loại công nhân là \"Thời vụ\" ở Hồ sơ công nhân.");
        });
        Button payBtn = Ui.primary("Thanh toán", "plus");
        payBtn.setOnAction(e -> {
            int row = t.getSelectedRow();
            if (row < 0) { Ui.error(panel, "Hãy chọn công nhân cần thanh toán."); return; }
            DailyRow r = cur.get(t.convertRowIndexToModel(row));
            if (!r.attended()) { Ui.error(panel, "Công nhân chưa được chấm công ngày này, chưa có tiền công để thanh toán."); return; }
            if (!r.valid()) { Ui.error(panel, "Chấm công ngày này chưa hợp lệ, chưa thể thanh toán."); return; }
            if (r.remaining().signum() <= 0) { Ui.info(panel, "Tiền công ngày này đã trả đủ."); return; }
            Ui.safe(panel, () -> {
                LocalDate workDate = Ui.dateOf(day);
                askPayment(panel, "Trả lương ngày - " + r.name(), r.workerId(), r.remaining(), LocalDate.now(), p -> {
                    service.payDaily(r.workerId(), workDate, p.cash() ? "CASH" : "TRANSFER", money(p.amount()),
                            Ui.parseDate(p.day(), "Ngày thanh toán", true), p.ref().getText(), p.cash() ? "" : p.account().getText());
                    reloadDaily.run();
                    reload();
                    Ui.info(panel, "Đã thanh toán lương ngày.");
                });
            });
        });
        Node top = Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Ngày công:"), day, load,
                Ui.caption("Chỉ công nhân loại \"Thời vụ\" – trả theo tiền công của ngày"));
        panel.setTop(top);
        BorderPane.setMargin(top, new Insets(0, 0, 8, 0));
        panel.setCenter(Ui.scroll(t));
        var bottom = Ui.flow(Pos.CENTER_RIGHT, payBtn);
        panel.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(8, 0, 0, 0));
        reloadDaily.run();
        Ui.showContent(this, "Trả lương theo ngày (công nhân thời vụ)", panel, 860, 420);
    }

    private record WorkerChoice(int id, String label) {
        @Override public String toString() { return label; }
    }

    private void history() {
        Period p = selectedPeriod();
        if (p == null) return;
        List<WorkerChoice> people = new ArrayList<>();
        people.add(new WorkerChoice(0, "Tất cả công nhân"));
        for (Object[] x : attendanceService.workers()) people.add(new WorkerChoice((Integer) x[0], (String) x[1] + " - " + (String) x[2]));
        // Mặc định lọc theo khoảng ngày THANH TOÁN thực tế của kỳ này (ngày trả thường sau ngày kết thúc kỳ lương).
        LocalDate dFrom = p.from(), dTo = p.to();
        List<Payment> existing = service.payments(p.id(), null, null, null, null);
        if (!existing.isEmpty()) {
            dFrom = existing.get(0).date();
            dTo = existing.get(0).date();
            for (Payment x : existing) {
                if (x.date().isBefore(dFrom)) dFrom = x.date();
                if (x.date().isAfter(dTo)) dTo = x.date();
            }
        }
        ComboBox<WorkerChoice> worker = Ui.combo(people);
        DatePicker from = Ui.datePicker(dFrom), to = Ui.datePicker(dTo);
        GridPane f = Ui.form(2, new Node[]{Ui.caption("Công nhân"), Ui.required("Từ ngày thanh toán"), Ui.required("Đến ngày thanh toán")},
                new Node[]{worker, from, to});
        Ui.dialog(this, "Lọc lịch sử thanh toán", f, () -> {
            WorkerChoice w = worker.getValue();
            Integer wid = w == null || w.id() == 0 ? null : w.id();
            List<Payment> list = service.payments(p.id(), wid, Ui.dateOf(from), Ui.dateOf(to), null);
            DataTable t = Ui.table("Mã", "Mã CN", "Công nhân", "Kỳ lương", "Hình thức", "Số tiền", "Ngày", "Mã tham chiếu", "Tài khoản nhận");
            for (Payment x : list)
                t.addRow(x.id(), x.workerCode(), x.workerName(), x.periodLabel(), "CASH".equals(x.method()) ? "Tiền mặt (lãnh tay)" : "Chuyển khoản",
                        Ui.money(x.amount()), Ui.fmt(x.date()), x.reference(), x.account());
            if (list.isEmpty()) Ui.info(this, "Kỳ lương này chưa có khoản thanh toán nào trong khoảng ngày đã chọn.");
            Ui.showContent(this, "Lịch sử thanh toán", Ui.scroll(t), 900, 360);
        });
    }

    private void summary() {
        Period p = selectedPeriod();
        if (p == null) return;
        Ui.safe(this, () -> { last = service.paymentSummary(p.id()); showResult(last); });
    }

    private void showResult(Result r) {
        DataTable t = Ui.table(r.headers().toArray(new String[0]));
        for (var row : r.rows()) t.addRow(row.toArray());
        Ui.showContent(this, r.title(), Ui.scroll(t), 720, 300);
    }

    private void export(boolean pdf) {
        if (last == null) { Ui.error(this, "Hãy mở báo cáo trước khi xuất."); return; }
        String ext = pdf ? ".pdf" : ".xlsx";
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bao-cao-thanh-toan" + ext);
        File f = fc.showSaveDialog(Ui.ownerOf(this));
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(ext)) f = new File(f.getPath() + ext);
        File out = f;
        Ui.safe(this, () -> { service.export(last, out, pdf); Ui.info(this, "Đã xuất: " + out.getAbsolutePath()); });
    }
}
