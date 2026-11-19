package ctv.nhansu;

import ctv.util.Db;
import ctv.nhansu.HrModels.*;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import ctv.util.Labels;
import ctv.util.Ownership;
import ctv.util.Session;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nhóm 1 (TV1) – công nhân, kỹ năng, bậc thợ, gán kỹ năng, đơn giá, báo cáo nhân lực. Chạy trên CSDL Form 06. */
public class HrDAO {

    private static LocalDate date(ResultSet rs, int i) throws SQLException { return rs.getObject(i, LocalDate.class); }

    private static int count(String sql, Object... params) {
        return Db.query(sql, rs -> rs.getInt(1), params).get(0);
    }

    // ---------------- giao dịch + nhật ký (audit_logs) cho nhóm 1.3 ----------------
    @FunctionalInterface private interface Tx<T> { T run(Connection c) throws SQLException; }

    /** Chạy body trong MỘT giao dịch: lỗi thì hoàn tác cả dữ liệu lẫn dòng nhật ký. */
    private static <T> T tx(String errorPrefix, Tx<T> body) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                T r = body.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (SQLException e) {
            throw new BusinessException(errorPrefix + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, Object... p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }

    /** Thêm dòng và trả về khóa tự tăng. */
    private static int insertReturningId(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    private static int update(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, p); return ps.executeUpdate(); }
    }

