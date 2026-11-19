# Quản lý nhân công công trình (JavaFX) – Phần 3 – Thủ kho

Một trong 3 phần tách từ dự án `ConstructionLaborManagementFX`. Mỗi phần **build và chạy độc lập**,
cả ba dùng **chung một CSDL MySQL** (`database/construction_labor.sql`).

## Phần này gồm
- Nghiệp vụ riêng: `ctv.kho` (loại thiết bị, thiết bị, vị trí kho, nhập/xuất/thu hồi, báo cáo tồn kho, lịch sử giao dịch)
- Mã chức năng (function_code): **3.1 – 3.4**
- Phần dùng chung (giống nhau ở cả 3 repo): `ctv.gui`, `ctv.util`, `ctv.model`, `ctv.dashboard`, `resources/`, `database/`, `scripts/`.

## File sao chép từ phần khác (giữ đồng bộ khi sửa)
- `ctv.nhansu.AuthDAO/AuthService/AuthModels` – bản sao từ **Phần 1** (đăng nhập).
- `ctv.nhansu.HrModels/HrDAO` – bản sao từ **Phần 1** (Dashboard đếm công nhân).

## Chạy
1. MySQL: chạy `database/construction_labor.sql`.
2. Biến môi trường `DB_URL`, `DB_USER`, `DB_PASS`.
3. Phát triển: `mvn javafx:run` – Đóng gói: `mvn package` → `java -jar target/construction-labor-management-fx-1.0.jar` (JDK 17+).

Menu chỉ hiện chức năng của phần này; Dashboard hiện số liệu theo quyền tài khoản đăng nhập.
