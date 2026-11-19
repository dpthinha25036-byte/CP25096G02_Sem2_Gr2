package ctv.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.application.Platform;

/**
 * Chuyển đổi ngôn ngữ giao diện Việt / Anh (Vietnamese / English UI switch).
 *
 * <p>Mã nguồn viết sẵn bằng tiếng Việt; khi chọn tiếng Anh, mọi chuỗi hiển thị được dịch lúc chạy theo từ điển
 * {@code i18n/vi_en.tsv} (khóa = câu tiếng Việt, giá trị = câu tiếng Anh). Dữ liệu người dùng nhập không bị dịch.
 */
public final class I18n {
    private I18n() {}

    public enum Lang { VI, EN }

    /** Khóa trong Node.getProperties(): node mang khóa này (và con của nó) không bị dịch. */
    public static final String NO_I18N = "no-i18n";

    private static final String PREF_KEY = "language";
    private static final Pattern HAS_VI = Pattern.compile("[^\\x00-\\x7F]");
    private static final Pattern BAC_N = Pattern.compile("(?<![\\p{L}])Bậc\\s+(\\d+)");
    private static final Set<String> EXACT_ONLY = Set.of("Nam", "Nữ", "Kho", "Tạm", "Cần", "Mã", "Có", "Bậc", "Tổng", "Từ");

    private static final Map<String, String> DICT = new HashMap<>();
    private static List<String> phraseKeys = new ArrayList<>();
    private static final Map<String, String> CACHE = new HashMap<>();
    private static volatile Lang lang = Lang.VI;

    static {
        load();
        try {
            lang = "EN".equals(Preferences.userRoot().node("ctv-labor").get(PREF_KEY, "VI")) ? Lang.EN : Lang.VI;
        } catch (RuntimeException ignored) { /* không đọc được cấu hình: dùng tiếng Việt */ }
    }

    // ---------- trạng thái ----------
    public static Lang language() { return lang; }
    public static boolean isEnglish() { return lang == Lang.EN; }

    public static void setLanguage(Lang l) {
        lang = l;
        synchronized (CACHE) { CACHE.clear(); }
        try {
            Preferences.userRoot().node("ctv-labor").put(PREF_KEY, l.name());
        } catch (RuntimeException ignored) { /* không lưu được thì thôi */ }
    }

    public static void toggle() { setLanguage(isEnglish() ? Lang.VI : Lang.EN); }

    /** Nhãn nút chuyển ngôn ngữ: hiện ngôn ngữ sẽ chuyển sang. */
    public static String switchLabel() { return isEnglish() ? "Tiếng Việt" : "English"; }

