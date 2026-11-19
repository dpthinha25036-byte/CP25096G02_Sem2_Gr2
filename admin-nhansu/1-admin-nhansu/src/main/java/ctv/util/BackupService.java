package ctv.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sao lưu / phục hồi CSDL bằng mysqldump / mysql (Form 03, mục VI.3).
 * Dùng cùng cấu hình DB_URL, DB_USER, DB_PASS với DBConnection. Thư mục sao lưu mặc định: ./backups (đổi bằng BACKUP_DIR).
 * Cần mysqldump và mysql trong PATH (hoặc đặt MYSQL_BIN trỏ tới thư mục bin của MySQL).
 */
public final class BackupService {
    private BackupService() {}

    private static final Pattern URL = Pattern.compile("jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+)");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static File backupDir() {
        String d = System.getenv("BACKUP_DIR");
        File f = new File(d == null || d.isBlank() ? "backups" : d);
        f.mkdirs();
        return f;
    }

    /** Tạo tệp ctv_labor_yyyyMMdd_HHmmss.sql trong thư mục sao lưu; trả về tệp đã tạo. */
    public static File backup() {
        File out = new File(backupDir(), cfg().db + "_" + LocalDateTime.now().format(STAMP) + ".sql");
        List<String> cmd = base("mysqldump");
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--default-character-set=utf8mb4");
        cmd.add("--result-file=" + out.getAbsolutePath());
        cmd.add(cfg().db);
        run(cmd, null, null);
        if (!out.isFile() || out.length() == 0) throw new BusinessException("Tệp sao lưu rỗng, hãy kiểm tra cấu hình CSDL.");
        return out;
    }

    /** Phục hồi CSDL từ tệp .sql (ghi đè dữ liệu hiện tại). Tự sao lưu bản hiện tại trước khi phục hồi; trả về tệp an toàn đó. */
    public static File restore(File sql) {
        if (sql == null || !sql.isFile()) throw new BusinessException("Không tìm thấy tệp sao lưu.");
        File safety = backup();
        List<String> cmd = base("mysql");
        cmd.add("--default-character-set=utf8mb4");
        cmd.add(cfg().db);
        run(cmd, sql, null);
        return safety;
    }

    // ---------------------------------------------------------------- nội bộ
    private record Cfg(String host, String port, String db, String user, String pass) {}

    private static Cfg cfg() {
        String url = env("DB_URL", "jdbc:mysql://localhost:3306/ctv_labor");
        Matcher m = URL.matcher(url);
        if (!m.find()) throw new BusinessException("DB_URL không hợp lệ: " + url);
        return new Cfg(m.group(1), m.group(2) == null ? "3306" : m.group(2), m.group(3),
                env("DB_USER", "root"), env("DB_PASS", "root"));
    }

    private static List<String> base(String exe) {
        Cfg c = cfg();
        List<String> cmd = new ArrayList<>();
        cmd.add(locate(exe));
        cmd.add("-h"); cmd.add(c.host);
        cmd.add("-P"); cmd.add(c.port);
        cmd.add("-u"); cmd.add(c.user);
        return cmd;
    }

    /** Tìm mysqldump / mysql: MYSQL_BIN → PATH → các thư mục cài đặt phổ biến (MySQL, MariaDB, XAMPP, Laragon, WAMP, Homebrew). */
    private static String locate(String exe) {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String file = win ? exe + ".exe" : exe;
        List<File> dirs = new ArrayList<>();
        String bin = System.getenv("MYSQL_BIN");
        if (bin != null && !bin.isBlank()) dirs.add(new File(bin));
        String path = System.getenv("PATH");
        if (path != null) for (String d : path.split(File.pathSeparator)) if (!d.isBlank()) dirs.add(new File(d));
        if (win) {
            for (String root : new String[]{"C:\\Program Files\\MySQL", "C:\\Program Files (x86)\\MySQL", "C:\\Program Files\\MariaDB*"})
                addChildren(dirs, root);
            for (String d : new String[]{"C:\\xampp\\mysql\\bin", "D:\\xampp\\mysql\\bin", "C:\\wamp64\\bin\\mysql", "C:\\laragon\\bin\\mysql"})
                addChildren(dirs, d);
        } else {
            for (String d : new String[]{"/usr/local/mysql/bin", "/opt/homebrew/bin", "/usr/local/bin", "/opt/lampp/bin", "/usr/bin"})
                dirs.add(new File(d));
        }
        for (File d : dirs) {
            File f = new File(d, file);
            if (f.isFile()) return f.getAbsolutePath();
            File sub = new File(d, "bin/" + file);
            if (sub.isFile()) return sub.getAbsolutePath();
        }
        return exe;   // để ProcessBuilder báo lỗi rõ ràng
    }

    private static void addChildren(List<File> dirs, String root) {
        if (root.endsWith("*")) {   // C:\Program Files\MariaDB*
            File parent = new File(root.substring(0, root.lastIndexOf('\\')));
            File[] kids = parent.listFiles((x, n) -> n.startsWith("MariaDB"));
            if (kids != null) for (File k : kids) dirs.add(new File(k, "bin"));
            return;
        }
        File r = new File(root);
        dirs.add(r);
        File[] kids = r.listFiles(File::isDirectory);
        if (kids != null) for (File k : kids) { dirs.add(k); dirs.add(new File(k, "bin")); }
    }

    private static void run(List<String> cmd, File stdin, File stdout) {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("MYSQL_PWD", cfg().pass);   // không đưa mật khẩu lên dòng lệnh
        if (stdin != null) pb.redirectInput(stdin);
        if (stdout != null) pb.redirectOutput(stdout);
        try {
            Process p = pb.start();
            String log = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (p.waitFor() != 0) throw new BusinessException("Lỗi " + cmd.get(0) + ": " + log);
        } catch (IOException e) {
            throw new BusinessException("Không chạy được " + cmd.get(0)
                    + ". Hãy cài MySQL client và thêm vào PATH (hoặc đặt biến MYSQL_BIN trỏ tới thư mục bin của MySQL).", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Thao tác bị ngắt.", e);
        }
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }
}
