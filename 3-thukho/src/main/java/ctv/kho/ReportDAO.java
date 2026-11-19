package ctv.kho;

import ctv.util.Db;
import ctv.util.Labels;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 3.4 – các truy vấn thống kê / báo cáo. Mỗi báo cáo trả về tiêu đề cột + các dòng dữ liệu. */
public class ReportDAO {

    public record Result(String title, List<String> headers, List<List<Object>> rows) {}

    /** Biểu thức SQL suy ra tình trạng hiển thị (5 giá trị) cho từng dòng tồn kho; cùng quy tắc với màn hình 3.2. */
    private static final String STATUS_SQL = """
            CASE WHEN e.condition_status='BROKEN' THEN 'BROKEN'
                 WHEN e.condition_status='NEED_REPAIR' THEN 'REPAIR'
                 WHEN e.usage_status='MAINTENANCE' THEN 'MAINT'
                 WHEN s.worksite_id IS NOT NULL THEN 'IN_USE'
                 ELSE 'READY' END""";

    /** Số lượng thiết bị cần bảo trì: tình trạng Bảo trì, Cần sửa chữa hoặc Hỏng (tính theo số lượng, dùng chung cho cả hai màn hình). */
    public long maintenanceQuantity() {
        List<Long> r = Db.query("""
                SELECT COALESCE(SUM(s.quantity),0) FROM equipment_stock s JOIN equipment e ON e.equipment_id=s.equipment_id
                WHERE e.condition_status<>'GOOD' OR e.usage_status='MAINTENANCE'""", rs -> rs.getLong(1));
        return r.isEmpty() ? 0 : r.get(0);
    }

    public Result stockInWarehouse(Integer warehouseId) {
        Result r = run("Thiết bị trong kho",
                List.of("Mã TB", "Tên thiết bị", "Loại", "Kho", "Vị trí", "Số lượng", "Tình trạng"),
                """
                SELECT e.equipment_code, e.equipment_name, t.type_name, w.warehouse_name, l.location_code, s.quantity, %STATUS%
                FROM equipment_stock s JOIN equipment e ON e.equipment_id=s.equipment_id
                JOIN equipment_types t ON t.type_id=e.type_id
                JOIN storage_locations l ON l.location_id=s.location_id JOIN warehouses w ON w.warehouse_id=l.warehouse_id
                WHERE s.quantity>0 AND (? IS NULL OR w.warehouse_id=?) ORDER BY w.warehouse_name, e.equipment_code""".replace("%STATUS%", STATUS_SQL),
                new int[]{6}, warehouseId, warehouseId);
        translateStatus(r, 6);
        return r;
    }

    public Result stockAtWorksite(Integer worksiteId) {
        Result r = run("Thiết bị tại công trình",
                List.of("Công trình", "Mã TB", "Tên thiết bị", "Loại", "Số lượng", "Tình trạng"),
                """
                SELECT ws.worksite_name, e.equipment_code, e.equipment_name, t.type_name, s.quantity, %STATUS%
                FROM equipment_stock s JOIN equipment e ON e.equipment_id=s.equipment_id
                JOIN equipment_types t ON t.type_id=e.type_id JOIN worksites ws ON ws.worksite_id=s.worksite_id
                WHERE s.quantity>0 AND (? IS NULL OR ws.worksite_id=?) ORDER BY ws.worksite_name, e.equipment_code""".replace("%STATUS%", STATUS_SQL),
                new int[]{5}, worksiteId, worksiteId);
        translateStatus(r, 5);
        return r;
    }

    /** Đổi mã tình trạng (READY, IN_USE...) ở cột col (bắt đầu từ 1) sang nhãn tiếng Việt, bỏ qua dòng "Tổng cộng". */
    private static void translateStatus(Result r, int col) {
        for (List<Object> row : r.rows())
            if (row.get(col - 1) instanceof String code && !code.isEmpty()) row.set(col - 1, Labels.status(code));
    }

