package ctv.nhansu;

import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Option;
import ctv.nhansu.HrModels.Level;
import ctv.nhansu.HrModels.Rate;
import ctv.nhansu.HrModels.Skill;
import ctv.nhansu.HrModels.Worker;
import ctv.nhansu.HrModels.WorkerSkillRow;
import ctv.util.Labels;
import java.time.LocalDate;
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

/** Gán kỹ năng cho công nhân và quản lý đơn giá theo ca (chức năng 1.3): ba thẻ – Gán kỹ năng, Đơn giá, Lịch sử đơn giá. */
public class SkillRatePanel extends BorderPane {
    /** Mỗi màn hình là một mục riêng trên thanh bên trái (không còn gộp thẻ trong một giao diện). */
    public enum Page { ASSIGN, RATE, HISTORY }

    private final HrService service = new HrService();

    public SkillRatePanel() { this(Page.ASSIGN); }

    public SkillRatePanel(Page page) {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);
        Button btnLamMoi = Ui.secondary("Làm mới");
        Node tab;
        Runnable reloader;
        switch (page) {
            case RATE -> {
                RateTab t = new RateTab();
                tab = t; reloader = t::reload;
                root.north(Ui.header("circle-dollar-sign", "Đơn giá theo ca", "Quản lý đơn giá của từng kỹ năng, bậc thợ theo loại ca", btnLamMoi));
            }
            case HISTORY -> {
                HistoryTab t = new HistoryTab();
                tab = t; reloader = t::reload;
                root.north(Ui.header("history", "Lịch sử đơn giá", "Các lần thay đổi đơn giá theo ca", btnLamMoi));
            }
            default -> {
                AssignTab t = new AssignTab();
                tab = t; reloader = t::reload;
                root.north(Ui.header("user-check", "Gán kỹ năng công nhân", "Gán bậc thợ cho công nhân theo từng kỹ năng", btnLamMoi));
            }
        }
        root.center(tab);
        btnLamMoi.setOnAction(e -> reloader.run());
        // Dữ liệu có thể đã đổi ở màn hình khác (vd. đổi đơn giá -> lịch sử): nạp lại mỗi khi màn hình được hiện.
        visibleProperty().addListener((o, was, now) -> { if (now) reloader.run(); });
    }

    /** Ô lọc theo kỹ năng dùng chung cho các thẻ: phần tử đầu là chuỗi "Tất cả kỹ năng", các phần tử sau là Skill. */
    private static void fillSkillFilter(ComboBox<Object> cbo, List<Skill> skills) {
        Object keep = cbo.getValue();
        List<Object> items = new ArrayList<>();
        items.add("Tất cả kỹ năng");
        items.addAll(skills);
        cbo.getItems().setAll(items);
        cbo.getSelectionModel().selectFirst();
        if (keep instanceof Skill k) for (Skill s : skills) if (s.id() == k.id()) cbo.setValue(s);
    }

    private static Integer skillIdOf(ComboBox<Object> cbo) {
        return cbo.getValue() instanceof Skill s ? s.id() : null;
    }

    private static ComboBox<Object> skillFilter() {
        ComboBox<Object> c = new ComboBox<>();
        Ui.translateCells(c);
        Ui.size(c, 220, Ui.TOOLBAR_H);
        return c;
    }

    /** Khi đổi kỹ năng trong hộp thoại thì nạp lại danh sách bậc thợ của kỹ năng đó. */
    private void bindLevels(ComboBox<Skill> skill, ComboBox<Level> level) {
        LevelPicker.bind(this, service, skill, level);
    }

    // =====================================================================
    private final class AssignTab extends BorderPane {
        private final TextField txtTimKiem = Ui.searchPlain(26, "Tìm theo mã hoặc tên công nhân...");
        private final ComboBox<Object> cboKyNang = skillFilter();
        private final DataTable table = Ui.table("#", "Mã CN", "Công nhân", "Kỹ năng", "Bậc thợ", "Thao tác");
        private List<WorkerSkillRow> current = List.of();
        private java.util.Set<Integer> mine = java.util.Set.of();
        private boolean loading;

        AssignTab() {
            Button btnTim = Ui.primary("Tìm kiếm");
            Button btnGan = Ui.primary("Gán / cập nhật bậc", "plus");
            Responsive.Row bar = new Responsive.Row(10);
            bar.center(txtTimKiem).east(Ui.flow(Pos.CENTER_RIGHT, cboKyNang, btnTim, btnGan));
            Ui.tab(this, Ui.card(bar), Ui.scroll(table));
            table.installActions(5, false, () -> { WorkerSkillRow r = selected(); if (r != null) showDialog(r); }, this::delete,
                    idx -> idx < current.size() && mine.contains(current.get(idx).id()));
            btnTim.setOnAction(e -> reload());
            txtTimKiem.setOnAction(e -> reload());
            cboKyNang.setOnAction(e -> { if (!loading) reload(); });
            btnGan.setOnAction(e -> showDialog(null));
            reload();
        }

        void reload() {
            Ui.safe(this, () -> {
                loading = true;
                fillSkillFilter(cboKyNang, service.skills(""));
                loading = false;
                current = service.workerSkills(txtTimKiem.getText(), skillIdOf(cboKyNang));
                mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.WORKER_SKILL, current, WorkerSkillRow::id);
                table.setRowCount(0);
                int i = 1;
                for (WorkerSkillRow r : current)
                    table.addRow(i++, r.workerCode(), r.workerName(), r.skillName(), r.levelName(), "");
            });
        }

        private WorkerSkillRow selected() {
            int r = table.getSelectedRow();
            if (r < 0) { Ui.error(this, "Hãy chọn một dòng trong bảng."); return null; }
            return current.get(table.convertRowIndexToModel(r));
        }

        /** row == null: gán mới (chọn công nhân, kỹ năng, bậc); có row: đổi bậc của kỹ năng đó. */
        private void showDialog(WorkerSkillRow row) {
            List<Worker> workers = service.workingWorkers();
            List<Skill> skills = service.skills("");
            if (workers.isEmpty() || skills.isEmpty()) {
                Ui.error(this, "Cần có công nhân đang làm việc và ít nhất một kỹ năng trước khi gán.");
                return;
            }
            ComboBox<Worker> worker = Ui.combo(workers);
            ComboBox<Skill> skill = Ui.combo(skills);
            ComboBox<Level> level = new ComboBox<>();
            bindLevels(skill, level);
            if (row != null) {
                for (Worker w : workers) if (w.id() == row.workerId()) worker.setValue(w);
                for (Skill s : skills) if (s.id() == row.skillId()) skill.setValue(s);
                LevelPicker.select(level, row.levelId());
                worker.setDisable(true);
                skill.setDisable(true);
            }
            GridPane f = Ui.form(1,
                    new Node[]{Ui.required("Công nhân"), Ui.required("Kỹ năng"), Ui.required("Bậc thợ")},
                    new Node[]{worker, skill, level});
            Ui.dialog(this, row == null ? "Gán kỹ năng cho công nhân" : "Cập nhật bậc thợ", f, () -> {
                service.assignSkill(worker.getValue(), skill.getValue(), level.getValue());
                reload();
            });
        }

        private void delete() {
            WorkerSkillRow r = selected();
            if (r == null || !Ui.confirm(this, "Bỏ kỹ năng " + r.skillName() + " của " + r.workerName() + "?")) return;
            Ui.safe(this, () -> { service.removeWorkerSkill(r.id()); reload(); });
        }
    }

    // =====================================================================
    private final class RateTab extends BorderPane {
        private final ComboBox<Object> cboKyNang = skillFilter();
        private final DataTable table = Ui.table("#", "Kỹ năng", "Bậc thợ", "Loại ca", "Đơn giá (VNĐ)", "Áp dụng từ", "Thao tác");
        private List<Rate> current = List.of();
        private java.util.Set<Integer> mine = java.util.Set.of();
        private boolean loading;

        RateTab() {
            Button btnThem = Ui.primary("Thêm / đổi đơn giá", "plus");
            Responsive.Row bar = new Responsive.Row(10);
            bar.center(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Kỹ năng:"), cboKyNang)).east(Ui.flow(Pos.CENTER_RIGHT, btnThem));
            Ui.tab(this, Ui.card(bar), Ui.scroll(table));
            // Đơn giá không xóa (cần giữ lịch sử); cột cuối chỉ có nút Sửa.
            table.installActions(6, false, () -> { Rate r = selected(); if (r != null) showDialog(r); },
                    () -> Ui.info(this, "Đơn giá không được xóa để giữ lịch sử. Hãy đổi sang mức giá mới."),
                    idx -> idx < current.size() && mine.contains(current.get(idx).id()));
            cboKyNang.setOnAction(e -> { if (!loading) reload(); });
            btnThem.setOnAction(e -> showDialog(null));
            reload();
        }

        void reload() {
            Ui.safe(this, () -> {
                loading = true;
                fillSkillFilter(cboKyNang, service.skills(""));
                loading = false;
                current = service.rates(skillIdOf(cboKyNang));
                mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.RATE, current, Rate::id);
                table.setRowCount(0);
                int i = 1;
                for (Rate r : current)
                    table.addRow(i++, r.skillName(), r.levelName(), Labels.shiftType(r.shiftType()),
                            Ui.money(r.rate()), Ui.fmt(r.effectiveDate()), "");
            });
        }

        private Rate selected() {
            int r = table.getSelectedRow();
            if (r < 0) { Ui.error(this, "Hãy chọn một đơn giá trong bảng."); return null; }
            return current.get(table.convertRowIndexToModel(r));
        }

        /** Mỗi (kỹ năng, bậc, loại ca) chỉ có một đơn giá hiện hành; lưu giá mới sẽ ghi vào lịch sử. */
        private void showDialog(Rate r) {
            List<Skill> skills = service.skills("");
            if (skills.isEmpty()) { Ui.error(this, "Hãy tạo kỹ năng và bậc thợ trước khi khai báo đơn giá."); return; }
            ComboBox<Skill> skill = Ui.combo(skills);
            ComboBox<Level> level = new ComboBox<>();
            bindLevels(skill, level);
            ComboBox<Option> shift = Ui.combo(Labels.SHIFT_TYPES);
            TextField rate = Ui.text(14);
            DatePicker effective = DatePopup.field(LocalDate.now(), false);
            if (r != null) {
                for (Skill s : skills) if (s.id() == r.skillId()) skill.setValue(s);
                LevelPicker.select(level, r.levelId());
                shift.setValue(Labels.option(Labels.SHIFT_TYPES, r.shiftType()));
                rate.setText(r.rate().toBigInteger().toString());
                skill.setDisable(true);
                level.setDisable(true);
                shift.setDisable(true);
            }
            GridPane f = Ui.form(2,
                    new Node[]{Ui.required("Kỹ năng"), Ui.required("Bậc thợ"), Ui.required("Loại ca"),
                            Ui.required("Đơn giá (VNĐ)"), Ui.required("Áp dụng từ")},
                    new Node[]{skill, level, shift, rate, effective});
            Ui.dialog(this, r == null ? "Thêm đơn giá" : "Đổi đơn giá", f, () -> {
                service.saveRate(skill.getValue(), level.getValue(),
                        shift.getValue().code(), rate.getText(), Ui.parseDate(effective, "Ngày áp dụng", true));
                reload();
            });
        }
    }

    // =====================================================================
    private final class HistoryTab extends BorderPane {
        private final ComboBox<Object> cboKyNang = skillFilter();
        private final DataTable table = Ui.table("#", "Thời điểm", "Kỹ năng", "Bậc thợ", "Loại ca",
                "Giá cũ", "Giá mới", "Áp dụng từ", "Người thay đổi");
        private boolean loading;

        HistoryTab() {
            Ui.tab(this, Ui.card(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Kỹ năng:"), cboKyNang)), Ui.scroll(table));
            cboKyNang.setOnAction(e -> { if (!loading) reload(); });
            reload();
        }

        void reload() {
            Ui.safe(this, () -> {
                loading = true;
                fillSkillFilter(cboKyNang, service.skills(""));
                loading = false;
                table.setRowCount(0);
                int i = 1;
                for (Object[] h : service.rateHistories(skillIdOf(cboKyNang))) {
                    String when = h[0] == null ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format((java.util.Date) h[0]);
                    table.addRow(i++, when, h[1], h[2], Labels.shiftType((String) h[3]),
                            h[4] == null ? "—" : Ui.money((java.math.BigDecimal) h[4]), Ui.money((java.math.BigDecimal) h[5]),
                            Ui.fmt((LocalDate) h[6]), h[7]);
                }
            });
        }
    }
}
