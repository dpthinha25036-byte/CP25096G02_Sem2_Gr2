-- =====================================================================
-- Chuyển mã chức năng nhóm 1 trong user_permissions sang đánh số của Form 02A v3
--   cũ 1.1 (hồ sơ công nhân)          -> mới 1.2
--   cũ 1.2 (kỹ năng và đơn giá)       -> mới 1.3
--   cũ 1.3 (tài khoản và phân quyền)  -> mới 1.1
--   1.4 và nhóm 2.x, 3.x giữ nguyên.
-- Chạy MỘT lần trên CSDL cũ:  mysql -u root -p ctv_labor < database/migrate_function_codes_02A.sql
-- CSDL tạo mới từ construction_labor.sql không cần chạy (chưa có dòng user_permissions).
-- Có chốt chặn: chỉ chạy khi chưa chuyển (Quản trị viên còn dòng quyền '1.3' cũ và chưa có '1.1').
-- Nên sao lưu trước (Cài đặt > Sao lưu ngay, hoặc scripts/backup.sh).
-- =====================================================================
USE ctv_labor;

SET @old_admin = (SELECT COUNT(*) FROM user_permissions up JOIN users u ON u.user_id = up.user_id
                  WHERE u.role_id = 1 AND up.function_code = '1.3');
SET @new_admin = (SELECT COUNT(*) FROM user_permissions up JOIN users u ON u.user_id = up.user_id
                  WHERE u.role_id = 1 AND up.function_code = '1.1');

-- Một câu UPDATE duy nhất nên ba mã đổi chỗ cho nhau cùng lúc, không bị đổi chồng.
UPDATE user_permissions
   SET function_code = CASE function_code
                         WHEN '1.1' THEN '1.2'
                         WHEN '1.2' THEN '1.3'
                         WHEN '1.3' THEN '1.1'
                       END
 WHERE function_code IN ('1.1', '1.2', '1.3')
   AND @old_admin > 0 AND @new_admin = 0;

SELECT ROW_COUNT() AS dong_da_chuyen;
-- Kết quả 0: chưa có dữ liệu cũ hoặc đã chuyển rồi (không chạy lại).
-- Tài khoản chưa có dòng nào trong user_permissions dùng quyền mặc định mới của vai trò, không cần chuyển.
