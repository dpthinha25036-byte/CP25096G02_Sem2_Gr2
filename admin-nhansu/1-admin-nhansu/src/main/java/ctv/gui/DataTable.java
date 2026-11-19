package ctv.gui;

import ctv.util.I18n;
import java.util.List;
import java.util.function.IntPredicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Bảng dữ liệu (thay JTable + DefaultTableModel): mỗi dòng là một Object[]. Chỉ đọc; chữ trong ô được dịch khi hiển thị
 * (dữ liệu giữ nguyên tiếng Việt). Có cột "Thao tác" (Sửa / Xóa) và cột nhãn trạng thái bo tròn như bản Swing.
 */
public class DataTable extends TableView<Object[]> {
    private String[] headers = new String[0];

    public DataTable(String... columns) {
        getStyleClass().add("ctv-table");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);   // giãn kín khung chứa
        setMinSize(0, 0);                                  // skin mặc định đòi cao tối thiểu ~400px làm bảng tràn khỏi màn hình thấp
        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setColumnResizePolicy(UNCONSTRAINED_RESIZE_POLICY);   // bề rộng cột do stretch() tự chia để luôn phủ kín bảng
        widthProperty().addListener((o, a, b) -> stretch());
        heightProperty().addListener((o, a, b) -> Platform.runLater(this::stretch));
        setFixedCellSize(Ui.px(36));
        skinProperty().addListener((o, a, b) -> Platform.runLater(this::stretch));
        getItems().addListener((javafx.collections.ListChangeListener<Object[]>) c -> Platform.runLater(this::stretch));
        setPlaceholder(new Label(""));
        setColumns(columns);
    }

    // ---------- model ----------
    /** Đặt lại toàn bộ cột (dùng cho bảng có số cột thay đổi). */
    public final void setColumns(String... columns) {
        headers = columns.clone();
        getColumns().clear();
        getItems().clear();
        for (int i = 0; i < columns.length; i++) {
            final int idx = i;
            TableColumn<Object[], Object> c = new TableColumn<>(I18n.t(columns[i]));
            c.setSortable(false);
            c.setReorderable(false);
            c.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(
                    idx < cd.getValue().length ? cd.getValue()[idx] : null));
            c.setCellFactory(col -> new TextCell());
            c.setPrefWidth(Ui.px(Math.max(70, columns[i].length() * 9 + 30)));
            c.getProperties().put("base", c.getPrefWidth());
            if ("#".equals(columns[i])) {
                c.setMinWidth(Ui.px(50));
                c.setMaxWidth(Ui.px(50));
            }
            getColumns().add(c);
        }
    }

    public void addRow(Object... row) { getItems().add(row); fitLater(); }

    public void addRow(List<?> row) { addRow(row.toArray()); }

    /** Như DefaultTableModel.setRowCount: 0 → xóa hết các dòng. */
    public void setRowCount(int n) {
        if (n <= 0) getItems().clear();
        else if (n < getItems().size()) getItems().remove(n, getItems().size());
        fitLater();
    }

    public void removeRow(int r) { getItems().remove(r); }
    public int getRowCount() { return getItems().size(); }
    public int getColumnCount() { return headers.length; }
    public Object getValueAt(int r, int c) { return getItems().get(r)[c]; }

    public void setValueAt(Object v, int r, int c) {
        Object[] row = getItems().get(r).clone();
        row[c] = v;
        getItems().set(r, row);
    }

    /** Chỉ số dòng đang chọn, -1 nếu chưa chọn. */
    public int getSelectedRow() { return getSelectionModel().getSelectedIndex(); }
    public int convertRowIndexToModel(int r) { return r; }
    public void selectRow(int r) { getSelectionModel().select(r); }

    // ---------- bề rộng cột ----------
    private boolean fitQueued;

    /** Bề rộng ưa thích của từng cột = rộng hơn giữa tiêu đề và nội dung (tối đa 40 dòng đầu), có chặn trên/dưới. */
    private void fitLater() {
        if (fitQueued) return;
        fitQueued = true;
        Platform.runLater(() -> {
            fitQueued = false;
            Font f = Ui.font(FontWeight.NORMAL, 13), fb = Ui.font(FontWeight.BOLD, 13);
            int rows = Math.min(getItems().size(), 40);
            for (int c = 0; c < getColumns().size(); c++) {
                TableColumn<Object[], ?> col = getColumns().get(c);
                if (col.getMinWidth() == col.getMaxWidth() && col.getMaxWidth() > 0 && col.getMaxWidth() < 1e6) continue;
                double w = textWidth(col.getText(), fb) + Ui.px(36);
                for (int r = 0; r < rows; r++) {
                    Object v = getItems().get(r)[c];
                    if (v != null) w = Math.max(w, textWidth(I18n.t(String.valueOf(v)), f) + Ui.px(16));
                }
                col.setPrefWidth(Math.max(Ui.px(60), Math.min(w, Ui.px(360))));
                col.getProperties().put("base", col.getPrefWidth());
                col.getProperties().remove("last");
            }
            stretch();
        });
    }

    private static boolean capped(TableColumn<Object[], ?> c) { return c.getMaxWidth() < 1000; }

    private static double fixedWidth(TableColumn<Object[], ?> c) {
        return Math.max(c.getMinWidth(), Math.min(c.getPrefWidth(), c.getMaxWidth()));
    }

    /** Bề rộng gốc của cột co giãn: giá trị do fitLater đặt, hoặc giá trị đặt tay từ bên ngoài (khác giá trị stretch đã đặt). */
    private static double baseOf(TableColumn<Object[], ?> c) {
        Object last = c.getProperties().get("last"), base = c.getProperties().get("base");
        if (last instanceof Double l && Math.abs(l - c.getPrefWidth()) < 0.5 && base instanceof Double b) return b;
        c.getProperties().put("base", c.getPrefWidth());
        return c.getPrefWidth();
    }

    /** Chia bề rộng bảng cho các cột (cột có max nhỏ giữ nguyên) để các cột phủ kín khung, không tràn ngang. */
    private void stretch() {
        if (getColumns().isEmpty() || getWidth() <= 0) return;
        double avail = getWidth() - 4 - getColumns().size();   // trừ viền và sai số làm tròn điểm ảnh của từng cột
        boolean vbar = false;
        for (Node n : lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar sb && sb.getOrientation() == Orientation.VERTICAL) {
                if (!sb.getProperties().containsKey("hooked")) {
                    // Thanh cuộn dọc hiện / ẩn SAU khi bố cục xong: nghe thay đổi để chia lại cột, không để cột Thao tác bị che.
                    sb.getProperties().put("hooked", true);
                    sb.visibleProperty().addListener((o, a, b) -> Platform.runLater(this::stretch));
                    sb.widthProperty().addListener((o, a, b) -> Platform.runLater(this::stretch));
                }
                if (sb.isVisible()) { avail -= Math.max(sb.getWidth(), 12); vbar = true; }
            }
        }
        // Dự đoán: số dòng vượt chiều cao bảng thì chắc chắn sẽ có thanh cuộn dọc (chừa chỗ trước khi nó kịp hiện).
        if (!vbar && getHeight() > 0 && getItems().size() * getFixedCellSize() > getHeight() - getFixedCellSize() - 2) avail -= 14;
        java.util.List<TableColumn<Object[], ?>> flex = new java.util.ArrayList<>();
        java.util.Map<TableColumn<Object[], ?>, Double> width = new java.util.HashMap<>();
        double used = 0;
        for (TableColumn<Object[], ?> c : getColumns()) {
            if (capped(c)) { double w = fixedWidth(c); width.put(c, w); used += w; } else flex.add(c);
        }
        // Cột co giãn chia theo tỉ lệ; cột nào nhỏ hơn bề rộng tối thiểu thì giữ ở mức tối thiểu và chia lại phần còn lại.
        for (int pass = 0; pass < 6 && !flex.isEmpty(); pass++) {
            double base = 0;
            for (TableColumn<Object[], ?> c : flex) base += baseOf(c);
            double scale = base <= 0 ? 1 : Math.max(0.3, (avail - used) / base);
            boolean clamped = false;
            for (java.util.Iterator<TableColumn<Object[], ?>> it = flex.iterator(); it.hasNext(); ) {
                TableColumn<Object[], ?> c = it.next();
                double w = baseOf(c) * scale;
                if (w < c.getMinWidth()) { width.put(c, c.getMinWidth()); used += c.getMinWidth(); it.remove(); clamped = true; }
            }
            if (!clamped) {
                for (TableColumn<Object[], ?> c : flex) width.put(c, Math.floor(baseOf(c) * scale));
                flex.clear();
            }
        }
        for (TableColumn<Object[], ?> c : getColumns()) {
            double w = width.getOrDefault(c, c.getPrefWidth());
            c.setPrefWidth(w);
            c.getProperties().put("last", c.getPrefWidth());
        }
    }

    private static double textWidth(String s, Font f) {
        Text t = new Text(s == null ? "" : s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private static final class TextCell extends TableCell<Object[], Object> {
        TextCell() { setAlignment(Pos.CENTER_LEFT); }

        @Override protected void updateItem(Object v, boolean empty) {
            super.updateItem(v, empty);
            setText(empty || v == null ? null : I18n.t(String.valueOf(v)));
        }
    }

    // ---------- cột "Thao tác" ----------
    public void installActions(int col, Runnable onEdit, Runnable onDelete) {
        installActions(col, true, onEdit, onDelete);
    }

    public void installActions(int col, boolean icons, Runnable onEdit, Runnable onDelete) {
        installActions(col, icons, onEdit, onDelete, r -> true);
    }

    /**
     * Cột "Thao tác": hai nút Sửa / Xóa. icons = true: nút vuông icon (5.16); false: nút chữ "Sửa" / icon thùng rác (các bảng khác).
     * Bấm sẽ chọn dòng rồi gọi hàm tương ứng. rowEnabled(chỉ số dòng) = false thì dòng đó không hiện nút (chỉ xem).
     */
    public void installActions(int col, boolean icons, Runnable onEdit, Runnable onDelete, IntPredicate rowEnabled) {
        TableColumn<Object[], ?> tc = getColumns().get(col);
        // lề trái 10 + hai nút + khoảng cách + đệm hai bên của ô (8 + 8) + dư vài điểm ảnh: đủ chỗ để nút Xóa không bị cắt
        double btn = icons ? 30 : 42, gap = icons ? 8 : 6;
        double total = Ui.px((int) (10 + btn * 2 + gap + 16 + 8));
        tc.setMinWidth(total);
        tc.setMaxWidth(total + Ui.px(20));
        @SuppressWarnings("unchecked")
        TableColumn<Object[], Object> oc = (TableColumn<Object[], Object>) tc;
        oc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                if (empty || !rowEnabled.test(getIndex())) { setGraphic(null); return; }
                Button edit = icons ? Ui.iconButton("pencil", Ui.EDIT_FG, Ui.EDIT_BG, 30, 30)
                        : Ui.textButton(I18n.t("Sửa"), Ui.EDIT_FG, Ui.EDIT_BG, 42, 24);
                Button del = icons ? Ui.iconButton("trash-2", Ui.DEL_FG, Ui.DEL_BG, 30, 30)
                        : Ui.iconButton("trash-2", Ui.DEL_FG, Ui.DEL_BG, 42, 24);   // nút Xóa luôn là biểu tượng thùng rác
                edit.setOnAction(e -> { selectRow(getIndex()); onEdit.run(); });
                del.setOnAction(e -> { selectRow(getIndex()); onDelete.run(); });
                HBox box = new HBox(Ui.px(icons ? 8 : 6), edit, del);
                box.setAlignment(Pos.CENTER_LEFT);
                box.setPadding(new Insets(0));
                setGraphic(box);
            }
        });
    }

    // ---------- cột tùy biến ----------
    /** Cột có nút/ô do hàm factory(chỉ số dòng) tạo; width là bề rộng cố định của cột. */
    public void installCells(int col, double width, java.util.function.IntFunction<Node> factory) {
        TableColumn<Object[], ?> tc = getColumns().get(col);
        tc.setMinWidth(width + Ui.px(20));   // + đệm hai bên của ô bảng
        tc.setMaxWidth(width + Ui.px(50));
        @SuppressWarnings("unchecked")
        TableColumn<Object[], Object> oc = (TableColumn<Object[], Object>) tc;
        oc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                setGraphic(empty || getIndex() < 0 || getIndex() >= getItems().size() ? null : factory.apply(getIndex()));
            }
        });
    }

    /** Cột hộp chọn (giá trị Boolean trong dòng); editable(dòng) = false thì ô bị khóa. */
    public void installCheckColumn(int col, IntPredicate editable) {
        TableColumn<Object[], ?> tc = getColumns().get(col);
        @SuppressWarnings("unchecked")
        TableColumn<Object[], Object> oc = (TableColumn<Object[], Object>) tc;
        oc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                if (empty) { setGraphic(null); return; }
                javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox();
                cb.setSelected(Boolean.TRUE.equals(v));
                cb.setDisable(!editable.test(getIndex()));
                int idx = getIndex();
                cb.setOnAction(e -> getItems().get(idx)[col] = cb.isSelected());
                setGraphic(cb);
                setAlignment(Pos.CENTER_LEFT);
            }
        });
    }

    /** Cột chữ đậm có màu theo dòng (ví dụ đỏ khi thiếu nhân lực, xanh khi đủ). */
    public void installColoredText(int col, java.util.function.Function<Object[], Color> colorOf) {
        TableColumn<Object[], ?> tc = getColumns().get(col);
        @SuppressWarnings("unchecked")
        TableColumn<Object[], Object> oc = (TableColumn<Object[], Object>) tc;
        oc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null || getIndex() < 0 || getIndex() >= getItems().size()) { setText(null); return; }
                setText(I18n.t(String.valueOf(v)));
                setFont(Ui.font(FontWeight.BOLD, 13));
                setTextFill(colorOf.apply(getItems().get(getIndex())));
            }
        });
    }

    // ---------- cột nhãn trạng thái ----------
    /** Nhãn trạng thái bo tròn: xanh lá = sẵn sàng/tốt/nhập kho, xanh dương = đang dùng/xuất, cam = bảo trì/cần sửa/thu hồi, đỏ = hỏng. */
    public void installBadge(int col) {
        TableColumn<Object[], ?> tc = getColumns().get(col);
        tc.setMinWidth(Ui.px(130));
        @SuppressWarnings("unchecked")
        TableColumn<Object[], Object> oc = (TableColumn<Object[], Object>) tc;
        oc.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                if (empty || v == null) { setGraphic(null); return; }
                String x = String.valueOf(v);
                Color bg, fg;
                if (x.equals("Đang làm việc") || x.equals("Hoàn thành") || x.equals("Đã kết thúc")) { bg = Color.web("#CAF2DA"); fg = Color.web("#15803D"); }
                else if (x.equals("Chờ phân công")) { bg = Color.web("#D1E2FE"); fg = Color.web("#1D4ED8"); }
                else if (x.contains("Tạm") || x.equals("Chuẩn bị")) { bg = Color.web("#FDDFC7"); fg = Color.web("#B45309"); }
                else if (x.startsWith("Đã nghỉ") || x.equals("Đã hủy")) { bg = Color.web("#FAD4D4"); fg = Color.web("#B42318"); }
                else if (x.contains("Hỏng") || x.contains("khóa")) { bg = Color.web("#FAD4D4"); fg = Color.web("#B42318"); }
                else if (x.contains("Bảo trì") || x.contains("sửa") || x.equals("Thu hồi")) { bg = Color.web("#FDDFC7"); fg = Color.web("#B45309"); }
                else if (x.contains("Đang") || x.equals("Xuất")) { bg = Color.web("#D1E2FE"); fg = Color.web("#1D4ED8"); }
                else { bg = Color.web("#CAF2DA"); fg = Color.web("#15803D"); }
                Label l = new Label(I18n.t(x));
                l.setFont(Ui.font(FontWeight.BOLD, 13));
                l.setTextFill(fg);
                l.setPadding(new Insets(Ui.px(3), Ui.px(14), Ui.px(3), Ui.px(14)));
                l.setStyle("-fx-background-color:" + Ui.hex(bg) + ";-fx-background-radius:20;");
                setGraphic(l);
                setAlignment(Pos.CENTER_LEFT);
                setCursor(Cursor.DEFAULT);
            }
        });
    }
}