    // ---------- dịch chuỗi ----------
    private static void load() {
        try (InputStream in = I18n.class.getResourceAsStream("/i18n/vi_en.tsv")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String k = line.substring(0, tab).strip(), v = line.substring(tab + 1).strip();
                if (!k.isEmpty()) DICT.put(k, v);
            }
        } catch (Exception ignored) { /* thiếu từ điển: giữ nguyên tiếng Việt */ }
        for (String k : DICT.keySet()) {
            if (k.length() >= 3 && !EXACT_ONLY.contains(k)) phraseKeys.add(k);
        }
        phraseKeys.sort(Comparator.comparingInt(String::length).reversed());
    }

    /** Dịch một chuỗi hiển thị sang ngôn ngữ hiện tại (tiếng Việt thì trả nguyên văn). */
    public static String t(String s) {
        if (s == null || s.isEmpty() || lang == Lang.VI) return s;
        // Dữ liệu trong CSDL có thể ở dạng chữ tổ hợp (NFD) hoặc có khoảng trắng đặc biệt: chuẩn hóa để khớp từ điển.
        s = java.text.Normalizer.normalize(s.replace('\u00A0', ' '), java.text.Normalizer.Form.NFC);
        synchronized (CACHE) {
            String hit = CACHE.get(s);
            if (hit != null) return hit;
            String out = translate(s);
            if (CACHE.size() > 6000) CACHE.clear();
            CACHE.put(s, out);
            return out;
        }
    }

    private static String translate(String s) {
        String core = s.strip();
        if (core.isEmpty()) return s;
        int lead = s.indexOf(core.charAt(0));
        String pre = s.substring(0, lead), post = s.substring(lead + core.length());
        String exact = DICT.get(core);
        if (exact != null) return pre + exact + post;
        if (!HAS_VI.matcher(core).find() && !DICT.containsKey(core)) return s;   // không có chữ tiếng Việt
        String cur = BAC_N.matcher(core).replaceAll("Level $1");   // "Bậc 1" -> "Level 1" (bậc thợ là dữ liệu trong CSDL)
        for (String k : phraseKeys) {
            if (cur.indexOf(k) >= 0) cur = replacePhrase(cur, k, DICT.get(k));
        }
        return pre + cur + post;
    }

    /** Thay cụm k bằng v, chỉ khi k đứng riêng (không dính chữ cái liền kề) để khỏi cắt giữa từ. */
    private static String replacePhrase(String s, String k, String v) {
        StringBuilder sb = new StringBuilder();
        int from = 0, i;
        boolean startsLetter = Character.isLetter(k.charAt(0)), endsLetter = Character.isLetter(k.charAt(k.length() - 1));
        while ((i = s.indexOf(k, from)) >= 0) {
            int end = i + k.length();
            boolean okBefore = !startsLetter || i == 0 || !Character.isLetter(s.charAt(i - 1));
            boolean okAfter = !endsLetter || end >= s.length() || !Character.isLetter(s.charAt(end));
            sb.append(s, from, i);
            sb.append(okBefore && okAfter ? v : k);
            from = end;
        }
        return sb.append(s, from, s.length()).toString();
    }

    // ---------- dịch cả cây node JavaFX ----------
    /** Dịch chữ của node và mọi node con (nút, nhãn, tiêu đề cột, gợi ý, tooltip, tab). */
    public static void translateTree(Node n) {
        if (lang == Lang.VI || n == null) return;
        if (n.getProperties().containsKey(NO_I18N)) return;   // dữ liệu người dùng (tên, tài khoản...) giữ nguyên
        if (n instanceof Labeled l) {
            String old = l.getText(), nw = t(old);
            if (old != null && !old.equals(nw)) l.setText(nw);
            if (l.getTooltip() != null) {
                String to = l.getTooltip().getText(), tn = t(to);
                if (to != null && !to.equals(tn)) l.getTooltip().setText(tn);
            }
        }
        if (n instanceof TextInputControl ti) {
            String old = ti.getPromptText(), nw = t(old);
            if (old != null && !old.equals(nw)) ti.setPromptText(nw);
        }
        if (n instanceof ComboBox<?> cb) cb.setPromptText(t(cb.getPromptText()));
        if (n instanceof TableView<?> tv) {
            for (TableColumn<?, ?> c : tv.getColumns()) {
                String old = c.getText(), nw = t(old);
                if (old != null && !old.equals(nw)) c.setText(nw);
            }
        }
        if (n instanceof TabPane tp) {
            tp.getTabs().forEach(tab -> {
                String old = tab.getText(), nw = t(old);
                if (old != null && !old.equals(nw)) tab.setText(nw);
                translateTree(tab.getContent());
            });
        }
        // ScrollPane / TitledPane chưa có skin (cửa sổ chưa hiện) thì getChildrenUnmodifiable() rỗng: duyệt thẳng content.
        if (n instanceof ScrollPane sp) translateTree(sp.getContent());
        if (n instanceof TitledPane tp) translateTree(tp.getContent());
        if (n instanceof Parent p) {
            for (Node ch : new ArrayList<>(p.getChildrenUnmodifiable())) translateTree(ch);
        }
    }

    /** Dịch sau khi dựng giao diện (gọi được từ mọi luồng). */
    public static void refresh(Node root) {
        if (Platform.isFxApplicationThread()) translateTree(root);
        else Platform.runLater(() -> translateTree(root));
    }
}
