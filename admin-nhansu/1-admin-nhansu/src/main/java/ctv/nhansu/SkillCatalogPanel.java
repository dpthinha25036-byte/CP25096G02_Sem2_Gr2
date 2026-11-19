package ctv.nhansu;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.nhansu.HrModels.Level;
import ctv.nhansu.HrModels.Skill;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** Danh mục kỹ năng và bậc thợ (chức năng 1.3): hai thẻ – Kỹ năng, Bậc thợ (bậc thợ thuộc từng kỹ năng, Form 06). */
public class SkillCatalogPanel extends BorderPane {
    /** Mỗi màn hình là một mục riêng trên thanh bên trái (không còn gộp thẻ trong một giao diện). */
    public enum Page { SKILL, LEVEL }

    private final HrService service = new HrService();

    public SkillCatalogPanel() { this(Page.SKILL); }

    public SkillCatalogPanel(Page page) {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);
        Button btnLamMoi = Ui.secondary("Làm mới");
        Runnable reloader;
        if (page == Page.LEVEL) {
            LevelTab t = new LevelTab();
            root.north(Ui.header("layers", "Danh mục bậc thợ", "Các bậc thợ của từng kỹ năng", btnLamMoi));
            root.center(t);
            reloader = () -> { t.reloadSkills(); t.reload(); };
        } else {
            SkillTab t = new SkillTab();
            root.north(Ui.header("wrench", "Danh mục kỹ năng", "Danh mục kỹ năng của công nhân", btnLamMoi));
            root.center(t);
            reloader = t::reload;
        }
        btnLamMoi.setOnAction(e -> reloader.run());
        // Nạp lại mỗi khi màn hình được hiện lại từ menu.
        visibleProperty().addListener((o, was, now) -> { if (now) reloader.run(); });
    }

    // =====================================================================
    private final class SkillTab extends BorderPane {
        private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã hoặc tên kỹ năng...");
        private final DataTable table = Ui.table("#", "Mã kỹ năng", "Tên kỹ năng", "Mô tả", "Thao tác");
        private List<Skill> current = List.of();
        private java.util.Set<Integer> mine = java.util.Set.of();

        SkillTab() {
            Button btnThem = Ui.primary("Thêm kỹ năng", "plus");
            Button btnTim = Ui.primary("Tìm kiếm");
            Responsive.Row bar = new Responsive.Row(10);
            bar.center(txtTimKiem).east(Ui.flow(Pos.CENTER_RIGHT, btnTim, btnThem));
            Ui.tab(this, Ui.card(bar), Ui.scroll(table));
            table.installActions(4, false, () -> { Skill s = selected(); if (s != null) showDialog(s); }, this::delete,
                    idx -> idx < current.size() && mine.contains(current.get(idx).id()));
            btnTim.setOnAction(e -> reload());
            txtTimKiem.setOnAction(e -> reload());
            btnThem.setOnAction(e -> showDialog(null));
            reload();
        }

        void reload() {
            Ui.safe(this, () -> {
                current = service.skills(txtTimKiem.getText());
                mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.SKILL, current, Skill::id);
                table.setRowCount(0);
                int i = 1;
                for (Skill s : current) table.addRow(i++, s.code(), s.name(), s.description(), "");
            });
        }

        private Skill selected() {
            int r = table.getSelectedRow();
            if (r < 0) { Ui.error(this, "Hãy chọn một kỹ năng trong bảng."); return null; }
            return current.get(table.convertRowIndexToModel(r));
        }

        private void showDialog(Skill s) {
            TextField code = Ui.text(14), name = Ui.text(14), desc = Ui.text(14);
            if (s != null) { code.setText(s.code()); name.setText(s.name()); desc.setText(s.description()); }
            GridPane f = Ui.form(2,
                    new Node[]{Ui.required("Mã kỹ năng"), Ui.required("Tên kỹ năng"), Ui.caption("Mô tả")},
                    new Node[]{code, name, desc});
            Ui.dialog(this, s == null ? "Thêm kỹ năng" : "Sửa kỹ năng", f, () -> { service.saveSkill(s, code.getText(), name.getText(), desc.getText()); reload(); });
        }

        private void delete() {
            Skill s = selected();
            if (s == null || !Ui.confirm(this, "Xóa kỹ năng " + s.name() + "?")) return;
            Ui.safe(this, () -> { service.deleteSkill(s.id()); reload(); });
        }
    }

    // =====================================================================
    private final class LevelTab extends BorderPane {
        private static final String ALL = "Tất cả kỹ năng";
        private final ComboBox<Object> cboKyNang = new ComboBox<>();
        private final DataTable table = Ui.table("#", "Mã bậc", "Tên bậc", "Kỹ năng", "Mô tả", "Thao tác");
        private List<Level> current = List.of();
        private java.util.Set<Integer> mine = java.util.Set.of();
        private List<Skill> skills = List.of();
        private boolean loading;

        LevelTab() {
            Ui.translateCells(cboKyNang);
            Ui.size(cboKyNang, 260, Ui.TOOLBAR_H);
            Button btnThem = Ui.primary("Thêm bậc thợ", "plus");
            Responsive.Row bar = new Responsive.Row(10);
            bar.center(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Kỹ năng:"), cboKyNang)).east(Ui.flow(Pos.CENTER_RIGHT, btnThem));
            Ui.tab(this, Ui.card(bar), Ui.scroll(table));
            table.installActions(5, false, () -> { Level l = selected(); if (l != null) showDialog(l); }, this::delete,
                    idx -> idx < current.size() && mine.contains(current.get(idx).id()));
            cboKyNang.setOnAction(e -> { if (!loading) reload(); });
            btnThem.setOnAction(e -> showDialog(null));
            reloadSkills();
            reload();
        }

        void reloadSkills() {
            Ui.safe(this, () -> {
                loading = true;
                Object keep = cboKyNang.getValue();
                skills = service.skills("");
                List<Object> items = new ArrayList<>();
                items.add(ALL);
                items.addAll(skills);
                cboKyNang.getItems().setAll(items);
                cboKyNang.getSelectionModel().selectFirst();
                if (keep instanceof Skill k) for (Skill s : skills) if (s.id() == k.id()) cboKyNang.setValue(s);
                loading = false;
            });
        }

        void reload() {
            Ui.safe(this, () -> {
                Object sel = cboKyNang.getValue();
                current = service.levels(sel instanceof Skill s ? s.id() : null);
                mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.LEVEL, current, Level::id);
                table.setRowCount(0);
                int i = 1;
                for (Level l : current) table.addRow(i++, l.code(), l.name(), l.skillName(), l.description(), "");
            });
        }

        private Level selected() {
            int r = table.getSelectedRow();
            if (r < 0) { Ui.error(this, "Hãy chọn một bậc thợ trong bảng."); return null; }
            return current.get(table.convertRowIndexToModel(r));
        }

        private void showDialog(Level l) {
            if (skills.isEmpty()) { Ui.error(this, "Hãy tạo kỹ năng trước khi thêm bậc thợ."); return; }
            ComboBox<Skill> skill = Ui.combo(skills);
            TextField code = Ui.text(14), name = Ui.text(14), desc = Ui.text(14);
            if (l != null) {
                for (Skill s : skills) if (s.id() == l.skillId()) skill.setValue(s);
                skill.setDisable(true);   // bậc thợ đã tạo không đổi sang kỹ năng khác
                code.setText(l.code()); name.setText(l.name()); desc.setText(l.description());
            } else if (cboKyNang.getValue() instanceof Skill s) {
                skill.setValue(s);
            }
            GridPane f = Ui.form(2,
                    new Node[]{Ui.required("Kỹ năng"), Ui.required("Mã bậc thợ"), Ui.required("Tên bậc thợ"), Ui.caption("Mô tả")},
                    new Node[]{skill, code, name, desc});
            Ui.dialog(this, l == null ? "Thêm bậc thợ" : "Sửa bậc thợ", f, () -> {
                service.saveLevel(l, skill.getValue(), code.getText(), name.getText(), desc.getText());
                reload();
            });
        }

        private void delete() {
            Level l = selected();
            if (l == null || !Ui.confirm(this, "Xóa bậc thợ " + l.name() + " của kỹ năng " + l.skillName() + "?")) return;
            Ui.safe(this, () -> { service.deleteLevel(l.id()); reload(); });
        }
    }
}
