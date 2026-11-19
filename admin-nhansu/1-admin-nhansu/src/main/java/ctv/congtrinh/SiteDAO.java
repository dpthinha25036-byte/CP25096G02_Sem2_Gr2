package ctv.congtrinh;

import ctv.util.Db;
import ctv.congtrinh.SiteModels.*;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import ctv.util.Session;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Nhóm 2 (TV2) – công trình, tiến độ, nhu cầu nhân lực, phân công, cảnh báo thiếu/đủ. Chạy trên CSDL Form 06. */
public class SiteDAO {

    private static LocalDate date(ResultSet rs, int i) throws SQLException { return rs.getObject(i, LocalDate.class); }

    private static int count(String sql, Object... params) {
        return Db.query(sql, rs -> rs.getInt(1), params).get(0);
    }

    // =====================================================================
    // 2.1 – Công trình (worksites, worksite_progress)
    // =====================================================================
    private static Site mapSite(ResultSet rs) throws SQLException {
        return new Site(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                date(rs, 6), date(rs, 7), date(rs, 8), date(rs, 9), rs.getString(10), rs.getBigDecimal(11),
                (Integer) rs.getObject(12), rs.getString(13), rs.getString(14));
    }

    private static final String SITE_SELECT = """
        SELECT w.worksite_id, w.worksite_code, w.worksite_name, w.address, w.investor, w.planned_start, w.planned_end,
               w.actual_start, w.actual_end, w.status, w.progress_percent, w.created_by, u.full_name, r.role_name
        FROM worksites w LEFT JOIN users u ON u.user_id = w.created_by LEFT JOIN roles r ON r.role_id = u.role_id""";

