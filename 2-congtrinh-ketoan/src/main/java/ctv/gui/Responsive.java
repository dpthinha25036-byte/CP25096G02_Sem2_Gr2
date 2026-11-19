package ctv.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Các bộ bố cục co giãn theo kích thước cửa sổ, dùng chung cho mọi màn hình (không đụng tới nghiệp vụ):
 * <ul>
 *   <li>{@link Row}: như BorderPane (west / center / east); khi hẹp thì xếp chồng các phần theo chiều dọc.</li>
 *   <li>{@link Grid}: lưới thẻ tự giảm số cột khi hẹp (4 → 2 → 1 cột).</li>
 * </ul>
 * Nhóm nút / bộ lọc tự xuống dòng dùng {@code Ui.flow(...)} (FlowPane).
 */
public final class Responsive {
    private Responsive() {}

    // ------------------------------------------------------------------ Row
    /** west / center / east trên một hàng; không đủ chỗ → các phần xếp chồng, mỗi phần một dòng đầy bề rộng. */
    public static class Row extends Pane {
        private final double gap, centerMin;
        private Node west, center, east;

        public Row(double gap) { this(gap, 200); }

        public Row(double gap, double centerMin) {
            this.gap = Ui.px((int) gap);
            this.centerMin = Ui.px((int) centerMin);
        }

        public Row west(Node n) { west = n; getChildren().add(n); return this; }
        public Row center(Node n) { center = n; getChildren().add(n); return this; }
        public Row east(Node n) { east = n; getChildren().add(n); return this; }

        private Node[] parts() { return new Node[]{west, center, east}; }

        private boolean fits(double width) {
            if (width <= 0) return true;
            double need = 0;
            int n = 0;
            for (Node c : parts()) {
                if (c == null || !c.isManaged() || !c.isVisible()) continue;
                need += c == center ? Math.max(centerMin, c.minWidth(-1)) : c.prefWidth(-1);
                n++;
            }
            return width + 3 >= need + gap * Math.max(0, n - 1);   // +3: làm tròn điểm ảnh khi chia tỉ lệ màn hình
        }

        @Override protected double computePrefWidth(double height) {
            double w = 0;
            int n = 0;
            for (Node c : parts()) {
                if (c == null || !c.isVisible()) continue;
                w += c == center ? Math.max(centerMin, c.prefWidth(-1)) : c.prefWidth(-1);
                n++;
            }
            Insets in = getInsets();
            return w + gap * Math.max(0, n - 1) + in.getLeft() + in.getRight();
        }

        @Override protected double computeMinWidth(double height) {
            double w = 0;
            for (Node c : parts()) if (c != null && c.isVisible()) w = Math.max(w, c.minWidth(-1));
            Insets in = getInsets();
            return w + in.getLeft() + in.getRight();
        }

        @Override protected double computePrefHeight(double width) {
            Insets in = getInsets();
            double inner = width < 0 ? -1 : width - in.getLeft() - in.getRight();
            double h = 0;
            if (inner < 0 || fits(inner)) {
                for (Node c : parts()) {
                    if (c == null || !c.isVisible()) continue;
                    double cw = inner < 0 ? -1 : (c == center ? Math.max(centerMin, inner - sideWidths() - gap * sideCount()) : c.prefWidth(-1));
                    h = Math.max(h, c.prefHeight(cw));
                }
            } else {
                int n = 0;
                for (Node c : parts()) {
                    if (c == null || !c.isVisible()) continue;
                    h += c.prefHeight(inner) + (n++ > 0 ? gap / 2 + 2 : 0);
                }
            }
            return h + in.getTop() + in.getBottom();
        }

        private double sideWidths() {
            double w = 0;
            if (west != null && west.isVisible()) w += west.prefWidth(-1);
            if (east != null && east.isVisible()) w += east.prefWidth(-1);
            return w;
        }

        private int sideCount() {
            int n = 0;
            if (west != null && west.isVisible()) n++;
            if (east != null && east.isVisible()) n++;
            return n;
        }

