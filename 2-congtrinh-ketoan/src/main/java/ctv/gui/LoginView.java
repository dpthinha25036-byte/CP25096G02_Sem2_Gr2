package ctv.gui;

import ctv.nhansu.AuthService;
import ctv.util.BusinessException;
import ctv.util.I18n;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/** Màn hình đăng nhập (Form 05 – mục 5.1, frmDangNhap). Đăng nhập thành công thì gọi onSuccess để mở giao diện chính. */
public class LoginView extends BorderPane {
    private final AuthService auth = new AuthService();
    private final Runnable onSuccess;
    private final TextField txtTenDangNhap = Ui.text(20);
    private final PasswordField txtMatKhau = Ui.password(20);
    private final TextField txtMatKhauHien = Ui.text(20);     // hiện mật khẩu: ô chữ chồng lên ô mật khẩu, cùng nội dung
    private final CheckBox chkHienMatKhau = new CheckBox("Hiện mật khẩu");
    private final Label lblThongBao = new Label(" ");
    private final Button btnDangNhap = Ui.primary("Đăng nhập");
    private final Button btnThoat = Ui.cancel("Thoát");

    public LoginView(Stage stage, Runnable onSuccess) {
        this.onSuccess = onSuccess;

        // Nền gradient nằm trong khung cuộn: màn hình thấp/hẹp vẫn thấy đủ thẻ đăng nhập (cuộn được), thẻ luôn ở giữa.
        StackPane bg = new StackPane(buildCard());
        bg.setAlignment(Pos.CENTER);
        bg.setPadding(new Insets(16));
        bg.setStyle("-fx-background-color:linear-gradient(from 0% 0% to 100% 100%, #1A1743, #4A2BD0);");
        Node sp = bg;

        WindowControls bar = new WindowControls(stage, Color.WHITE, () -> System.exit(0));
        HBox strip = new HBox(bar);
        strip.setAlignment(Pos.CENTER_RIGHT);
        strip.setStyle("-fx-background-color:#1A1743;");
        WindowControls.makeDraggable(strip, stage);
        setTop(strip);
        setCenter(sp);

        txtMatKhauHien.textProperty().bindBidirectional(txtMatKhau.textProperty());
        txtMatKhauHien.setVisible(false);
        chkHienMatKhau.selectedProperty().addListener((o, was, on) -> {
            txtMatKhauHien.setVisible(on);
            txtMatKhau.setVisible(!on);
        });
        btnDangNhap.setDefaultButton(true);
        btnDangNhap.setOnAction(e -> doLogin());
        btnThoat.setOnAction(e -> System.exit(0));
        Platform.runLater(txtTenDangNhap::requestFocus);
    }

    private Node buildCard() {
        VBox card = new VBox();
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(24, 32, 24, 32));
        card.setMaxWidth(364);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color:white;-fx-background-radius:28;-fx-background:white;");

        StackPane logo = new StackPane(Ui.icon("users", Color.WHITE, 30));
        logo.setPrefSize(58, 58);
        logo.setMinSize(58, 58);
        logo.setMaxSize(58, 58);
        logo.setStyle("-fx-background-color:linear-gradient(from 0% 0% to 100% 100%, #7C4DFF, #4A2BD0);-fx-background-radius:18;");
        VBox.setMargin(logo, new Insets(0, 0, 14, 0));

