package ctv.dashboard;

import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.util.I18n;
import ctv.util.Labels;
import ctv.util.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;

/**
 * Dashboard tổng quan (trang chủ). Mỗi nhóm số liệu chỉ hiện khi tài khoản có quyền tương ứng:
 * nhân sự (1.2/1.3/1.4), công trình (2.1–2.4), chấm công & lương (2.5–2.9), kho thiết bị (3.x), hệ thống (1.1).
 * Số liệu đọc từ các bảng của Form 06 và được nạp lại mỗi khi mở trang hoặc bấm "Làm mới".
 */
public class DashboardPanel extends BorderPane {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final Color GOOD = ctv.gui.Ui.GOOD, WARN = ctv.gui.Ui.WARN, BAD = ctv.gui.Ui.BAD, GREY = Color.web("#9A99C4");

    private final DashboardDAO dao = new DashboardDAO();
    private final VBox body = new VBox(0);

    /** Tên mục menu sẽ mở khi bấm vào từng ô số liệu (theo tiêu đề ô); thử lần lượt, mục nào tài khoản được phép thì mở. */
    private static final Map<String, String[]> TARGETS = Map.ofEntries(
            Map.entry("Tổng công nhân", new String[]{"Hồ sơ công nhân", "Báo cáo nhân lực"}),
            Map.entry("Đang làm việc", new String[]{"Hồ sơ công nhân", "Báo cáo nhân lực"}),
            Map.entry("Tạm nghỉ", new String[]{"Hồ sơ công nhân", "Báo cáo nhân lực"}),
            Map.entry("Đã nghỉ việc", new String[]{"Hồ sơ công nhân", "Báo cáo nhân lực"}),
            Map.entry("Tổng công trình", new String[]{"Quản lý công trình"}),
            Map.entry("Đang thi công", new String[]{"Quản lý công trình"}),
            Map.entry("Tiến độ TB (đang thi công)", new String[]{"Quản lý công trình"}),
            Map.entry("Công nhân đang phân công", new String[]{"Phân công công nhân"}),
            Map.entry("Chấm công hôm nay", new String[]{"Chấm công & kiểm tra"}),
            Map.entry("Hợp lệ", new String[]{"Chấm công & kiểm tra"}),
            Map.entry("Cảnh báo", new String[]{"Chấm công & kiểm tra"}),
            Map.entry("Không hợp lệ", new String[]{"Chấm công & kiểm tra"}),
            Map.entry("Tổng tiền công đã tổng hợp", new String[]{"Kỳ lương & chốt sổ"}),
            Map.entry("Đã thanh toán", new String[]{"Thanh toán lương"}),
            Map.entry("Còn phải trả", new String[]{"Thanh toán lương"}),
            Map.entry("Bảng lương chưa trả đủ", new String[]{"Thanh toán lương"}),
            Map.entry("Loại thiết bị", new String[]{"Quản lý thiết bị", "Danh mục loại thiết bị"}),
            Map.entry("Số lượng trong kho", new String[]{"Báo cáo tồn kho", "Quản lý thiết bị"}),
            Map.entry("Số lượng tại công trình", new String[]{"Báo cáo tồn kho", "Quản lý thiết bị"}),
            Map.entry("Cần sửa / hỏng", new String[]{"Quản lý thiết bị"}),
            Map.entry("Tổng tài khoản", new String[]{"Tài khoản & phân quyền"}),
            Map.entry("Bị khóa", new String[]{"Tài khoản & phân quyền"}),
            Map.entry("Đang hoạt động", new String[]{"Tài khoản & phân quyền"}));

    private final Consumer<String[]> navigate;

    public DashboardPanel() { this(null); }

    /** navigate: mở một mục menu của cửa sổ chính (nhận danh sách tên mục ứng viên). */
    public DashboardPanel(Consumer<String[]> navigate) {
        this.navigate = navigate;
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        javafx.scene.control.Button btnLamMoi = Ui.secondary("Làm mới", "refresh-cw");
        btnLamMoi.setOnAction(e -> reload());
        root.north(Ui.header("house", "Dashboard", "Tổng quan hệ thống – " + Session.fullName() + " (" + Session.roleName() + ")", btnLamMoi));

        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);                                  // bám bề rộng khung nhìn: không bị tràn / cuộn ngang
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background-color:transparent;-fx-background:#F7F6FE;");
        body.setStyle("-fx-background-color:transparent;");
        root.center(sp);

