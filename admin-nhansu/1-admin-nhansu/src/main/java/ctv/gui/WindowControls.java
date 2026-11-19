package ctv.gui;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;

/**
 * Cụm 3 nút điều khiển cửa sổ (thu nhỏ xuống thanh tác vụ, phóng to / khôi phục, đóng) cho cửa sổ không có thanh tiêu đề gốc.
 * Không có chữ tên cửa sổ; kèm hàm cho phép kéo cửa sổ bằng một vùng bất kỳ.
 */
public final class WindowControls extends HBox {
    private enum Kind { MIN, MAX, CLOSE }

    /** onClose chạy khi bấm nút đóng (ví dụ hỏi xác nhận rồi thoát). */
    public WindowControls(Stage stage, Color fg, Runnable onClose) {
        super(2);
        setAlignment(Pos.CENTER_RIGHT);
        Button min = button(Kind.MIN, stage, fg, "Thu nhỏ");
        Button max = button(Kind.MAX, stage, fg, "Phóng to / Khôi phục");
        Button close = button(Kind.CLOSE, stage, fg, "Đóng");
        min.setOnAction(e -> stage.setIconified(true));
        max.setOnAction(e -> {
            if (filled(stage)) restore(stage); else fill(stage);
            max.setGraphic(glyph(Kind.MAX, stage, fg));
        });
        close.setOnAction(e -> onClose.run());
        getChildren().addAll(min, max, close);
    }

    private static double[] saved;   // x, y, rộng, cao trước khi phóng to

    private static javafx.geometry.Rectangle2D area(Stage s) {
        var screens = javafx.stage.Screen.getScreensForRectangle(s.getX(), s.getY(), Math.max(1, s.getWidth()), Math.max(1, s.getHeight()));
        return (screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.get(0)).getVisualBounds();
    }

    /** Cửa sổ đang phủ kín vùng làm việc của màn hình (không tính thanh tác vụ). */
    public static boolean filled(Stage s) {
        var b = area(s);
        return Math.abs(s.getWidth() - b.getWidth()) < 3 && Math.abs(s.getHeight() - b.getHeight()) < 3;
    }

    /** Phóng to đúng vùng làm việc (tự tính, không dùng setMaximized vì cửa sổ không viền bị lệch trên Windows). */
    public static void fill(Stage s) {
        var b = area(s);
        if (!filled(s)) saved = new double[]{s.getX(), s.getY(), s.getWidth(), s.getHeight()};
        s.setX(b.getMinX());
        s.setY(b.getMinY());
        s.setWidth(b.getWidth());
        s.setHeight(b.getHeight());
    }

    public static void restore(Stage s) {
        var b = area(s);
        double w = saved != null ? saved[2] : b.getWidth() * 0.8, h = saved != null ? saved[3] : b.getHeight() * 0.8;
        if (Math.abs(w - b.getWidth()) < 3 && Math.abs(h - b.getHeight()) < 3) { w = b.getWidth() * 0.8; h = b.getHeight() * 0.8; }
        s.setWidth(w);
        s.setHeight(h);
        s.setX(b.getMinX() + (b.getWidth() - w) / 2);
        s.setY(b.getMinY() + (b.getHeight() - h) / 2);
    }

    private static Button button(Kind k, Stage stage, Color fg, String tip) {
        Button b = new Button();
        b.setGraphic(glyph(k, stage, fg));
        b.setTooltip(new Tooltip(ctv.util.I18n.t(tip)));
        b.setFocusTraversable(false);
        b.setPrefSize(40, 28);
        b.setMinSize(40, 28);
        b.setCursor(Cursor.DEFAULT);
        String hover = k == Kind.CLOSE ? "rgba(220,38,38,0.78)"
                : String.format("rgba(%d,%d,%d,0.16)", (int) (fg.getRed() * 255), (int) (fg.getGreen() * 255), (int) (fg.getBlue() * 255));
        String normal = "-fx-background-color:transparent;-fx-background-radius:0;-fx-padding:0;";
        b.setStyle(normal);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + hover + ";-fx-background-radius:0;-fx-padding:0;"));
        b.setOnMouseExited(e -> b.setStyle(normal));
        return b;
    }

    private static Node glyph(Kind kind, Stage stage, Color color) {
        double s = 12;
        javafx.scene.Group g = new javafx.scene.Group();
        switch (kind) {
            case MIN -> g.getChildren().add(line(0, s - 2, s, s - 2, color));
            case MAX -> {
                if (filled(stage)) {
                    g.getChildren().addAll(rect(2, 0, s - 3, s - 3, color), rect(0, 2, s - 3, s - 3, color));
                } else {
                    g.getChildren().add(rect(0, 0, s - 1, s - 1, color));
                }
            }
            case CLOSE -> g.getChildren().addAll(line(0, 0, s, s, color), line(s, 0, 0, s, color));
        }
        return g;
    }

    private static Line line(double x1, double y1, double x2, double y2, Color c) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(c);
        l.setStrokeWidth(1.4);
        l.setStrokeLineCap(StrokeLineCap.BUTT);
        return l;
    }

    private static Rectangle rect(double x, double y, double w, double h, Color c) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setFill(Color.TRANSPARENT);
        r.setStroke(c);
        r.setStrokeWidth(1.4);
        return r;
    }

    /** Cho phép kéo cả cửa sổ khi nhấn giữ chuột trên vùng handle (không kéo khi đang phóng to). */
    public static void makeDraggable(Node handle, Stage stage) {
        double[] off = new double[2];
        handle.setOnMousePressed(e -> { off[0] = stage.getX() - e.getScreenX(); off[1] = stage.getY() - e.getScreenY(); });
        handle.setOnMouseDragged(e -> {
            if (filled(stage)) return;
            stage.setX(e.getScreenX() + off[0]);
            stage.setY(e.getScreenY() + off[1]);
        });
    }
}
