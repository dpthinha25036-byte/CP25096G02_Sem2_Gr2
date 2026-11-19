package ctv.gui;

import ctv.congtrinh.AssignmentPanel;
import ctv.congtrinh.AttendancePanel;
import ctv.congtrinh.DemandPanel;
import ctv.congtrinh.PaymentPanel;
import ctv.congtrinh.PayrollPanel;
import ctv.congtrinh.ShortagePanel;
import ctv.congtrinh.WorksitePanel;
import ctv.dashboard.DashboardPanel;
import ctv.nhansu.AuthService;
import ctv.util.I18n;
import ctv.util.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/**
 * Cửa sổ chính: menu bên tối (sidebar) + vùng nội dung theo Form 04 / Form 05.
 * Menu chỉ hiện các chức năng (function_code) mà người dùng đăng nhập được phép; màn hình được tạo khi mở lần đầu.
 */
public class MainView extends BorderPane {
    private static final int SIDE_WIDE = 240, SIDE_COMPACT = 68, COMPACT_BELOW = 1100;

    private final Stage stage;
    private final StackPane content = new StackPane();
    private final List<NavButton> menuItems = new ArrayList<>();
    private final Set<String> built = new HashSet<>();
    private final List<Label> groupLabels = new ArrayList<>();
    private final ScrollPane sideScroll;
    private final VBox side = new VBox(2);
    private final Label brandText;
    private final Label who;
    private final Node brandIcon;
    private final VBox brand;
    private boolean compact;

    public MainView(Stage stage) {
        this.stage = stage;
        content.setStyle("-fx-background-color:" + Ui.hex(Ui.CONTENT_BG) + ";");

        side.setStyle("-fx-background-color:" + Ui.hex(Ui.SIDEBAR) + ";");
        side.setPadding(new Insets(18, 10, 14, 10));
        sideScroll = new ScrollPane(side);
        sideScroll.setFitToWidth(true);
        sideScroll.setFitToHeight(true);
        sideScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sideScroll.setStyle("-fx-background-color:" + Ui.hex(Ui.SIDEBAR) + ";-fx-background:" + Ui.hex(Ui.SIDEBAR) + ";");
        sideScroll.setPrefWidth(SIDE_WIDE);
        sideScroll.setMinWidth(SIDE_WIDE);
        sideScroll.setMaxWidth(SIDE_WIDE);

        brandIcon = Ui.icon("box", Ui.SIDEBAR_SELECTED, 44);
        brandText = new Label(I18n.isEnglish() ? "Construction Labor\nManagement" : "Quản Lý Nhân Công\nXây Dựng");
        brandText.setFont(Ui.font(FontWeight.BOLD, 18));
        brandText.setTextFill(Ui.WHITE);
        brandText.setTextAlignment(TextAlignment.CENTER);
        brandText.setAlignment(Pos.CENTER);
        brand = new VBox(8, new StackPane(brandIcon), brandText);
        brand.setAlignment(Pos.CENTER);
        VBox.setMargin(brand, new Insets(0, 0, 22, 0));
        side.getChildren().add(brand);

        addPage("Dashboard", "house", () -> new DashboardPanel(this::openPage));
        if (Session.can("2.1", "2.2", "2.3", "2.4")) side.getChildren().add(group("CÔNG TRÌNH"));
        addPage("Quản lý công trình", "building-2", WorksitePanel::new, "2.1");
        addPage("Nhu cầu nhân lực", "clipboard-list", DemandPanel::new, "2.2");
        addPage("Phân công công nhân", "clipboard-check", AssignmentPanel::new, "2.3");
        addPage("Cảnh báo nhân lực", "triangle-alert", ShortagePanel::new, "2.4");
        if (Session.can("2.5", "2.6", "2.7", "2.8", "2.9", "2.10")) side.getChildren().add(group("CHẤM CÔNG & LƯƠNG"));
        // Ba tab cũ của "Chấm công & lương" giờ là ba mục riêng trên thanh bên.
        addPage("Chấm công & kiểm tra", "calendar-check", AttendancePanel::new, "2.5", "2.6");
        addPage("Kỳ lương & chốt sổ", "lock", PayrollPanel::new, "2.7", "2.8", "2.10");
        addPage("Thanh toán lương", "banknote", PaymentPanel::new, "2.9");

        Region glue = new Region();
        VBox.setVgrow(glue, Priority.ALWAYS);
        side.getChildren().add(glue);

        who = new Label(Session.fullName() + "\n" + I18n.t(Session.roleName()));
        who.getProperties().put(I18n.NO_I18N, true);   // tên người dùng không dịch; vai trò đã dịch ở trên
        who.setFont(Ui.FONT());
        who.setTextFill(Ui.WHITE);
        // Nút Cài đặt nhỏ (chỉ icon bánh răng) nằm ngang hàng với tên và vai trò người dùng.
        Button btnSettings = new Button();
        btnSettings.setGraphic(Ui.icon("settings", Ui.WHITE, 20));
        btnSettings.setTooltip(new Tooltip(I18n.t("Cài đặt")));
        btnSettings.setStyle("-fx-background-color:transparent;-fx-padding:4;");
        btnSettings.setCursor(Cursor.HAND);
        btnSettings.setOnAction(e -> SettingsDialog.show(this, this::switchLanguage, this::logout));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox userBox = new HBox(8, who, sp, btnSettings);
        userBox.setAlignment(Pos.CENTER_LEFT);
        userBox.setPadding(new Insets(12));
        userBox.setStyle("-fx-background-color:" + Ui.hex(Ui.SIDEBAR_USER) + ";");
        side.getChildren().add(userBox);

        setLeft(sideScroll);

        WindowControls controls = new WindowControls(stage, Ui.TEXT, () -> {
            if (Ui.confirm(this, "Bạn có chắc muốn thoát ứng dụng?")) System.exit(0);
        });
        HBox strip = new HBox(controls);
        strip.setAlignment(Pos.CENTER_RIGHT);
        strip.setStyle("-fx-background-color:" + Ui.hex(Ui.CONTENT_BG) + ";");
        WindowControls.makeDraggable(strip, stage);
        BorderPane right = new BorderPane(content);
        right.setTop(strip);
        setCenter(right);

        menuItems.get(0).fire();   // mở sẵn Dashboard (trang chủ)

        widthProperty().addListener((o, was, w) -> applyCompact(w.doubleValue() < COMPACT_BELOW));
    }

