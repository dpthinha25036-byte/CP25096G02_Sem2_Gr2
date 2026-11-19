package ctv.gui;

import ctv.util.DBConnection;
import ctv.util.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Điểm vào ứng dụng JavaFX. Một cửa sổ (Stage) không có thanh tiêu đề gốc, đổi Scene giữa màn hình đăng nhập và
 * giao diện chính; kiểm tra kết nối CSDL trước khi hiện đăng nhập.
 */
public class MainApp extends Application {
    private static Stage stage;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage primary) {
        stage = primary;
        stage.initStyle(StageStyle.UNDECORATED);   // bỏ thanh tiêu đề gốc; dùng cụm nút WindowControls (thu nhỏ, phóng to, đóng)
        try (java.sql.Connection ignored = DBConnection.get()) {
            // kết nối được → mở ứng dụng
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR,
                    I18n.t(String.valueOf(ex.getMessage())) + "\n\n"
                            + I18n.t("Kiểm tra MySQL, đã chạy database/construction_labor.sql chưa, và biến môi trường DB_URL / DB_USER / DB_PASS."),
                    ButtonType.OK);
            a.setTitle(I18n.t("Không kết nối được CSDL"));
            a.setHeaderText(null);
            a.showAndWait();
            Platform.exit();
            return;
        }
        showLogin();
    }

    public static Stage stage() { return stage; }

    /** Mở màn hình đăng nhập; đăng nhập xong thì mở cửa sổ chính theo quyền của tài khoản. */
    public static void showLogin() {
        LoginView v = new LoginView(stage, MainApp::showMain);
        show(v, "Hệ thống quản lý nhân công công trình - Đăng nhập", 1250, 900, 400, 420);
    }

    public static void showMain() {
        MainView v = new MainView(stage);
        show(v, "Quản Lý Nhân Công Xây Dựng - " + ctv.util.Session.fullName() + " (" + ctv.util.Session.roleName() + ")",
                1280, 780, 800, 520);
        WindowControls.fill(stage);   // mở kín vùng làm việc; nút giữa của WindowControls để khôi phục / phóng to
    }

    /** Đặt cỡ cửa sổ theo vùng làm việc của màn hình: không lớn hơn màn hình; cỡ tối thiểu theo thiết kế. */
    private static void show(Parent root, String title, int prefW, int prefH, int minW, int minH) {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        double w = Math.min(prefW, b.getWidth() * 0.96), h = Math.min(prefH, b.getHeight() * 0.94);
        Scene scene = new Scene(root, w, h);
        Ui.style(scene);
        I18n.translateTree(root);
        stage.setTitle(I18n.t(title));
        stage.setScene(scene);
        stage.setMinWidth(Math.min(minW, b.getWidth()));
        stage.setMinHeight(Math.min(minH, b.getHeight()));
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(b.getMinX() + (b.getWidth() - w) / 2);
        stage.setY(b.getMinY() + (b.getHeight() - h) / 2);
        if (!stage.isShowing()) stage.show();
    }
}