    /** Thống kê theo loại (Form 05 – 5.21): tổng, trong kho, tại công trình và số lượng theo tình trạng Tốt / Cần sửa chữa / Hỏng. */
    public Result byType(Integer typeId) {
        return run("Thống kê thiết bị theo loại",
                List.of("Loại thiết bị", "Tổng", "Trong kho", "Tại công trình", "Tốt", "Cần sửa chữa", "Hỏng"),
                """
                SELECT t.type_name,
                       COALESCE(SUM(s.quantity),0),
                       COALESCE(SUM(CASE WHEN s.location_id IS NOT NULL THEN s.quantity END),0),
                       COALESCE(SUM(CASE WHEN s.worksite_id IS NOT NULL THEN s.quantity END),0),
                       COALESCE(SUM(CASE WHEN e.condition_status='GOOD' THEN s.quantity END),0),
                       COALESCE(SUM(CASE WHEN e.condition_status='NEED_REPAIR' THEN s.quantity END),0),
                       COALESCE(SUM(CASE WHEN e.condition_status='BROKEN' THEN s.quantity END),0)
                FROM equipment_types t LEFT JOIN equipment e ON e.type_id=t.type_id
                LEFT JOIN equipment_stock s ON s.equipment_id=e.equipment_id
                WHERE (? IS NULL OR t.type_id=?) GROUP BY t.type_id, t.type_name ORDER BY t.type_name""",
                new int[]{2, 3, 4, 5, 6, 7}, typeId, typeId);
    }

    /** 3.4.4: thống kê theo 5 tình trạng (Sẵn sàng, Đang sử dụng, Bảo trì, Cần sửa chữa, Hỏng); lọc theo mã tình trạng (tùy chọn). */
    public Result byCondition(String status) {
        Result r = run("Thống kê thiết bị theo tình trạng",
                List.of("Tình trạng", "Số loại thiết bị", "Tổng số lượng"),
                """
                SELECT st, COUNT(DISTINCT equipment_id), COALESCE(SUM(qty),0) FROM (
                    SELECT e.equipment_id, COALESCE(s.quantity,0) AS qty, %STATUS% AS st
                    FROM equipment e LEFT JOIN equipment_stock s ON s.equipment_id=e.equipment_id) x
                WHERE (? IS NULL OR st=?)
                GROUP BY st
                ORDER BY FIELD(st,'READY','IN_USE','MAINT','REPAIR','BROKEN')""".replace("%STATUS%", STATUS_SQL),
                new int[]{2, 3}, status, status);
        translateStatus(r, 1);
        return r;
    }

    public Result receipts(LocalDate from, LocalDate to) {
        return run("Báo cáo nhập kho " + from + " → " + to,
                List.of("Số phiếu", "Ngày nhập", "Nguồn", "Mã TB", "Tên thiết bị", "Vị trí", "Số lượng"),
                """
                SELECT r.receipt_code, r.receipt_date, r.source, e.equipment_code, e.equipment_name,
                       CONCAT(w.warehouse_name,' / ',l.location_code), d.quantity
                FROM goods_receipts r JOIN goods_receipt_details d ON d.receipt_id=r.receipt_id
                JOIN equipment e ON e.equipment_id=d.equipment_id JOIN storage_locations l ON l.location_id=d.location_id
                JOIN warehouses w ON w.warehouse_id=l.warehouse_id
                WHERE r.receipt_date BETWEEN ? AND ? ORDER BY r.receipt_date, r.receipt_code""",
                new int[]{7}, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
    }

    public Result issues(LocalDate from, LocalDate to, Integer worksiteId) {
        return run("Báo cáo xuất thiết bị " + from + " → " + to,
                List.of("Số phiếu", "Ngày xuất", "Công trình", "Người nhận (chức vụ)", "Mã TB", "Tên thiết bị", "Số lượng"),
                """
                SELECT i.issue_code, i.issue_date, ws.worksite_name, i.receiver_name, e.equipment_code, e.equipment_name, d.quantity
                FROM issue_slips i JOIN issue_slip_details d ON d.issue_id=i.issue_id
                JOIN equipment e ON e.equipment_id=d.equipment_id JOIN worksites ws ON ws.worksite_id=i.worksite_id
                WHERE i.issue_date BETWEEN ? AND ? AND (? IS NULL OR i.worksite_id=?) ORDER BY i.issue_date, i.issue_code""",
                new int[]{7}, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), worksiteId, worksiteId);
    }

