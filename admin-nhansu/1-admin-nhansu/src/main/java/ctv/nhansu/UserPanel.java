package ctv.nhansu;

import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import ctv.model.Models.Option;
import ctv.nhansu.AuthModels.Role;
import ctv.nhansu.AuthModels.UserAccount;
import ctv.util.I18n;
import ctv.util.Permissions;
import ctv.util.Session;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Quản lý tài khoản và phân quyền (chức năng 1.3.3 – 1.3.8, Form 05 – mục 5.4, frmTaiKhoan). */
public class UserPanel extends BorderPane {
    private static final Option ALL_STATUS = new Option(null, "Tất cả trạng thái");
    private static final List<Option> STATUSES = List.of(ALL_STATUS,
            new Option("ACTIVE", "Hoạt động"), new Option("LOCKED", "Bị khóa"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> LOGIN_ACTION = Map.of("LOGIN", "Đăng nhập", "LOGOUT", "Đăng xuất");
    private static final Map<String, String> LOGIN_RESULT = Map.of(
            "SUCCESS", "Thành công", "WRONG_PASSWORD", "Sai mật khẩu", "LOCKED", "Tài khoản bị khóa");

    private static final int ACTION_COL = 6;

    private final AuthService service = new AuthService();
    private final TextField txtTimKiem = Ui.searchPlain(30, "Tìm theo tên đăng nhập hoặc họ tên...");
    private final ComboBox<Object> cboVaiTro = new ComboBox<>();
    private final ComboBox<Option> cboTrangThai = Ui.combo(STATUSES);
    private final DataTable table = Ui.table("#", "Tên đăng nhập", "Họ tên", "Vai trò", "Trạng thái", "Tạo lúc", "Thao tác");
    private List<UserAccount> current = List.of();
    private List<Role> roles = List.of();
    private boolean loading;

    public UserPanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Ui.translateCells(cboVaiTro);
        Ui.size(cboVaiTro, 170, Ui.TOOLBAR_H);
        Ui.size(cboTrangThai, 170, Ui.TOOLBAR_H);

        Button btnNhatKy = Ui.secondary("Nhật ký");
        Button btnThem = Ui.primary("Thêm tài khoản", "plus");
        Button btnTim = Ui.primary("Tìm kiếm");

        Responsive.Row bar = new Responsive.Row(10);
        bar.center(txtTimKiem).east(Ui.flow(Pos.CENTER_RIGHT, cboVaiTro, cboTrangThai, btnTim));

        root.north(Ui.stack(
                Ui.header("shield-check", "Tài khoản & phân quyền", "Quản lý tài khoản, khóa/mở khóa, phân quyền và theo dõi nhật ký",
                        btnNhatKy, btnThem),
                Ui.card(bar)));
        root.center(Ui.scroll(table));

        table.installBadge(4);
        installActionColumn();

        btnTim.setOnAction(e -> reload());
        txtTimKiem.setOnAction(e -> reload());
        cboVaiTro.setOnAction(e -> { if (!loading) reload(); });
        cboTrangThai.setOnAction(e -> { if (!loading) reload(); });
        btnThem.setOnAction(e -> showUserDialog(null));
        btnNhatKy.setOnAction(e -> showLogs());
        loadRoles();
        reload();
    }

    private void loadRoles() {
        Ui.safe(this, () -> {
            roles = service.roles();
            loading = true;
            List<Object> items = new ArrayList<>();
            items.add("Tất cả vai trò");
            items.addAll(roles);
            cboVaiTro.getItems().setAll(items);
            cboVaiTro.getSelectionModel().selectFirst();
            loading = false;
        });
    }

    private void reload() {
        Ui.safe(this, () -> {
            Object r = cboVaiTro.getValue();
            Integer roleId = r instanceof Role role ? role.id() : null;
            current = service.search(txtTimKiem.getText(), roleId, cboTrangThai.getValue().code());
            table.setRowCount(0);
            int i = 1;
            for (UserAccount u : current)
                table.addRow(i++, u.username(), u.fullName(), u.roleName(),
                        u.locked() ? "Bị khóa" : "Hoạt động",
                        u.createdAt() == null ? "" : u.createdAt().format(DATE), "");
        });
    }

    private UserAccount at(int row) { return current.get(table.convertRowIndexToModel(row)); }

    // ---------- cột Thao tác: ba nút chữ Sửa / Khóa-Mở / Quyền ----------
    private void installActionColumn() {
        table.installCells(ACTION_COL, 8 + 70 + 70 + 80 + 12, row -> {
            if (row >= current.size()) return null;
            UserAccount u = at(row);
            Button edit = Ui.textButton(I18n.t("Sửa"), Ui.EDIT_FG, Ui.EDIT_BG, 56, 24);
            Button lock = u.locked()
                    ? Ui.textButton(I18n.t("Mở"), Color.web("#15803D"), Color.web("#CAF2DA"), 60, 24)
                    : Ui.textButton(I18n.t("Khóa"), Ui.DEL_FG, Ui.DEL_BG, 60, 24);
            Button perm = Ui.textButton(I18n.t("Quyền"), Ui.PRIMARY, Color.web("#E2D9FD"), 76, 24);
            edit.setOnAction(e -> { table.selectRow(row); showUserDialog(u); });
            lock.setOnAction(e -> { table.selectRow(row); toggleLock(u); });
            perm.setOnAction(e -> { table.selectRow(row); showPermissionDialog(u); });
            HBox box = new HBox(6, edit, lock, perm);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(0, 0, 0, 8));
            return box;
        });
    }

