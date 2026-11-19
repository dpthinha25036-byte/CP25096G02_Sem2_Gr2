package ctv.util;

/** Lỗi nghiệp vụ / lỗi dữ liệu; thông điệp hiển thị thẳng cho người dùng. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
    public BusinessException(String message, Throwable cause) { super(message, cause); }
}