    public Result returns(LocalDate from, LocalDate to, Integer worksiteId) {
        Result r = run("Báo cáo thu hồi thiết bị " + from + " → " + to,
                List.of("Số phiếu", "Ngày thu hồi", "Công trình", "Người trả", "Mã TB", "Tên thiết bị", "Số lượng", "Tình trạng sau"),
                """
                SELECT t.return_code, t.return_date, ws.worksite_name, t.returner_name, e.equipment_code, e.equipment_name, d.quantity, d.condition_after
                FROM return_slips t JOIN return_slip_details d ON d.return_id=t.return_id
                JOIN equipment e ON e.equipment_id=d.equipment_id JOIN worksites ws ON ws.worksite_id=t.worksite_id
                WHERE t.return_date BETWEEN ? AND ? AND (? IS NULL OR t.worksite_id=?) ORDER BY t.return_date, t.return_code""",
                new int[]{7}, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), worksiteId, worksiteId);
        r.rows().forEach(row -> row.set(7, Labels.condition((String) row.get(7))));
        return r;
    }

    /**
     * 3.4.5 – 3.4.7 lịch sử giao dịch kho (Form 05 – 5.22): gộp phiếu nhập, xuất, thu hồi, mỗi dòng chi tiết một dòng kết quả.
     * @param type "Nhập kho" | "Xuất" | "Thu hồi" | null (tất cả)
     * Cột cuối là mã người lập (created_by); ReportService đổi thành tên.
     */
    public Result history(LocalDate from, LocalDate to, String type, String keyword) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String like = kw == null ? null : "%" + kw + "%";
        return run("Lịch sử giao dịch kho " + from + " → " + to,
                List.of("Số phiếu", "Loại", "Ngày", "Thiết bị", "Số lượng", "Công trình / Nguồn", "Người lập"),
                """
                SELECT * FROM (
                  SELECT r.receipt_code AS slip_code, 'Nhập kho' AS slip_type, r.receipt_date AS slip_date,
                         e.equipment_name AS equip_name, d.quantity AS qty, COALESCE(r.source, '') AS party, r.created_by AS made_by
                  FROM goods_receipts r JOIN goods_receipt_details d ON d.receipt_id=r.receipt_id
                  JOIN equipment e ON e.equipment_id=d.equipment_id
                  UNION ALL
                  SELECT i.issue_code, 'Xuất', i.issue_date, e.equipment_name, d.quantity, ws.worksite_code, i.created_by
                  FROM issue_slips i JOIN issue_slip_details d ON d.issue_id=i.issue_id
                  JOIN equipment e ON e.equipment_id=d.equipment_id JOIN worksites ws ON ws.worksite_id=i.worksite_id
                  UNION ALL
                  SELECT t.return_code, 'Thu hồi', t.return_date, e.equipment_name, d.quantity, ws.worksite_code, t.created_by
                  FROM return_slips t JOIN return_slip_details d ON d.return_id=t.return_id
                  JOIN equipment e ON e.equipment_id=d.equipment_id JOIN worksites ws ON ws.worksite_id=t.worksite_id
                ) h
                WHERE h.slip_date BETWEEN ? AND ? AND (? IS NULL OR h.slip_type=?)
                  AND (? IS NULL OR h.slip_code LIKE ? OR h.equip_name LIKE ?)
                ORDER BY h.slip_date DESC, h.slip_code DESC""",
                new int[]{}, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), type, type, kw, like, like);
    }

    /** 3.4.8 – tổng hợp tồn kho hiện tại của toàn bộ thiết bị. */
    public Result currentSummary() {
        Result r = run("Tổng hợp tồn kho hiện tại",
                List.of("Mã TB", "Tên thiết bị", "Loại", "Trong kho", "Tại công trình", "Tổng", "Tình trạng", "Người phụ trách"),
                """
                SELECT e.equipment_code, e.equipment_name, t.type_name,
                       COALESCE(SUM(CASE WHEN s.location_id IS NOT NULL THEN s.quantity END),0),
                       COALESCE(SUM(CASE WHEN s.worksite_id IS NOT NULL THEN s.quantity END),0),
                       COALESCE(SUM(s.quantity),0), e.condition_status, e.responsible_person
                FROM equipment e JOIN equipment_types t ON t.type_id=e.type_id
                LEFT JOIN equipment_stock s ON s.equipment_id=e.equipment_id
                GROUP BY e.equipment_id, e.equipment_code, e.equipment_name, t.type_name, e.condition_status, e.responsible_person
                ORDER BY e.equipment_code""",
                new int[]{4, 5, 6});
        r.rows().forEach(row -> row.set(6, Labels.condition((String) row.get(6))));
        return r;
    }

    /** sumCols: chỉ số cột (bắt đầu từ 1) là số lượng, dùng để thêm dòng "Tổng cộng" ở cuối. */
    private Result run(String title, List<String> headers, String sql, int[] sumCols, Object... params) {
        List<List<Object>> rows = Db.query(sql, rs -> {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= headers.size(); i++) row.add(rs.getObject(i));
            return row;
        }, params);
        if (!rows.isEmpty() && sumCols.length > 0) {
            List<Object> total = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) total.add(i == 0 ? "Tổng cộng" : "");
            for (int col : sumCols) {
                long sum = 0;
                for (List<Object> r : rows) sum += ((Number) r.get(col - 1)).longValue();
                total.set(col - 1, sum);
            }
            rows.add(total);
        }
        return new Result(title, headers, rows);
    }
}
