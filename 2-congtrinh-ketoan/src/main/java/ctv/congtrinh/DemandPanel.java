package ctv.congtrinh;

import ctv.congtrinh.SiteModels.Demand;
import ctv.congtrinh.SiteModels.Site;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Ui;
import ctv.nhansu.HrModels.Level;
import ctv.nhansu.HrModels.Skill;
import ctv.nhansu.HrService;
import ctv.nhansu.LevelPicker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** Nhu cầu nhân lực theo giai đoạn, kỹ năng, bậc thợ (chức năng 2.2) – lưu vào manpower_plans + manpower_demands. */
public class DemandPanel extends BorderPane {
    private static final String ALL = "Tất cả công trình";

    private final SiteService service = new SiteService();
    private final HrService hr = new HrService();
    private final ComboBox<Object> cboCongTrinh = new ComboBox<>();
    private final TextField txtTimKiem = Ui.searchPlain(22, "Tìm theo giai đoạn, kỹ năng hoặc bậc thợ...");
    private final DataTable table = Ui.table("#", "Công trình", "Thời gian áp dụng", "Giai đoạn",
            "Kỹ năng", "Bậc tối thiểu", "Số lượng", "Thao tác");
    private List<Demand> current = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();
    private boolean loading;

    public DemandPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Ui.translateCells(cboCongTrinh);
        Ui.size(cboCongTrinh, 300, Ui.TOOLBAR_H);
        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnThem = Ui.primary("Thêm nhu cầu", "plus");

        root.north(Ui.stack(
                Ui.header("clipboard-list", "Nhu cầu nhân lực", "Lập kế hoạch số lượng công nhân theo giai đoạn, kỹ năng và bậc thợ", btnLamMoi, btnThem),
                Ui.card(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Công trình:"), cboCongTrinh, txtTimKiem))));
        root.center(Ui.scroll(table));

        table.installActions(7, false, () -> { Demand d = selected(); if (d != null) showDialog(d); }, this::delete,
                idx -> idx < current.size() && mine.contains(current.get(idx).demandId()));   // chỉ người lập nhu cầu mới được sửa / xóa
        cboCongTrinh.setOnAction(e -> { if (!loading) reload(); });
        txtTimKiem.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> reload());
        btnThem.setOnAction(e -> showDialog(null));
        reload();
    }

    private void reload() {
        Ui.safe(this, () -> {
            loading = true;
            Object keep = cboCongTrinh.getValue();
            List<Site> sites = service.allSites();
            List<Object> items = new ArrayList<>();
            items.add(ALL);
            items.addAll(sites);
            cboCongTrinh.getItems().setAll(items);
            cboCongTrinh.getSelectionModel().selectFirst();
            if (keep instanceof Site k) for (Site s : sites) if (s.id() == k.id()) cboCongTrinh.setValue(s);
            loading = false;

            Object sel = cboCongTrinh.getValue();
            String kw = txtTimKiem.getText() == null ? "" : txtTimKiem.getText().trim().toLowerCase(java.util.Locale.ROOT);
            current = service.demands(sel instanceof Site s ? s.id() : null).stream()
                    .filter(d -> kw.isEmpty() || d.phase().toLowerCase(java.util.Locale.ROOT).contains(kw)
                            || d.skillName().toLowerCase(java.util.Locale.ROOT).contains(kw)
                            || d.levelName().toLowerCase(java.util.Locale.ROOT).contains(kw))
                    .toList();
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.DEMAND, current, Demand::demandId);
            table.setRowCount(0);
            int i = 1;
            for (Demand d : current)
                table.addRow(i++, d.worksiteName(), Ui.fmt(d.fromDate()) + " - " + Ui.fmt(d.toDate()), d.phase(),
                        d.skillName(), d.levelName(), d.quantity(), "");
        });
    }

    private Demand selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một dòng nhu cầu trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private void showDialog(Demand d) {
        List<Site> sites = service.allSites();
        List<Skill> skills = hr.skills("");
        if (sites.isEmpty() || skills.isEmpty()) { Ui.error(this, "Cần có công trình và kỹ năng trước khi lập nhu cầu."); return; }
        ComboBox<Site> site = Ui.combo(sites);
        ComboBox<Skill> skill = Ui.combo(skills);
        ComboBox<Level> level = new ComboBox<>();
        LevelPicker.bind(this, hr, skill, level);
        TextField phase = Ui.text(14);
        DatePicker from = DatePopup.field(d == null ? LocalDate.now() : d.fromDate(), false);
        DatePicker to = DatePopup.field(d == null ? null : d.toDate(), true);
        Spinner<Integer> qty = Ui.intSpinner(d == null ? 1 : d.quantity(), 1, 100000);
        if (d != null) {
            for (Site s : sites) if (s.id() == d.worksiteId()) site.setValue(s);
            for (Skill s : skills) if (s.id() == d.skillId()) skill.setValue(s);
            LevelPicker.select(level, d.levelId());
            phase.setText(d.phase());
        } else if (cboCongTrinh.getValue() instanceof Site cur) {
            for (Site s : sites) if (s.id() == cur.id()) site.setValue(s);
        }
        GridPane f = Ui.form(2, 125,
                new Node[]{Ui.required("Công trình"), Ui.required("Giai đoạn thi công"), Ui.required("Kỹ năng"), Ui.required("Bậc thợ tối thiểu"),
                        Ui.required("Từ ngày"), Ui.required("Đến ngày"), Ui.required("Số lượng cần")},
                new Node[]{site, phase, skill, level, from, to, qty});
        Ui.dialog(this, d == null ? "Thêm nhu cầu nhân lực" : "Sửa nhu cầu nhân lực", f, () -> {
            service.saveDemand(d, site.getValue(), Ui.parseDate(from, "Từ ngày", true), Ui.parseDate(to, "Đến ngày", true),
                    phase.getText(), skill.getValue(), level.getValue(), qty.getValue());
            reload();
        });
    }

    private void delete() {
        Demand d = selected();
        if (d == null || !Ui.confirm(this, "Xóa nhu cầu " + d.skillName() + " (" + d.phase() + ") của " + d.worksiteName() + "?")) return;
        Ui.safe(this, () -> { service.deleteDemand(d); reload(); });
    }
}