    /** Cửa sổ hẹp: thanh bên chỉ còn icon (rê chuột để xem tên); đủ rộng thì hiện đầy đủ chữ. */
    private void applyCompact(boolean on) {
        if (on == compact) return;
        compact = on;
        double w = on ? SIDE_COMPACT : SIDE_WIDE;
        sideScroll.setPrefWidth(w);
        sideScroll.setMinWidth(w);
        sideScroll.setMaxWidth(w);
        side.setPadding(on ? new Insets(12, 6, 14, 6) : new Insets(18, 10, 14, 10));
        brandText.setVisible(!on);
        brandText.setManaged(!on);
        brand.getChildren().set(0, new StackPane(Ui.icon("box", Ui.SIDEBAR_SELECTED, on ? 30 : 44)));
        for (Label g : groupLabels) { g.setVisible(!on); g.setManaged(!on); }
        for (NavButton n : menuItems) {
            n.setText(on ? null : n.label);
            n.setTooltip(on ? new Tooltip(n.label) : null);
            n.setAlignment(on ? Pos.CENTER : Pos.CENTER_LEFT);
            n.setPadding(on ? new Insets(9, 0, 9, 0) : new Insets(9, 14, 9, 14));
        }
        who.setVisible(!on);
        who.setManaged(!on);
    }

    private Label group(String text) {
        Label l = new Label(I18n.t(text));
        l.setFont(Ui.font(FontWeight.BOLD, 11));
        l.setTextFill(Ui.SIDEBAR_GROUP);
        l.setPadding(new Insets(6, 0, 6, 12));
        l.setMaxWidth(Double.MAX_VALUE);
        groupLabels.add(l);
        return l;
    }

    /** Nút menu bên: nền trong suốt, mục đang chọn là khối bo tròn màu tím nhạt (Form 05). */
    private static final class NavButton extends Button {
        final String label;

        NavButton(String text, String icon) {
            super(I18n.t(text), Ui.icon(icon, Ui.WHITE, 18));
            this.label = I18n.t(text);
            getStyleClass().add("nav-btn");
            setFont(Ui.FONT());
            setGraphicTextGap(12);
            setMaxWidth(Double.MAX_VALUE);
            setPrefHeight(38);
            setMinHeight(38);
            setFocusTraversable(false);
            setCursor(Cursor.HAND);
        }

        void setActive(boolean on) {
            getStyleClass().remove("active");
            if (on) getStyleClass().add("active");
        }
    }

    /** Thêm một mục menu; bỏ qua nếu người dùng không có quyền nào trong codes. Màn hình được tạo khi mở lần đầu. */
    private void addPage(String text, String icon, Supplier<Node> factory, String... codes) {
        if (codes.length > 0 && !Session.can(codes)) return;
        NavButton b = new NavButton(text, icon);
        final Node[] page = {null};
        b.setOnAction(e -> {
            if (page[0] == null) {
                try {
                    page[0] = factory.get();
                    I18n.translateTree(page[0]);
                    content.getChildren().add(page[0]);
                } catch (RuntimeException ex) {
                    Ui.error(this, "Không mở được màn hình \"" + text + "\": " + ex.getMessage());
                    return;
                }
            }
            for (Node n : content.getChildren()) n.setVisible(n == page[0]);
            for (NavButton m : menuItems) m.setActive(false);
            b.setActive(true);
        });
        menuItems.add(b);
        side.getChildren().add(b);
    }

    /** Mở mục menu đầu tiên trong danh sách mà tài khoản được phép (dùng khi bấm ô số liệu ở Dashboard). */
    private void openPage(String[] names) {
        for (String name : names) {
            String t = I18n.t(name);
            for (NavButton b : menuItems) {
                if (b.label.equals(t)) { b.fire(); return; }
            }
        }
    }

    /** Sau khi đổi ngôn ngữ Việt <-> Anh: dựng lại cửa sổ chính với ngôn ngữ mới (giữ nguyên phiên đăng nhập). */
    private void switchLanguage() {
        // Ngôn ngữ đã được SettingsDialog đặt xong (I18n.setLanguage); ở đây chỉ dựng lại cửa sổ.
        MainApp.showMain();
    }

    private void logout() {
        // Việc xác nhận đã được hỏi trong hộp thoại Cài đặt trước khi gọi hàm này.
        try {
            new AuthService().logout();
        } catch (RuntimeException ex) {
            Session.logout();   // lỗi ghi nhật ký không được giữ người dùng lại trong phiên cũ
        }
        MainApp.showLogin();
    }
}
