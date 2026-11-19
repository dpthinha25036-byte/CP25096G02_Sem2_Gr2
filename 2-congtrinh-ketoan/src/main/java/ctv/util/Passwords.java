package ctv.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Mã hóa mật khẩu bằng PBKDF2-HMAC-SHA256 (có muối). Chuỗi lưu: pbkdf2$số_vòng$muối$băm (Base64). */
public final class Passwords {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] h = derive(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(h);
    }

    /** true nếu mật khẩu khớp chuỗi đã lưu; chuỗi lưu sai định dạng (vd. 'CHANGE_ME') luôn trả về false. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith("pbkdf2$")) return false;
        String[] p = stored.split("\\$");
        if (p.length != 4) return false;
        try {
            int iterations = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations, int bits) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password, salt, iterations, bits)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new BusinessException("Không mã hóa được mật khẩu: " + e.getMessage(), e);
        }
    }
}
