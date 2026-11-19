package ctv.gui;

import ctv.util.BusinessException;
import ctv.util.I18n;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Chuẩn giao diện theo Form 04 (màu, font Segoe UI) và các hàm tiện ích dùng chung cho mọi màn hình (JavaFX). */
public final class Ui {
    private Ui() {}

    // ---------- màu theo Form 04 ----------
    public static final Color PRIMARY = Color.web("#5C23E2");
    public static final Color SECONDARY = Color.web("#5343DE");
    public static final Color SIDEBAR = Color.web("#1A1743");
    public static final Color SIDEBAR_SELECTED = Color.web("#9E65F3");
    public static final Color CONTENT_BG = Color.web("#F7F6FE");
    public static final Color WHITE = Color.WHITE;
    public static final Color TITLE = Color.web("#21117E");
    public static final Color INPUT_BG = Color.web("#FBFAFE");
    public static final Color BORDER = Color.web("#D8D7F5");
    public static final Color CANCEL_BG = Color.web("#ECEAF9");
    public static final Color TABLE_HEADER = Color.web("#CCC2EA");
    public static final Color TEXT = Color.web("#292757");
    public static final Color CAPTION = Color.web("#2B1E73");
    public static final Color PLACEHOLDER = Color.web("#7A79A8");
    public static final Color DIALOG_TITLE = Color.web("#503AD9");
    public static final Color WINDOW_TITLE = Color.web("#DDD9F6");
    public static final Color EDIT_BG = Color.web("#DCE8FF"), EDIT_FG = Color.web("#2563EB");
    public static final Color DEL_BG = Color.web("#FDE2E2"), DEL_FG = Color.web("#DC2626");
    public static final Color[] CARD_COLORS = {Color.web("#E2D9FD"), Color.web("#D1E2FE"), Color.web("#CAF2DA"), Color.web("#FDDFC7")};
    /** Màu icon tròn trên thẻ thống kê (cùng thứ tự với CARD_COLORS). */
    public static final Color[] CARD_ACCENTS = {Color.web("#5C23E2"), Color.web("#2F6FEB"), Color.web("#1FA463"), Color.web("#F59A23")};

    /** Màu trạng thái (tốt / cảnh báo / lỗi) lấy cùng bảng màu icon thẻ thống kê và nút Xóa. */
    public static final Color GOOD = CARD_ACCENTS[2], WARN = CARD_ACCENTS[3], BAD = DEL_FG;
    /** Nền ô người dùng và nhãn nhóm trên thanh menu bên (tông sáng hơn SIDEBAR một bậc). */
    public static final Color SIDEBAR_USER = Color.web("#2A2663"), SIDEBAR_GROUP = Color.web("#B9B5E8");

    private static String family;

    /** Font Segoe UI nếu máy có (Windows), không thì font mặc định của hệ thống (macOS / Linux). */
    public static Font font(FontWeight w, int size) {
        if (family == null) {
            family = Font.getDefault().getFamily();
            if (Font.getFamilies().contains("Segoe UI")) family = "Segoe UI";
        }
        return Font.font(family, w, size);
    }

    // Form 04 ghi 11 pt (nhãn, bảng) và 20 pt (tiêu đề); dùng 13 cho dễ đọc như bản Swing.
    public static Font FONT() { return font(FontWeight.NORMAL, 13); }
    public static Font FONT_BOLD() { return font(FontWeight.BOLD, 13); }
    public static Font FONT_TITLE() { return font(FontWeight.BOLD, 20); }

    /** JavaFX tự co giãn theo DPI nên điểm ảnh thiết kế giữ nguyên. */
    public static int px(int v) { return v; }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    /** Đặt kích thước ưa thích cho một Region. */
    public static <T extends Region> T size(T r, double w, double h) {
        r.setPrefSize(w, h);
        return r;
    }

    /** Như {@link #size} nhưng không vượt quá ~90% / 80% vùng làm việc của màn hình (hộp thoại xem bảng trên màn hình nhỏ). */
    public static <T extends Region> T fit(T r, int w, int h) {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        r.setPrefSize(Math.min(w, b.getWidth() * 0.9), Math.min(h, b.getHeight() * 0.8));
        return r;
    }

    /** Gắn bảng kiểu chung của ứng dụng vào Scene. */
    public static void style(Scene s) {
        var url = Ui.class.getResource("/ctv.css");
        if (url != null) s.getStylesheets().add(url.toExternalForm());
    }

    // ---------- icon ----------
    public static Node icon(String name, Color color, int size) { return Icons.icon(name, color, size); }

