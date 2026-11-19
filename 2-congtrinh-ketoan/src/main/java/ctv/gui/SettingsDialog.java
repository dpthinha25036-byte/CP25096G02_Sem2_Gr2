package ctv.gui;

import ctv.util.I18n;
import ctv.util.Permissions;
import ctv.util.Session;
import java.io.File;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Hộp thoại "Cài đặt" mở từ nút ở chân thanh bên: thông tin cá nhân, chọn ngôn ngữ Việt / Anh và đăng xuất.
 * Đổi ngôn ngữ / đăng xuất được giao lại cho cửa sổ chính qua hai callback (hộp thoại đóng trước khi gọi).
 */
public final class SettingsDialog {
    private SettingsDialog() {}

    public static void show(Node parent, Runnable onLanguageChanged, Runnable onLogout) {
        Stage d = new Stage(StageStyle.UNDECORATED);
        d.initModality(Modality.APPLICATION_MODAL);
        Window owner = Ui.ownerOf(parent);
        if (owner != null) d.initOwner(owner);

        // ----- thanh tiêu đề (giống các hộp thoại khác) -----
        Label head = new Label(I18n.t("Cài đặt"), Ui.icon("settings", Ui.WHITE, 18));
        head.setFont(Ui.font(FontWeight.BOLD, 14));
        head.setTextFill(Ui.WHITE);
        head.setGraphicTextGap(10);
        Button x = new Button();
        x.setGraphic(Ui.icon("x", Ui.WHITE, 16));
        x.setStyle("-fx-background-color:transparent;-fx-padding:2 6 2 6;");
        x.setCursor(Cursor.HAND);
        x.setOnAction(e -> d.close());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bar = new HBox(head, sp, x);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 8, 8, 14));
        bar.setStyle("-fx-background-color:" + Ui.hex(Ui.DIALOG_TITLE) + ";");

        // ----- nội dung -----
        VBox body = new VBox(8);
        body.setPadding(new Insets(16, 20, 8, 20));
        body.setStyle("-fx-background-color:white;");
        body.getChildren().addAll(section("Thông tin cá nhân"), profileCard(), gap(8),
                section("Ngôn ngữ"), languageRow(d, onLanguageChanged), gap(8));
        if (Session.can("1.1")) {
            body.getChildren().addAll(section("Sao lưu & phục hồi dữ liệu"), backupRow(body), gap(8));
        }
        Button logout = Ui.cancel("Đăng xuất", "x");
        logout.setTextFill(Ui.DEL_FG);
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setPrefHeight(40);
        logout.setOnAction(e -> {
            // Xác nhận ngay trên hộp thoại Cài đặt (chủ sở hữu là hộp này nên luôn hiện phía trước), rồi mới đóng và đăng xuất.
            if (!Ui.confirm(logout, "Bạn có chắc muốn đăng xuất?")) return;
            d.close();
            javafx.application.Platform.runLater(onLogout);
        });
        body.getChildren().add(logout);

        Button close = Ui.cancel("Đóng");
        close.setOnAction(e -> d.close());
        HBox foot = new HBox(close);
        foot.setAlignment(Pos.CENTER);
        foot.setPadding(new Insets(0, 0, 10, 0));
        foot.setStyle("-fx-background-color:white;");

        Rectangle2D scr = Screen.getPrimary().getVisualBounds();
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:white;-fx-background:white;");
        scroll.setPrefViewportWidth(Math.min(450, scr.getWidth() * 0.95));
        scroll.setPrefViewportHeight(Math.min(600, scr.getHeight() * 0.92 - 110));   // tạm; chỉnh theo nội dung bên dưới

        BorderPane root = new BorderPane(scroll);
        root.setTop(bar);
        root.setBottom(foot);
        root.setStyle("-fx-background-color:white;-fx-border-color:" + Ui.hex(Ui.DIALOG_TITLE) + ";");

        double[] press = new double[2];
        bar.setOnMousePressed(e -> { press[0] = e.getSceneX(); press[1] = e.getSceneY(); });
        bar.setOnMouseDragged(e -> { d.setX(e.getScreenX() - press[0]); d.setY(e.getScreenY() - press[1]); });

        Scene scene = new Scene(root);
        Ui.style(scene);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) d.close(); });
        d.setScene(scene);
        I18n.translateTree(root);
        // Thu hộp thoại vừa đủ chứa nội dung (đo sau khi áp CSS và dịch chữ); chỉ cuộn khi cao hơn màn hình.
        root.applyCss();
        root.layout();
        double fit = body.prefHeight(scroll.getPrefViewportWidth() - 2) + 4;
        scroll.setPrefViewportHeight(Math.min(fit, scr.getHeight() * 0.92 - 110));
        d.sizeToScene();
        d.showAndWait();
    }

    private static Region gap(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    /** Hai nút Sao lưu / Phục hồi (chỉ Admin): gọi BackupService (mysqldump / mysql). */
    private static Node backupRow(Node parent) {
        Button b = Ui.cancel("Sao lưu ngay", "save");
        Button r = Ui.cancel("Phục hồi từ tệp...", "arrow-up-from-line");
        b.setOnAction(e -> runInBackground(b, r, ctv.util.BackupService::backup,
                f -> Ui.info(parent, "Đã sao lưu: " + f.getAbsolutePath()),
                ex -> Ui.error(parent, ex.getMessage())));
        r.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File dir = ctv.util.BackupService.backupDir();
            if (dir != null && dir.isDirectory()) fc.setInitialDirectory(dir);
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("Tệp sao lưu (*.sql)"), "*.sql"));
            File chosen = fc.showOpenDialog(Ui.ownerOf(parent));
            if (chosen == null) return;
            if (!Ui.confirm(parent, "Phục hồi sẽ ghi đè dữ liệu hiện tại bằng tệp đã chọn. Hệ thống tự sao lưu bản hiện tại trước. Tiếp tục?")) return;
            runInBackground(b, r, () -> ctv.util.BackupService.restore(chosen),
                    safe -> Ui.info(parent, "Đã phục hồi. Bản trước khi phục hồi: " + safe.getAbsolutePath()),
                    ex -> Ui.error(parent, ex.getMessage()));
        });
        return equalRow(b, r);
    }

    /**
     * Chạy việc chậm (mysqldump / mysql) trên luồng nền để cửa sổ không bị đứng; khóa hai nút trong lúc chạy.
     * Kết quả (thành công / lỗi) được trả về luồng giao diện.
     */
    private static void runInBackground(Button b, Button r, java.util.function.Supplier<File> work,
                                        java.util.function.Consumer<File> onOk,
                                        java.util.function.Consumer<RuntimeException> onFail) {
        javafx.concurrent.Task<File> task = new javafx.concurrent.Task<>() {
            @Override protected File call() { return work.get(); }
        };
        task.setOnSucceeded(ev -> { b.setDisable(false); r.setDisable(false); onOk.accept(task.getValue()); });
        task.setOnFailed(ev -> {
            b.setDisable(false); r.setDisable(false);
            Throwable t = task.getException();
            onFail.accept(t instanceof RuntimeException re ? re : new RuntimeException(String.valueOf(t), t));
        });
        b.setDisable(true); r.setDisable(true);
        Thread th = new Thread(task, "backup-restore");
        th.setDaemon(true);
        th.start();
    }

    private static HBox equalRow(Button a, Button b) {
        // hai nút đều nhau, thẳng lề
        for (Button x : new Button[]{a, b}) {
            x.setMaxWidth(Double.MAX_VALUE);
            x.setPrefHeight(40);
            HBox.setHgrow(x, Priority.ALWAYS);
        }
        return new HBox(10, a, b);
    }

    private static Label section(String text) {
        Label l = Ui.captionBold(text);
        l.setFont(Ui.font(FontWeight.BOLD, 13));
        l.setTextFill(Ui.TITLE);
        return l;
    }

    /** Thẻ thông tin: avatar chữ cái đầu, họ tên, vai trò, tên đăng nhập và các chức năng được phép. */
    private static Node profileCard() {
        GridPane card = new GridPane();
        card.setHgap(14);
        card.setVgap(6);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#F6F5FD;-fx-background-radius:14;-fx-border-color:" + Ui.hex(Ui.BORDER) + ";-fx-border-radius:14;");

        card.add(avatar(initials(Session.fullName())), 0, 0, 1, 2);
        Label name = new Label(Session.fullName());
        name.getProperties().put(I18n.NO_I18N, true);
        name.setFont(Ui.font(FontWeight.BOLD, 16));
        name.setTextFill(Ui.TITLE);
        Label role = new Label(I18n.t(Session.roleName()));
        role.getProperties().put(I18n.NO_I18N, true);
        role.setFont(Ui.FONT());
        role.setTextFill(Ui.PRIMARY);
        card.add(name, 1, 0);
        card.add(role, 1, 1);

        VBox rows = new VBox(6, infoRow("Tên đăng nhập", Session.username(), false), infoRow("Họ và tên", Session.fullName(), false),
                infoRow("Vai trò", I18n.t(Session.roleName()), false));
        StringBuilder fn = new StringBuilder();
        for (Permissions.Function f : Permissions.ALL) {
            if (Session.can(f.code())) fn.append(fn.length() > 0 ? "\n" : "").append("• ").append(I18n.t(f.name()));
        }
        rows.getChildren().add(infoRow("Chức năng được phép", fn.toString(), false));
        GridPane.setMargin(rows, new Insets(12, 0, 0, 0));
        card.add(rows, 0, 2, 2, 1);
        GridPane.setHgrow(name, Priority.ALWAYS);
        return card;
    }

    /** translate = false: giá trị đã được dịch sẵn (hoặc là dữ liệu người dùng) nên không dịch lại. */
    private static Node infoRow(String caption, String value, boolean translate) {
        Label c = Ui.caption(caption);
        c.setMinWidth(130);
        c.setPrefWidth(130);
        c.setAlignment(Pos.TOP_LEFT);
        Label v = new Label(value == null ? "" : value);
        v.setFont(Ui.FONT_BOLD());
        v.setTextFill(Ui.TEXT);
        v.setWrapText(true);
        if (!translate) v.getProperties().put(I18n.NO_I18N, true);
        HBox row = new HBox(10, c, v);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(v, Priority.ALWAYS);
        return row;
    }

    /** Hai nút Tiếng Việt / English; nút của ngôn ngữ đang dùng được tô đậm. */
    private static Node languageRow(Stage d, Runnable onChanged) {
        return equalRow(langButton(d, "Tiếng Việt", I18n.Lang.VI, onChanged), langButton(d, "English", I18n.Lang.EN, onChanged));
    }

    private static Button langButton(Stage d, String label, I18n.Lang lang, Runnable onChanged) {
        boolean current = I18n.language() == lang;
        Button b = current ? Ui.primary(label, "circle-check") : Ui.cancel(label);
        b.setOnAction(e -> {
            if (I18n.language() == lang) return;
            I18n.setLanguage(lang);
            d.close();
            onChanged.run();
        });
        return b;
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String last = parts[parts.length - 1];
        return last.substring(0, 1).toUpperCase();
    }

    /** Hình tròn màu tím có chữ cái đầu của tên. */
    private static Node avatar(String text) {
        Circle c = new Circle(26);
        c.setFill(new javafx.scene.paint.LinearGradient(0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.web("#7C4DFF")), new javafx.scene.paint.Stop(1, Color.web("#4A2BD0"))));
        Label l = new Label(text);
        l.setFont(Ui.font(FontWeight.BOLD, 20));
        l.setTextFill(Color.WHITE);
        return new StackPane(c, l);
    }
}
