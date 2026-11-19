package ctv.kho;

import ctv.kho.SlipDAO;
import ctv.kho.StockDAO;
import ctv.model.Models.SlipLine;
import ctv.util.BusinessException;
import ctv.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3.2.12, 3.3 – nhập kho, xuất thiết bị, thu hồi thiết bị.
 * Mỗi phiếu = MỘT giao dịch: ghi phiếu + chi tiết + cập nhật tồn kho; lỗi ở bất kỳ bước nào thì rollback toàn bộ.
 */
public class InventoryService {

    /** Lập phiếu nhập kho, cộng tồn tại vị trí lưu. Trả về số phiếu. */
    public String createReceipt(LocalDate date, String source, List<SlipLine> lines, int userId) {
        requireLines(lines);
        if (date == null) throw new BusinessException("Chưa chọn ngày nhập.");
        return tx(c -> {
            String code = SlipDAO.nextCode(c, "PN", "goods_receipts", "receipt_id");
            int id = SlipDAO.insertReceipt(c, code, date, source == null || source.isBlank() ? null : source.trim(), userId);
            for (SlipLine l : lines) {
                SlipDAO.insertReceiptDetail(c, id, l.equipmentId(), l.locationId(), l.quantity());
                StockDAO.add(c, l.equipmentId(), l.locationId(), null, l.quantity());
                StockDAO.refreshUsage(c, l.equipmentId());
            }
            return code;
        });
    }

    /** Lập phiếu xuất đến công trình: kiểm tra tồn (3.3.5), trừ kho, cộng vào công trình. */
    public String createIssue(LocalDate date, int worksiteId, String receiver, String receiverPosition, List<SlipLine> lines, int userId) {
        requireLines(lines);
        if (date == null) throw new BusinessException("Chưa chọn ngày xuất.");
        if (receiver == null || receiver.isBlank()) throw new BusinessException("Phải ghi nhận người nhận thiết bị.");
        if (receiverPosition == null || receiverPosition.isBlank()) throw new BusinessException("Phải ghi nhận chức vụ người nhận.");
        return tx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT status FROM worksites WHERE worksite_id=?")) {
                ps.setInt(1, worksiteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new BusinessException("Không tìm thấy công trình.");
                    if ("COMPLETED".equals(rs.getString(1))) throw new BusinessException("Công trình đã hoàn thành, không thể xuất thêm thiết bị.");
                }
            }
            for (SlipLine l : lines) {
                try (PreparedStatement ps = c.prepareStatement("SELECT condition_status, usage_status FROM equipment WHERE equipment_id=?")) {
                    ps.setInt(1, l.equipmentId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BusinessException("Không tìm thấy thiết bị " + l.equipmentCode() + ".");
                        if ("BROKEN".equals(rs.getString(1)) || "MAINTENANCE".equals(rs.getString(2)))
                            throw new BusinessException("Thiết bị " + l.equipmentCode() + " đang Bảo trì hoặc Hỏng, không thể xuất.");
                    }
                }
            }
            // Kiểm tra tồn có tính cả các dòng trùng thiết bị + vị trí trong cùng phiếu
            Map<String, Integer> used = new HashMap<>();
            for (SlipLine l : lines) {
                String key = l.equipmentId() + ":" + l.locationId();
                int already = used.getOrDefault(key, 0);
                int have = StockDAO.lockedQty(c, l.equipmentId(), l.locationId(), null);
                if (already + l.quantity() > have)
                    throw new BusinessException("Thiếu tồn kho: " + l.equipmentCode() + " tại " + l.locationLabel()
                            + " chỉ còn " + (have - already) + ", yêu cầu xuất " + l.quantity() + ".");
                used.put(key, already + l.quantity());
            }
            String code = SlipDAO.nextCode(c, "PX", "issue_slips", "issue_id");
            int id = SlipDAO.insertIssue(c, code, date, worksiteId, receiver.trim(), blankToNull(receiverPosition), userId);
            for (SlipLine l : lines) {
                SlipDAO.insertIssueDetail(c, id, l.equipmentId(), l.locationId(), l.quantity());
                StockDAO.add(c, l.equipmentId(), l.locationId(), null, -l.quantity());
                StockDAO.add(c, l.equipmentId(), null, worksiteId, l.quantity());
                StockDAO.refreshUsage(c, l.equipmentId());
            }
            return code;
        });
    }

    /** Lập phiếu thu hồi: trừ ở công trình, cộng vào kho, cập nhật tình trạng sau thu hồi (3.3.9). */
    public String createReturn(LocalDate date, int worksiteId, String returner, String returnerPosition, List<SlipLine> lines, int userId) {
        requireLines(lines);
        if (date == null) throw new BusinessException("Chưa chọn ngày thu hồi.");
        if (returner == null || returner.isBlank()) throw new BusinessException("Phải ghi nhận người trả thiết bị.");
        if (returnerPosition == null || returnerPosition.isBlank()) throw new BusinessException("Phải ghi nhận chức vụ người trả.");
        final String who = SlipDAO.plainName(returner);   // chỉ họ tên; chức vụ được ghép khi ghi phiếu
        return tx(c -> {
            if (!SlipDAO.hasReceived(c, worksiteId, who))
                throw new BusinessException("Người trả phải là công nhân đã nhận thiết bị khi xuất cho công trình này. \"" + who + "\" chưa nhận thiết bị nào.");
            Map<Integer, Integer> used = new HashMap<>();
            for (SlipLine l : lines) {
                int already = used.getOrDefault(l.equipmentId(), 0);
                int have = StockDAO.lockedQty(c, l.equipmentId(), null, worksiteId);
                if (already + l.quantity() > have)
                    throw new BusinessException("Số lượng thu hồi vượt số đang ở công trình: " + l.equipmentCode()
                            + " chỉ còn " + (have - already) + ".");
                int held = SlipDAO.heldQty(c, worksiteId, who, l.equipmentId());
                if (already + l.quantity() > held)
                    throw new BusinessException("Số lượng thu hồi vượt số thiết bị " + who + " đang giữ: " + l.equipmentCode()
                            + " chỉ còn " + Math.max(held - already, 0) + ".");
                used.put(l.equipmentId(), already + l.quantity());
            }
            String code = SlipDAO.nextCode(c, "PT", "return_slips", "return_id");
            int id = SlipDAO.insertReturn(c, code, date, worksiteId, who, blankToNull(returnerPosition), userId);
            for (SlipLine l : lines) {
                SlipDAO.insertReturnDetail(c, id, l.equipmentId(), l.locationId(), l.quantity(), l.conditionAfter());
                StockDAO.add(c, l.equipmentId(), null, worksiteId, -l.quantity());
                StockDAO.add(c, l.equipmentId(), l.locationId(), null, l.quantity());
                SlipDAO.setCondition(c, l.equipmentId(), l.conditionAfter());
                StockDAO.refreshUsage(c, l.equipmentId());
            }
            return code;
        });
    }

    // ----- giao dịch -----
    private interface TxWork<T> { T run(Connection c) throws SQLException; }

    private <T> T tx(TxWork<T> work) {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof BusinessException be) throw be;
                throw new BusinessException("Lưu phiếu thất bại, dữ liệu đã được hoàn tác: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi giao dịch: " + e.getMessage(), e);
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private void requireLines(List<SlipLine> lines) {
        if (lines == null || lines.isEmpty()) throw new BusinessException("Phiếu chưa có dòng thiết bị nào.");
        for (SlipLine l : lines)
            if (l.quantity() <= 0) throw new BusinessException("Số lượng của " + l.equipmentCode() + " phải là số nguyên dương.");
    }
}