    // ---------- chữ ----------
    public static Label title(String text) {
        Label l = new Label(I18n.t(text));
        l.setFont(font(FontWeight.BOLD, 20));
        l.setTextFill(TITLE);
        return l;
    }

    public static Label caption(String text) {
        Label l = new Label(I18n.t(text));
        l.setFont(FONT());
        l.setTextFill(CAPTION);
        return l;
    }

    public static Label captionBold(String text) {
        Label l = new Label(I18n.t(text));
        l.setFont(FONT_BOLD());
        l.setTextFill(CAPTION);
        return l;
    }

    private static HBox withStar(Label l) {
        Label star = new Label("*");
        star.setFont(l.getFont());
        star.setTextFill(Color.RED);
        HBox h = new HBox(3, l, star);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Nhãn có dấu * đỏ (trường bắt buộc). */
    public static HBox required(String text) { return withStar(caption(text)); }
    public static HBox requiredBold(String text) { return withStar(captionBold(text)); }

    // ---------- khung nội dung ----------
    /** Khung nội dung của một màn hình: nền xám tím, lề 16/20, các vùng north / center / south cách nhau 12px. */
    public static class Content extends BorderPane {
        public Content() {
            setStyle("-fx-background-color:" + hex(CONTENT_BG) + ";");
            setPadding(new Insets(16, 20, 16, 20));
        }

        public Content north(Node n) { setTop(n); BorderPane.setMargin(n, new Insets(0, 0, 12, 0)); return this; }
        public Content center(Node n) { setCenter(n); return this; }
        public Content south(Node n) { setBottom(n); BorderPane.setMargin(n, new Insets(12, 0, 0, 0)); return this; }
    }

    public static Content contentPanel() { return new Content(); }

    /** Bố cục một thẻ con trong màn hình: thanh lọc ở trên, bảng ở dưới, cách nhau 10px, cách tiêu đề 10px. */
    public static void tab(BorderPane p, Node top, Node center) {
        p.setPadding(new Insets(10, 0, 0, 0));
        p.setTop(top);
        BorderPane.setMargin(top, new Insets(0, 0, 10, 0));
        p.setCenter(center);
    }

    /** Dòng đầu màn hình theo Form 05: tiêu đề bên trái; ngày hiện tại + các nút thao tác bên phải. */
    public static Node header(String iconName, String title, String subtitle, Node... actions) {
        Responsive.Row north = new Responsive.Row(10, 0);
        north.west(heading(iconName, title, subtitle));
        if (actions.length >= 4) {
            // Nhiều nút thao tác: ngày hiện tại ở hàng đầu, các nút xuống hàng riêng để không đè lên tiêu đề.
            north.east(flow(Pos.CENTER_RIGHT, todayChip()));
            VBox wrap = new VBox(8, north, flow(Pos.CENTER_RIGHT, actions));
            return wrap;
        }
        List<Node> all = new ArrayList<>();
        all.add(todayChip());
        all.addAll(Arrays.asList(actions));
        north.east(flow(Pos.CENTER_RIGHT, all.toArray(new Node[0])));
        return north;
    }

    /** Xếp các khối theo chiều dọc, cách nhau 10px. */
    public static VBox stack(Node... parts) {
        VBox p = new VBox(10, parts);
        return p;
    }

    /** Đầu màn hình: icon + tiêu đề (căn trái) và tiêu đề phụ ngay bên dưới. */
    public static Node heading(String iconName, String title, String subtitle) {
        VBox t = new VBox(2, title(title), caption(subtitle));
        t.setAlignment(Pos.CENTER_LEFT);
        HBox p = new HBox(12, icon(iconName, PRIMARY, 40), t);
        p.setAlignment(Pos.CENTER_LEFT);
        return p;
    }

    /** Ô hiển thị ngày hiện tại ở đầu màn hình. */
    public static Label todayChip() {
        String s = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy",
                Locale.forLanguageTag(I18n.isEnglish() ? "en" : "vi")));
        Label l = new Label(s, icon("calendar", TITLE, 16));
        l.setFont(FONT());
        l.setTextFill(TITLE);
        l.setGraphicTextGap(8);
        l.setPadding(new Insets(7, 12, 7, 12));
        l.setStyle("-fx-background-color:white;-fx-border-color:" + hex(BORDER) + ";");
        return l;
    }

    /** Nhóm nút / bộ lọc tự xuống dòng khi hết chỗ. */
    public static FlowPane flow(Pos align, Node... comps) {
        FlowPane p = new FitFlow(8, 4, comps);
        p.setPrefWrapLength(5000);   // chiều cao ưa thích (khi chưa biết bề rộng) tính theo một dòng
        p.setAlignment(align);
        p.setRowValignment(javafx.geometry.VPos.CENTER);
        return p;
    }

    /**
     * FlowPane có bề rộng ưa thích = tổng bề rộng các con xếp một dòng (không phải prefWrapLength). Nhờ vậy Responsive.Row
     * biết bộ lọc thật sự cần bao nhiêu chỗ: đủ chỗ thì nằm cùng hàng với ô tìm kiếm, hẹp thì mới xuống dòng.
     */
    private static final class FitFlow extends FlowPane {
        FitFlow(double hgap, double vgap, Node... children) { super(hgap, vgap, children); }

        @Override protected double computePrefWidth(double forHeight) {
            if (forHeight >= 0) return super.computePrefWidth(forHeight);
            double w = 0;
            int n = 0;
            for (Node c : getChildren()) {
                if (!c.isManaged()) continue;
                w += Math.ceil(c.prefWidth(-1));   // FlowPane xếp theo kích thước đã làm tròn lên từng con
                n++;
            }
            Insets in = getInsets();
            // +4: dư vài điểm ảnh để làm tròn / chia tỉ lệ màn hình không làm nút cuối bị đẩy xuống dòng
            return Math.ceil(w + getHgap() * Math.max(0, n - 1) + in.getLeft() + in.getRight()) + 4;
        }
    }

    // ---------- nút ----------
    public static Button primary(String text) { return button(text, PRIMARY, WHITE); }
    public static Button secondary(String text) { return button(text, SECONDARY, WHITE); }
    public static Button cancel(String text) { return button(text, CANCEL_BG, TEXT); }

    public static Button primary(String text, String iconName) { return withIcon(primary(text), iconName, WHITE); }
    public static Button secondary(String text, String iconName) { return withIcon(secondary(text), iconName, WHITE); }
    public static Button cancel(String text, String iconName) { return withIcon(cancel(text), iconName, TEXT); }

    private static Button withIcon(Button b, String iconName, Color c) {
        b.setGraphic(icon(iconName, c, 16));
        b.setGraphicTextGap(8);
        return b;
    }

    /** Nút bo tròn hai đầu, đổi màu nhẹ khi rê chuột, mờ đi khi bị vô hiệu hóa (kiểu .ctv-btn trong ctv.css). */
    private static Button button(String text, Color bg, Color fg) {
        Button b = new Button(I18n.t(text));
        b.getStyleClass().add("ctv-btn");
        b.setFont(FONT_BOLD());
        b.setTextFill(fg);
        b.setStyle("-fx-background-color:" + hex(bg) + ";-fx-background-radius:18;-fx-padding:8 16 8 16;");
        b.setCursor(Cursor.HAND);
        b.setFocusTraversable(false);
        return b;
    }

    /** Nút vuông / chữ nhật bo góc chỉ có icon (cột Thao tác, nút X của hộp thoại...). */
    public static Button iconButton(String iconName, Color fg, Color bg, int w, int h) {
        Button b = new Button();
        b.setGraphic(icon(iconName, fg, 16));
        b.getStyleClass().add("ctv-btn");
        b.setPrefSize(w, h);
        b.setMinSize(w, h);
        b.setMaxSize(w, h);
        b.setStyle("-fx-background-color:" + hex(bg) + ";-fx-background-radius:" + (h >= 30 ? 8 : 12) + ";-fx-padding:0;");
        b.setCursor(Cursor.HAND);
        b.setFocusTraversable(false);
        return b;
    }

    public static Button textButton(String text, Color fg, Color bg, int w, int h) {
        Button b = new Button(text);
        b.getStyleClass().add("ctv-btn");
        b.setFont(font(FontWeight.BOLD, 12));
        b.setTextFill(fg);
        b.setPrefSize(w, h);
        b.setMinSize(w, h);
        b.setMaxSize(w, h);
        b.setStyle("-fx-background-color:" + hex(bg) + ";-fx-background-radius:12;-fx-padding:0;");
        b.setCursor(Cursor.HAND);
        b.setFocusTraversable(false);
        return b;
    }

    // ---------- ô nhập ----------
    public static TextField text(int cols) {
        TextField t = new TextField();
        t.setPrefColumnCount(cols);
        t.getStyleClass().add("ctv-input");
        return t;
    }

    /** Ô nhập mật khẩu cùng kiểu với {@link #text(int)}. */
    public static PasswordField password(int cols) {
        PasswordField t = new PasswordField();
        t.setPrefColumnCount(cols);
        t.getStyleClass().add("ctv-input");
        return t;
    }

    /** Chiều cao chung của ô tìm kiếm, ô lọc và nút trên thanh tìm kiếm (Form 05). */
    public static final int TOOLBAR_H = 42;

    /** Ô tìm kiếm có chữ gợi ý (placeholder). Dùng {@link #withSearchIcon} để hiện biểu tượng kính lúp trong ô. */
    public static TextField search(int cols, String placeholder) {
        TextField t = searchPlain(cols, placeholder);
        t.setPadding(new Insets(7, 8, 7, 34));   // chừa chỗ cho biểu tượng
        return t;
    }

    /** Ô tìm kiếm kèm biểu tượng kính lúp bên trái (Form 05 – Quản lý thiết bị). */
    public static Node withSearchIcon(TextField t) {
        Node ic = icon("search", Color.web("#7A79A8"), 16);
        ic.setMouseTransparent(true);
        StackPane p = new StackPane(t, ic);
        StackPane.setAlignment(ic, Pos.CENTER_LEFT);
        StackPane.setMargin(ic, new Insets(0, 0, 0, 12));
        t.setMaxWidth(Double.MAX_VALUE);
        return p;
    }

    /**
     * Thanh tìm kiếm / bộ lọc theo Form 05: thẻ trắng, ô tìm kiếm giãn hết chỗ trống bên trái, các ô lọc và nút Tìm kiếm
     * nằm bên phải cùng một hàng (hẹp quá thì xuống dòng). Mọi ô và nút có cùng chiều cao.
     */
    public static Region searchBar(Node search, Node... controls) {
        Responsive.Row bar = new Responsive.Row(10);
        bar.center(search);
        if (controls.length > 0) bar.east(flow(Pos.CENTER_RIGHT, controls));
        return card(bar);
    }

    private static void normalizeHeights(Node n) {
        if (n instanceof javafx.scene.control.ComboBox<?> || n instanceof javafx.scene.control.TextField
                || n instanceof javafx.scene.control.DatePicker || n instanceof Button) {
            Region r = (Region) n;
            r.setMinHeight(TOOLBAR_H);
            r.setPrefHeight(TOOLBAR_H);
            r.setMaxHeight(TOOLBAR_H);
            return;
        }
        if (n instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) normalizeHeights(c);
    }

    public static TextField searchPlain(int cols, String placeholder) {
        TextField t = text(cols);
        t.setPromptText(I18n.t(placeholder));
        t.setMinHeight(TOOLBAR_H);
        t.setPrefHeight(TOOLBAR_H);   // cùng chiều cao với các ô lọc / nút bên cạnh để không bị lệch hàng
        return t;
    }

    /** ComboBox hiển thị chữ đã dịch; chọn sẵn phần tử đầu (nếu có). */
    public static <T> ComboBox<T> combo(List<T> items) {
        ComboBox<T> c = new ComboBox<>();
        c.getItems().setAll(items);
        translateCells(c);
        if (!items.isEmpty()) c.getSelectionModel().selectFirst();
        return c;
    }

    /** Dịch chữ hiển thị của ComboBox (cả ô đóng lẫn danh sách thả xuống). */
    public static <T> void translateCells(ComboBox<T> c) {
        c.getStyleClass().add("ctv-input");
        c.setButtonCell(new TranslatedCell<>());
        c.setCellFactory(v -> new TranslatedCell<>());
    }

    private static final class TranslatedCell<T> extends ListCell<T> {
        @Override protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : I18n.t(String.valueOf(item)));
        }
    }

    public static Spinner<Integer> intSpinner(int value, int min, int max) {
        Spinner<Integer> s = new Spinner<>();
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value, 1));
        s.setEditable(true);
        // Gõ tay xong rồi bấm ra ngoài: ghi nhận giá trị (mặc định JavaFX không tự ghi nhận).
        s.focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                try {
                    s.getValueFactory().setValue(Integer.parseInt(s.getEditor().getText().trim()));
                } catch (NumberFormatException e) {
                    s.getEditor().setText(String.valueOf(s.getValue()));
                }
            }
        });
        return s;
    }

    // ---------- ngày ----------
    public static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Ô chọn ngày dd/MM/yyyy có nút mở lịch. initial = null → để trống. Đọc giá trị bằng dateOf / parseDate. */
    public static DatePicker datePicker(LocalDate initial) { return DatePopup.field(initial, false); }

    /** Ô nhập ngày dd/MM/yyyy gõ tay (có chữ gợi ý). initial = null → để trống. */
    public static TextField dateText(LocalDate initial) {
        TextField t = text(12);
        t.setPromptText("dd/MM/yyyy");
        if (initial != null) t.setText(initial.format(DATE_FMT));
        return t;
    }

    /** Bộ chuyển đổi dd/MM/yyyy cho DatePicker (chuỗi sai định dạng → null, việc báo lỗi do parseDate đảm nhiệm). */
    static StringConverter<LocalDate> dateConverter() {
        return new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : d.format(DATE_FMT); }

            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s.trim(), DATE_FMT); } catch (DateTimeParseException e) { return null; }
            }
        };
    }

    public static LocalDate dateOf(DatePicker t) { return parseDate(t, "Ngày", true); }

    public static LocalDate dateOf(TextInputControl t) { return parseDate(t, "Ngày", true); }

    public static void setDate(DatePicker t, LocalDate d) { t.setValue(d); t.getEditor().setText(d == null ? "" : d.format(DATE_FMT)); }

    public static void setDate(TextInputControl t, LocalDate d) { t.setText(d == null ? "" : d.format(DATE_FMT)); }

    public static LocalDate parseDate(DatePicker t, String label, boolean required) { return parseText(t.getEditor().getText(), label, required); }

    public static LocalDate parseDate(TextInputControl t, String label, boolean required) { return parseText(t.getText(), label, required); }

    /** Đọc ngày dd/MM/yyyy. Để trống → null (nếu required thì báo lỗi); sai định dạng → BusinessException. */
    private static LocalDate parseText(String raw, String label, boolean required) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            if (required) throw new BusinessException(label + " không được để trống.");
            return null;
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + " phải đúng dạng dd/MM/yyyy (ví dụ 25/12/2026).");
        }
    }

    /** Ngày hiển thị trong bảng (null → chuỗi rỗng). */
    public static String fmt(LocalDate d) { return d == null ? "" : d.format(DATE_FMT); }

    /** Số tiền hiển thị có dấu phân cách hàng nghìn, ví dụ 350.000. */
    public static String money(java.math.BigDecimal v) {
        return v == null ? "" : String.format(Locale.forLanguageTag("vi"), "%,.0f", v);
    }

    // ---------- bảng ----------
    public static DataTable table(String... columns) { return new DataTable(columns); }

    /** Bảng (hoặc nội dung bất kỳ) đặt trong khung trắng bo góc viền nhạt theo Form 05. */
    public static Region scroll(Node c) {
        Node inner = c;
        if (!(c instanceof TableView) && !(c instanceof ScrollPane)) {
            ScrollPane sp = new ScrollPane(c);
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background-color:transparent;-fx-background:white;");
            inner = sp;
        }
        return rounded(inner, 14, WHITE, BORDER);
    }

    /** Cho phép node con giãn kín khung chứa (StackPane chỉ giãn con tới maxWidth / maxHeight của con). */
    private static void fill(Node n) {
        if (n instanceof Region r) r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** Khung nền bo góc, cắt nội dung con theo góc bo. */
    public static StackPane rounded(Node inner, int arc, Color bg, Color border) {
        StackPane p = new StackPane(inner);
        fill(inner);
        p.setMinSize(0, 0);
        p.setStyle("-fx-background-color:" + hex(bg) + ";-fx-background-radius:" + arc + ";"
                + (border == null ? "" : "-fx-border-color:" + hex(border) + ";-fx-border-radius:" + arc + ";"));
        Rectangle clip = new Rectangle();
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        clip.widthProperty().bind(p.widthProperty());
        clip.heightProperty().bind(p.heightProperty());
        p.setClip(clip);
        return p;
    }

    // ---------- thẻ, bố cục ----------
    /** Thẻ thống kê: icon tròn bên trái + tiêu đề nhỏ + số lớn. */
    public static Region statCard(String iconName, Color accent, String caption, Label valueLabel, Color bg) {
        valueLabel.setFont(font(FontWeight.BOLD, 26));
        valueLabel.setTextFill(TITLE);
        valueLabel.setStyle("-fx-text-fill:" + hex(TITLE) + ";");
        Label cap = caption(caption);
        cap.setStyle("-fx-text-fill:" + hex(CAPTION) + ";");
        VBox text = new VBox(2, cap, valueLabel);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox p = new HBox(14);
        p.setAlignment(Pos.CENTER_LEFT);
        p.setPadding(new Insets(12, 16, 12, 16));
        p.setStyle("-fx-background-color:" + hex(bg) + ";-fx-background-radius:18;");
        if (iconName != null) {
            Circle circle = new Circle(26, accent);
            StackPane badge = new StackPane(circle, icon(iconName, WHITE, 26));
            p.getChildren().add(badge);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        p.getChildren().add(text);
        return p;
    }

    public static Region statCard(String caption, Label valueLabel, Color bg) { return statCard(null, null, caption, valueLabel, bg); }

    /** Khung trắng viền nhạt (dùng cho thanh tìm kiếm / bộ lọc). */
    public static Region card(Node inner) {
        StackPane p = new StackPane(inner);
        fill(inner);
        normalizeHeights(inner);
        p.setAlignment(Pos.CENTER_LEFT);
        p.setPadding(new Insets(10, 12, 10, 12));
        p.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:" + hex(BORDER) + ";-fx-border-radius:14;");
        return p;
    }

    /** Phân trang có số trang: ‹ 1 2 3 › (page tính từ 0). */
    public static void pager(Pane host, int page, int total, int pageSize, IntConsumer onPage) {
        host.getChildren().clear();
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        Button prev = cancel("‹");
        prev.setDisable(page <= 0);
        prev.setOnAction(e -> onPage.accept(page - 1));
        host.getChildren().add(prev);
        int start = Math.max(0, Math.min(page - 2, pages - 5)), end = Math.min(pages, start + 5);
        for (int i = start; i < end; i++) {
            final int n = i;
            Button b = i == page ? primary(String.valueOf(i + 1)) : cancel(String.valueOf(i + 1));
            b.setOnAction(e -> onPage.accept(n));
            host.getChildren().add(b);
        }
        Button next = cancel("›");
        next.setDisable(page + 1 >= pages);
        next.setOnAction(e -> onPage.accept(page + 1));
        host.getChildren().add(next);
    }

    /** Dựng biểu mẫu nhiều cột: nhãn bên trái ô nhập, nhãn có độ rộng cố định để các ô thẳng hàng. */
    public static GridPane form(int cols, Node[] labels, Node[] fields) { return form(cols, 115, labels, fields); }

    public static GridPane form(int cols, int labelWidth, Node[] labels, Node[] fields) {
        GridPane p = new GridPane();
        p.setHgap(12);
        p.setVgap(10);
        p.setStyle("-fx-background-color:white;");
        for (int c = 0; c < cols; c++) {
            ColumnConstraints lc = new ColumnConstraints(labelWidth, labelWidth, labelWidth);
            ColumnConstraints fc = new ColumnConstraints();
            fc.setHgrow(Priority.ALWAYS);
            fc.setFillWidth(true);
            fc.setMinWidth(120);
            p.getColumnConstraints().addAll(lc, fc);
        }
        for (int i = 0; i < labels.length; i++) {
            int col = i % cols, row = i / cols;
            p.add(labels[i], col * 2, row);
            if (fields[i] instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(fields[i], Priority.ALWAYS);
            p.add(fields[i], col * 2 + 1, row);
        }
        return p;
    }

    /** Biểu mẫu một cột. */
    public static GridPane form(Node[] labels, Node[] fields) { return form(1, labels, fields); }

    // ---------- thông báo ----------
    /** Cửa sổ chứa node (có thể null nếu node chưa được gắn vào cửa sổ nào). */
    public static Window ownerOf(Node n) {
        return n != null && n.getScene() != null ? n.getScene().getWindow() : null;
    }

    private static Alert alert(Node parent, Alert.AlertType type, String title, String msg, ButtonType... buttons) {
        Alert a = new Alert(type, I18n.t(msg), buttons);
        a.setTitle(I18n.t(title));
        a.setHeaderText(null);
        Window w = ownerOf(parent);
        if (w != null) a.initOwner(w);
        style(a.getDialogPane().getScene());
        return a;
    }

    public static void error(Node parent, String msg) {
        box(parent, "Lỗi", "triangle-alert", messageLabel(msg), new String[]{I18n.isEnglish() ? "OK" : "Đồng ý"}, 0);
    }

    public static void info(Node parent, String msg) {
        box(parent, "Thông báo", "circle-check", messageLabel(msg), new String[]{I18n.isEnglish() ? "OK" : "Đồng ý"}, 0);
    }

    public static boolean confirm(Node parent, String msg) {
        return box(parent, "Xác nhận", "triangle-alert", messageLabel(msg),
                new String[]{I18n.isEnglish() ? "Yes" : "Có", I18n.isEnglish() ? "No" : "Không"}, 0) == 0;
    }

    private static Label messageLabel(String msg) {
        Label l = new Label(I18n.t(msg));
        l.setFont(FONT());
        l.setStyle("-fx-text-fill:" + hex(TEXT) + ";");
        l.setWrapText(true);
        l.setMinWidth(300);
        l.setMaxWidth(460);
        return l;
    }

    /**
     * Hộp thoại thông báo / xác nhận tự dựng (cùng kiểu thanh tiêu đề tím với các hộp thoại khác) thay cho Alert của JavaFX
     * (Alert bị hiển thị trống trên cửa sổ không viền). Trả về chỉ số nút đã bấm, -1 nếu đóng bằng X / Esc.
     */
    private static int box(Node parent, String title, String iconName, Node content, String[] labels, int primaryIdx) {
        Stage d = new Stage(StageStyle.UNDECORATED);
        d.initModality(Modality.APPLICATION_MODAL);
        Window owner = ownerOf(parent);
        if (owner != null) d.initOwner(owner);
        int[] res = {-1};

        Label head = new Label(I18n.t(title), icon(iconName, WHITE, 18));
        head.setFont(font(FontWeight.BOLD, 14));
        head.setTextFill(WHITE);
        head.setStyle("-fx-text-fill:white;");
        head.setGraphicTextGap(10);
        Button x = new Button();
        x.setGraphic(icon("x", WHITE, 16));
        x.setStyle("-fx-background-color:transparent;-fx-padding:2 6 2 6;");
        x.setCursor(Cursor.HAND);
        x.setOnAction(e -> d.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(head, spacer, x);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 8, 8, 14));
        bar.setStyle("-fx-background-color:" + hex(DIALOG_TITLE) + ";");

        HBox foot = new HBox(10);
        foot.setAlignment(Pos.CENTER);
        foot.setPadding(new Insets(6, 0, 14, 0));
        foot.setStyle("-fx-background-color:white;");
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = i == primaryIdx ? primary(labels[i]) : cancel(labels[i]);
            b.setMinWidth(84);
            if (i == primaryIdx) b.setDefaultButton(true);
            b.setOnAction(e -> { res[0] = idx; d.close(); });
            foot.getChildren().add(b);
        }

        StackPane pad = new StackPane(content);
        pad.setAlignment(Pos.CENTER_LEFT);
        pad.setPadding(new Insets(20, 24, 12, 24));
        pad.setStyle("-fx-background-color:white;-fx-background:white;");

        BorderPane root = new BorderPane(pad);
        root.setTop(bar);
        root.setBottom(foot);
        root.setStyle("-fx-background-color:white;-fx-background:white;-fx-border-color:" + hex(DIALOG_TITLE) + ";");

        double[] press = new double[2];
        bar.setOnMousePressed(e -> { press[0] = e.getSceneX(); press[1] = e.getSceneY(); });
        bar.setOnMouseDragged(e -> { d.setX(e.getScreenX() - press[0]); d.setY(e.getScreenY() - press[1]); });

        Scene scene = new Scene(root);
        style(scene);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) d.close(); });
        d.setScene(scene);
        I18n.translateTree(root);
        d.showAndWait();
        return res[0];
    }

    /** Hộp thoại chỉ xem (nút Đóng) chứa một node, thay JOptionPane.showMessageDialog(PLAIN_MESSAGE) với bảng bên trong. */
    public static void showContent(Node parent, String title, Region content, int w, int h) {
        Stage d = new Stage();
        d.initModality(Modality.APPLICATION_MODAL);
        Window owner = ownerOf(parent);
        if (owner != null) d.initOwner(owner);
        d.setTitle(I18n.t(title));
        fit(content, w, h);
        Button close = cancel("Đóng");
        close.setOnAction(e -> d.close());
        HBox foot = new HBox(close);
        foot.setAlignment(Pos.CENTER);
        BorderPane root = new BorderPane(content);
        root.setBottom(foot);
        BorderPane.setMargin(foot, new Insets(10, 0, 0, 0));
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color:white;");
        Scene scene = new Scene(root);
        style(scene);
        d.setScene(scene);
        I18n.translateTree(root);
        d.showAndWait();
    }

    /** Đổi chuỗi HTML đơn giản của bản Swing (b, br, html, &amp; &lt; &gt;) thành văn bản thuần nhiều dòng cho Label. */
    public static String htmlToText(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    /** Hộp thoại OK / Hủy chứa một node (thay JOptionPane.showConfirmDialog OK_CANCEL với bảng bên trong). */
    public static boolean chooseContent(Node parent, String title, Region content, int w, int h) {
        fit(content, w, h);
        return box(parent, title, "box", content,
                new String[]{I18n.isEnglish() ? "OK" : "Đồng ý", I18n.isEnglish() ? "Cancel" : "Hủy"}, 0) == 0;
    }

    /** Hộp thoại nhiều lựa chọn; trả về chỉ số nút đã bấm, -1 nếu đóng hộp thoại. */
    public static int options(Node parent, String msg, String title, String... labels) {
        return box(parent, title, "triangle-alert", messageLabel(msg), labels, 0);
    }

    /** Chạy một thao tác, mọi lỗi nghiệp vụ / dữ liệu hiển thị thành hộp thoại thay vì làm sập chương trình. */
    public static void safe(Node parent, Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            error(parent, e.getMessage());
        } catch (RuntimeException e) {
            error(parent, "Lỗi không xác định: " + e);
        }
    }

    // ---------- hộp thoại ----------
    public static boolean dialog(Node parent, String title, Node content) { return dialog(parent, title, content, null); }

    /**
     * Hộp thoại theo Form 04: thanh tiêu đề chữ trắng nền #503AD9, nội dung nền trắng, nút Lưu / Hủy căn giữa ở cuối.
     * Việc kiểm tra + lưu chạy NGAY TRONG hộp thoại: bấm Lưu mà dữ liệu sai/thiếu (BusinessException hoặc lỗi khác) thì hiện
     * thông báo lỗi và GIỮ NGUYÊN form; chỉ khi onSave chạy xong không lỗi thì hộp thoại mới đóng.
     * Trả về true nếu đã lưu thành công, false nếu người dùng bấm Hủy / đóng form.
     */
    public static boolean dialog(Node parent, String title, Node content, Runnable onSave) {
        Stage d = new Stage(StageStyle.UNDECORATED);
        d.initModality(Modality.APPLICATION_MODAL);
        Window owner = ownerOf(parent);
        if (owner != null) d.initOwner(owner);
        boolean[] ok = {false};

        Label head = new Label(I18n.t(title), icon("box", WHITE, 18));
        head.setFont(font(FontWeight.BOLD, 14));
        head.setTextFill(WHITE);
        head.setGraphicTextGap(10);
        Button x = new Button();
        x.setGraphic(icon("x", WHITE, 16));
        x.setStyle("-fx-background-color:transparent;-fx-padding:2 6 2 6;");
        x.setCursor(Cursor.HAND);
        x.setOnAction(e -> d.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(head, spacer, x);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 8, 8, 14));
        bar.setStyle("-fx-background-color:" + hex(DIALOG_TITLE) + ";");

        Button save = primary("Lưu", "save"), cancelBtn = cancel("Hủy", "x");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            if (onSave != null) {
                try {
                    onSave.run();
                } catch (BusinessException ex) {
                    error(bar, ex.getMessage());
                    return;                       // giữ form mở
                } catch (RuntimeException ex) {
                    error(bar, "Lỗi không xác định: " + ex);
                    return;                       // giữ form mở
                }
            }
            ok[0] = true;
            d.close();
        });
        cancelBtn.setOnAction(e -> d.close());
        HBox foot = new HBox(8, save, cancelBtn);
        foot.setAlignment(Pos.CENTER);
        foot.setPadding(new Insets(4, 0, 10, 0));
        foot.setStyle("-fx-background-color:white;");

        Rectangle2D scr = Screen.getPrimary().getVisualBounds();
        StackPane pad = new StackPane(content);
        pad.setPadding(new Insets(14, 16, 6, 16));
        pad.setStyle("-fx-background-color:white;");
        // Bọc trong khung cuộn: hộp thoại cao/rộng hơn màn hình nhỏ thì cuộn, không bị cắt mất nút Lưu / Hủy.
        ScrollPane body = new ScrollPane(pad);
        body.setFitToWidth(true);
        body.setStyle("-fx-background-color:white;-fx-background:white;");
        double maxH = scr.getHeight() * 0.92 - 110, maxW = scr.getWidth() * 0.95;
        body.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        BorderPane root = new BorderPane(body);
        root.setTop(bar);
        root.setBottom(foot);
        root.setStyle("-fx-background-color:white;-fx-border-color:" + hex(DIALOG_TITLE) + ";");

        // kéo hộp thoại bằng thanh tiêu đề (vì đã bỏ khung hệ điều hành)
        double[] press = new double[2];
        bar.setOnMousePressed(e -> { press[0] = e.getSceneX(); press[1] = e.getSceneY(); });
        bar.setOnMouseDragged(e -> { d.setX(e.getScreenX() - press[0]); d.setY(e.getScreenY() - press[1]); });

        Scene scene = new Scene(root);
        style(scene);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) d.close(); });
        d.setScene(scene);
        I18n.translateTree(root);
        // Đo kích thước SAU khi đã áp CSS và dịch chữ: đo sớm thì ô nhập / nhãn chưa có kiểu nên cao thấp hơn thực tế,
        // làm hộp thoại bị thu nhỏ và phải cuộn. Chỉ cuộn khi nội dung thật sự cao / rộng hơn màn hình.
        root.applyCss();
        root.layout();
        double w = Math.max(pad.prefWidth(-1) + 6, 440);
        body.setPrefViewportWidth(Math.min(w, maxW));
        body.setPrefViewportHeight(Math.min(pad.prefHeight(Math.min(w, maxW) - 6) + 6, maxH));
        d.sizeToScene();
        d.showAndWait();
        return ok[0];
    }
}