        Label title = new Label(I18n.isEnglish() ? "Construction Labor\nManagement System" : "Hệ thống quản lý nhân công\ncông trình");
        title.setFont(Ui.font(FontWeight.BOLD, 22));
        title.setTextFill(Ui.TITLE);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER);
        VBox.setMargin(title, new Insets(0, 0, 6, 0));

        Label sub = new Label(I18n.t("Vui lòng đăng nhập để tiếp tục"));
        sub.setFont(Ui.FONT());
        sub.setTextFill(Ui.PLACEHOLDER);
        VBox.setMargin(sub, new Insets(0, 0, 22, 0));

        Label lblUser = Ui.captionBold("Tên đăng nhập"), lblPass = Ui.captionBold("Mật khẩu");
        for (Label l : new Label[]{lblUser, lblPass}) l.setMaxWidth(Double.MAX_VALUE);
        // Ô nhập bo tròn giống các thanh search (bán kính 10), nền tím rất nhạt, viền mảnh.
        String sq = "-fx-background-radius:10;-fx-border-radius:10;-fx-background-color:#FBFAFE;-fx-border-color:#D8D7F5;";
        txtTenDangNhap.setStyle(sq);
        txtMatKhau.setStyle(sq);
        txtMatKhauHien.setStyle(sq);
        Ui.size(txtTenDangNhap, 300, 40);
        Ui.size(txtMatKhau, 300, 40);
        Ui.size(txtMatKhauHien, 300, 40);
        StackPane pwdBox = new StackPane(txtMatKhau, txtMatKhauHien);
        VBox.setMargin(lblUser, new Insets(0, 0, 4, 0));
        VBox.setMargin(txtTenDangNhap, new Insets(0, 0, 12, 0));
        VBox.setMargin(lblPass, new Insets(0, 0, 4, 0));
        VBox.setMargin(pwdBox, new Insets(0, 0, 6, 0));

        chkHienMatKhau.setText(I18n.t("Hiện mật khẩu"));
        chkHienMatKhau.setFont(Ui.FONT());
        chkHienMatKhau.setTextFill(Ui.TEXT);
        chkHienMatKhau.setMaxWidth(Double.MAX_VALUE);
        chkHienMatKhau.setAlignment(Pos.CENTER_LEFT);   // checkbox nằm bên trái
        VBox.setMargin(chkHienMatKhau, new Insets(0, 0, 6, 0));

        lblThongBao.setFont(Ui.FONT());
        lblThongBao.setTextFill(Ui.DEL_FG);
        lblThongBao.setWrapText(true);
        lblThongBao.setPrefSize(300, 38);
        lblThongBao.setMinHeight(38);
        lblThongBao.setAlignment(Pos.TOP_LEFT);
        VBox.setMargin(lblThongBao, new Insets(0, 0, 8, 0));

        fill(btnDangNhap, 42);
        fill(btnThoat, 42);
        VBox.setMargin(btnDangNhap, new Insets(0, 0, 10, 0));
        VBox.setMargin(btnThoat, new Insets(0, 0, 8, 0));

        Button btnLang = Ui.cancel(I18n.switchLabel());   // Việt <-> Anh
        fill(btnLang, 36);
        btnLang.setTooltip(new javafx.scene.control.Tooltip("Ngôn ngữ / Language"));
        btnLang.setOnAction(e -> {
            I18n.toggle();
            MainApp.showLogin();
        });

        title.setStyle("-fx-text-fill:" + Ui.hex(Ui.TITLE) + ";");
        sub.setStyle("-fx-text-fill:" + Ui.hex(Ui.PLACEHOLDER) + ";");
        lblUser.setStyle("-fx-text-fill:" + Ui.hex(Ui.CAPTION) + ";");
        lblPass.setStyle("-fx-text-fill:" + Ui.hex(Ui.CAPTION) + ";");
        chkHienMatKhau.setStyle("-fx-text-fill:" + Ui.hex(Ui.TEXT) + ";-fx-font-size:13px;");
        lblThongBao.setStyle("-fx-text-fill:" + Ui.hex(Ui.DEL_FG) + ";");
        card.getChildren().addAll(logo, title, sub, lblUser, txtTenDangNhap, lblPass, pwdBox, chkHienMatKhau,
                lblThongBao, btnDangNhap, btnThoat, btnLang);
        return card;
    }

    private static void fill(Button b, double h) {
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(h);
        b.setMinHeight(h);
    }

    private void doLogin() {
        showMessage(" ");
        btnDangNhap.setDisable(true);
        try {
            auth.login(txtTenDangNhap.getText(), txtMatKhau.getText());
        } catch (BusinessException e) {
            showMessage(e.getMessage());
            txtMatKhau.clear();
            (txtTenDangNhap.getText().isBlank() ? txtTenDangNhap : txtMatKhau).requestFocus();
            return;
        } catch (RuntimeException e) {
            showMessage("Lỗi không xác định: " + e);
            return;
        } finally {
            btnDangNhap.setDisable(false);
        }
        onSuccess.run();
    }

    private void showMessage(String msg) { lblThongBao.setText(I18n.t(msg)); }
}
