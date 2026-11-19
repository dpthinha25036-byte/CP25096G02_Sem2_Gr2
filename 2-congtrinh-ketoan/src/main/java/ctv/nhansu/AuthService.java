package ctv.nhansu;

import ctv.nhansu.AuthDAO;
import ctv.nhansu.AuthModels.Role;
import ctv.nhansu.AuthModels.UserAccount;
import ctv.util.BusinessException;
import ctv.util.Passwords;
import ctv.util.Permissions;
import ctv.util.Session;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Đăng nhập, đăng xuất, quản lý tài khoản và phân quyền (chức năng 1.3.1 – 1.3.8). */
public class AuthService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,50}");
    private static final int MIN_PASSWORD = 6;
    private static final String FEATURE = "1.1";

    private final AuthDAO dao = new AuthDAO();

    // ---------- 1.3.1, 1.3.2, 1.3.7 ----------
    /** Xác thực; thành công thì gán Session. Mọi lần đăng nhập (đúng hoặc sai) đều được ghi nhật ký. */
    public void login(String username, String password) {
        String user = username == null ? "" : username.trim();
        if (user.isEmpty()) throw new BusinessException("Vui lòng nhập tên đăng nhập.");
        if (password == null || password.isEmpty()) throw new BusinessException("Vui lòng nhập mật khẩu.");

        UserAccount u = dao.findByUsername(user);
        if (u == null || !Passwords.verify(password, u.passwordHash())) {
            dao.logLogin(u == null ? null : u.id(), user, "LOGIN", "WRONG_PASSWORD");
            throw new BusinessException("Sai tên đăng nhập hoặc mật khẩu.");
        }
        if (u.locked()) {
            dao.logLogin(u.id(), u.username(), "LOGIN", "LOCKED");
            throw new BusinessException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        Set<String> allowed = dao.allowedCodes(u.id());
        if (allowed == null || u.roleId() == Permissions.ROLE_ADMIN) allowed = Permissions.defaultsFor(u.roleId());   // quản trị viên: quyền cố định theo vai trò
        Session.login(u.id(), u.username(), u.fullName(), u.roleId(), u.roleName(), Permissions.sanitize(u.roleId(), allowed));
        dao.logLogin(u.id(), u.username(), "LOGIN", "SUCCESS");
    }

    public void logout() {
        if (Session.isLoggedIn()) dao.logLogin(Session.userId(), Session.username(), "LOGOUT", "SUCCESS");
        Session.logout();
    }

    // ---------- 1.3.3 – 1.3.6 ----------
    private void requireManage() {
        if (!Session.can(FEATURE)) throw new BusinessException("Bạn không có quyền quản lý tài khoản và phân quyền.");
    }

    public List<Role> roles() { return dao.roles(); }

    public List<UserAccount> search(String keyword, Integer roleId, String status) {
        requireManage();
        return dao.search(keyword, roleId, status);
    }

    public void addUser(String username, String password, String fullName, Role role) {
        requireManage();
        String user = username == null ? "" : username.trim();
        String name = fullName == null ? "" : fullName.trim();
        if (!USERNAME.matcher(user).matches())
            throw new BusinessException("Tên đăng nhập gồm 3–50 ký tự: chữ không dấu, số, dấu chấm hoặc gạch dưới.");
        checkPassword(password, true);
        checkName(name);
        if (role == null) throw new BusinessException("Hãy chọn vai trò.");
        if (dao.findByUsername(user) != null) throw new BusinessException("Tên đăng nhập đã tồn tại.");

        int id = dao.insertUser(user, Passwords.hash(password), name, role.id());
        dao.savePermissions(id, toMap(Permissions.defaultsFor(role.id())));
        dao.audit(Session.userId(), "users", id, "INSERT", null, describe(user, name, role.name(), "ACTIVE"));
    }

    /** Sửa họ tên, vai trò và (tùy chọn) đặt lại mật khẩu. Đổi vai trò thì phân quyền được đặt lại theo vai trò mới. */
    public void updateUser(UserAccount old, String fullName, Role role, String newPassword) {
        requireManage();
        String name = fullName == null ? "" : fullName.trim();
        checkName(name);
        if (role == null) throw new BusinessException("Hãy chọn vai trò.");
        boolean roleChanged = role.id() != old.roleId();
        if (roleChanged && old.id() == Session.userId())
            throw new BusinessException("Không thể đổi vai trò của tài khoản đang đăng nhập.");
        if (roleChanged && old.roleId() == Permissions.ROLE_ADMIN && !old.locked()
                && dao.countActiveAdmins(Permissions.ROLE_ADMIN) <= 1)
            throw new BusinessException("Hệ thống phải còn ít nhất một quản trị viên đang hoạt động.");
        boolean resetPw = newPassword != null && !newPassword.isEmpty();
        if (resetPw) checkPassword(newPassword, false);

        dao.updateUser(old.id(), name, role.id(), resetPw ? Passwords.hash(newPassword) : null);
        if (roleChanged) dao.savePermissions(old.id(), toMap(Permissions.defaultsFor(role.id())));
        dao.audit(Session.userId(), "users", old.id(), "UPDATE",
                describe(old.username(), old.fullName(), old.roleName(), old.status()),
                describe(old.username(), name, role.name(), old.status()) + (resetPw ? "; đặt lại mật khẩu" : ""));
    }

    /** Khóa tài khoản đang hoạt động hoặc mở khóa tài khoản đang bị khóa. */
    public void toggleLock(UserAccount u) {
        requireManage();
        String next = u.locked() ? "ACTIVE" : "LOCKED";
        if (!u.locked()) {
            if (u.id() == Session.userId()) throw new BusinessException("Không thể khóa tài khoản đang đăng nhập.");
            if (u.roleId() == Permissions.ROLE_ADMIN && dao.countActiveAdmins(Permissions.ROLE_ADMIN) <= 1)
                throw new BusinessException("Hệ thống phải còn ít nhất một quản trị viên đang hoạt động.");
        }
        dao.setStatus(u.id(), next);
        dao.audit(Session.userId(), "users", u.id(), "UPDATE", "status=" + u.status(), "status=" + next);
    }

    // ---------- 1.3.6 phân quyền ----------
    /** Quyền hiện tại của tài khoản: dòng đã lưu, nếu chưa có thì mặc định theo vai trò. */
    public Set<String> permissionsOf(UserAccount u) {
        requireManage();
        Set<String> s = dao.allowedCodes(u.id());
        return Permissions.sanitize(u.roleId(), s != null ? s : Permissions.defaultsFor(u.roleId()));
    }

    public void savePermissions(UserAccount u, Set<String> allowed) {
        requireManage();
        if (u.roleId() == Permissions.ROLE_ADMIN)
            throw new BusinessException("Quyền của quản trị viên cố định (chỉ quản lý tài khoản, phân quyền và nhật ký), không thể thay đổi.");
        Set<String> before = permissionsOf(u);
        dao.savePermissions(u.id(), toMap(Permissions.sanitize(u.roleId(), allowed)));
        dao.audit(Session.userId(), "user_permissions", u.id(), "UPDATE",
                "allowed=" + String.join(",", new LinkedHashSet<>(before)), "allowed=" + String.join(",", allowed));
    }

    // ---------- 1.3.7, 1.3.8 xem nhật ký ----------
    public List<Object[]> loginLogs() { requireManage(); return dao.loginLogs(500); }
    public List<Object[]> auditLogs() { requireManage(); return dao.auditLogs(500); }

    // ---------- tiện ích ----------
    private static Map<String, Boolean> toMap(Set<String> allowed) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (Permissions.Function f : Permissions.ALL) m.put(f.code(), allowed.contains(f.code()));
        return m;
    }

    private static void checkPassword(String p, boolean required) {
        if (p == null || p.length() < MIN_PASSWORD)
            throw new BusinessException((required ? "Mật khẩu" : "Mật khẩu mới") + " phải có ít nhất " + MIN_PASSWORD + " ký tự.");
    }

    private static void checkName(String name) {
        if (name.isEmpty()) throw new BusinessException("Vui lòng nhập họ tên.");
        if (name.length() > 100) throw new BusinessException("Họ tên tối đa 100 ký tự.");
    }

    private static String describe(String username, String fullName, String role, String status) {
        return "username=" + username + "; full_name=" + fullName + "; role=" + role + "; status=" + status;
    }
}
