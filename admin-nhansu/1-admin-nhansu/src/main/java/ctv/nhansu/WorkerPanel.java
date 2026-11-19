package ctv.nhansu;

import ctv.gui.DataTable;
import ctv.gui.DatePopup;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Option;
import ctv.nhansu.HrModels.Level;
import ctv.nhansu.HrModels.Skill;
import ctv.nhansu.HrModels.Worker;
import ctv.nhansu.HrModels.WorkerType;
import ctv.util.I18n;
import ctv.util.Labels;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

/** Hồ sơ công nhân (chức năng 1.2) – thêm, sửa, xóa, đổi trạng thái, phân loại, tìm kiếm và lọc. */
public class WorkerPanel extends BorderPane {
    private static final Option ALL_STATUS = new Option(null, "Tất cả trạng thái");

    private final HrService service = new HrService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo mã, họ tên, số điện thoại hoặc CCCD...");
    private final ComboBox<Option> cboTrangThai;
    private final ComboBox<Object> cboLoai = new ComboBox<>();
    private final ComboBox<Object> cboKyNang = new ComboBox<>();
    private final ComboBox<Object> cboBac = new ComboBox<>();
    private final DataTable table = Ui.table("#", "Mã CN", "Họ và tên", "Ngày sinh", "Giới tính",
            "CCCD", "Điện thoại", "Loại", "Ngày vào làm", "Trạng thái", "Hình thức trả lương", "Thao tác");
    private List<Worker> current = List.of();
    private java.util.Set<Integer> mine = java.util.Set.of();
    private List<WorkerType> types = List.of();
    private List<Skill> skills = List.of();
    private boolean loading;

    public WorkerPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        List<Option> st = new ArrayList<>();
        st.add(ALL_STATUS);
        st.addAll(Labels.DISPLAY_WORK_STATUSES);
        cboTrangThai = Ui.combo(st);
        Ui.translateCells(cboLoai);
        Ui.translateCells(cboKyNang);
        Ui.translateCells(cboBac);
        Ui.size(cboTrangThai, 170, Ui.TOOLBAR_H);
        Ui.size(cboLoai, 150, Ui.TOOLBAR_H);
        Ui.size(cboKyNang, 160, Ui.TOOLBAR_H);
        Ui.size(cboBac, 160, Ui.TOOLBAR_H);

        Button btnLamMoi = Ui.secondary("Làm mới");
        Button btnThem = Ui.primary("Thêm công nhân", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");

        Node filters = Ui.flow(Pos.CENTER_RIGHT, cboTrangThai, cboLoai, cboKyNang, cboBac, btnTim);
        Responsive.Row bar = new Responsive.Row(10);
        bar.center(txtTimKiem).east(filters);

        root.north(Ui.stack(
                Ui.header("users", "Hồ sơ công nhân", "Quản lý hồ sơ, trạng thái làm việc và phân loại công nhân", btnLamMoi, btnThem),
                Ui.card(bar)));
        root.center(Ui.scroll(table));

        table.installBadge(9);
        table.installActions(11, false,
                () -> { Worker w = selected(); if (w != null) showDialog(w); },
                this::delete,
                idx -> idx < current.size() && mine.contains(current.get(idx).id()));   // chỉ người thêm công nhân mới được sửa / xóa

        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboTrangThai.setOnAction(e -> { if (!loading) reload(); });
        cboLoai.setOnAction(e -> { if (!loading) reload(); });
        cboKyNang.setOnAction(e -> { if (!loading) { loadLevels(); reload(); } });
        cboBac.setOnAction(e -> { if (!loading) reload(); });
        btnLamMoi.setOnAction(e -> {
            loading = true;
            txtTimKiem.setText("");
            cboTrangThai.getSelectionModel().selectFirst();
            if (!cboKyNang.getItems().isEmpty()) cboKyNang.getSelectionModel().selectFirst();
            loadTypes();
            loadSkills();
            loading = false;
            reload();
        });
        btnThem.setOnAction(e -> showDialog(null));
        loadTypes();
        loadSkills();
        reload();
    }

