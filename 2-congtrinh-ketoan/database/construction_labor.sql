-- =====================================================================
-- CONSTRUCTION LABOR MANAGEMENT - CSDL hợp nhất theo Form 06 (33 bảng)
--   Nhóm 1 (TV1): roles, users, user_permissions, login_logs, audit_logs,
--                 worker_types, workers, skills, skill_levels, worker_skills,
--                 shift_rates, rate_histories                      (12 bảng)
--   Nhóm 2 (TV2): worksites, worksite_progress, manpower_plans, manpower_demands,
--                 assignments, attendances, payroll_periods, payroll_details,
--                 unlock_requests, wage_payments                   (10 bảng)
--   Nhóm 3 (TV3): equipment_types, warehouses, storage_locations, equipment,
--                 equipment_stock, goods_receipts, goods_receipt_details,
--                 issue_slips, issue_slip_details, return_slips,
--                 return_slip_details                              (11 bảng)
-- Chạy bằng MySQL 8+: mysql -u root -p < construction_labor.sql
-- =====================================================================
CREATE DATABASE IF NOT EXISTS ctv_labor
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ctv_labor;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- NHÓM 1 - Nhân sự, kỹ năng, đơn giá và tài khoản
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
  role_id     INT AUTO_INCREMENT PRIMARY KEY,
  role_name   VARCHAR(50)  NOT NULL UNIQUE,
  description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS users (
  user_id       INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  role_id       INT          NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | LOCKED
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE IF NOT EXISTS user_permissions (
  permission_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id       INT         NOT NULL,
  function_code VARCHAR(20) NOT NULL,
  is_allowed    TINYINT(1)  NOT NULL DEFAULT 1,
  CONSTRAINT fk_perm_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS login_logs (
  log_id      INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NULL,
  username    VARCHAR(50) NOT NULL,
  action_time DATETIME    NOT NULL,
  action_type VARCHAR(10) NOT NULL,                         -- LOGIN | LOGOUT
  result      VARCHAR(20) NOT NULL,                         -- SUCCESS | WRONG_PASSWORD | LOCKED
  CONSTRAINT fk_loginlog_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  audit_id    INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT         NOT NULL,
  action_time DATETIME    NOT NULL,
  table_name  VARCHAR(50) NOT NULL,
  record_id   INT         NOT NULL,
  action_type VARCHAR(10) NOT NULL,                         -- INSERT | UPDATE | DELETE
  old_value   TEXT,
  new_value   TEXT,
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS worker_types (
  worker_type_id INT AUTO_INCREMENT PRIMARY KEY,
  type_name      VARCHAR(50) NOT NULL UNIQUE,
  description    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS workers (
  worker_id             INT AUTO_INCREMENT PRIMARY KEY,
  worker_code           VARCHAR(20)  NOT NULL UNIQUE,
  full_name             VARCHAR(100) NOT NULL,
  birth_date            DATE         NOT NULL,
  gender                VARCHAR(10)  NOT NULL,
  id_card_no            VARCHAR(12)  NOT NULL UNIQUE,
  phone                 VARCHAR(15),
  address               VARCHAR(255),
  worker_type_id        INT          NOT NULL,
  hire_date             DATE         NOT NULL,
  work_status           VARCHAR(20)  NOT NULL DEFAULT 'WORKING',  -- WORKING | ON_LEAVE | RESIGNED
  status_effective_date DATE,
  bank_name             VARCHAR(100),                              -- ngân hàng nhận lương (trống = lãnh lương tay)
  bank_account_no       VARCHAR(30),
  bank_account_holder   VARCHAR(100),
  CONSTRAINT fk_workers_type FOREIGN KEY (worker_type_id) REFERENCES worker_types(worker_type_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_workers_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS skills (
  skill_id    INT AUTO_INCREMENT PRIMARY KEY,
  skill_code  VARCHAR(20)  NOT NULL UNIQUE,
  skill_name  VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_skills_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS skill_levels (
  level_id    INT AUTO_INCREMENT PRIMARY KEY,
  level_code  VARCHAR(20) NOT NULL UNIQUE,
  level_name  VARCHAR(50) NOT NULL,
  skill_id    INT         NOT NULL,
  description VARCHAR(255),
  CONSTRAINT fk_level_skill FOREIGN KEY (skill_id) REFERENCES skills(skill_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_skill_levels_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS worker_skills (
  worker_skill_id INT AUTO_INCREMENT PRIMARY KEY,
  worker_id       INT NOT NULL,
  skill_id        INT NOT NULL,
  level_id        INT NOT NULL,
  UNIQUE KEY uq_worker_skill (worker_id, skill_id),
  CONSTRAINT fk_ws_worker FOREIGN KEY (worker_id) REFERENCES workers(worker_id),
  CONSTRAINT fk_ws_skill  FOREIGN KEY (skill_id)  REFERENCES skills(skill_id),
  CONSTRAINT fk_ws_level  FOREIGN KEY (level_id)  REFERENCES skill_levels(level_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_worker_skills_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS shift_rates (
  rate_id        INT AUTO_INCREMENT PRIMARY KEY,
  skill_id       INT           NOT NULL,
  level_id       INT           NOT NULL,
  shift_type     VARCHAR(10)   NOT NULL,                    -- REGULAR | OVERTIME
  rate           DECIMAL(15,2) NOT NULL,
  effective_date DATE          NOT NULL,
  CONSTRAINT fk_rate_skill FOREIGN KEY (skill_id) REFERENCES skills(skill_id),
  CONSTRAINT fk_rate_level FOREIGN KEY (level_id) REFERENCES skill_levels(level_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_shift_rates_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS rate_histories (
  history_id     INT AUTO_INCREMENT PRIMARY KEY,
  rate_id        INT           NOT NULL,
  old_rate       DECIMAL(15,2),
  new_rate       DECIMAL(15,2) NOT NULL,
  effective_date DATE          NOT NULL,
  changed_by     INT           NOT NULL,
  changed_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ratehist_rate FOREIGN KEY (rate_id)    REFERENCES shift_rates(rate_id),
  CONSTRAINT fk_ratehist_user FOREIGN KEY (changed_by) REFERENCES users(user_id)
);

-- ---------------------------------------------------------------------
-- NHÓM 2 - Công trình, phân công, chấm công và tiền lương
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS worksites (
  worksite_id      INT AUTO_INCREMENT PRIMARY KEY,
  worksite_code    VARCHAR(20)  NOT NULL UNIQUE,
  worksite_name    VARCHAR(150) NOT NULL,
  address          VARCHAR(255) NOT NULL,
  investor         VARCHAR(150),
  planned_start    DATE         NOT NULL,
  planned_end      DATE         NOT NULL,
  actual_start     DATE,
  actual_end       DATE,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PREPARING', -- PREPARING | IN_PROGRESS | PAUSED | COMPLETED
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_by       INT NULL,                                  -- người thêm công trình (quản lý công trình chỉ sửa/xóa công trình của mình)
  CONSTRAINT chk_ws_progress CHECK (progress_percent BETWEEN 0 AND 100),
  CONSTRAINT fk_ws_user FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS worksite_progress (
  progress_id      INT AUTO_INCREMENT PRIMARY KEY,
  worksite_id      INT          NOT NULL,
  progress_percent DECIMAL(5,2) NOT NULL,
  update_date      DATE         NOT NULL,
  note             VARCHAR(255),
  CONSTRAINT fk_wsprog_site FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id)
);

CREATE TABLE IF NOT EXISTS manpower_plans (
  plan_id     INT AUTO_INCREMENT PRIMARY KEY,
  worksite_id INT      NOT NULL,
  from_date   DATE     NOT NULL,
  to_date     DATE     NOT NULL,
  created_by  INT      NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_plan_site FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id),
  CONSTRAINT fk_plan_user FOREIGN KEY (created_by)  REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS manpower_demands (
  demand_id       INT AUTO_INCREMENT PRIMARY KEY,
  plan_id         INT          NOT NULL,
  phase_name      VARCHAR(100) NOT NULL,
  skill_id        INT          NOT NULL,
  level_id        INT          NOT NULL,
  quantity_needed INT          NOT NULL,
  CONSTRAINT fk_dem_plan  FOREIGN KEY (plan_id)  REFERENCES manpower_plans(plan_id),
  CONSTRAINT fk_dem_skill FOREIGN KEY (skill_id) REFERENCES skills(skill_id),
  CONSTRAINT fk_dem_level FOREIGN KEY (level_id) REFERENCES skill_levels(level_id),
  CONSTRAINT chk_dem_qty CHECK (quantity_needed > 0),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_manpower_demands_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS assignments (
  assignment_id     INT AUTO_INCREMENT PRIMARY KEY,
  worker_id         INT         NOT NULL,
  worksite_id       INT         NOT NULL,
  start_date        DATE        NOT NULL,
  expected_end_date DATE,
  actual_end_date   DATE,
  status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | FINISHED | CANCELLED
  cancel_reason     VARCHAR(255),
  CONSTRAINT fk_asg_worker FOREIGN KEY (worker_id)   REFERENCES workers(worker_id),
  CONSTRAINT fk_asg_site   FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_assignments_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS attendances (
  attendance_id     INT AUTO_INCREMENT PRIMARY KEY,
  worker_id         INT          NOT NULL,
  worksite_id       INT          NOT NULL,
  work_date         DATE         NOT NULL,
  check_in          TIME,
  check_out         TIME,
  regular_shifts    DECIMAL(4,1) NOT NULL DEFAULT 0,
  overtime_shifts   DECIMAL(4,1) NOT NULL DEFAULT 0,
  regular_rate      DECIMAL(15,2),
  overtime_rate     DECIMAL(15,2),
  validation_status VARCHAR(20)  NOT NULL DEFAULT 'VALID',  -- VALID | WARNING | INVALID
  validation_note   VARCHAR(255),
  UNIQUE KEY uq_att_worker_date (worker_id, work_date),
  CONSTRAINT fk_att_worker FOREIGN KEY (worker_id)   REFERENCES workers(worker_id),
  CONSTRAINT fk_att_site   FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_attendances_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS payroll_periods (
  period_id  INT AUTO_INCREMENT PRIMARY KEY,
  start_date DATE        NOT NULL,                           -- Thứ Năm
  end_date   DATE        NOT NULL,                           -- Thứ Tư
  status     VARCHAR(20) NOT NULL DEFAULT 'DRAFT',           -- DRAFT | CLOSED | REOPENED
  closed_by  INT NULL,
  closed_at  DATETIME NULL,
  CONSTRAINT fk_period_user FOREIGN KEY (closed_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS payroll_details (
  detail_id       INT AUTO_INCREMENT PRIMARY KEY,
  period_id       INT           NOT NULL,
  worker_id       INT           NOT NULL,
  regular_shifts  DECIMAL(6,1)  NOT NULL,
  overtime_shifts DECIMAL(6,1)  NOT NULL,
  total_amount    DECIMAL(15,2) NOT NULL,
  payment_status  VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',  -- UNPAID | PARTIAL | PAID
  UNIQUE KEY uq_pay_period_worker (period_id, worker_id),
  CONSTRAINT fk_pd_period FOREIGN KEY (period_id) REFERENCES payroll_periods(period_id),
  CONSTRAINT fk_pd_worker FOREIGN KEY (worker_id) REFERENCES workers(worker_id)
);

CREATE TABLE IF NOT EXISTS unlock_requests (
  request_id   INT AUTO_INCREMENT PRIMARY KEY,
  period_id    INT          NOT NULL,
  requested_by INT          NOT NULL,
  reason       VARCHAR(255) NOT NULL,
  requested_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',     -- PENDING | APPROVED | REJECTED
  approved_by  INT NULL,
  approved_at  DATETIME NULL,
  CONSTRAINT fk_unlock_period FOREIGN KEY (period_id)    REFERENCES payroll_periods(period_id),
  CONSTRAINT fk_unlock_req    FOREIGN KEY (requested_by) REFERENCES users(user_id),
  CONSTRAINT fk_unlock_appr   FOREIGN KEY (approved_by)  REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS wage_payments (
  payment_id     INT AUTO_INCREMENT PRIMARY KEY,
  detail_id      INT           NOT NULL,
  method         VARCHAR(20)   NOT NULL,                    -- CASH | TRANSFER
  amount         DECIMAL(15,2) NOT NULL,
  payment_date   DATE          NOT NULL,
  reference_code VARCHAR(50),
  recorded_by    INT           NOT NULL,
  work_date      DATE          NULL,                        -- chỉ dùng khi trả lương HẰNG NGÀY cho công nhân thời vụ: ngày công được trả
  CONSTRAINT fk_wp_detail FOREIGN KEY (detail_id)   REFERENCES payroll_details(detail_id),
  CONSTRAINT fk_wp_user   FOREIGN KEY (recorded_by) REFERENCES users(user_id),
  CONSTRAINT chk_wp_amount CHECK (amount > 0)
);

-- ---------------------------------------------------------------------
-- NHÓM 3 - Thiết bị, kho và giao dịch kho
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_types (
  type_id     INT AUTO_INCREMENT PRIMARY KEY,
  type_code   VARCHAR(20)  NOT NULL UNIQUE,
  type_name   VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_equipment_types_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS warehouses (
  warehouse_id   INT AUTO_INCREMENT PRIMARY KEY,
  warehouse_code VARCHAR(20)  NOT NULL UNIQUE,
  warehouse_name VARCHAR(100) NOT NULL,
  address        VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS storage_locations (
  location_id   INT AUTO_INCREMENT PRIMARY KEY,
  warehouse_id  INT         NOT NULL,
  location_code VARCHAR(20) NOT NULL,
  description   VARCHAR(255),
  UNIQUE KEY uq_loc_wh_code (warehouse_id, location_code),
  CONSTRAINT fk_loc_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_storage_locations_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS equipment (
  equipment_id       INT AUTO_INCREMENT PRIMARY KEY,
  equipment_code     VARCHAR(20)  NOT NULL UNIQUE,
  equipment_name     VARCHAR(150) NOT NULL,
  type_id            INT          NOT NULL,
  condition_status   VARCHAR(20)  NOT NULL DEFAULT 'GOOD',     -- GOOD | NEED_REPAIR | BROKEN
  usage_status       VARCHAR(20)  NOT NULL DEFAULT 'IN_STOCK', -- IN_STOCK | IN_USE | MAINTENANCE
  responsible_person VARCHAR(100),
  CONSTRAINT fk_eq_type FOREIGN KEY (type_id) REFERENCES equipment_types(type_id),
  created_by INT NULL,   -- người thêm: chỉ người thêm được sửa/xóa, tài khoản khác chỉ xem
  CONSTRAINT fk_equipment_owner FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS equipment_stock (
  stock_id     INT AUTO_INCREMENT PRIMARY KEY,
  equipment_id INT NOT NULL,
  location_id  INT NULL,
  worksite_id  INT NULL,
  quantity     INT NOT NULL,
  CONSTRAINT fk_stock_eq   FOREIGN KEY (equipment_id) REFERENCES equipment(equipment_id),
  CONSTRAINT fk_stock_loc  FOREIGN KEY (location_id)  REFERENCES storage_locations(location_id),
  CONSTRAINT fk_stock_site FOREIGN KEY (worksite_id)  REFERENCES worksites(worksite_id),
  CONSTRAINT chk_stock_qty   CHECK (quantity >= 0),
  CONSTRAINT chk_stock_place CHECK ((location_id IS NULL) <> (worksite_id IS NULL))  -- đúng một trong hai
);

CREATE TABLE IF NOT EXISTS goods_receipts (
  receipt_id   INT AUTO_INCREMENT PRIMARY KEY,
  receipt_code VARCHAR(20) NOT NULL UNIQUE,
  receipt_date DATE        NOT NULL,
  source       VARCHAR(150),
  created_by   INT         NOT NULL,
  CONSTRAINT fk_gr_user FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS goods_receipt_details (
  detail_id    INT AUTO_INCREMENT PRIMARY KEY,
  receipt_id   INT NOT NULL,
  equipment_id INT NOT NULL,
  location_id  INT NOT NULL,
  quantity     INT NOT NULL,
  CONSTRAINT fk_grd_receipt FOREIGN KEY (receipt_id)   REFERENCES goods_receipts(receipt_id),
  CONSTRAINT fk_grd_eq      FOREIGN KEY (equipment_id) REFERENCES equipment(equipment_id),
  CONSTRAINT fk_grd_loc     FOREIGN KEY (location_id)  REFERENCES storage_locations(location_id),
  CONSTRAINT chk_grd_qty CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS issue_slips (
  issue_id      INT AUTO_INCREMENT PRIMARY KEY,
  issue_code    VARCHAR(20)  NOT NULL UNIQUE,
  issue_date    DATE         NOT NULL,
  worksite_id   INT          NOT NULL,
  receiver_name VARCHAR(100) NOT NULL,
  created_by    INT          NOT NULL,
  CONSTRAINT fk_is_site FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id),
  CONSTRAINT fk_is_user FOREIGN KEY (created_by)  REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS issue_slip_details (
  detail_id    INT AUTO_INCREMENT PRIMARY KEY,
  issue_id     INT NOT NULL,
  equipment_id INT NOT NULL,
  location_id  INT NOT NULL,
  quantity     INT NOT NULL,
  CONSTRAINT fk_isd_issue FOREIGN KEY (issue_id)     REFERENCES issue_slips(issue_id),
  CONSTRAINT fk_isd_eq    FOREIGN KEY (equipment_id) REFERENCES equipment(equipment_id),
  CONSTRAINT fk_isd_loc   FOREIGN KEY (location_id)  REFERENCES storage_locations(location_id),
  CONSTRAINT chk_isd_qty CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS return_slips (
  return_id     INT AUTO_INCREMENT PRIMARY KEY,
  return_code   VARCHAR(20)  NOT NULL UNIQUE,
  return_date   DATE         NOT NULL,
  worksite_id   INT          NOT NULL,
  returner_name VARCHAR(100) NOT NULL,
  created_by    INT          NOT NULL,
  CONSTRAINT fk_rs_site FOREIGN KEY (worksite_id) REFERENCES worksites(worksite_id),
  CONSTRAINT fk_rs_user FOREIGN KEY (created_by)  REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS return_slip_details (
  detail_id       INT AUTO_INCREMENT PRIMARY KEY,
  return_id       INT         NOT NULL,
  equipment_id    INT         NOT NULL,
  location_id     INT         NOT NULL,
  quantity        INT         NOT NULL,
  condition_after VARCHAR(20) NOT NULL,                      -- GOOD | NEED_REPAIR | BROKEN
  CONSTRAINT fk_rsd_return FOREIGN KEY (return_id)    REFERENCES return_slips(return_id),
  CONSTRAINT fk_rsd_eq     FOREIGN KEY (equipment_id) REFERENCES equipment(equipment_id),
  CONSTRAINT fk_rsd_loc    FOREIGN KEY (location_id)  REFERENCES storage_locations(location_id),
  CONSTRAINT chk_rsd_qty CHECK (quantity > 0)
);

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- DỮ LIỆU MẪU TỐI THIỂU (INSERT IGNORE: chạy lại không bị trùng)
-- =====================================================================
INSERT IGNORE INTO roles (role_id, role_name, description) VALUES
  (1, 'Quản trị viên',     'Quản lý tài khoản, phân quyền, nhật ký hệ thống'),
  (2, 'Quản lý nhân sự',   'Hồ sơ công nhân, kỹ năng, đơn giá'),
  (3, 'Quản lý công trình','Công trình, nhu cầu nhân lực, phân công, cảnh báo thiếu/đủ nhân lực'),
  (4, 'Kế toán',           'Chấm công, tổng hợp tiền công, chốt lương, thanh toán lương'),
  (5, 'Thủ kho',           'Loại thiết bị, thiết bị, vị trí, nhập kho, xuất và thu hồi, báo cáo kho');

-- Tài khoản mẫu (mật khẩu lưu dạng PBKDF2, xem ctv.util.Passwords):
--   admin / admin123  (Quản trị viên)        nhansu01 / 123456   (Quản lý nhân sự)
--   congtrinh01 / 123456 (Quản lý công trình)  ketoan01 / 123456  (Kế toán)  thukho01 / 123456 (Thủ kho)
-- Hãy đổi mật khẩu ngay trong màn hình "Tài khoản & phân quyền" khi triển khai thật.
-- Tài khoản chưa có dòng nào trong user_permissions sẽ dùng quyền mặc định của vai trò (ctv.util.Permissions).
INSERT IGNORE INTO users (user_id, username, password_hash, full_name, role_id) VALUES
  (1, 'admin',       'pbkdf2$120000$PSVL0gtbcUe7FR57vOc5VA==$wKKTTkntC5jixlpDvuLtfNFOdrOrdcknrP08DnD2VfY=', 'Quản trị viên',    1),
  (2, 'nhansu01',    'pbkdf2$120000$F/YOvj/WvGoyxgi8E5Ta3Q==$EJB36lNJ4pPmN0+zsnUacNuDD2tAVVylJ0F8qSzZPn0=', 'Nguyễn Thị Hà',    2),
  (3, 'congtrinh01', 'pbkdf2$120000$F/YOvj/WvGoyxgi8E5Ta3Q==$EJB36lNJ4pPmN0+zsnUacNuDD2tAVVylJ0F8qSzZPn0=', 'Phạm Quốc Việt',   3),
  (4, 'ketoan01',    'pbkdf2$120000$F/YOvj/WvGoyxgi8E5Ta3Q==$EJB36lNJ4pPmN0+zsnUacNuDD2tAVVylJ0F8qSzZPn0=', 'Lê Thị Mai',       4),
  (5, 'thukho01',    'pbkdf2$120000$F/YOvj/WvGoyxgi8E5Ta3Q==$EJB36lNJ4pPmN0+zsnUacNuDD2tAVVylJ0F8qSzZPn0=', 'Trần Văn Kho',     5);

-- 5 vai trò, mỗi vai trò một nhóm chức năng riêng (ctv.util.Permissions): Admin, Nhân sự, Công trình, Kế toán, Thủ kho.
-- CSDL cũ muốn áp lại quyền mặc định mới cho mọi tài khoản thì chạy một lần: DELETE FROM user_permissions;

-- Cơ sở dữ liệu cũ đã có admin với mật khẩu giữ chỗ 'CHANGE_ME' thì cập nhật thành admin123.
UPDATE users SET password_hash = 'pbkdf2$120000$PSVL0gtbcUe7FR57vOc5VA==$wKKTTkntC5jixlpDvuLtfNFOdrOrdcknrP08DnD2VfY=' WHERE username = 'admin' AND password_hash = 'CHANGE_ME';

INSERT IGNORE INTO worker_types (worker_type_id, type_name, description) VALUES
  (1, 'Chính thức', 'Công nhân hợp đồng chính thức'),
  (2, 'Thời vụ',    'Công nhân thời vụ');

INSERT IGNORE INTO skills (skill_id, skill_code, skill_name, description) VALUES
  (1, 'SK-THO',  'Thợ hồ',   'Xây, tô, hoàn thiện'),
  (2, 'SK-DIEN', 'Thợ điện', 'Thi công điện'),
  (3, 'SK-HAN',  'Thợ hàn',  'Gia công, hàn kết cấu');

-- Bậc thợ thuộc từng kỹ năng. Quy ước: level_id tăng dần theo bậc (bậc 1 < bậc 2 < ...),
-- phần "nhu cầu nhân lực" dùng quy ước này để hiểu "bậc thợ tối thiểu".
INSERT IGNORE INTO skill_levels (level_id, level_code, level_name, skill_id) VALUES
  (1, 'THO-B1',  'Bậc 1', 1), (2, 'THO-B2',  'Bậc 2', 1), (3, 'THO-B3',  'Bậc 3', 1),
  (4, 'DIEN-B1', 'Bậc 1', 2), (5, 'DIEN-B2', 'Bậc 2', 2), (6, 'DIEN-B3', 'Bậc 3', 2),
  (7, 'HAN-B1',  'Bậc 1', 3), (8, 'HAN-B2',  'Bậc 2', 3), (9, 'HAN-B3',  'Bậc 3', 3);

INSERT IGNORE INTO equipment_types (type_id, type_code, type_name, description) VALUES
  (1, 'LTB01', 'Máy khoan', 'Máy khoan, máy đục'),
  (2, 'LTB02', 'Giàn giáo', 'Giàn giáo, cột chống');

INSERT IGNORE INTO warehouses (warehouse_id, warehouse_code, warehouse_name, address) VALUES
  (1, 'KHO-A', 'Kho A', 'Kho Trung Tâm'),
  (2, 'KHO-B', 'Kho B', 'Kho Trung Tâm');

INSERT IGNORE INTO storage_locations (location_id, warehouse_id, location_code, description) VALUES
  (1, 1, 'A1-01', 'Kệ A1, ô 01'),
  (2, 1, 'A1-02', 'Kệ A1, ô 02'),
  (3, 2, 'B1-01', 'Kệ B1, ô 01');

-- =====================================================================
-- DỮ LIỆU MẪU ĐỂ DÙNG THỬ (INSERT IGNORE: chạy lại không bị trùng).
-- Gồm công nhân, công trình, phân công, chấm công 7 ngày gần nhất, bảng lương, thiết bị, tồn kho và phiếu kho.
-- Không cần dữ liệu mẫu này khi triển khai thật: xóa phần từ dòng này đến hết file.
-- =====================================================================
INSERT IGNORE INTO workers (worker_id, worker_code, full_name, birth_date, gender, id_card_no, phone, address, worker_type_id, hire_date, work_status, status_effective_date) VALUES
  (1, 'CN001', 'Nguyễn Văn An', '1985-01-10', 'Nam', '079201000001', '0901000001', 'TP. Hồ Chí Minh', 1, '2025-01-05', 'WORKING', NULL),
  (2, 'CN002', 'Trần Văn Bình', '1986-02-11', 'Nam', '079201000002', '0901000002', 'TP. Hồ Chí Minh', 2, '2025-02-05', 'WORKING', NULL),
  (3, 'CN003', 'Lê Văn Cường', '1987-03-12', 'Nam', '079201000003', '0901000003', 'TP. Hồ Chí Minh', 1, '2025-03-05', 'WORKING', NULL),
  (4, 'CN004', 'Phạm Văn Dũng', '1988-04-13', 'Nam', '079201000004', '0901000004', 'TP. Hồ Chí Minh', 1, '2025-04-05', 'WORKING', NULL),
  (5, 'CN005', 'Hoàng Thị Em', '1989-05-14', 'Nữ', '079201000005', '0901000005', 'TP. Hồ Chí Minh', 1, '2025-05-05', 'WORKING', NULL),
  (6, 'CN006', 'Vũ Văn Phúc', '1990-06-15', 'Nam', '079201000006', '0901000006', 'TP. Hồ Chí Minh', 2, '2025-06-05', 'WORKING', NULL),
  (7, 'CN007', 'Đặng Văn Giang', '1991-07-16', 'Nam', '079201000007', '0901000007', 'TP. Hồ Chí Minh', 2, '2025-07-05', 'WORKING', NULL),
  (8, 'CN008', 'Bùi Thị Hoa', '1992-08-17', 'Nữ', '079201000008', '0901000008', 'TP. Hồ Chí Minh', 1, '2025-08-05', 'WORKING', NULL),
  (9, 'CN009', 'Ngô Văn Hùng', '1993-09-18', 'Nam', '079201000009', '0901000009', 'TP. Hồ Chí Minh', 1, '2025-09-05', 'ON_LEAVE', '2026-10-15'),
  (10, 'CN010', 'Đỗ Văn Khoa', '1994-01-10', 'Nam', '079201000010', '0901000010', 'TP. Hồ Chí Minh', 2, '2025-01-05', 'RESIGNED', '2026-10-15');
INSERT IGNORE INTO worker_skills (worker_skill_id, worker_id, skill_id, level_id) VALUES
  (1, 1, 1, 2),
  (2, 2, 1, 1),
  (3, 3, 2, 6),
  (4, 4, 3, 8),
  (5, 5, 1, 3),
  (6, 6, 2, 4),
  (7, 7, 3, 7),
  (8, 8, 1, 2),
  (9, 9, 2, 5),
  (10, 10, 3, 9);
INSERT IGNORE INTO shift_rates (rate_id, skill_id, level_id, shift_type, rate, effective_date) VALUES
  (1, 1, 1, 'REGULAR', 350000, '2026-01-01'),
  (2, 1, 1, 'OVERTIME', 525000, '2026-01-01'),
  (3, 1, 2, 'REGULAR', 420000, '2026-01-01'),
  (4, 1, 2, 'OVERTIME', 630000, '2026-01-01'),
  (5, 1, 3, 'REGULAR', 500000, '2026-01-01'),
  (6, 1, 3, 'OVERTIME', 750000, '2026-01-01'),
  (7, 2, 4, 'REGULAR', 380000, '2026-01-01'),
  (8, 2, 4, 'OVERTIME', 570000, '2026-01-01'),
  (9, 2, 5, 'REGULAR', 450000, '2026-01-01'),
  (10, 2, 5, 'OVERTIME', 675000, '2026-01-01'),
  (11, 2, 6, 'REGULAR', 550000, '2026-01-01'),
  (12, 2, 6, 'OVERTIME', 825000, '2026-01-01'),
  (13, 3, 7, 'REGULAR', 400000, '2026-01-01'),
  (14, 3, 7, 'OVERTIME', 600000, '2026-01-01'),
  (15, 3, 8, 'REGULAR', 480000, '2026-01-01'),
  (16, 3, 8, 'OVERTIME', 720000, '2026-01-01'),
  (17, 3, 9, 'REGULAR', 580000, '2026-01-01'),
  (18, 3, 9, 'OVERTIME', 870000, '2026-01-01');
INSERT IGNORE INTO worksites (worksite_id, worksite_code, worksite_name, address, investor, planned_start, planned_end, actual_start, actual_end, status, progress_percent, created_by) VALUES
  (1, 'CT001', 'Nhà Hàng ABC',        '12 Nguyễn Huệ, Q.1, TP.HCM',      'Công ty ABC',     '2026-09-15', '2027-01-30', '2026-09-20', NULL,         'IN_PROGRESS', 35, 3),
  (2, 'CT002', 'Khách Sạn Sao Mai',   '88 Trần Phú, TP. Nha Trang',      'Tập đoàn Sao Mai','2026-08-01', '2027-03-31', '2026-08-05', NULL,         'IN_PROGRESS', 60, 3),
  (3, 'CT003', 'Chung Cư Phú Gia',    '5 Lê Lợi, TP. Biên Hòa',          'Phú Gia Land',    '2026-12-01', '2027-08-30', NULL,         NULL,         'PREPARING',    0, 3),
  (4, 'CT004', 'Trường Học Hòa Bình', '30 Hòa Bình, Q. Tân Phú, TP.HCM', 'Sở Giáo dục',     '2026-03-01', '2026-09-30', '2026-03-01', '2026-09-28', 'COMPLETED',  100, 3);
INSERT IGNORE INTO worksite_progress (progress_id, worksite_id, progress_percent, update_date, note) VALUES
  (1, 1, 10, '2026-10-01', 'Hoàn thành móng'), (2, 1, 35, '2026-10-28', 'Xây phần thô tầng 1'),
  (3, 2, 30, '2026-09-15', 'Hoàn thành phần móng'), (4, 2, 60, '2026-10-30', 'Hoàn thiện tầng 3'),
  (5, 4, 100, '2026-09-28', 'Bàn giao công trình');
INSERT IGNORE INTO manpower_plans (plan_id, worksite_id, from_date, to_date, created_by) VALUES
  (1, 1, '2026-10-01', '2026-12-31', 3), (2, 2, '2026-10-01', '2026-12-31', 3);
INSERT IGNORE INTO manpower_demands (demand_id, plan_id, phase_name, skill_id, level_id, quantity_needed) VALUES
  (1, 1, 'Phần thô', 1, 1, 3), (2, 1, 'Phần thô', 2, 5, 2),
  (3, 2, 'Hoàn thiện', 3, 7, 3), (4, 2, 'Hoàn thiện', 1, 2, 2);
INSERT IGNORE INTO assignments (assignment_id, worker_id, worksite_id, start_date, expected_end_date, actual_end_date, status, cancel_reason) VALUES
  (1, 1, 1, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (2, 2, 1, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (3, 3, 1, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (4, 4, 2, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (5, 5, 2, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (6, 6, 2, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (7, 7, 2, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (8, 8, 2, '2026-10-01', '2026-12-31', NULL, 'ACTIVE', NULL),
  (11, 10, 4, '2026-03-01', '2026-09-30', '2026-09-28', 'FINISHED', NULL);
-- Chấm công 7 ngày gần nhất (tính từ ngày chạy script)
INSERT IGNORE INTO attendances (worker_id, worksite_id, work_date, check_in, check_out, regular_shifts, overtime_shifts, regular_rate, overtime_rate, validation_status, validation_note) VALUES
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '16:00:00', 1.0, 0, 350000, 525000, 'VALID', NULL),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '16:00:00', 1.0, 0, 550000, 825000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 480000, 720000, 'VALID', NULL),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '16:00:00', 1.0, 0, 500000, 750000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '16:00:00', 1.0, 0, 380000, 570000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 0 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 420000, 630000, 'VALID', NULL),
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '16:00:00', 1.0, 0, 350000, 525000, 'VALID', NULL),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 550000, 825000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '16:00:00', 1.0, 0, 480000, 720000, 'VALID', NULL),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '16:00:00', 1.0, 0, 500000, 750000, 'WARNING', 'Giờ ra sớm hơn quy định'),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 400000, 600000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 350000, 525000, 'VALID', NULL),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '16:00:00', 1.0, 0, 550000, 825000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '16:00:00', 1.0, 0, 480000, 720000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 380000, 570000, 'VALID', NULL),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '16:00:00', 1.0, 0, 400000, 600000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'WARNING', 'Giờ ra sớm hơn quy định'),
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '16:00:00', 1.0, 0, 350000, 525000, 'VALID', NULL),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '16:00:00', 1.0, 0, 550000, 825000, 'VALID', NULL),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 500000, 750000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '16:00:00', 1.0, 0, 380000, 570000, 'VALID', NULL),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '16:00:00', 1.0, 0, 400000, 600000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '16:00:00', 1.0, 0, 350000, 525000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 480000, 720000, 'WARNING', 'Giờ ra sớm hơn quy định'),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '16:00:00', 1.0, 0, 500000, 750000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '16:00:00', 1.0, 0, 380000, 570000, 'VALID', NULL),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '16:00:00', 1.0, 0, 400000, 600000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 420000, 630000, 'VALID', NULL),
  (1, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'WARNING', 'Giờ ra sớm hơn quy định'),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 550000, 825000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '16:00:00', 1.0, 0, 480000, 720000, 'VALID', NULL),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '16:00:00', 1.0, 0, 500000, 750000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '16:00:00', 1.0, 0, 380000, 570000, 'VALID', NULL),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 400000, 600000, 'VALID', NULL),
  (8, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '07:00:00', '16:00:00', 1.0, 0, 420000, 630000, 'VALID', NULL),
  (2, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 350000, 525000, 'VALID', NULL),
  (3, 1, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '16:00:00', 1.0, 0, 550000, 825000, 'VALID', NULL),
  (4, 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '16:00:00', 1.0, 0, 480000, 720000, 'VALID', NULL),
  (5, 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '16:00:00', 1.0, 0, 500000, 750000, 'VALID', NULL),
  (6, 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '20:00:00', 1.0, 0.5, 380000, 570000, 'VALID', NULL),
  (7, 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '07:00:00', '16:00:00', 1.0, 0, 400000, 600000, 'VALID', NULL);
INSERT IGNORE INTO payroll_periods (period_id, start_date, end_date, status, closed_by, closed_at) VALUES
  (1, '2026-10-08', '2026-10-14', 'CLOSED', 4, '2026-10-14 18:00:00'),
  (2, '2026-10-15', '2026-10-21', 'CLOSED', 4, '2026-10-21 18:00:00'),
  (3, '2026-10-22', '2026-10-28', 'CLOSED', 4, '2026-10-28 18:00:00');
INSERT IGNORE INTO payroll_details (detail_id, period_id, worker_id, regular_shifts, overtime_shifts, total_amount, payment_status) VALUES
  (1, 1, 1, 6.0, 0.0, 2520000, 'PAID'),
  (2, 1, 2, 5.0, 2.0, 2800000, 'PAID'),
  (3, 1, 3, 6.0, 0.0, 3300000, 'PAID'),
  (4, 1, 4, 6.0, 2.0, 4320000, 'PAID'),
  (5, 1, 5, 5.0, 0.0, 2500000, 'PAID'),
  (6, 1, 6, 6.0, 2.0, 3420000, 'PAID'),
  (7, 1, 7, 6.0, 0.0, 2400000, 'PAID'),
  (8, 1, 8, 5.0, 2.0, 3360000, 'PAID'),
  (9, 2, 1, 5.0, 0.0, 2100000, 'PAID'),
  (10, 2, 2, 6.0, 2.0, 3150000, 'PAID'),
  (11, 2, 3, 6.0, 0.0, 3300000, 'PAID'),
  (12, 2, 4, 5.0, 2.0, 3840000, 'PAID'),
  (13, 2, 5, 6.0, 0.0, 3000000, 'PAID'),
  (14, 2, 6, 6.0, 2.0, 3420000, 'PAID'),
  (15, 2, 7, 5.0, 0.0, 2000000, 'PAID'),
  (16, 2, 8, 6.0, 2.0, 3780000, 'PAID'),
  (17, 3, 1, 6.0, 0.0, 2520000, 'PARTIAL'),
  (18, 3, 2, 6.0, 2.0, 3150000, 'UNPAID'),
  (19, 3, 3, 5.0, 0.0, 2750000, 'PARTIAL'),
  (20, 3, 4, 6.0, 2.0, 4320000, 'UNPAID'),
  (21, 3, 5, 6.0, 0.0, 3000000, 'PARTIAL'),
  (22, 3, 6, 5.0, 2.0, 3040000, 'UNPAID'),
  (23, 3, 7, 6.0, 0.0, 2400000, 'PARTIAL'),
  (24, 3, 8, 6.0, 2.0, 3780000, 'UNPAID');
INSERT IGNORE INTO wage_payments (payment_id, detail_id, method, amount, payment_date, reference_code, recorded_by) VALUES
  (1, 1, 'TRANSFER', 2520000, '2026-10-14', NULL, 4),
  (2, 2, 'CASH', 2800000, '2026-10-14', NULL, 4),
  (3, 3, 'TRANSFER', 3300000, '2026-10-14', NULL, 4),
  (4, 4, 'CASH', 4320000, '2026-10-14', NULL, 4),
  (5, 5, 'TRANSFER', 2500000, '2026-10-14', NULL, 4),
  (6, 6, 'CASH', 3420000, '2026-10-14', NULL, 4),
  (7, 7, 'TRANSFER', 2400000, '2026-10-14', NULL, 4),
  (8, 8, 'CASH', 3360000, '2026-10-14', NULL, 4),
  (9, 9, 'TRANSFER', 2100000, '2026-10-21', NULL, 4),
  (10, 10, 'CASH', 3150000, '2026-10-21', NULL, 4),
  (11, 11, 'TRANSFER', 3300000, '2026-10-21', NULL, 4),
  (12, 12, 'CASH', 3840000, '2026-10-21', NULL, 4),
  (13, 13, 'TRANSFER', 3000000, '2026-10-21', NULL, 4),
  (14, 14, 'CASH', 3420000, '2026-10-21', NULL, 4),
  (15, 15, 'TRANSFER', 2000000, '2026-10-21', NULL, 4),
  (16, 16, 'CASH', 3780000, '2026-10-21', NULL, 4),
  (17, 17, 'CASH', 1260000, '2026-10-30', NULL, 4),
  (18, 19, 'CASH', 1375000, '2026-10-30', NULL, 4),
  (19, 21, 'CASH', 1500000, '2026-10-30', NULL, 4),
  (20, 23, 'CASH', 1200000, '2026-10-30', NULL, 4);

-- Thiết bị, tồn kho và phiếu kho (số liệu khớp nhau: nhập 5/4/2/100/60 − xuất + thu hồi = tồn)
INSERT IGNORE INTO equipment (equipment_id, equipment_code, equipment_name, type_id, condition_status, usage_status, responsible_person) VALUES
  (1, 'MK-001', 'Máy khoan Bosch GBH 2-26',   1, 'GOOD',        'IN_USE',   'Đỗ Văn Tài'),
  (2, 'MK-002', 'Máy khoan Makita HR2470',    1, 'GOOD',        'IN_USE',   'Đỗ Văn Tài'),
  (3, 'MD-001', 'Máy đục bê tông Hitachi',    1, 'NEED_REPAIR', 'IN_STOCK', 'Đỗ Văn Tài'),
  (4, 'GG-001', 'Giàn giáo khung 1.7m',       2, 'GOOD',        'IN_USE',   'Đỗ Văn Tài'),
  (5, 'GG-002', 'Cột chống thép điều chỉnh',  2, 'GOOD',        'IN_USE',   'Đỗ Văn Tài');
INSERT IGNORE INTO equipment_stock (stock_id, equipment_id, location_id, worksite_id, quantity) VALUES
  (1, 1, 1, NULL, 3), (2, 2, 1, NULL, 2), (3, 3, 2, NULL, 2), (4, 4, 3, NULL, 70), (5, 5, 3, NULL, 40),
  (6, 1, NULL, 1, 2), (7, 4, NULL, 1, 30), (8, 2, NULL, 2, 2), (9, 5, NULL, 2, 20);
INSERT IGNORE INTO goods_receipts (receipt_id, receipt_code, receipt_date, source, created_by) VALUES
  (1, 'PN00001', '2026-10-01', 'Công ty Thiết bị Xây dựng Miền Nam', 5);
INSERT IGNORE INTO goods_receipt_details (detail_id, receipt_id, equipment_id, location_id, quantity) VALUES
  (1, 1, 1, 1, 5), (2, 1, 2, 1, 4), (3, 1, 3, 2, 2), (4, 1, 4, 3, 100), (5, 1, 5, 3, 60);
INSERT IGNORE INTO issue_slips (issue_id, issue_code, issue_date, worksite_id, receiver_name, created_by) VALUES
  (1, 'PX00001', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 1, 'Nguyễn Văn An – Phạm Quốc Việt (Quản lý công trình)', 5),
  (2, 'PX00002', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2, 'Phạm Văn Dũng – Phạm Quốc Việt (Quản lý công trình)', 5);
INSERT IGNORE INTO issue_slip_details (detail_id, issue_id, equipment_id, location_id, quantity) VALUES
  (1, 1, 1, 1, 2), (2, 1, 4, 3, 40), (3, 2, 2, 1, 2), (4, 2, 5, 3, 20);
INSERT IGNORE INTO return_slips (return_id, return_code, return_date, worksite_id, returner_name, created_by) VALUES
  (1, 'PT00001', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 'Nguyễn Văn An – Phạm Quốc Việt (Quản lý công trình)', 5);
INSERT IGNORE INTO return_slip_details (detail_id, return_id, equipment_id, location_id, quantity, condition_after) VALUES
  (1, 1, 4, 3, 10, 'GOOD');

-- ---------------------------------------------------------------------
-- Chủ sở hữu dữ liệu: dữ liệu do tài khoản nào thêm thì chỉ tài khoản đó được sửa / xóa, tài khoản khác chỉ xem.
-- Dữ liệu mẫu ở trên chưa có người thêm nên giao cho tài khoản đầu tiên của vai trò phụ trách
-- (ứng dụng cũng tự làm việc này khi kết nối - ctv.util.DBConnection).
-- ---------------------------------------------------------------------
UPDATE workers          SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 2) WHERE created_by IS NULL;
UPDATE skills           SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 2) WHERE created_by IS NULL;
UPDATE skill_levels     SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 2) WHERE created_by IS NULL;
UPDATE worker_skills    SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 2) WHERE created_by IS NULL;
UPDATE shift_rates      SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 2) WHERE created_by IS NULL;
UPDATE worksites        SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 3) WHERE created_by IS NULL;
UPDATE manpower_demands SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 3) WHERE created_by IS NULL;
UPDATE assignments      SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 3) WHERE created_by IS NULL;
UPDATE attendances      SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 4) WHERE created_by IS NULL;
UPDATE equipment_types  SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 5) WHERE created_by IS NULL;
UPDATE equipment        SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 5) WHERE created_by IS NULL;
UPDATE storage_locations SET created_by = (SELECT MIN(user_id) FROM users WHERE role_id = 5) WHERE created_by IS NULL;
