package ctv.util;

import java.util.Set;

/** Người dùng đang đăng nhập và các chức năng (function_code) được phép. Do AuthService gán sau khi xác thực. */
public final class Session {
    private static int userId;
    private static String username = "";
    private static String fullName = "";
    private static int roleId;
    private static String roleName = "";
    private static Set<String> allowed = Set.of();

    private Session() {}

    public static void login(int id, String user, String name, int role, String roleLabel, Set<String> codes) {
        userId = id; username = user; fullName = name; roleId = role; roleName = roleLabel; allowed = Set.copyOf(codes);
    }

    public static void logout() {
        userId = 0; username = ""; fullName = ""; roleId = 0; roleName = ""; allowed = Set.of();
    }

    public static boolean isLoggedIn() { return userId > 0; }
    public static int userId() { return userId; }
    public static String username() { return username; }
    public static String fullName() { return fullName; }
    public static int roleId() { return roleId; }
    public static String roleName() { return roleName; }
    public static boolean isAdmin() { return roleId == Permissions.ROLE_ADMIN; }

    /**
     * Kiểm tra quyền truy cập chức năng: mọi vai trò (kể cả Admin) chỉ dùng được chức năng đã được cấp.
     */
    public static boolean can(String... codes) {
        for (String c : codes) if (allowed.contains(c)) return true;
        return false;
    }
}