    /** Đọc một chuỗi mô tả (cột 1) của bản ghi trước khi sửa/xóa; null nếu không có bản ghi. */
    private static String snapshot(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    /** Ghi audit_logs: ai (user_id), lúc nào, bảng nào, bản ghi nào, thao tác gì, giá trị cũ → mới. */
    private static void audit(Connection c, int userId, String table, int recordId, String action,
                              String oldValue, String newValue) throws SQLException {
        update(c, """
            INSERT INTO audit_logs(user_id, action_time, table_name, record_id, action_type, old_value, new_value)
            VALUES (?,NOW(),?,?,?,?,?)""", userId, table, recordId, action, oldValue, newValue);
    }

    private static final String SKILL_SNAP =
            "SELECT CONCAT('mã=', skill_code, '; tên=', skill_name, '; mô tả=', COALESCE(description,'')) FROM skills WHERE skill_id=?";
    private static final String LEVEL_SNAP = """
            SELECT CONCAT('mã=', l.level_code, '; tên=', l.level_name, '; kỹ năng=', s.skill_name, '; mô tả=', COALESCE(l.description,''))
            FROM skill_levels l JOIN skills s ON s.skill_id = l.skill_id WHERE l.level_id=?""";
    private static final String WORKER_SKILL_SNAP = """
            SELECT CONCAT('công nhân=', w.worker_code, '; kỹ năng=', s.skill_name, '; bậc=', l.level_name)
            FROM worker_skills ws JOIN workers w ON w.worker_id = ws.worker_id
            JOIN skills s ON s.skill_id = ws.skill_id JOIN skill_levels l ON l.level_id = ws.level_id
            WHERE ws.worker_skill_id=?""";

    // =====================================================================
    // 1.2 – Công nhân (workers, worker_types)
    // =====================================================================
    public List<WorkerType> workerTypes() {
        return Db.query("SELECT worker_type_id, type_name FROM worker_types ORDER BY worker_type_id",
                rs -> new WorkerType(rs.getInt(1), rs.getString(2)));
    }

    private static final String WORKER_SELECT = """
        SELECT w.worker_id, w.worker_code, w.full_name, w.birth_date, w.gender, w.id_card_no, w.phone, w.address,
               w.worker_type_id, t.type_name, w.hire_date, w.work_status, w.status_effective_date,
               w.bank_name, w.bank_account_no, w.bank_account_holder,
               EXISTS (SELECT 1 FROM assignments a WHERE a.worker_id = w.worker_id AND a.status = 'ACTIVE')
        FROM workers w JOIN worker_types t ON t.worker_type_id = w.worker_type_id
        """;

    /** Điều kiện SQL: công nhân chưa có phân công ACTIVE nào. */
    private static final String NOT_ASSIGNED =
            "NOT EXISTS (SELECT 1 FROM assignments a WHERE a.worker_id = w.worker_id AND a.status = 'ACTIVE')";
    /** Trạng thái hiệu lực: WORKING mà chưa phân công => WAITING. */
    public static final String EFFECTIVE_STATUS =
            "CASE WHEN w.work_status = 'WORKING' AND " + NOT_ASSIGNED + " THEN 'WAITING' ELSE w.work_status END";

    private static Worker mapWorker(ResultSet rs) throws SQLException {
        return new Worker(rs.getInt(1), rs.getString(2), rs.getString(3), date(rs, 4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getInt(9), rs.getString(10), date(rs, 11), rs.getString(12), date(rs, 13),
                rs.getString(14), rs.getString(15), rs.getString(16), rs.getBoolean(17));
    }

    /** Tìm / lọc công nhân theo mã, tên, SĐT, CCCD; lọc theo trạng thái, loại, kỹ năng và bậc thợ (null = tất cả). */
    public List<Worker> searchWorkers(String keyword, String status, Integer typeId, Integer skillId, Integer levelId) {
        StringBuilder sql = new StringBuilder(WORKER_SELECT).append(" WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.worker_code LIKE ? OR w.full_name LIKE ? OR w.phone LIKE ? OR w.id_card_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) p.add(like);
        }
        if (Labels.WAITING.equals(status)) sql.append(" AND w.work_status = 'WORKING' AND ").append(NOT_ASSIGNED);
        else if (status != null) {
            sql.append(" AND w.work_status = ?"); p.add(status);
            if ("WORKING".equals(status)) sql.append(" AND NOT ").append(NOT_ASSIGNED);   // "Đang làm việc" = đã có phân công
        }
        if (typeId != null) { sql.append(" AND w.worker_type_id = ?"); p.add(typeId); }
        // Kỹ năng và bậc thợ phải cùng một dòng gán (worker_skills), nên lọc chung trong một EXISTS.
        if (skillId != null || levelId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM worker_skills ws WHERE ws.worker_id = w.worker_id");
            if (skillId != null) { sql.append(" AND ws.skill_id = ?"); p.add(skillId); }
            if (levelId != null) { sql.append(" AND ws.level_id = ?"); p.add(levelId); }
            sql.append(")");
        }
        sql.append(" ORDER BY w.worker_code");
        return Db.query(sql.toString(), HrDAO::mapWorker, p.toArray());
    }

    /** Công nhân đang làm việc (WORKING) – dùng cho ComboBox gán kỹ năng / phân công. */
    public List<Worker> workingWorkers() {
        return Db.query(WORKER_SELECT + " WHERE w.work_status = 'WORKING' ORDER BY w.full_name", HrDAO::mapWorker);
    }

    public boolean workerCodeExists(String code, int excludeId) {
        return count("SELECT COUNT(*) FROM workers WHERE worker_code=? AND worker_id<>?", code, excludeId) > 0;
    }

    public boolean idCardExists(String idCard, int excludeId) {
        return count("SELECT COUNT(*) FROM workers WHERE id_card_no=? AND worker_id<>?", idCard, excludeId) > 0;
    }

    public void insertWorker(String code, String name, LocalDate birth, String gender, String idCard, String phone,
                             String address, int typeId, LocalDate hire, String status, LocalDate statusDate,
                             String bankName, String bankAccountNo, String bankAccountHolder) {
        Db.exec("""
            INSERT INTO workers(worker_code, full_name, birth_date, gender, id_card_no, phone, address,
                                worker_type_id, hire_date, work_status, status_effective_date,
                                bank_name, bank_account_no, bank_account_holder, created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                code, name, birth, gender, idCard, phone, address, typeId, hire, status, statusDate,
                bankName, bankAccountNo, bankAccountHolder, Session.userId());
    }

    public void updateWorker(int id, String code, String name, LocalDate birth, String gender, String idCard, String phone,
                             String address, int typeId, LocalDate hire, String status, LocalDate statusDate,
                             String bankName, String bankAccountNo, String bankAccountHolder) {
        Db.exec("""
            UPDATE workers SET worker_code=?, full_name=?, birth_date=?, gender=?, id_card_no=?, phone=?, address=?,
                               worker_type_id=?, hire_date=?, work_status=?, status_effective_date=?,
                               bank_name=?, bank_account_no=?, bank_account_holder=?
            WHERE worker_id=?""",
                code, name, birth, gender, idCard, phone, address, typeId, hire, status, statusDate,
                bankName, bankAccountNo, bankAccountHolder, id);
    }

    /** Xóa công nhân: chỉ khi chưa phát sinh phân công / chấm công / lương. Kỹ năng đã gán bị xóa theo. */
    public void deleteWorker(int id) {
        if (count("SELECT COUNT(*) FROM assignments WHERE worker_id=?", id) > 0
                || count("SELECT COUNT(*) FROM attendances WHERE worker_id=?", id) > 0
                || count("SELECT COUNT(*) FROM payroll_details WHERE worker_id=?", id) > 0)
            throw new BusinessException("Công nhân đã có phân công / chấm công / lương, không thể xóa. "
                    + "Hãy chuyển trạng thái sang \"Đã nghỉ việc\".");
        // Một giao dịch duy nhất: xóa kỹ năng đã gán (có ghi nhật ký) rồi xóa công nhân; lỗi ở bước nào thì hoàn tác tất cả.
        tx("Lỗi ghi dữ liệu: ", c -> {
            List<Integer> wsIds = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT worker_skill_id FROM worker_skills WHERE worker_id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) wsIds.add(rs.getInt(1)); }
            }
            int userId = Session.userId();
            for (int wsId : wsIds) {
                String before = snapshot(c, WORKER_SKILL_SNAP, wsId);
                update(c, "DELETE FROM worker_skills WHERE worker_skill_id=?", wsId);
                audit(c, userId, "worker_skills", wsId, "DELETE", before, null);
            }
            update(c, "DELETE FROM workers WHERE worker_id=?", id);
            return null;
        });
    }

    // =====================================================================
    // 1.3 – Kỹ năng, bậc thợ (skills, skill_levels)
    // =====================================================================
    public List<Skill> skills(String keyword) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return Db.query("""
            SELECT skill_id, skill_code, skill_name, description FROM skills
            WHERE skill_code LIKE ? OR skill_name LIKE ? ORDER BY skill_code""",
                rs -> new Skill(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), like, like);
    }

    public boolean skillCodeExists(String code, int excludeId) {
        return count("SELECT COUNT(*) FROM skills WHERE skill_code=? AND skill_id<>?", code, excludeId) > 0;
    }

    public void insertSkill(String code, String name, String desc, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            int id = insertReturningId(c, "INSERT INTO skills(skill_code, skill_name, description, created_by) VALUES (?,?,?,?)", code, name, desc, userId);
            audit(c, userId, "skills", id, "INSERT", null, snapshot(c, SKILL_SNAP, id));
            return null;
        });
    }

    public void updateSkill(int id, String code, String name, String desc, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            String before = snapshot(c, SKILL_SNAP, id);
            update(c, "UPDATE skills SET skill_code=?, skill_name=?, description=? WHERE skill_id=?", code, name, desc, id);
            audit(c, userId, "skills", id, "UPDATE", before, snapshot(c, SKILL_SNAP, id));
            return null;
        });
    }

    public void deleteSkill(int id, int userId) {
        if (count("SELECT COUNT(*) FROM skill_levels WHERE skill_id=?", id) > 0)
            throw new BusinessException("Kỹ năng còn bậc thợ, hãy xóa các bậc thợ trước.");
        if (count("SELECT COUNT(*) FROM worker_skills WHERE skill_id=?", id) > 0
                || count("SELECT COUNT(*) FROM shift_rates WHERE skill_id=?", id) > 0
                || count("SELECT COUNT(*) FROM manpower_demands WHERE skill_id=?", id) > 0)
            throw new BusinessException("Kỹ năng đang được dùng (gán cho công nhân / đơn giá / nhu cầu nhân lực), không thể xóa.");
        tx("Lỗi ghi dữ liệu: ", c -> {
            String before = snapshot(c, SKILL_SNAP, id);
            update(c, "DELETE FROM skills WHERE skill_id=?", id);
            audit(c, userId, "skills", id, "DELETE", before, null);
            return null;
        });
    }

    /** Bậc thợ theo kỹ năng (null = tất cả), sắp theo kỹ năng rồi level_id (bậc thấp → cao). */
    public List<Level> levels(Integer skillId) {
        return Db.query("""
            SELECT l.level_id, l.level_code, l.level_name, l.skill_id, s.skill_name, l.description
            FROM skill_levels l JOIN skills s ON s.skill_id = l.skill_id
            WHERE (? IS NULL OR l.skill_id = ?) ORDER BY s.skill_code, l.level_id""",
                rs -> new Level(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6)),
                skillId, skillId);
    }

    public boolean levelCodeExists(String code, int excludeId) {
        return count("SELECT COUNT(*) FROM skill_levels WHERE level_code=? AND level_id<>?", code, excludeId) > 0;
    }

    public void insertLevel(String code, String name, int skillId, String desc, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            int id = insertReturningId(c, "INSERT INTO skill_levels(level_code, level_name, skill_id, description, created_by) VALUES (?,?,?,?,?)",
                    code, name, skillId, desc, userId);
            audit(c, userId, "skill_levels", id, "INSERT", null, snapshot(c, LEVEL_SNAP, id));
            return null;
        });
    }

    public void updateLevel(int id, String code, String name, String desc, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            String before = snapshot(c, LEVEL_SNAP, id);
            update(c, "UPDATE skill_levels SET level_code=?, level_name=?, description=? WHERE level_id=?", code, name, desc, id);
            audit(c, userId, "skill_levels", id, "UPDATE", before, snapshot(c, LEVEL_SNAP, id));
            return null;
        });
    }

    public void deleteLevel(int id, int userId) {
        if (count("SELECT COUNT(*) FROM worker_skills WHERE level_id=?", id) > 0
                || count("SELECT COUNT(*) FROM shift_rates WHERE level_id=?", id) > 0
                || count("SELECT COUNT(*) FROM manpower_demands WHERE level_id=?", id) > 0)
            throw new BusinessException("Bậc thợ đang được dùng (gán cho công nhân / đơn giá / nhu cầu nhân lực), không thể xóa.");
        tx("Lỗi ghi dữ liệu: ", c -> {
            String before = snapshot(c, LEVEL_SNAP, id);
            update(c, "DELETE FROM skill_levels WHERE level_id=?", id);
            audit(c, userId, "skill_levels", id, "DELETE", before, null);
            return null;
        });
    }

    // =====================================================================
    // 1.3 – Gán kỹ năng + bậc thợ cho công nhân (worker_skills)
    // =====================================================================
    public List<WorkerSkillRow> workerSkills(String keyword, Integer skillId) {
        StringBuilder sql = new StringBuilder("""
            SELECT ws.worker_skill_id, w.worker_id, w.worker_code, w.full_name, s.skill_id, s.skill_name, l.level_id, l.level_name
            FROM worker_skills ws JOIN workers w ON w.worker_id = ws.worker_id
            JOIN skills s ON s.skill_id = ws.skill_id JOIN skill_levels l ON l.level_id = ws.level_id
            WHERE 1=1""");
        List<Object> p = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.worker_code LIKE ? OR w.full_name LIKE ?)");
            p.add("%" + keyword.trim() + "%");
            p.add("%" + keyword.trim() + "%");
        }
        if (skillId != null) { sql.append(" AND ws.skill_id = ?"); p.add(skillId); }
        sql.append(" ORDER BY w.worker_code, s.skill_code");
        return Db.query(sql.toString(), rs -> new WorkerSkillRow(rs.getInt(1), rs.getInt(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), rs.getInt(7), rs.getString(8)), p.toArray());
    }

    /** Mỗi công nhân chỉ có một bậc cho mỗi kỹ năng: đã có thì cập nhật bậc (UNIQUE worker_id + skill_id). */
    public void assignSkill(int workerId, int skillId, int levelId, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            Integer existing = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT worker_skill_id FROM worker_skills WHERE worker_id=? AND skill_id=? FOR UPDATE")) {
                bind(ps, workerId, skillId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) existing = rs.getInt(1); }
            }
            if (existing == null) {
                int id = insertReturningId(c, "INSERT INTO worker_skills(worker_id, skill_id, level_id, created_by) VALUES (?,?,?,?)",
                        workerId, skillId, levelId, userId);
                audit(c, userId, "worker_skills", id, "INSERT", null, snapshot(c, WORKER_SKILL_SNAP, id));
            } else {
                // Dòng đã có (cùng công nhân + kỹ năng): chỉ người đã gán mới được đổi bậc thợ.
                if (!Ownership.isMine(Ownership.ownerIn(c, Ownership.Kind.WORKER_SKILL, existing)))
                    throw new BusinessException("Bạn chỉ được sửa kỹ năng của công nhân do chính mình thêm.");
                String before = snapshot(c, WORKER_SKILL_SNAP, existing);
                update(c, "UPDATE worker_skills SET level_id=? WHERE worker_skill_id=?", levelId, existing);
                audit(c, userId, "worker_skills", existing, "UPDATE", before, snapshot(c, WORKER_SKILL_SNAP, existing));
            }
            return null;
        });
    }

    public void removeWorkerSkill(int workerSkillId, int userId) {
        tx("Lỗi ghi dữ liệu: ", c -> {
            String before = snapshot(c, WORKER_SKILL_SNAP, workerSkillId);
            update(c, "DELETE FROM worker_skills WHERE worker_skill_id=?", workerSkillId);
            audit(c, userId, "worker_skills", workerSkillId, "DELETE", before, null);
            return null;
        });
    }

    // =====================================================================
    // 1.3 – Đơn giá theo ca (shift_rates) và lịch sử (rate_histories)
    // =====================================================================
    public List<Rate> rates(Integer skillId) {
        return Db.query("""
            SELECT r.rate_id, r.skill_id, s.skill_name, r.level_id, l.level_name, r.shift_type, r.rate, r.effective_date
            FROM shift_rates r JOIN skills s ON s.skill_id = r.skill_id JOIN skill_levels l ON l.level_id = r.level_id
            WHERE (? IS NULL OR r.skill_id = ?)
              AND r.rate_id = (SELECT x.rate_id FROM shift_rates x
                               WHERE x.skill_id = r.skill_id AND x.level_id = r.level_id AND x.shift_type = r.shift_type
                               ORDER BY (x.effective_date > CURDATE()), ABS(DATEDIFF(x.effective_date, CURDATE())), x.rate_id DESC
                               LIMIT 1)
            ORDER BY s.skill_code, l.level_id, r.shift_type""",
                rs -> new Rate(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7), date(rs, 8)), skillId, skillId);
    }

    /**
     * Lưu đơn giá của (kỹ năng, bậc, loại ca) trong MỘT giao dịch. Mỗi mức giá là MỘT dòng shift_rates có ngày hiệu lực riêng:
     * đổi giá thì thêm dòng mới (giữ nguyên các dòng cũ để ngày chấm công trước ngày áp dụng vẫn tra được đơn giá cũ);
     * chỉ khi đã có dòng cùng ngày hiệu lực thì sửa giá của dòng đó (hiệu chỉnh nhập sai).
     * Mỗi lần lưu ghi một dòng rate_histories (giá cũ → giá mới) để không mất lịch sử.
     */
    public void saveRate(int skillId, int levelId, String shiftType, BigDecimal newRate, LocalDate effective, int userId) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                Integer rateId = null;
                BigDecimal old = null;
                // Giá đang hiệu lực ngay trước (hoặc đúng vào) ngày hiệu lực mới -> giá cũ ghi vào lịch sử.
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT rate FROM shift_rates WHERE skill_id=? AND level_id=? AND shift_type=? AND effective_date<=?
                        ORDER BY effective_date DESC, rate_id DESC LIMIT 1 FOR UPDATE""")) {
                    ps.setInt(1, skillId); ps.setInt(2, levelId); ps.setString(3, shiftType); ps.setObject(4, effective);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) old = rs.getBigDecimal(1);
                    }
                }
                // Đã có dòng cùng ngày hiệu lực thì hiệu chỉnh dòng đó, ngược lại thêm dòng mới.
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT rate_id FROM shift_rates WHERE skill_id=? AND level_id=? AND shift_type=? AND effective_date=? FOR UPDATE")) {
                    ps.setInt(1, skillId); ps.setInt(2, levelId); ps.setString(3, shiftType); ps.setObject(4, effective);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) rateId = rs.getInt(1);
                    }
                }
                if (rateId == null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO shift_rates(skill_id, level_id, shift_type, rate, effective_date, created_by) VALUES (?,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, skillId); ps.setInt(2, levelId); ps.setString(3, shiftType);
                        ps.setBigDecimal(4, newRate); ps.setObject(5, effective); ps.setInt(6, userId);
                        ps.executeUpdate();
                        try (ResultSet k = ps.getGeneratedKeys()) { k.next(); rateId = k.getInt(1); }
                    }
                } else {
                    // Đã có dòng cùng ngày hiệu lực: chỉ người đã thêm dòng đó mới được hiệu chỉnh giá.
                    if (!Ownership.isMine(Ownership.ownerIn(c, Ownership.Kind.RATE, rateId)))
                        throw new BusinessException("Bạn chỉ được sửa đơn giá do chính mình thêm.");
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE shift_rates SET rate=? WHERE rate_id=?")) {
                        ps.setBigDecimal(1, newRate); ps.setInt(2, rateId);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO rate_histories(rate_id, old_rate, new_rate, effective_date, changed_by)
                        VALUES (?,?,?,?,?)""")) {
                    ps.setInt(1, rateId); ps.setBigDecimal(2, old); ps.setBigDecimal(3, newRate);
                    ps.setObject(4, effective); ps.setInt(5, userId);
                    ps.executeUpdate();
                }
                audit(c, userId, "shift_rates", rateId, old == null ? "INSERT" : "UPDATE",
                        old == null ? null : "ca=" + shiftType + "; đơn giá=" + old.toPlainString(),
                        "ca=" + shiftType + "; đơn giá=" + newRate.toPlainString() + "; hiệu lực=" + effective);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (SQLException e) {
            throw new BusinessException("Lỗi lưu đơn giá: " + e.getMessage(), e);
        }
    }

    /** Lịch sử thay đổi đơn giá. Cột: Thời điểm, Kỹ năng, Bậc, Ca, Giá cũ, Giá mới, Ngày hiệu lực, Người đổi. */
    public List<Object[]> rateHistories(Integer skillId) {
        return Db.query("""
            SELECT h.changed_at, s.skill_name, l.level_name, r.shift_type, h.old_rate, h.new_rate, h.effective_date, u.full_name
            FROM rate_histories h JOIN shift_rates r ON r.rate_id = h.rate_id
            JOIN skills s ON s.skill_id = r.skill_id JOIN skill_levels l ON l.level_id = r.level_id
            JOIN users u ON u.user_id = h.changed_by
            WHERE (? IS NULL OR r.skill_id = ?) ORDER BY h.history_id DESC""",
                rs -> new Object[]{rs.getTimestamp(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), date(rs, 7), rs.getString(8)}, skillId, skillId);
    }

    // =====================================================================
    // 1.4 – Báo cáo tổng hợp nhân lực
    // =====================================================================
    /** Thống kê số công nhân theo: "Kỹ năng", "Bậc thợ", "Trạng thái", "Loại công nhân". Mỗi dòng {nhãn, số công nhân}. */
    public List<Object[]> countBy(String dimension) {
        String sql = switch (dimension) {
            case "Kỹ năng" -> """
                SELECT s.skill_name, COUNT(DISTINCT w.worker_id) FROM workers w
                LEFT JOIN worker_skills ws ON ws.worker_id = w.worker_id LEFT JOIN skills s ON s.skill_id = ws.skill_id
                GROUP BY s.skill_id, s.skill_name ORDER BY 2 DESC""";
            case "Bậc thợ" -> """
                SELECT CONCAT(s.skill_name, ' - ', l.level_name), COUNT(DISTINCT w.worker_id) FROM workers w
                LEFT JOIN worker_skills ws ON ws.worker_id = w.worker_id LEFT JOIN skills s ON s.skill_id = ws.skill_id
                LEFT JOIN skill_levels l ON l.level_id = ws.level_id
                GROUP BY s.skill_id, s.skill_name, l.level_id, l.level_name ORDER BY 2 DESC""";
            case "Trạng thái" -> "SELECT " + EFFECTIVE_STATUS + " AS st, COUNT(*) FROM workers w GROUP BY st ORDER BY 2 DESC";
            default -> """
                SELECT t.type_name, COUNT(*) FROM workers w JOIN worker_types t ON t.worker_type_id = w.worker_type_id
                GROUP BY t.worker_type_id, t.type_name ORDER BY 2 DESC""";
        };
        boolean status = dimension.equals("Trạng thái");
        return Db.query(sql, rs -> {
            String label = rs.getString(1);
            if (label == null) label = "Chưa gán";
            else if (status) label = ctv.util.Labels.workStatus(label);
            return new Object[]{label, rs.getInt(2)};
        });
    }

    /** Số công nhân theo từng trạng thái làm việc (WORKING, ON_LEAVE, RESIGNED) cho các thẻ thống kê. */
    public Map<String, Integer> statusCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("WORKING", 0); m.put("ON_LEAVE", 0); m.put("RESIGNED", 0);
        Db.query("SELECT work_status, COUNT(*) FROM workers GROUP BY work_status",
                rs -> { m.put(rs.getString(1), rs.getInt(2)); return 0; });
        return m;
    }
}
