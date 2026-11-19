package ctv.dashboard;

import ctv.nhansu.HrDAO;
import ctv.util.Db;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Số liệu tổng hợp cho màn hình Dashboard (chỉ đọc, dùng các bảng của Form 06). */
public class DashboardDAO {

    /** Một giá trị số (COUNT/SUM/AVG). */
    public double number(String sql, Object... params) {
        return Db.query(sql, rs -> rs.getDouble(1), params).stream().findFirst().orElse(0.0);
    }

    /** Danh sách (nhãn, giá trị) từ truy vấn hai cột. */
    public List<Object[]> pairs(String sql, Object... params) {
        return Db.query(sql, rs -> new Object[]{rs.getString(1), rs.getDouble(2)}, params);
    }

    /** Giá trị theo ngày trong khoảng [from, to]; ngày không có dữ liệu = 0. Truy vấn trả về (DATE, số). */
    public Map<LocalDate, Double> byDay(String sql, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> m = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) m.put(d, 0.0);
        for (Object[] r : Db.query(sql, rs -> new Object[]{rs.getDate(1).toLocalDate(), rs.getDouble(2)}, from, to))
            m.put((LocalDate) r[0], (Double) r[1]);
        return m;
    }

    // ---------------- nhân sự ----------------
    public List<Object[]> workersByStatus() {
        return pairs("SELECT " + HrDAO.EFFECTIVE_STATUS + " AS st, COUNT(*) FROM workers w GROUP BY st ORDER BY st");
    }

    public List<Object[]> workersByType() {
        return pairs("""
            SELECT t.type_name, COUNT(w.worker_id) FROM worker_types t
            LEFT JOIN workers w ON w.worker_type_id = t.worker_type_id GROUP BY t.worker_type_id, t.type_name ORDER BY t.type_name""");
    }

    public List<Object[]> workersBySkill() {
        return pairs("""
            SELECT s.skill_name, COUNT(ws.worker_id) FROM skills s
            LEFT JOIN worker_skills ws ON ws.skill_id = s.skill_id GROUP BY s.skill_id, s.skill_name ORDER BY s.skill_name""");
    }

    // ---------------- công trình ----------------
    public List<Object[]> sitesByStatus() {
        return pairs("SELECT status, COUNT(*) FROM worksites GROUP BY status ORDER BY status");
    }

    public List<Object[]> activeSiteProgress() {
        return pairs("SELECT worksite_name, progress_percent FROM worksites WHERE status='IN_PROGRESS' ORDER BY progress_percent DESC LIMIT 8");
    }

    public List<Object[]> activeWorkersBySite() {
        return pairs("""
            SELECT w.worksite_name, COUNT(a.assignment_id) FROM worksites w
            JOIN assignments a ON a.worksite_id = w.worksite_id AND a.status = 'ACTIVE'
            GROUP BY w.worksite_id, w.worksite_name ORDER BY COUNT(a.assignment_id) DESC LIMIT 8""");
    }

    // ---------------- chấm công, lương ----------------
    public List<Object[]> attendanceTodayByStatus() {
        return pairs("SELECT validation_status, COUNT(*) FROM attendances WHERE work_date = CURDATE() GROUP BY validation_status");
    }

    public Map<LocalDate, Double> attendancePerDay(LocalDate from, LocalDate to) {
        return byDay("SELECT work_date, COUNT(*) FROM attendances WHERE work_date BETWEEN ? AND ? GROUP BY work_date", from, to);
    }

    /** 6 kỳ lương gần nhất: (nhãn "dd/MM – dd/MM", tổng tiền), cũ → mới. */
    public List<Object[]> payrollByPeriod() {
        List<Object[]> r = new ArrayList<>(Db.query("""
            SELECT DATE_FORMAT(p.start_date,'%d/%m'), DATE_FORMAT(p.end_date,'%d/%m'), COALESCE(SUM(d.total_amount),0)
            FROM payroll_periods p LEFT JOIN payroll_details d ON d.period_id = p.period_id
            GROUP BY p.period_id, p.start_date, p.end_date ORDER BY p.start_date DESC LIMIT 6""",
                rs -> new Object[]{rs.getString(1) + "–" + rs.getString(2), rs.getDouble(3)}));
        java.util.Collections.reverse(r);
        return r;
    }

    public List<Object[]> paymentStatusCounts() {
        return pairs("SELECT payment_status, COUNT(*) FROM payroll_details GROUP BY payment_status ORDER BY payment_status");
    }

    // ---------------- kho thiết bị ----------------
    public List<Object[]> equipmentByUsage() {
        return pairs("SELECT usage_status, COUNT(*) FROM equipment GROUP BY usage_status ORDER BY usage_status");
    }

    public List<Object[]> equipmentByCondition() {
        return pairs("SELECT condition_status, COUNT(*) FROM equipment GROUP BY condition_status ORDER BY condition_status");
    }

    /** Số phiếu nhập + xuất + thu hồi mỗi ngày. */
    public Map<LocalDate, Double> slipsPerDay(LocalDate from, LocalDate to) {
        return byDay("""
            SELECT d, COUNT(*) FROM (
              SELECT receipt_date d FROM goods_receipts UNION ALL
              SELECT issue_date FROM issue_slips UNION ALL
              SELECT return_date FROM return_slips) x
            WHERE d BETWEEN ? AND ? GROUP BY d""", from, to);
    }

    // ---------------- hệ thống ----------------
    public List<Object[]> usersByRole() {
        return pairs("""
            SELECT r.role_name, COUNT(u.user_id) FROM roles r LEFT JOIN users u ON u.role_id = r.role_id
            GROUP BY r.role_id, r.role_name ORDER BY r.role_id""");
    }

    public Map<LocalDate, Double> loginsPerDay(LocalDate from, LocalDate to) {
        return byDay("""
            SELECT DATE(action_time), COUNT(*) FROM login_logs
            WHERE action_type='LOGIN' AND result='SUCCESS' AND DATE(action_time) BETWEEN ? AND ? GROUP BY DATE(action_time)""", from, to);
    }
}