    /** Nạp lại danh sách một ô lọc: dòng đầu là "Tất cả ...", không kích hoạt lọc lại khi nạp. */
    private void fill(ComboBox<Object> c, String all, List<?> items, Object keep) {
        boolean was = loading;
        loading = true;
        List<Object> list = new ArrayList<>();
        list.add(all);
        list.addAll(items);
        c.getItems().setAll(list);
        c.getSelectionModel().selectFirst();
        if (keep != null) for (Object o : items) if (o.equals(keep)) c.setValue(o);
        loading = was;
    }

    /** Danh sách kỹ năng cho ô lọc; giữ kỹ năng đang chọn nếu còn tồn tại. */
    private void loadSkills() {
        Ui.safe(this, () -> {
            Object keep = cboKyNang.getValue();
            skills = service.skills("");
            Skill keepSkill = null;
            if (keep instanceof Skill k) for (Skill s : skills) if (s.id() == k.id()) keepSkill = s;
            fill(cboKyNang, "Tất cả kỹ năng", skills, keepSkill);
            loadLevels();
        });
    }

    /** Bậc thợ phụ thuộc kỹ năng: chưa chọn kỹ năng thì ô bậc thợ bị khóa. */
    private void loadLevels() {
        Object sk = cboKyNang.getValue();
        List<Level> levels = new ArrayList<>();
        if (sk instanceof Skill s) Ui.safe(this, () -> levels.addAll(service.levels(s.id())));
        fill(cboBac, "Tất cả bậc thợ", levels, null);
        cboBac.setDisable(!(sk instanceof Skill));
    }

    private void loadTypes() {
        Ui.safe(this, () -> {
            types = service.workerTypes();
            fill(cboLoai, "Tất cả loại", types, null);
        });
    }

    private void reload() {
        Ui.safe(this, () -> {
            String status = cboTrangThai.getValue().code();
            Object t = cboLoai.getValue();
            Integer typeId = t instanceof WorkerType wt ? wt.id() : null;
            Integer skillId = cboKyNang.getValue() instanceof Skill s ? s.id() : null;
            Integer levelId = cboBac.getValue() instanceof Level l ? l.id() : null;
            current = service.searchWorkers(txtTimKiem.getText(), status, typeId, skillId, levelId);
            mine = ctv.util.Ownership.mine(ctv.util.Ownership.Kind.WORKER, current, Worker::id);
            table.setRowCount(0);
            int i = 1;
            for (Worker w : current)
                table.addRow(i++, w.code(), w.fullName(), Ui.fmt(w.birthDate()), w.gender(), w.idCardNo(),
                        w.phone() == null ? "" : w.phone(), w.typeName(), Ui.fmt(w.hireDate()),
                        Labels.workStatus(w.displayStatus()), w.payMethodLabel(), "");
        });
    }

    private Worker selected() {
        int r = table.getSelectedRow();
        if (r < 0) { Ui.error(this, "Hãy chọn một công nhân trong bảng."); return null; }
        return current.get(table.convertRowIndexToModel(r));
    }

