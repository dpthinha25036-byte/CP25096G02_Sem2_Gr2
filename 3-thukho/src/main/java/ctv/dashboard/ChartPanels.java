package ctv.dashboard;

import ctv.gui.Ui;
import ctv.util.I18n;
import java.text.DecimalFormat;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.control.Label;

/** Biểu đồ vẽ bằng Canvas JavaFX (không cần thư viện ngoài) cho Dashboard: cột, vòng (donut) và thanh tiến độ ngang. */
public final class ChartPanels {
    private ChartPanels() {}

    public static final Color[] PALETTE = {
            ctv.gui.Ui.CARD_ACCENTS[0], ctv.gui.Ui.CARD_ACCENTS[1], ctv.gui.Ui.CARD_ACCENTS[2], ctv.gui.Ui.CARD_ACCENTS[3],
            ctv.gui.Ui.BAD, ctv.gui.Ui.SIDEBAR_SELECTED, ctv.gui.Ui.SECONDARY};
    private static final Color GRID = Color.web("#EEEDFA");
    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");

    private static Font small() { return Ui.font(FontWeight.NORMAL, 11); }
    private static Font smallBold() { return Ui.font(FontWeight.BOLD, 11); }

    private static double width(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private abstract static class Chart extends Pane {
        private final Canvas canvas = new Canvas();

        Chart() {
            getChildren().add(canvas);
            setPrefSize(300, 210);
            setMinHeight(150);
        }

        abstract boolean isEmpty();
        abstract void draw(GraphicsContext g, double w, double h);

        @Override protected void layoutChildren() {
            double w = getWidth(), h = getHeight();
            canvas.setWidth(w);
            canvas.setHeight(h);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.clearRect(0, 0, w, h);
            if (w <= 0 || h <= 0) return;
            if (isEmpty()) {
                g.setFont(Ui.FONT());
                g.setFill(Ui.PLACEHOLDER);
                String t = I18n.t("Chưa có dữ liệu");
                g.fillText(t, (w - width(t, Ui.FONT())) / 2, h / 2);
            } else draw(g, w, h);
        }
    }

    /** Khung thẻ trắng bo tròn: tiêu đề + biểu đồ. */
    public static Region card(String title, Region chart) {
        Label l = new Label(I18n.t(title));
        l.setFont(Ui.FONT_BOLD());
        l.setTextFill(Ui.TITLE);
        VBox box = new VBox(6, l, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        box.setPadding(new Insets(12, 14, 12, 14));
        return Ui.rounded(box, 16, Ui.WHITE, Ui.BORDER);
    }

    private static String fmt(double v) { return NUM.format(v); }

    private static String fit(String s, double maxW, Font f) {
        if (width(s, f) <= maxW) return s;
        while (s.length() > 1 && width(s + "…", f) > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Biểu đồ cột dọc một chuỗi số liệu. */
    public static Region bar(List<String> labels, List<Double> values, Color color) {
        return new Chart() {
            @Override boolean isEmpty() { return values.isEmpty() || values.stream().allMatch(v -> v == 0); }

            @Override void draw(GraphicsContext g, double w, double h) {
                double left = 8, right = 8, top = 22, bottom = 24;
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
                double step = niceStep(max);
                double axisMax = Math.ceil(max / step) * step;
                double ph = h - top - bottom, pw = w - left - right;
                g.setFont(small());
                for (int i = 0; i <= 4; i++) {
                    double y = top + ph - ph * i / 4.0;
                    g.setStroke(GRID);
                    g.setLineWidth(1);
                    g.strokeLine(left, y, w - right, y);
                }
                int n = values.size();
                double slot = pw / n;
                double bw = Math.min(46, slot * 0.6);
                for (int i = 0; i < n; i++) {
                    double v = values.get(i);
                    double bh = ph * v / axisMax;
                    double x = left + slot * i + (slot - bw) / 2, y = top + ph - bh;
                    g.setFill(color);
                    if (bh > 0) {
                        g.save();
                        g.beginPath();
                        g.rect(0, 0, w, top + ph);                      // cắt phần bo tròn dưới đáy cho đáy cột phẳng
                        g.clip();
                        g.fillRoundRect(x, y, bw, bh + 8, 8, 8);
                        g.restore();
                    }
                    g.setFont(smallBold());
                    g.setFill(Ui.TEXT);
                    String t = fmt(v);
                    g.fillText(t, x + (bw - width(t, smallBold())) / 2, y - 4);
                    g.setFont(small());
                    g.setFill(Ui.PLACEHOLDER);
                    String lb = fit(I18n.t(labels.get(i)), slot - 2, small());
                    g.fillText(lb, left + slot * i + (slot - width(lb, small())) / 2, h - 8);
                }
            }
        };
    }

    private static double niceStep(double max) {
        double raw = max / 4;
        if (raw <= 1) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / mag;
        return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * mag;
    }

    /** Biểu đồ vòng kèm chú thích (nhãn, số lượng, phần trăm). */
    public static Region donut(List<String> labels, List<Double> values) { return donut(labels, values, null); }

    public static Region donut(List<String> labels, List<Double> values, List<Color> colors) {
        return new Chart() {
            @Override boolean isEmpty() { return values.stream().mapToDouble(Double::doubleValue).sum() == 0; }

            Color col(int i) { return colors != null ? colors.get(i) : PALETTE[i % PALETTE.length]; }

            @Override void draw(GraphicsContext g, double w, double h) {
                double total = values.stream().mapToDouble(Double::doubleValue).sum();
                double d = Math.min(h - 10, w * 0.5), x0 = 6, y0 = (h - d) / 2, thick = Math.max(16, d / 5);
                double start = 90;
                g.setLineCap(StrokeLineCap.BUTT);
                g.setLineWidth(thick);
                for (int i = 0; i < values.size(); i++) {
                    double ext = -360.0 * values.get(i) / total;
                    g.setStroke(col(i));
                    if (ext != 0) g.strokeArc(x0 + thick / 2.0, y0 + thick / 2.0, d - thick, d - thick, start, ext, ArcType.OPEN);
                    start += ext;
                }
                g.setLineWidth(1);
                g.setFill(Ui.TITLE);
                Font big = Ui.font(FontWeight.BOLD, 20);
                g.setFont(big);
                String t = fmt(total);
                g.fillText(t, x0 + (d - width(t, big)) / 2, y0 + d / 2 + 2);
                g.setFont(small());
                g.setFill(Ui.PLACEHOLDER);
                String s = I18n.t("Tổng");
                g.fillText(s, x0 + (d - width(s, small())) / 2, y0 + d / 2 + 18);
                double lx = x0 + d + 18, ly = Math.max(14, (h - values.size() * 22) / 2);
                for (int i = 0; i < values.size(); i++) {
                    g.setFill(col(i));
                    g.fillRoundRect(lx, ly + i * 22, 11, 11, 4, 4);
                    g.setFont(small());
                    g.setFill(Ui.TEXT);
                    int pct = (int) Math.round(100 * values.get(i) / total);
                    String lb = fit(I18n.t(labels.get(i)), Math.max(40, w - lx - 80), small());
                    g.fillText(lb, lx + 17, ly + i * 22 + 10);
                    g.setFont(smallBold());
                    String v = fmt(values.get(i)) + " (" + pct + "%)";
                    g.fillText(v, w - 4 - width(v, smallBold()), ly + i * 22 + 10);
                }
            }
        };
    }

    /** Thanh ngang theo phần trăm (0–100), dùng cho tiến độ công trình. maxValue = 100 → hiển thị dạng %. */
    public static Region hbar(List<String> labels, List<Double> values, double maxValue, String suffix) {
        return new Chart() {
            @Override boolean isEmpty() { return values.isEmpty(); }

            @Override void draw(GraphicsContext g, double w, double h) {
                int n = values.size();
                double rowH = Math.min(30, (h - 4) / Math.max(n, 1));
                double labelW = Math.min(150, w / 3), barX = labelW + 8, barW = w - barX - 56;
                for (int i = 0; i < n; i++) {
                    double y = 4 + i * rowH;
                    g.setFont(small());
                    g.setFill(Ui.TEXT);
                    g.fillText(fit(I18n.t(labels.get(i)), labelW, small()), 2, y + rowH / 2 + 4);
                    g.setFill(GRID);
                    g.fillRoundRect(barX, y + rowH / 2 - 6, barW, 12, 12, 12);
                    double fw = barW * Math.min(1, values.get(i) / maxValue);
                    g.setFill(PALETTE[i % PALETTE.length]);
                    if (fw > 0) g.fillRoundRect(barX, y + rowH / 2 - 6, Math.max(fw, 12), 12, 12, 12);
                    g.setFont(smallBold());
                    g.setFill(Ui.TITLE);
                    g.fillText(fmt(values.get(i)) + suffix, barX + barW + 8, y + rowH / 2 + 4);
                }
            }
        };
    }
}
