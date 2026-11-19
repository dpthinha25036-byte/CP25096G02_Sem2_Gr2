package ctv.congtrinh;

import ctv.congtrinh.SiteModels.Site;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Option;
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

/** Quản lý công trình (chức năng 2.1) – thông tin, trạng thái, tiến độ và lịch sử cập nhật tiến độ. */
public class WorksitePanel extends BorderPane {
    private static final Option ALL_STATUS = new Option(null, "Tất cả trạng thái");

    private final SiteService service = new SiteService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã, tên công trình hoặc chủ đầu tư...");
    private final ComboBox<Option> cboTrangThai;
    private final DataTable table = Ui.table("#", "Mã CT", "Tên công trình", "Địa chỉ", "Chủ đầu tư",
            "Dự kiến", "Thực tế (độ lệch)", "Tiến độ", "Trạng thái", "Người thêm", "Thao tác");
    private List<Site> current = List.of();

    public WorksitePanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        List<Option> st = new ArrayList<>();
        st.add(ALL_STATUS);
        st.addAll(Labels.WORKSITE_STATUSES);
        cboTrangThai = Ui.combo(st);
        Ui.size(cboTrangThai, 170, Ui.TOOLBAR_H);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnTienDo = Ui.secondary("Lịch sử tiến độ", "history");
        Button btnTrangThai = Ui.secondary("Lịch sử trạng thái", "history");
        Button btnThem = Ui.primary("Thêm công trình", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");

        Responsive.Row bar = new Responsive.Row(10);
        bar.center(txtTimKiem).east(Ui.flow(Pos.CENTER_RIGHT, cboTrangThai, btnTim));

        root.north(Ui.stack(
                Ui.header("building-2", "Quản lý công trình", "Thông tin công trình, trạng thái và tiến độ thi công", btnLamMoi, btnTienDo, btnTrangThai, btnThem),
                Ui.card(bar)));
        root.center(Ui.scroll(table));

        table.installBadge(8);
        table.installActions(10, false, () -> { Site s = selected(); if (s != null) showDialog(s); }, this::delete,
                r -> r < current.size() && service.canModify(current.get(r)));   // chỉ người thêm công trình mới được sửa / xóa

        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboTrangThai.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> { txtTimKiem.setText(""); cboTrangThai.getSelectionModel().selectFirst(); reload(); });
        btnThem.setOnAction(e -> showDialog(null));
        btnTienDo.setOnAction(e -> showProgress());
        btnTrangThai.setOnAction(e -> showStatusHistory());
        reload();
    }

    private void reload() {
        Ui.safe(this, () -> {
            current = service.searchSites(txtTimKiem.getText(), cboTrangThai.getValue().code());
            table.setRowCount(0);
            int i = 1;
            for (Site s : current)
                table.addRow(i++, s.code(), s.name(), s.address(), s.investor() == null ? "" : s.investor(),
                        Ui.fmt(s.plannedStart()) + " - " + Ui.fmt(s.plannedEnd()), actualText(s), s.progress().stripTrailingZeros().toPlainString() + "%",
                        Labels.worksiteStatus(s.status()), s.creatorLabel(), "");
        });
    }

    /** Form 02A 2.1.7 – thời gian thực tế kèm độ lệch (ngày) so với dự kiến: dương = trễ, âm = sớm. */
    private static String actualText(Site s) {
        if (s.actualStart() == null && s.actualEnd() == null) return "Chưa có";
        StringBuilder sb = new StringBuilder();
        if (s.actualStart() != null) sb.append(Ui.fmt(s.actualStart())).append(dev(s.plannedStart(), s.actualStart()));
        sb.append(" - ");
        if (s.actualEnd() != null) sb.append(Ui.fmt(s.actualEnd())).append(dev(s.plannedEnd(), s.actualEnd()));
        else sb.append("...");
        return sb.toString();
    }

    private static String dev(LocalDate planned, LocalDate actual) {
        long d = java.time.temporal.ChronoUnit.DAYS.between(planned, actual);
        return " (" + (d > 0 ? "+" : "") + d + " ngày)";
    }

    private Site selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một công trình trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private void showDialog(Site s) {
        if (s != null && !service.canModify(s)) { Ui.error(this, "Bạn chỉ được sửa công trình do chính mình thêm."); return; }
        TextField code = Ui.text(14), name = Ui.text(14), address = Ui.text(14), investor = Ui.text(14),
                progress = Ui.text(14), note = Ui.text(14);
        // Ô ngày có nút mở lịch chọn ngày; ngày thực tế có thể để trống.
        DatePicker pStart = DatePopup.field(s == null ? LocalDate.now() : s.plannedStart(), false);
        DatePicker statusDate = DatePopup.field(LocalDate.now(), false), progressDate = DatePopup.field(LocalDate.now(), false);
        DatePicker pEnd = DatePopup.field(s == null ? null : s.plannedEnd(), false);
        DatePicker aStart = DatePopup.field(s == null ? null : s.actualStart(), true);
        DatePicker aEnd = DatePopup.field(s == null ? null : s.actualEnd(), true);
        ComboBox<Option> status = Ui.combo(Labels.WORKSITE_STATUSES);
        progress.setText("0");
        if (s != null) {
            code.setText(s.code()); name.setText(s.name()); address.setText(s.address()); investor.setText(s.investor());
            progress.setText(s.progress().stripTrailingZeros().toPlainString());
            status.setValue(Labels.option(Labels.WORKSITE_STATUSES, s.status()));
        }
        GridPane f = Ui.form(2, 130,
                new Node[]{Ui.required("Mã công trình"), Ui.required("Tên công trình"), Ui.required("Địa chỉ"), Ui.caption("Chủ đầu tư"),
                        Ui.required("Bắt đầu dự kiến"), Ui.required("Kết thúc dự kiến"), Ui.caption("Bắt đầu thực tế"), Ui.caption("Kết thúc thực tế"),
                        Ui.required("Trạng thái"), Ui.required("Ngày thay đổi trạng thái"), Ui.required("Tiến độ (%)"), Ui.required("Ngày cập nhật tiến độ"), Ui.caption("Ghi chú tiến độ")},
                new Node[]{code, name, address, investor, pStart, pEnd, aStart, aEnd, status, statusDate, progress, progressDate, note});
        Ui.dialog(this, s == null ? "Thêm công trình" : "Sửa công trình", f, () -> {
            service.saveSite(s, code.getText(), name.getText(), address.getText(), investor.getText(),
                    Ui.parseDate(pStart, "Ngày bắt đầu dự kiến", true), Ui.parseDate(pEnd, "Ngày kết thúc dự kiến", true),
                    Ui.parseDate(aStart, "Ngày bắt đầu thực tế", false), Ui.parseDate(aEnd, "Ngày kết thúc thực tế", false),
                    status.getValue().code(), Ui.parseDate(statusDate, "Ngày thay đổi trạng thái", true),
                    progress.getText(), Ui.parseDate(progressDate, "Ngày cập nhật tiến độ", true), note.getText());
            reload();
        });
    }

    private void delete() {
        Site s = selected();
        if (s == null) return;
        if (!service.canModify(s)) { Ui.error(this, "Bạn chỉ được xóa công trình do chính mình thêm."); return; }
        if (!Ui.confirm(this, "Xóa công trình " + s.name() + "?")) return;
        Ui.safe(this, () -> { service.deleteSite(s.id()); reload(); });
    }

    private void showStatusHistory() {
        Site s = selected(); if (s == null) return;
        Ui.safe(this, () -> {
            DataTable t = Ui.table("Ngày thay đổi", "Trạng thái");
            for (Object[] r : service.statusHistory(s.id())) t.addRow(Ui.fmt((LocalDate) r[0]), Labels.worksiteStatus((String) r[1]));
            Ui.showContent(this, "Lịch sử trạng thái - " + s.name(), Ui.scroll(t), 420, 250);
        });
    }

    private void showProgress() {
        Site s = selected();
        if (s == null) return;
        Ui.safe(this, () -> {
            DataTable t = Ui.table("Ngày cập nhật", "Tiến độ (%)", "Ghi chú");
            for (Object[] r : service.progressHistory(s.id()))
                t.addRow(Ui.fmt((LocalDate) r[0]), ((java.math.BigDecimal) r[1]).stripTrailingZeros().toPlainString(),
                        r[2] == null ? "" : r[2]);
            Ui.showContent(this, "Lịch sử tiến độ - " + s.name(), Ui.scroll(t), 520, 260);
        });
    }
}