    public List<Site> searchSites(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SITE_SELECT).append(" WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.worksite_code LIKE ? OR w.worksite_name LIKE ? OR w.investor LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            p.add(like); p.add(like); p.add(like);
        }
        if (status != null) { sql.append(" AND w.status = ?"); p.add(status); }
        sql.append(" ORDER BY w.worksite_code");
        return Db.query(sql.toString(), SiteDAO::mapSite, p.toArray());
    }

    /** Công trình đang thi công (IN_PROGRESS) – chỉ những công trình này mới được phân công công nhân. */
    public List<Site> activeSites() {
        return Db.query(SITE_SELECT + " WHERE w.status = 'IN_PROGRESS' ORDER BY w.worksite_code", SiteDAO::mapSite);
    }

    public List<Site> allSites() {
        return Db.query(SITE_SELECT + " ORDER BY w.worksite_code", SiteDAO::mapSite);
    }

    /** Mã người dùng đã thêm công trình (null nếu công trình cũ chưa ghi nhận hoặc không tồn tại). */
    public Integer ownerOf(int worksiteId) {
        List<Integer> r = Db.query("SELECT created_by FROM worksites WHERE worksite_id=?",
                rs -> (Integer) rs.getObject(1), worksiteId);
        return r.isEmpty() ? null : r.get(0);
    }

    public boolean siteCodeExists(String code, int excludeId) {
        return count("SELECT COUNT(*) FROM worksites WHERE worksite_code=? AND worksite_id<>?", code, excludeId) > 0;
    }

    /** Thêm công trình và ghi mốc tiến độ đầu tiên vào worksite_progress. */
    public void insertSite(String code, String name, String address, String investor, LocalDate plannedStart,
                           LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd, String status, LocalDate statusDate,
                           BigDecimal progress, LocalDate progressDate, String note) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO worksites(worksite_code, worksite_name, address, investor, planned_start, planned_end,
                                              actual_start, actual_end, status, progress_percent, created_by)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code); ps.setString(2, name); ps.setString(3, address); ps.setString(4, investor);
                    ps.setObject(5, plannedStart); ps.setObject(6, plannedEnd);
                    ps.setObject(7, actualStart); ps.setObject(8, actualEnd);
                    ps.setString(9, status); ps.setBigDecimal(10, progress); ps.setInt(11, Session.userId());
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getInt(1); }
                }
                addProgress(c, id, progress, progressDate, note);
                addStatus(c, id, null, status, statusDate);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi thêm công trình: " + e.getMessage(), e);
        }
    }

    /** Sửa công trình; nếu phần trăm tiến độ đổi thì ghi thêm một dòng lịch sử tiến độ (cùng giao dịch). */
    public void updateSite(int id, String code, String name, String address, String investor, LocalDate plannedStart,
                           LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd, String status, LocalDate statusDate,
                           BigDecimal progress, LocalDate progressDate, String note) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                BigDecimal oldProgress = null; String oldStatus = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT progress_percent,status FROM worksites WHERE worksite_id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { oldProgress = rs.getBigDecimal(1); oldStatus = rs.getString(2); } }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE worksites SET worksite_code=?, worksite_name=?, address=?, investor=?, planned_start=?,
                               planned_end=?, actual_start=?, actual_end=?, status=?, progress_percent=?
                        WHERE worksite_id=?""")) {
                    ps.setString(1, code); ps.setString(2, name); ps.setString(3, address); ps.setString(4, investor);
                    ps.setObject(5, plannedStart); ps.setObject(6, plannedEnd);
                    ps.setObject(7, actualStart); ps.setObject(8, actualEnd);
                    ps.setString(9, status); ps.setBigDecimal(10, progress); ps.setInt(11, id);
                    ps.executeUpdate();
                }
                if (oldProgress == null || oldProgress.compareTo(progress) != 0) addProgress(c, id, progress, progressDate, note);
                if (oldStatus == null || !oldStatus.equals(status)) addStatus(c, id, oldStatus, status, statusDate);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi sửa công trình: " + e.getMessage(), e);
        }
    }

    private static void addProgress(Connection c, int worksiteId, BigDecimal progress, LocalDate updateDate, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO worksite_progress(worksite_id, progress_percent, update_date, note) VALUES (?,?,?,?)")) {
            ps.setInt(1, worksiteId); ps.setBigDecimal(2, progress); ps.setObject(3, updateDate == null ? LocalDate.now() : updateDate); ps.setString(4, note);
            ps.executeUpdate();
        }
    }


    /** Mã bảng ghi trong audit_logs cho lịch sử đổi trạng thái công trình (CSDL Form 06 không có bảng riêng). */
    private static final String STATUS_LOG = "worksites.status";

    /**
     * Form 02A 2.1.6 – ghi lại lần đổi trạng thái (trạng thái cũ → mới, ngày thay đổi) vào audit_logs (Form 06, bảng 5).
     * new_value có dạng "TRẠNG_THÁI|yyyy-MM-dd".
     */
    private static void addStatus(Connection c, int worksiteId, String oldStatus, String status, LocalDate statusDate) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO audit_logs(user_id, action_time, table_name, record_id, action_type, old_value, new_value)
                VALUES (?, NOW(), ?, ?, ?, ?, ?)""")) {
            ps.setInt(1, Session.userId()); ps.setString(2, STATUS_LOG); ps.setInt(3, worksiteId);
            ps.setString(4, oldStatus == null ? "INSERT" : "UPDATE"); ps.setString(5, oldStatus);
            ps.setString(6, status + "|" + (statusDate == null ? LocalDate.now() : statusDate));
            ps.executeUpdate();
        }
    }

    /** Lịch sử trạng thái: {ngày thay đổi, trạng thái}, mới nhất trước. */
    public List<Object[]> statusHistory(int worksiteId) {
        return Db.query("SELECT new_value FROM audit_logs WHERE table_name=? AND record_id=? ORDER BY audit_id DESC",
                rs -> {
                    String[] v = rs.getString(1).split("\\|", 2);
                    return new Object[]{LocalDate.parse(v[1]), v[0]};
                }, STATUS_LOG, worksiteId);
    }

    /** Lịch sử cập nhật tiến độ: {ngày, % tiến độ, ghi chú}. */
    public List<Object[]> progressHistory(int worksiteId) {
        return Db.query("""
            SELECT update_date, progress_percent, note FROM worksite_progress
            WHERE worksite_id=? ORDER BY progress_id DESC""",
                rs -> new Object[]{date(rs, 1), rs.getBigDecimal(2), rs.getString(3)}, worksiteId);
    }

    /** Xóa công trình chưa phát sinh dữ liệu liên quan (kế hoạch, phân công, thiết bị, phiếu kho). */
    public void deleteSite(int id) {
        String[][] checks = {
                {"manpower_plans", "kế hoạch nhân lực"}, {"assignments", "phân công"},
                {"equipment_stock", "thiết bị đang tại công trình"}, {"issue_slips", "phiếu xuất thiết bị"},
                {"return_slips", "phiếu thu hồi thiết bị"}};
        for (String[] ck : checks)
            if (count("SELECT COUNT(*) FROM " + ck[0] + " WHERE worksite_id=?", id) > 0)
                throw new BusinessException("Công trình đã có " + ck[1] + ", không thể xóa.");
        Db.exec("DELETE FROM worksite_progress WHERE worksite_id=?", id);
        Db.exec("DELETE FROM worksites WHERE worksite_id=?", id);
    }

    // =====================================================================
    // 2.2 – Nhu cầu nhân lực (manpower_plans + manpower_demands)
    // =====================================================================
    public List<Demand> demands(Integer worksiteId) {
        return Db.query("""
            SELECT d.demand_id, p.plan_id, p.worksite_id, w.worksite_name, p.from_date, p.to_date, d.phase_name,
                   d.skill_id, s.skill_name, d.level_id, l.level_name, d.quantity_needed
            FROM manpower_demands d JOIN manpower_plans p ON p.plan_id = d.plan_id
            JOIN worksites w ON w.worksite_id = p.worksite_id
            JOIN skills s ON s.skill_id = d.skill_id JOIN skill_levels l ON l.level_id = d.level_id
            WHERE (? IS NULL OR p.worksite_id = ?) ORDER BY w.worksite_code, p.from_date, d.phase_name, s.skill_name""",
                rs -> new Demand(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), date(rs, 5), date(rs, 6),
                        rs.getString(7), rs.getInt(8), rs.getString(9), rs.getInt(10), rs.getString(11), rs.getInt(12)),
                worksiteId, worksiteId);
    }

    /** Tìm kế hoạch cùng công trình + cùng khoảng ngày để dùng lại; chưa có thì tạo mới. Chạy trong giao dịch của người gọi. */
    private static int planFor(Connection c, int worksiteId, LocalDate from, LocalDate to, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT plan_id FROM manpower_plans WHERE worksite_id=? AND from_date=? AND to_date=? LIMIT 1")) {
            ps.setInt(1, worksiteId); ps.setObject(2, from); ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO manpower_plans(worksite_id, from_date, to_date, created_by) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, worksiteId); ps.setObject(2, from); ps.setObject(3, to); ps.setInt(4, userId);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    /** Thêm một dòng nhu cầu (dùng lại hoặc tạo kế hoạch theo công trình + khoảng ngày) trong một giao dịch. */
    public void addDemand(int worksiteId, LocalDate from, LocalDate to, String phase, int skillId, int levelId,
                          int quantity, int userId) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                int planId = planFor(c, worksiteId, from, to, userId);
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO manpower_demands(plan_id, phase_name, skill_id, level_id, quantity_needed, created_by)
                        VALUES (?,?,?,?,?,?)""")) {
                    ps.setInt(1, planId); ps.setString(2, phase); ps.setInt(3, skillId); ps.setInt(4, levelId);
                    ps.setInt(5, quantity); ps.setInt(6, userId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi lưu nhu cầu nhân lực: " + e.getMessage(), e);
        }
    }

    /** Sửa một dòng nhu cầu; nếu đổi khoảng ngày / công trình thì chuyển sang kế hoạch phù hợp và dọn kế hoạch cũ nếu rỗng. */
    public void updateDemand(int demandId, int oldPlanId, int worksiteId, LocalDate from, LocalDate to, String phase,
                             int skillId, int levelId, int quantity, int userId) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                int planId = planFor(c, worksiteId, from, to, userId);
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE manpower_demands SET plan_id=?, phase_name=?, skill_id=?, level_id=?, quantity_needed=?
                        WHERE demand_id=?""")) {
                    ps.setInt(1, planId); ps.setString(2, phase); ps.setInt(3, skillId); ps.setInt(4, levelId);
                    ps.setInt(5, quantity); ps.setInt(6, demandId);
                    ps.executeUpdate();
                }
                if (planId != oldPlanId) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            DELETE FROM manpower_plans WHERE plan_id=?
                              AND NOT EXISTS (SELECT 1 FROM manpower_demands WHERE plan_id=?)""")) {
                        ps.setInt(1, oldPlanId); ps.setInt(2, oldPlanId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi sửa nhu cầu nhân lực: " + e.getMessage(), e);
        }
    }

    /** Xóa một dòng nhu cầu; kế hoạch không còn dòng nào thì xóa luôn để khỏi để lại kế hoạch rỗng. */
    public void deleteDemand(int demandId, int planId) {
        Db.exec("DELETE FROM manpower_demands WHERE demand_id=?", demandId);
        Db.exec("""
            DELETE FROM manpower_plans WHERE plan_id=?
              AND NOT EXISTS (SELECT 1 FROM manpower_demands WHERE plan_id=?)""", planId, planId);
    }

    // =====================================================================
    // 2.3 – Phân công công nhân (assignments)
    // =====================================================================
    public List<Assignment> assignments(String keyword, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.assignment_id, w.worker_id, w.worker_code, w.full_name, s.worksite_id, s.worksite_name,
                   a.start_date, a.expected_end_date, a.actual_end_date, a.status, a.cancel_reason
            FROM assignments a JOIN workers w ON w.worker_id = a.worker_id JOIN worksites s ON s.worksite_id = a.worksite_id
            WHERE 1=1""");
        List<Object> p = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.worker_code LIKE ? OR w.full_name LIKE ? OR s.worksite_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            p.add(like); p.add(like); p.add(like);
        }
        if (status != null) { sql.append(" AND a.status = ?"); p.add(status); }
        sql.append(" ORDER BY a.assignment_id DESC");
        return Db.query(sql.toString(), rs -> new Assignment(rs.getInt(1), rs.getInt(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), date(rs, 7), date(rs, 8), date(rs, 9),
                rs.getString(10), rs.getString(11)), p.toArray());
    }

    /**
     * Tạo phân công. Trong cùng giao dịch: khóa dòng công nhân, kiểm tra công nhân đang làm việc, công trình đang thi công
     * và công nhân không có phân công ACTIVE nào trùng khoảng thời gian.
     */
    public void assign(int workerId, int worksiteId, LocalDate start, LocalDate expectedEnd) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT work_status FROM workers WHERE worker_id=? FOR UPDATE")) {
                    ps.setInt(1, workerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BusinessException("Không tìm thấy công nhân.");
                        if (!"WORKING".equals(rs.getString(1)))
                            throw new BusinessException("Chỉ phân công được công nhân đang làm việc.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT status FROM worksites WHERE worksite_id=?")) {
                    ps.setInt(1, worksiteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BusinessException("Không tìm thấy công trình.");
                        if (!"IN_PROGRESS".equals(rs.getString(1)))
                            throw new BusinessException("Chỉ phân công vào công trình đang thi công.");
                    }
                }
                // Nếu công trình đã lập nhu cầu trong khoảng ngày, công nhân phải đáp ứng ít nhất một kỹ năng/bậc tương ứng.
                LocalDate newEnd = expectedEnd == null ? LocalDate.of(9999, 12, 31) : expectedEnd;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT COUNT(*) FROM manpower_demands d JOIN manpower_plans p ON p.plan_id=d.plan_id
                        WHERE p.worksite_id=? AND p.from_date<=? AND p.to_date>=?
                          AND EXISTS (SELECT 1 FROM worker_skills ws WHERE ws.worker_id=? AND ws.skill_id=d.skill_id AND ws.level_id>=d.level_id)
                        """)) {
                    ps.setInt(1, worksiteId); ps.setObject(2, newEnd); ps.setObject(3, start); ps.setInt(4, workerId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next();
                        int matches = rs.getInt(1);
                        int demands = count("SELECT COUNT(*) FROM manpower_demands d JOIN manpower_plans p ON p.plan_id=d.plan_id WHERE p.worksite_id=? AND p.from_date<=? AND p.to_date>=?", worksiteId, newEnd, start);
                        if (demands == 0) throw new BusinessException("Công trình chưa có nhu cầu nhân lực nào phủ kỳ phân công. Hãy lập nhu cầu nhân lực trước khi phân công.");
                        if (matches == 0) throw new BusinessException("Công nhân không có kỹ năng/bậc thợ phù hợp với nhu cầu của công trình trong thời gian phân công.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT COUNT(*) FROM assignments
                        WHERE worker_id=? AND status='ACTIVE'
                          AND start_date <= ? AND COALESCE(expected_end_date, '9999-12-31') >= ?""")) {
                    ps.setInt(1, workerId); ps.setObject(2, newEnd); ps.setObject(3, start);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) throw new BusinessException("Công nhân đã có phân công trùng khoảng thời gian này.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO assignments(worker_id, worksite_id, start_date, expected_end_date, status, created_by)
                        VALUES (?,?,?,?, 'ACTIVE', ?)""")) {
                    ps.setInt(1, workerId); ps.setInt(2, worksiteId); ps.setObject(3, start); ps.setObject(4, expectedEnd);
                    ps.setInt(5, Session.userId());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi lưu phân công: " + e.getMessage(), e);
        }
    }

    /** Sửa phân công đang hoạt động; kiểm tra trạng thái, kỹ năng và không chồng lịch. */
    public void updateAssignment(int id, int workerId, int worksiteId, LocalDate start, LocalDate expectedEnd) {
        try (Connection c=DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps=c.prepareStatement("SELECT work_status FROM workers WHERE worker_id=? FOR UPDATE")) {
                    ps.setInt(1,workerId); try(ResultSet rs=ps.executeQuery()){if(!rs.next()||!"WORKING".equals(rs.getString(1)))throw new BusinessException("Chỉ phân công được công nhân đang làm việc.");}
                }
                try (PreparedStatement ps=c.prepareStatement("SELECT status FROM worksites WHERE worksite_id=?")) {
                    ps.setInt(1,worksiteId); try(ResultSet rs=ps.executeQuery()){if(!rs.next()||!"IN_PROGRESS".equals(rs.getString(1)))throw new BusinessException("Chỉ phân công vào công trình đang thi công.");}
                }
                LocalDate end=expectedEnd==null?LocalDate.of(9999,12,31):expectedEnd;
                int demandCount=count("SELECT COUNT(*) FROM manpower_demands d JOIN manpower_plans p ON p.plan_id=d.plan_id WHERE p.worksite_id=? AND p.from_date<=? AND p.to_date>=?",worksiteId,end,start);
                int skillCount=count("SELECT COUNT(*) FROM manpower_demands d JOIN manpower_plans p ON p.plan_id=d.plan_id JOIN worker_skills ws ON ws.worker_id=? AND ws.skill_id=d.skill_id AND ws.level_id>=d.level_id WHERE p.worksite_id=? AND p.from_date<=? AND p.to_date>=?",workerId,worksiteId,end,start);
                if(demandCount==0)throw new BusinessException("Công trình chưa có nhu cầu nhân lực nào phủ kỳ phân công. Hãy lập nhu cầu nhân lực trước khi phân công.");
                if(skillCount==0)throw new BusinessException("Công nhân không đáp ứng kỹ năng/bậc thợ yêu cầu của công trình.");
                try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM assignments WHERE worker_id=? AND assignment_id<>? AND status='ACTIVE' AND start_date<=? AND COALESCE(expected_end_date,'9999-12-31')>=?")){ps.setInt(1,workerId);ps.setInt(2,id);ps.setObject(3,end);ps.setObject(4,start);try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)>0)throw new BusinessException("Công nhân đã có phân công trùng khoảng thời gian này.");}}
                try(PreparedStatement ps=c.prepareStatement("UPDATE assignments SET worker_id=?,worksite_id=?,start_date=?,expected_end_date=? WHERE assignment_id=? AND status='ACTIVE'")){ps.setInt(1,workerId);ps.setInt(2,worksiteId);ps.setObject(3,start);ps.setObject(4,expectedEnd);ps.setInt(5,id);if(ps.executeUpdate()==0)throw new BusinessException("Phân công không còn hoạt động.");}
                c.commit();
            } catch(SQLException|RuntimeException e){c.rollback();if(e instanceof BusinessException b)throw b;throw e;}
        } catch(SQLException e){throw new BusinessException("Lỗi sửa phân công: "+e.getMessage(),e);}
    }

    /** Kết thúc phân công đang ACTIVE: ghi ngày kết thúc thực tế là hôm nay. */
    public void finishAssignment(int id, LocalDate endDate) {
        Db.exec("UPDATE assignments SET status='FINISHED', actual_end_date=? WHERE assignment_id=? AND status='ACTIVE'", endDate, id);
    }

    /** Hủy phân công đang ACTIVE kèm lý do (cancel_reason). */
    public void cancelAssignment(int id, String reason) {
        Db.exec("UPDATE assignments SET status='CANCELLED', cancel_reason=? WHERE assignment_id=? AND status='ACTIVE'", reason, id);
    }

    // =====================================================================
    // 2.4 – Cảnh báo thiếu / đủ nhân lực
    // =====================================================================
    /**
     * Mỗi dòng nhu cầu so với số công nhân đã phân công. Cột trả về:
     * {mã CT, tên CT, từ ngày, đến ngày, giai đoạn, kỹ năng, bậc tối thiểu, cần, đã gán, thiếu}.
     * Công nhân được tính khi: đang làm việc, phân công ACTIVE tại công trình đó trùng khoảng ngày của kế hoạch,
     * có kỹ năng yêu cầu với bậc >= bậc tối thiểu (level_id của cùng kỹ năng tăng dần theo bậc).
     */
    public List<Object[]> shortage(Integer worksiteId) {
        // 1) Các dòng nhu cầu (theo thứ tự hiển thị).
        List<Object[]> demands = Db.query("""
            SELECT s.worksite_code, s.worksite_name, p.from_date, p.to_date, d.phase_name, sk.skill_name, lv.level_name,
                   d.quantity_needed, d.demand_id, p.worksite_id, d.skill_id, d.level_id
            FROM manpower_demands d JOIN manpower_plans p ON p.plan_id = d.plan_id
            JOIN worksites s ON s.worksite_id = p.worksite_id
            JOIN skills sk ON sk.skill_id = d.skill_id JOIN skill_levels lv ON lv.level_id = d.level_id
            WHERE (? IS NULL OR p.worksite_id = ?)
            ORDER BY s.worksite_code, p.from_date, d.phase_name, sk.skill_name""",
                rs -> new Object[]{rs.getString(1), rs.getString(2), date(rs, 3), date(rs, 4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12)},
                worksiteId, worksiteId);

        // 2) Cặp (dòng nhu cầu, công nhân) đủ điều kiện. Tính cả phân công ACTIVE (đang làm việc) và FINISHED
        //    (đã kết thúc: dùng ngày kết thúc thực tế) trùng khoảng ngày của kế hoạch.
        Map<Integer, List<Integer>> eligible = new HashMap<>();
        Db.query("""
            SELECT d.demand_id, a.worker_id
            FROM manpower_demands d JOIN manpower_plans p ON p.plan_id = d.plan_id
            JOIN assignments a ON a.worksite_id = p.worksite_id
            JOIN workers w ON w.worker_id = a.worker_id
            WHERE (? IS NULL OR p.worksite_id = ?)
              AND EXISTS (SELECT 1 FROM worker_skills ws WHERE ws.worker_id = a.worker_id
                          AND ws.skill_id = d.skill_id AND ws.level_id >= d.level_id)
              AND a.start_date <= p.to_date
              AND ( (a.status = 'ACTIVE' AND w.work_status = 'WORKING'
                     AND COALESCE(a.expected_end_date, '9999-12-31') >= p.from_date)
                 OR (a.status = 'FINISHED'
                     AND COALESCE(a.actual_end_date, a.expected_end_date, '9999-12-31') >= p.from_date) )
            GROUP BY d.demand_id, a.worker_id
            ORDER BY d.demand_id, a.worker_id""",
                rs -> { eligible.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getInt(2)); return null; },
                worksiteId, worksiteId);

        // 3) Mỗi công nhân chỉ được tính cho MỘT dòng nhu cầu trong cùng công trình và cùng khoảng ngày:
        //    xét dòng yêu cầu bậc cao trước, mỗi dòng nhận tối đa quantity_needed người chưa được tính ở dòng khác.
        List<Object[]> order = new ArrayList<>(demands);
        order.sort(Comparator.comparingInt((Object[] r) -> (Integer) r[9]).thenComparing((Object[] r) -> -(Integer) r[11])
                .thenComparingInt(r -> (Integer) r[8]));
        Map<Integer, Integer> assigned = new HashMap<>();
        Map<String, List<LocalDate[]>> used = new HashMap<>(); // "site|worker" -> các khoảng ngày kế hoạch đã dùng
        for (Object[] r : order) {
            int need = (Integer) r[7], demandId = (Integer) r[8], site = (Integer) r[9];
            LocalDate from = (LocalDate) r[2], to = (LocalDate) r[3];
            int count = 0;
            for (int workerId : eligible.getOrDefault(demandId, List.of())) {
                if (count >= need) break;
                List<LocalDate[]> ranges = used.computeIfAbsent(site + "|" + workerId, k -> new ArrayList<>());
                boolean overlap = false;
                for (LocalDate[] u : ranges) if (!u[0].isAfter(to) && !u[1].isBefore(from)) { overlap = true; break; }
                if (overlap) continue;
                ranges.add(new LocalDate[]{from, to});
                count++;
            }
            assigned.put(demandId, count);
        }

        List<Object[]> out = new ArrayList<>();
        for (Object[] r : demands) {
            int need = (Integer) r[7], got = assigned.getOrDefault((Integer) r[8], 0);
            out.add(new Object[]{r[0], r[1], r[2], r[3], r[4], r[5], r[6], need, got, Math.max(0, need - got), r[8], r[9], r[10], r[11]});
        }
        return out;
    }

    /** Danh sách công nhân đang làm việc, đủ kỹ năng/bậc và chưa có phân công chồng lịch. */
    public List<Object[]> suggestWorkers(int worksiteId,int skillId,int levelId,LocalDate from,LocalDate to) {
        return Db.query("""
            SELECT w.worker_id,w.worker_code,w.full_name,w.phone,ws.level_id,l.level_name
            FROM workers w JOIN worker_skills ws ON ws.worker_id=w.worker_id
            JOIN skill_levels l ON l.level_id=ws.level_id
            WHERE w.work_status='WORKING' AND ws.skill_id=? AND ws.level_id>=?
              AND NOT EXISTS (SELECT 1 FROM assignments a WHERE a.worker_id=w.worker_id AND a.status='ACTIVE'
                AND a.start_date<=? AND COALESCE(a.expected_end_date,'9999-12-31')>=?)
            ORDER BY ws.level_id DESC,w.worker_code""",
            rs -> new Object[]{rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getString(6)},
            skillId,levelId,to,from);
    }
}
