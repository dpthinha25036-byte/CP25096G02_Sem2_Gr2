<<<<<<< HEAD
# Quản lý nhân công công trình (JavaFX) – Phần 1 – Admin & Nhân sự

Một trong 3 phần tách từ dự án `ConstructionLaborManagementFX`. Mỗi phần **build và chạy độc lập**,
cả ba dùng **chung một CSDL MySQL** (`database/construction_labor.sql`).

## Phần này gồm
- Nghiệp vụ riêng: `ctv.nhansu` (hồ sơ công nhân, kỹ năng, bậc thợ, đơn giá, báo cáo nhân lực, tài khoản & phân quyền)
- Mã chức năng (function_code): **1.1 – 1.4**
- Phần dùng chung (giống nhau ở cả 3 repo): `ctv.gui`, `ctv.util`, `ctv.model`, `ctv.dashboard`, `resources/`, `database/`, `scripts/`.

## File sao chép từ phần khác (giữ đồng bộ khi sửa)
- `ctv.congtrinh.SiteService/SiteDAO/SiteModels` – bản sao từ **Phần 2**, chỉ để `HrReportPanel` (báo cáo nhân lực) đọc dữ liệu công trình.

## Chạy
1. MySQL: chạy `database/construction_labor.sql`.
2. Biến môi trường `DB_URL`, `DB_USER`, `DB_PASS`.
3. Phát triển: `mvn javafx:run` – Đóng gói: `mvn package` → `java -jar target/construction-labor-management-fx-1.0.jar` (JDK 17+).

Menu chỉ hiện chức năng của phần này; Dashboard hiện số liệu theo quyền tài khoản đăng nhập.
=======
# CP25096G02_Sem2_Gr2
>>>>>>> 2818248a4847748cc635113f5f4ae4755452e4d3