        /** Đặt con theo bề rộng cho trước, chiều cao ưa thích (không vượt quá hàng), căn giữa theo chiều dọc. */
        private static void placeCentered(Node c, double x, double y, double w, double h) {
            double ch = Math.min(h, c.prefHeight(w));
            c.resizeRelocate(x, y + (h - ch) / 2, w, ch);
        }

        @Override protected void layoutChildren() {
            Insets in = getInsets();
            double x0 = in.getLeft(), y0 = in.getTop();
            double w = getWidth() - in.getLeft() - in.getRight(), h = getHeight() - in.getTop() - in.getBottom();
            if (fits(w)) {
                double left = 0, right = 0;
                double ww = west != null && west.isVisible() ? west.prefWidth(-1) : 0;
                double ew = east != null && east.isVisible() ? east.prefWidth(-1) : 0;
                if (west != null && west.isVisible()) { placeCentered(west, x0, y0, ww, h); left = ww + gap; }
                if (east != null && east.isVisible()) { placeCentered(east, x0 + w - ew, y0, ew, h); right = ew + gap; }
                if (center != null && center.isVisible())
                    placeCentered(center, x0 + left, y0, Math.max(0, w - left - right), h);
            } else {
                double y = y0, g = gap / 2 + 2;
                for (Node c : parts()) {
                    if (c == null || !c.isVisible()) continue;
                    double ch = c.prefHeight(w);
                    c.resizeRelocate(x0, y, w, ch);
                    y += ch + g;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Grid
    /** Lưới các ô đều nhau; số cột = bề rộng / bề rộng ô tối thiểu (tối đa maxCols). */
    public static class Grid extends Pane {
        private final int maxCols;
        private final double gap, minCellW, minCellH;

        public Grid(int maxCols, int gap, int minCellW, int minCellH) {
            this.maxCols = maxCols;
            this.gap = Ui.px(gap);
            this.minCellW = Ui.px(minCellW);
            this.minCellH = Ui.px(minCellH);
        }

        public Grid add(Node n) { getChildren().add(n); return this; }

        private int cols(double width) {
            if (width <= 0) return maxCols;
            int n = (int) Math.max(1, Math.min(maxCols, (width + gap) / (minCellW + gap)));
            return maxCols == 4 && n == 3 ? 2 : n;        // 4 thẻ: tránh bố cục lẻ 3 + 1, chuyển 4 → 2 → 1 cột
        }

        private long visible() { return getChildren().stream().filter(Node::isVisible).count(); }

        private double cellH(double cw) {
            double h = minCellH;
            for (Node c : getChildren()) if (c.isVisible()) h = Math.max(h, c.prefHeight(cw));
            return h;
        }

        @Override protected double computePrefWidth(double height) {
            Insets in = getInsets();
            return maxCols * minCellW + (maxCols - 1) * gap + in.getLeft() + in.getRight();
        }

        @Override protected double computeMinWidth(double height) {
            Insets in = getInsets();
            return minCellW + in.getLeft() + in.getRight();
        }

        @Override protected double computePrefHeight(double width) {
            Insets in = getInsets();
            double w = width < 0 ? computePrefWidth(-1) - in.getLeft() - in.getRight() : width - in.getLeft() - in.getRight();
            int cols = cols(w);
            double cw = (w - gap * (cols - 1)) / cols;
            int rows = (int) Math.max(1, (visible() + cols - 1) / cols);
            return rows * cellH(cw) + (rows - 1) * gap + in.getTop() + in.getBottom();
        }

        @Override protected void layoutChildren() {
            Insets in = getInsets();
            double w = getWidth() - in.getLeft() - in.getRight();
            int cols = cols(w);
            double cw = (w - gap * (cols - 1)) / cols, ch = cellH(cw);
            int i = 0;
            for (Node c : getChildren()) {
                if (!c.isVisible()) continue;
                int r = i / cols, k = i % cols;
                c.resizeRelocate(in.getLeft() + k * (cw + gap), in.getTop() + r * (ch + gap), cw, ch);
                i++;
            }
        }
    }
}