        // Nạp lại mỗi khi trang được hiển thị (đổi từ màn hình khác về)
        visibleProperty().addListener((o, was, now) -> { if (now) reload(); });
        reload();
    }

    private void reload() {
        Ui.safe(this, () -> {
            body.getChildren().clear();
            boolean any = false;
            if (Session.can("1.2", "1.3", "1.4")) { hr(); any = true; }
            if (Session.can("2.1", "2.2", "2.3", "2.4")) { sites(); any = true; }
            if (Session.can("2.5", "2.6")) { attendance(); any = true; }
            if (Session.can("2.7", "2.8", "2.9")) { payroll(); any = true; }
            if (Session.can("3.1", "3.2", "3.3", "3.4")) { warehouse(); any = true; }
            if (Session.can("1.1")) { system(); any = true; }
            if (!any) {
                Label l = new Label(I18n.t("Tài khoản này chưa được cấp chức năng nào để hiển thị số liệu."));
                l.setFont(Ui.FONT());
                l.setTextFill(Ui.PLACEHOLDER);
                l.setMaxWidth(Double.MAX_VALUE);
                l.setAlignment(Pos.CENTER);
                body.getChildren().add(l);
            }
        });
    }

    // ------------------------------------------------------------------ nhóm số liệu
    private void hr() {
        List<Object[]> st = dao.workersByStatus();
        double total = sum(st), working = valueOf(st, "WORKING") + valueOf(st, "WAITING"), leave = valueOf(st, "ON_LEAVE"), resigned = valueOf(st, "RESIGNED");
        section("NHÂN SỰ");
        cards(card("users", 0, "Tổng công nhân", n(total)), card("circle-check", 2, "Đang làm việc", n(working)),
                card("calendar", 3, "Tạm nghỉ", n(leave)), card("history", 1, "Đã nghỉ việc", n(resigned)));
        charts(donutOf("Công nhân theo trạng thái", st, Labels::workStatus, statusColors(st, "WORKING", GOOD, "WAITING", ChartPanels.PALETTE[0], "ON_LEAVE", WARN, "RESIGNED", GREY)),
                barOf("Công nhân theo kỹ năng", dao.workersBySkill(), s -> s, ChartPanels.PALETTE[1]));
    }

    private void sites() {
        List<Object[]> st = dao.sitesByStatus();
        double active = valueOf(st, "IN_PROGRESS");
        double avg = dao.number("SELECT COALESCE(AVG(progress_percent),0) FROM worksites WHERE status='IN_PROGRESS'");
        double assigned = dao.number("SELECT COUNT(*) FROM assignments WHERE status='ACTIVE'");
        section("CÔNG TRÌNH");
        cards(card("warehouse", 0, "Tổng công trình", n(sum(st))), card("wrench", 1, "Đang thi công", n(active)),
                card("bar-chart-3", 2, "Tiến độ TB (đang thi công)", n(Math.round(avg * 10) / 10.0) + "%"),
                card("users", 3, "Công nhân đang phân công", n(assigned)));
        charts(donutOf("Công trình theo trạng thái", st, Labels::worksiteStatus, null),
                hbarOf("Tiến độ các công trình đang thi công", dao.activeSiteProgress(), 100, "%"));
        charts(barOf("Công nhân đang phân công theo công trình", dao.activeWorkersBySite(), s -> s, ChartPanels.PALETTE[0]));
    }

    private void attendance() {
        List<Object[]> today = dao.attendanceTodayByStatus();
        LocalDate to = LocalDate.now(), from = to.minusDays(6);
        section("CHẤM CÔNG");
        cards(card("calendar", 0, "Chấm công hôm nay", n(sum(today))), card("circle-check", 2, "Hợp lệ", n(valueOf(today, "VALID"))),
                card("history", 3, "Cảnh báo", n(valueOf(today, "WARNING"))), card("x", 1, "Không hợp lệ", n(valueOf(today, "INVALID"))));
        charts(daysBar("Lượt chấm công 7 ngày gần nhất", dao.attendancePerDay(from, to), ChartPanels.PALETTE[0]),
                donutOf("Chấm công hôm nay theo kiểm tra", today, DashboardPanel::validationLabel,
                        statusColors(today, "VALID", GOOD, "WARNING", WARN, "INVALID", BAD)));
    }

    private void payroll() {
        List<Object[]> byPeriod = dao.payrollByPeriod();
        double total = dao.number("SELECT COALESCE(SUM(total_amount),0) FROM payroll_details");
        double paid = dao.number("SELECT COALESCE(SUM(amount),0) FROM wage_payments");
        List<Object[]> pay = dao.paymentStatusCounts();
        section("TIỀN LƯƠNG");
        cards(card("bar-chart-3", 0, "Tổng tiền công đã tổng hợp", money(total)), card("circle-check", 2, "Đã thanh toán", money(paid)),
                card("history", 3, "Còn phải trả", money(Math.max(0, total - paid))),
                card("calendar", 1, "Bảng lương chưa trả đủ", n(valueOf(pay, "UNPAID") + valueOf(pay, "PARTIAL"))));
        charts(barOf("Tổng tiền công 6 kỳ gần nhất (VNĐ)", byPeriod, s -> s, ChartPanels.PALETTE[2]),
                donutOf("Bảng lương theo tình trạng thanh toán", pay, DashboardPanel::payLabel,
                        statusColors(pay, "PAID", GOOD, "PARTIAL", WARN, "UNPAID", BAD)));
    }

    private void warehouse() {
        List<Object[]> usage = dao.equipmentByUsage(), cond = dao.equipmentByCondition();
        double inWh = dao.number("SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE location_id IS NOT NULL");
        double atSite = dao.number("SELECT COALESCE(SUM(quantity),0) FROM equipment_stock WHERE worksite_id IS NOT NULL");
        LocalDate to = LocalDate.now(), from = to.minusDays(6);
        section("KHO THIẾT BỊ");
        cards(card("box", 0, "Loại thiết bị", n(sum(usage))), card("warehouse", 2, "Số lượng trong kho", n(inWh)),
                card("map-pin", 1, "Số lượng tại công trình", n(atSite)),
                card("wrench", 3, "Cần sửa / hỏng", n(valueOf(cond, "NEED_REPAIR") + valueOf(cond, "BROKEN"))));
        charts(donutOf("Thiết bị theo tình trạng sử dụng", usage, Labels::usage, null),
                donutOf("Thiết bị theo chất lượng", cond, Labels::condition, statusColors(cond, "GOOD", GOOD, "NEED_REPAIR", WARN, "BROKEN", BAD)));
        charts(daysBar("Phiếu nhập / xuất / thu hồi 7 ngày gần nhất", dao.slipsPerDay(from, to), ChartPanels.PALETTE[3]));
    }

    private void system() {
        List<Object[]> roles = dao.usersByRole();
        LocalDate to = LocalDate.now(), from = to.minusDays(6);
        double locked = dao.number("SELECT COUNT(*) FROM users WHERE status='LOCKED'");
        section("HỆ THỐNG");
        cards(card("users", 0, "Tổng tài khoản", n(sum(roles))), card("circle-check", 2, "Đang hoạt động", n(sum(roles) - locked)),
                card("x", 1, "Bị khóa", n(locked)));
        charts(donutOf("Tài khoản theo vai trò", roles, s -> s, null),
                daysBar("Lượt đăng nhập thành công 7 ngày gần nhất", dao.loginsPerDay(from, to), ChartPanels.PALETTE[1]));
    }

    // ------------------------------------------------------------------ dựng giao diện
    private void section(String title) {
        Label l = new Label(I18n.t(title));
        l.setFont(Ui.font(FontWeight.BOLD, 12));
        l.setTextFill(Ui.PLACEHOLDER);
        l.setStyle("-fx-text-fill:" + Ui.hex(Ui.PLACEHOLDER) + ";");   // kiểu nội tuyến: không bị nền khung cuộn làm chữ trắng
        l.setPadding(new Insets(10, 2, 8, 0));
        body.getChildren().add(l);
    }

    private Region card(String icon, int colorIdx, String caption, String value) {
        Region c = Ui.statCard(icon, Ui.CARD_ACCENTS[colorIdx % 4], caption, new Label(value), Ui.CARD_COLORS[colorIdx % 4]);
        String[] target = TARGETS.get(caption);
        if (navigate != null && target != null) {
            c.setCursor(Cursor.HAND);
            Tooltip.install(c, new Tooltip(I18n.t("Xem chi tiết")));
            c.setOnMouseClicked(e -> navigate.accept(target));
        }
        return c;
    }

    private void cards(Region... cs) {
        Responsive.Grid row = new Responsive.Grid(4, 14, 215, 92);           // 4 → 2 → 1 cột khi cửa sổ hẹp
        for (Region c : cs) row.add(c);
        body.getChildren().add(row);
        body.getChildren().add(strut(12));
    }

    private void charts(Region... cs) {
        Responsive.Grid row = new Responsive.Grid(2, 14, 340, 270);          // 2 biểu đồ cạnh nhau → xếp dọc khi hẹp
        for (Region c : cs) row.add(c);
        body.getChildren().add(row);
        body.getChildren().add(strut(12));
    }

    private static Region strut(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    private static Region donutOf(String title, List<Object[]> rows, Function<String, String> label, List<Color> colors) {
        List<String> ls = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        for (Object[] r : rows) { ls.add(label.apply((String) r[0])); vs.add((Double) r[1]); }
        return ChartPanels.card(title, ChartPanels.donut(ls, vs, colors));
    }

    private static Region barOf(String title, List<Object[]> rows, Function<String, String> label, Color color) {
        List<String> ls = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        for (Object[] r : rows) { ls.add(label.apply((String) r[0])); vs.add((Double) r[1]); }
        return ChartPanels.card(title, ChartPanels.bar(ls, vs, color));
    }

    private static Region hbarOf(String title, List<Object[]> rows, double max, String suffix) {
        List<String> ls = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        for (Object[] r : rows) { ls.add((String) r[0]); vs.add((Double) r[1]); }
        return ChartPanels.card(title, ChartPanels.hbar(ls, vs, max, suffix));
    }

    private static Region daysBar(String title, Map<LocalDate, Double> days, Color color) {
        List<String> ls = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        days.forEach((d, v) -> { ls.add(d.format(DAY)); vs.add(v); });
        return ChartPanels.card(title, ChartPanels.bar(ls, vs, color));
    }

    // ------------------------------------------------------------------ tiện ích
    private static double sum(List<Object[]> rows) { return rows.stream().mapToDouble(r -> (Double) r[1]).sum(); }

    private static double valueOf(List<Object[]> rows, String code) {
        return rows.stream().filter(r -> code.equals(r[0])).mapToDouble(r -> (Double) r[1]).sum();
    }

    /** Màu theo mã trạng thái (cặp mã, màu lặp lại), cùng thứ tự với danh sách. */
    private static List<Color> statusColors(List<Object[]> rows, Object... pairs) {
        List<Color> cs = new ArrayList<>();
        for (Object[] r : rows) {
            Color c = GREY;
            for (int i = 0; i < pairs.length; i += 2) if (pairs[i].equals(r[0])) c = (Color) pairs[i + 1];
            cs.add(c);
        }
        return cs;
    }

    private static String n(double v) { return String.format("%,.0f", v).replace(',', '.'); }

    private static String money(double v) { return Ui.money(BigDecimal.valueOf(v)); }

    private static String validationLabel(String c) {
        return switch (c) { case "VALID" -> "Hợp lệ"; case "WARNING" -> "Cảnh báo"; case "INVALID" -> "Không hợp lệ"; default -> c; };
    }

    private static String payLabel(String c) {
        return switch (c) { case "PAID" -> "Đã trả đủ"; case "PARTIAL" -> "Trả một phần"; case "UNPAID" -> "Chưa trả"; default -> c; };
    }
}