    // ---------- thêm / sửa tài khoản ----------
    private void showUserDialog(UserAccount u) {
        if (roles.isEmpty()) { Ui.error(this, "Chưa có vai trò trong CSDL (bảng roles)."); return; }
        boolean edit = u != null;
        TextField username = Ui.text(14), fullName = Ui.text(14);
        PasswordField password = Ui.password(14);
        ComboBox<Role> role = Ui.combo(roles);
        if (edit) {
            username.setText(u.username());
            username.setDisable(true);
            fullName.setText(u.fullName());
            for (Role r : roles) if (r.id() == u.roleId()) role.setValue(r);
            password.setPromptText(I18n.t("Để trống nếu không đổi"));
            if (u.id() == Session.userId()) role.setDisable(true);
        }
        GridPane f = Ui.form(2,
                new Node[]{Ui.required("Tên đăng nhập"), edit ? Ui.caption("Mật khẩu mới") : Ui.required("Mật khẩu"),
                        Ui.required("Họ tên"), Ui.required("Vai trò")},
                new Node[]{username, password, fullName, role});
        Ui.dialog(this, edit ? "Sửa tài khoản" : "Thêm tài khoản", f, () -> {
            String pw = password.getText();
            if (edit) service.updateUser(u, fullName.getText(), role.getValue(), pw);
            else service.addUser(username.getText(), pw, fullName.getText(), role.getValue());
            reload();
        });
    }

    private void toggleLock(UserAccount u) {
        String verb = u.locked() ? "mở khóa" : "khóa";
        if (!Ui.confirm(this, "Bạn có chắc muốn " + verb + " tài khoản \"" + u.username() + "\"?")) return;
        Ui.safe(this, () -> { service.toggleLock(u); reload(); });
    }

