package ctv.kho;

import ctv.kho.ReportDAO;
import ctv.kho.ReportDAO.Result;
import ctv.util.BusinessException;
import ctv.util.Exporter;
import ctv.util.Session;
import java.io.File;
import java.time.LocalDate;

/** 3.4 – báo cáo tồn kho thiết bị. */
public class ReportService {
    private final ReportDAO dao = new ReportDAO();

    public Result stockInWarehouse(Integer warehouseId) { return dao.stockInWarehouse(warehouseId); }
    public Result stockAtWorksite(Integer worksiteId) { return dao.stockAtWorksite(worksiteId); }
    public Result byType(Integer typeId) { return dao.byType(typeId); }
    public Result byCondition(String condition) { return dao.byCondition(condition); }
    public long maintenanceQuantity() { return dao.maintenanceQuantity(); }
    public Result currentSummary() { return dao.currentSummary(); }

    public Result receipts(LocalDate from, LocalDate to) { range(from, to); return dao.receipts(from, to); }
    public Result issues(LocalDate from, LocalDate to, Integer worksiteId) { range(from, to); return dao.issues(from, to, worksiteId); }
    public Result returns(LocalDate from, LocalDate to, Integer worksiteId) { range(from, to); return dao.returns(from, to, worksiteId); }

    /** 3.4.5 – 3.4.7: lịch sử phiếu nhập / xuất / thu hồi trong khoảng thời gian. */
    public Result history(LocalDate from, LocalDate to, String type, String keyword) {
        range(from, to);
        Result r = dao.history(from, to, type, keyword);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.util.Map<Integer, String> names = new java.util.HashMap<>();
        ctv.util.Db.query("SELECT user_id, full_name FROM users", rs -> { names.put(rs.getInt(1), rs.getString(2)); return null; });
        for (java.util.List<Object> row : r.rows()) {
            row.set(2, LocalDate.parse(String.valueOf(row.get(2)).substring(0, 10)).format(fmt));
            Object uid = row.get(6);
            row.set(6, uid == null ? "" : names.getOrDefault(((Number) uid).intValue(), "Mã NV " + uid));
        }
        return r;
    }

    public void export(Result r, File file, boolean pdf) {
        if (pdf) Exporter.toPdf(file, r.title(), r.headers(), r.rows());
        else Exporter.toExcel(file, r.title(), r.headers(), r.rows());
    }

    private void range(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new BusinessException("Chưa chọn khoảng thời gian.");
        if (to.isBefore(from)) throw new BusinessException("Đến ngày phải sau hoặc bằng Từ ngày.");
    }
}
