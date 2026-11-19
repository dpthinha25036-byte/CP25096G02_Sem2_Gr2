package ctv.congtrinh;

import ctv.congtrinh.SiteModels.Assignment;
import ctv.congtrinh.SiteModels.Site;
import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Option;
import ctv.nhansu.HrModels.Worker;
import ctv.nhansu.HrService;
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

/** Phân công công nhân vào công trình (chức năng 2.3): tạo phân công, kết thúc, hủy kèm lý do. Kiểm tra trùng lịch khi tạo. */
public class AssignmentPanel extends BorderPane {
    private static final Option ALL_STATUS = new Option(null, "Tất cả trạng thái");

    private final SiteService service = new SiteService();
    private final HrService hr = new HrService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã, tên công nhân hoặc công trình...");
    private final ComboBox<Option> cboTrangThai;
    private final DataTable table = Ui.table("#", "Mã CN", "Công nhân", "Công trình", "Bắt đầu",
            "Kết thúc dự kiến", "Kết thúc thực tế", "Trạng thái", "Ghi chú hủy");
    private List<Assignment> current = List.of();

    public AssignmentPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        List<Option> st = new ArrayList<>();
        st.add(ALL_STATUS);
        st.addAll(Labels.ASSIGNMENT_STATUSES);
        cboTrangThai = Ui.combo(st);
        Ui.size(cboTrangThai, 170, Ui.TOOLBAR_H);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnHuy = Ui.cancel("Hủy phân công", "x");
        Button btnKetThuc = Ui.secondary("Kết thúc phân công", "circle-check");
        Button btnSua = Ui.secondary("Sửa phân công", "pencil");
        Button btnThem = Ui.primary("Tạo phân công", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");

        Responsive.Row bar = new Responsive.Row(10);
        bar.center(txtTimKiem).east(Ui.flow(Pos.CENTER_RIGHT, cboTrangThai, btnTim));

        root.north(Ui.stack(
                Ui.header("clipboard-check", "Phân công công nhân", "Phân công công nhân đang làm việc vào công trình đang thi công",
                        btnLamMoi, btnHuy, btnKetThuc, btnSua, btnThem),
                Ui.card(bar)));
        root.center(Ui.scroll(table));
        table.installBadge(7);

        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboTrangThai.setOnAction(e -> reload());
        btnLamMoi.setOnAction(e -> { txtTimKiem.setText(""); cboTrangThai.getSelectionModel().selectFirst(); reload(); });
        btnThem.setOnAction(e -> showDialog(null));
        btnKetThuc.setOnAction(e -> finish());
        btnSua.setOnAction(e -> { Assignment a = selected(); if (a != null && owns(a, "sửa")) showDialog(a); });
        btnHuy.setOnAction(e -> cancel());
        reload();
    }

    private void reload() {
        Ui.safe(this, () -> {
            current = service.assignments(txtTimKiem.getText(), cboTrangThai.getValue().code());
            table.setRowCount(0);
            int i = 1;
            for (Assignment a : current)
                table.addRow(i++, a.workerCode(), a.workerName(), a.worksiteName(), Ui.fmt(a.startDate()),
                        Ui.fmt(a.expectedEnd()), Ui.fmt(a.actualEnd()), Labels.assignmentStatus(a.status()),
                        a.cancelReason() == null ? "" : a.cancelReason());
        });
    }

    /** Chỉ người tạo phân công mới được sửa / kết thúc / hủy; tài khoản khác chỉ xem. */
    private boolean owns(Assignment a, String action) {
        if (service.canModifyAssignment(a)) return true;
        Ui.error(this, "Bạn chỉ được " + action + " phân công do chính mình tạo.");
        return false;
    }

    private Assignment selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một phân công trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private void showDialog(Assignment old) {
        List<Worker> workers = hr.workingWorkers();
        List<Site> sites = service.activeSites();
        if (workers.isEmpty()) { Ui.error(this, "Chưa có công nhân đang làm việc để phân công."); return; }
        if (sites.isEmpty()) { Ui.error(this, "Chưa có công trình nào ở trạng thái \"Đang thi công\"."); return; }
        ComboBox<Worker> worker = Ui.combo(workers);
        ComboBox<Site> site = Ui.combo(sites);
        DatePicker start = DatePopup.field(old == null ? LocalDate.now() : old.startDate(), false);
        DatePicker end = DatePopup.field(old == null ? null : old.expectedEnd(), true);
        if (old != null) {
            for (Worker w : workers) if (w.id() == old.workerId()) worker.setValue(w);
            for (Site s : sites) if (s.id() == old.worksiteId()) site.setValue(s);
        }
        GridPane f = Ui.form(1, 135,
                new Node[]{Ui.required("Công nhân"), Ui.required("Công trình"), Ui.required("Ngày bắt đầu"), Ui.caption("Kết thúc dự kiến")},
                new Node[]{worker, site, start, end});
        Ui.dialog(this, old == null ? "Tạo phân công" : "Sửa phân công", f, () -> {
            if (old == null) service.assign(worker.getValue(), site.getValue(),
                    Ui.parseDate(start, "Ngày bắt đầu", true), Ui.parseDate(end, "Ngày kết thúc dự kiến", false));
            else service.updateAssignment(old, worker.getValue(), site.getValue(),
                    Ui.parseDate(start, "Ngày bắt đầu", true), Ui.parseDate(end, "Ngày kết thúc dự kiến", false));
            reload();
        });
    }

    private void finish() {
        Assignment a = selected(); if (a == null || !owns(a, "kết thúc")) return;
        DatePicker end = DatePopup.field(LocalDate.now(), false);
        GridPane f = Ui.form(new Node[]{Ui.required("Ngày kết thúc thực tế")}, new Node[]{end});
        Ui.dialog(this, "Kết thúc phân công - " + a.workerName(), f, () -> { service.finishAssignment(a, Ui.parseDate(end, "Ngày kết thúc", true)); reload(); });
    }

    private void cancel() {
        Assignment a = selected();
        if (a == null || !owns(a, "hủy")) return;
        TextField reason = Ui.text(24);
        GridPane f = Ui.form(1, 100, new Node[]{Ui.required("Lý do hủy")}, new Node[]{reason});
        Ui.dialog(this, "Hủy phân công - " + a.workerName(), f, () -> { service.cancelAssignment(a, reason.getText()); reload(); });
    }
}