    // ---------- phân quyền ----------
    private void showPermissionDialog(UserAccount u) {
        boolean admin = u.roleId() == Permissions.ROLE_ADMIN;
        @SuppressWarnings("unchecked")
        Set<String>[] holder = new Set[1];
        Ui.safe(this, () -> holder[0] = service.permissionsOf(u));
        if (holder[0] == null) return;
        Set<String> allowed = admin ? Permissions.defaultsFor(u.roleId()) : holder[0];

        DataTable pt = Ui.table("Mã", "Chức năng", "Nhóm", "Cho phép");
        for (Permissions.Function fn : Permissions.ALL)
            pt.addRow(fn.code(), I18n.t(fn.name()), I18n.t(fn.group()), allowed.contains(fn.code()));
        pt.installCheckColumn(3, r -> !admin && !Permissions.isBlocked(u.roleId(), (String) pt.getValueAt(r, 0)));
        pt.getColumns().get(0).setMaxWidth(60);
        pt.getColumns().get(3).setMaxWidth(90);
        Region sc = Ui.scroll(pt);
        Ui.fit(sc, 600, 400);

        Label who = Ui.captionBold("Tài khoản: " + u.username() + " – " + u.fullName() + " (" + u.roleName() + ")");
        VBox north = new VBox(4, who, Ui.caption(admin ? "Admin chỉ dùng chức năng quản trị hệ thống (1.1); quyền được cố định theo vai trò, không truy cập các chức năng nghiệp vụ."
                : "Tích chọn các chức năng được phép; có hiệu lực từ lần đăng nhập tiếp theo."));

        Button reset = Ui.secondary("Đặt theo vai trò mặc định");
        reset.setDisable(admin);
        reset.setOnAction(e -> {
            Set<String> d = Permissions.defaultsFor(u.roleId());
            for (int i = 0; i < pt.getRowCount(); i++) pt.setValueAt(d.contains((String) pt.getValueAt(i, 0)), i, 3);
        });

        BorderPane content = new BorderPane(sc);
        content.setStyle("-fx-background-color:white;");
        content.setTop(north);
        BorderPane.setMargin(north, new Insets(0, 0, 10, 0));
        HBox south = new HBox(reset);
        content.setBottom(south);
        BorderPane.setMargin(south, new Insets(10, 0, 0, 0));

        if (admin) { Ui.dialog(this, "Phân quyền người dùng", content); return; }
        Ui.dialog(this, "Phân quyền người dùng", content, () -> {
            Set<String> chosen = new LinkedHashSet<>();
            for (int i = 0; i < pt.getRowCount(); i++)
                if (Boolean.TRUE.equals(pt.getValueAt(i, 3))) chosen.add((String) pt.getValueAt(i, 0));
            service.savePermissions(u, chosen);
            Ui.info(this, "Đã lưu phân quyền. Quyền mới có hiệu lực từ lần đăng nhập tiếp theo của \"" + u.username() + "\".");
        });
    }

    // ---------- nhật ký (chỉ xem) ----------
    private void showLogs() {
        List<Object[]> logins, audits;
        try {
            logins = service.loginLogs();
            audits = service.auditLogs();
        } catch (RuntimeException e) {
            Ui.error(this, e.getMessage());
            return;
        }
        DataTable lm = Ui.table("#", "Thời điểm", "Tên đăng nhập", "Hành động", "Kết quả");
        int i = 1;
        for (Object[] r : logins)
            lm.addRow(i++, r[0], r[1], LOGIN_ACTION.getOrDefault((String) r[2], (String) r[2]),
                    LOGIN_RESULT.getOrDefault((String) r[3], (String) r[3]));
        DataTable at = Ui.table("#", "Thời điểm", "Người thực hiện", "Bảng", "Mã bản ghi",
                "Thao tác", "Giá trị cũ", "Giá trị mới");
        i = 1;
        for (Object[] r : audits) at.addRow(i++, r[0], r[1], r[2], r[3], r[4], r[5], r[6]);
        at.getColumns().get(6).setPrefWidth(260);
        at.getColumns().get(7).setPrefWidth(260);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab(I18n.t("Nhật ký đăng nhập") + " (" + logins.size() + ")", Ui.scroll(lm)));
        tabs.getTabs().add(new Tab(I18n.t("Nhật ký thay đổi dữ liệu") + " (" + audits.size() + ")", Ui.scroll(at)));

        Stage d = new Stage();
        d.initModality(Modality.APPLICATION_MODAL);
        if (Ui.ownerOf(this) != null) d.initOwner(Ui.ownerOf(this));
        d.setTitle(I18n.t("Nhật ký hệ thống"));
        Button close = Ui.cancel("Đóng");
        close.setOnAction(e -> d.close());
        HBox foot = new HBox(close);
        foot.setAlignment(Pos.CENTER);
        BorderPane p = new BorderPane(tabs);
        p.setBottom(foot);
        BorderPane.setMargin(foot, new Insets(10, 0, 0, 0));
        p.setPadding(new Insets(12, 14, 12, 14));
        p.setStyle("-fx-background-color:" + Ui.hex(Ui.CONTENT_BG) + ";");
        Ui.fit(p, 1000, 560);
        Scene scene = new Scene(p);
        Ui.style(scene);
        d.setScene(scene);
        I18n.translateTree(p);
        d.showAndWait();
    }
}
