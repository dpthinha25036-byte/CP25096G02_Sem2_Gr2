package ctv.nhansu;

import java.time.LocalDateTime;

/** Mô hình dữ liệu cho tài khoản và vai trò (nhóm 1.1). */
public final class AuthModels {
    private AuthModels() {}

    public record Role(int id, String name) {
        @Override public String toString() { return name; }
    }

    public record UserAccount(int id, String username, String fullName, int roleId, String roleName,
                              String status, LocalDateTime createdAt, String passwordHash) {
        public boolean locked() { return "LOCKED".equals(status); }
    }
}
