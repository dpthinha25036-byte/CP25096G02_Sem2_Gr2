# Quản lý nhân công công trình (JavaFX) – Phần 2 – Công trình & Kế toán

Một trong 3 phần tách từ dự án `ConstructionLaborManagementFX`. Mỗi phần **build và chạy độc lập**,
cả ba dùng **chung một CSDL MySQL** (`database/construction_labor.sql`).

## Phần này gồm
- Nghiệp vụ riêng: `ctv.congtrinh` (công trình, nhu cầu, phân công, cảnh báo nhân lực, chấm công, kỳ lương, chốt sổ, thanh toán)
- Mã chức năng (function_code): **2.1 – 2.10**
- Phần dùng chung (giống nhau ở cả 3 repo): `ctv.gui`, `ctv.util`, `ctv.model`, `ctv.dashboard`, `resources/`, `database/`, `scripts/`.

## File sao chép từ phần khác (giữ đồng bộ khi sửa)
- `ctv.nhansu.AuthDAO/AuthService/AuthModels` – bản sao từ **Phần 1** (đăng nhập).
- `ctv.nhansu.HrModels/HrDAO/HrService/LevelPicker` – bản sao từ **Phần 1** (chọn công nhân, kỹ năng, bậc thợ).

## Chạy
1. MySQL: chạy `database/construction_labor.sql`.
2. Biến môi trường `DB_URL`, `DB_USER`, `DB_PASS`.
3. Phát triển: `mvn javafx:run` – Đóng gói: `mvn package` → `java -jar target/construction-labor-management-fx-1.0.jar` (JDK 17+).

Menu chỉ hiện chức năng của phần này; Dashboard hiện số liệu theo quyền tài khoản đăng nhập.