    private void showDialog(Worker w) {
        if (types.isEmpty()) { Ui.error(this, "Chưa có loại công nhân trong CSDL (bảng worker_types)."); return; }
        TextField code = Ui.text(14), name = Ui.text(14), idCard = Ui.text(14), phone = Ui.text(14), address = Ui.text(14);
        DatePicker birth = DatePopup.field(w == null ? null : w.birthDate(), true);
        DatePicker hire = DatePopup.field(w == null ? LocalDate.now() : w.hireDate(), false);
        DatePicker statusDate = DatePopup.field(w == null || w.statusEffectiveDate() == null ? LocalDate.now() : w.statusEffectiveDate(), false);
        ComboBox<Option> gender = Ui.combo(Labels.GENDERS);
        ComboBox<WorkerType> type = Ui.combo(types);
        // Công nhân mới thêm (hoặc chưa có phân công) ở trạng thái "Chờ phân công"; có phân công đang hoạt động thì là "Đang làm việc".
        boolean assigned = w != null && w.hasActiveAssignment();
        ComboBox<Option> status = Ui.combo(List.of(
                assigned ? new Option("WORKING", "Đang làm việc") : new Option(Labels.WAITING, "Chờ phân công"),
                new Option("ON_LEAVE", "Tạm nghỉ"), new Option("RESIGNED", "Đã nghỉ việc")));
        TextField bankNo = Ui.text(14), bankHolder = Ui.text(14);
        Label receive = Ui.caption("Lãnh lương tay");
        Runnable refreshReceive = () -> receive.setText(I18n.t(bankNo.getText().isBlank()
                ? "Lãnh lương tay (chưa có tài khoản)" : "Chuyển khoản vào tài khoản"));
        bankNo.textProperty().addListener((o, a, b) -> refreshReceive.run());
        // Chủ tài khoản mặc định = tên công nhân; tự cập nhật theo ô Họ và tên cho tới khi người dùng tự sửa.
        boolean[] holderTouched = {w != null && w.bankAccountHolder() != null && !w.bankAccountHolder().isBlank()
                && !w.bankAccountHolder().equals(w.fullName())};
        boolean[] syncing = {false};
        bankHolder.textProperty().addListener((o, a, b) -> { if (!syncing[0]) holderTouched[0] = !bankHolder.getText().isBlank(); });
        name.textProperty().addListener((o, a, b) -> {
            if (holderTouched[0]) return;
            syncing[0] = true;
            bankHolder.setText(name.getText().trim());
            syncing[0] = false;
        });
        if (w != null) {
            bankNo.setText(w.bankAccountNo()); syncing[0] = true; bankHolder.setText(w.holderName()); syncing[0] = false;
            code.setText(w.code()); name.setText(w.fullName()); idCard.setText(w.idCardNo());
            phone.setText(w.phone()); address.setText(w.address());
            gender.setValue(Labels.option(Labels.GENDERS, w.gender()));
            for (WorkerType t : types) if (t.id() == w.typeId()) type.setValue(t);
            status.setValue(Labels.option(Labels.DISPLAY_WORK_STATUSES, w.displayStatus()));
        }
        GridPane f = Ui.form(2,
                new Node[]{Ui.required("Mã công nhân"), Ui.required("Họ và tên"), Ui.required("Ngày sinh"),
                        Ui.required("Giới tính"), Ui.required("Số CCCD"), Ui.caption("Điện thoại"),
                        Ui.required("Loại công nhân"), Ui.required("Ngày vào làm"), Ui.required("Trạng thái"), Ui.required("Ngày hiệu lực trạng thái"), Ui.caption("Địa chỉ"),
                        Ui.caption("Số tài khoản"), Ui.caption("Chủ tài khoản"), Ui.caption("Hình thức nhận lương")},
                new Node[]{code, name, birth, gender, idCard, phone, type, hire, status, statusDate, address,
                        bankNo, bankHolder, receive});
        refreshReceive.run();
        Ui.dialog(this, w == null ? "Thêm công nhân" : "Sửa công nhân", f, () -> {
            service.saveWorker(w, code.getText(), name.getText(),
                    Ui.parseDate(birth, "Ngày sinh", true), gender.getValue().code(), idCard.getText(),
                    phone.getText(), address.getText(), type.getValue(),
                    Ui.parseDate(hire, "Ngày vào làm", true), dbStatus(status.getValue().code()),
                    Ui.parseDate(statusDate, "Ngày hiệu lực trạng thái", true),
                    w == null ? null : w.bankName(), bankNo.getText(), bankHolder.getText());
            reload();
        });
    }

    /** "Chờ phân công" chỉ là trạng thái suy ra; trong CSDL vẫn lưu là WORKING. */
    private static String dbStatus(String code) { return Labels.WAITING.equals(code) ? "WORKING" : code; }

    private void delete() {
        Worker w = selected();
        if (w == null || !Ui.confirm(this, "Xóa công nhân " + w.fullName() + "?")) return;
        Ui.safe(this, () -> { service.deleteWorker(w.id()); reload(); });
    }
}
