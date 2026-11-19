package ctv.congtrinh;

import ctv.congtrinh.SiteDAO;
import ctv.nhansu.HrModels.*;
import ctv.congtrinh.SiteModels.*;
import ctv.util.BusinessException;
import ctv.util.Ownership;
import ctv.util.Ownership.Kind;
import ctv.util.Permissions;
import ctv.util.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Nhóm 2 – nghiệp vụ công trình, nhu cầu nhân lực, phân công, cảnh báo thiếu/đủ. */
public class SiteService {
    private final SiteDAO dao = new SiteDAO();

    // ---------------- công trình ----------------
    public List<Site> searchSites(String keyword, String status) { return dao.searchSites(keyword, status); }
    public List<Site> allSites() { return dao.allSites(); }
    public List<Site> activeSites() { return dao.activeSites(); }
    public List<Object[]> progressHistory(int id) { return dao.progressHistory(id); }
    public List<Object[]> statusHistory(int id) { return dao.statusHistory(id); }
    public void deleteSite(int id) { requireOwner(id, "xóa"); dao.deleteSite(id); }

    /** Công trình do người dùng hiện tại thêm: chỉ tài khoản đã thêm được sửa / xóa (mọi vai trò), tài khoản khác chỉ xem. */
    public boolean canModify(Site s) { return Ownership.isMine(s.createdBy()); }

    private void requireOwner(int siteId, String action) { Ownership.require(Kind.SITE, siteId, action); }

    /** Phân công do người dùng hiện tại tạo (chỉ người tạo được sửa / kết thúc / hủy). */
    public boolean canModifyAssignment(Assignment a) { return Ownership.canModify(Kind.ASSIGNMENT, a.id()); }

    public void saveSite(Site old, String code, String name, String address, String investor, LocalDate plannedStart,
                         LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd, String status, LocalDate statusDate,
                         String progressText, LocalDate progressDate, String note) {
        int id = old == null ? 0 : old.id();
        code = need(code, "Mã công trình", 20);
        name = need(name, "Tên công trình", 150);
        address = need(address, "Địa chỉ", 255);
        investor = blankToNull(investor);
        if (investor != null && investor.length() > 150) throw new BusinessException("Chủ đầu tư tối đa 150 ký tự.");
        if (plannedStart == null || plannedEnd == null) throw new BusinessException("Ngày bắt đầu và kết thúc dự kiến không được để trống.");
        if (plannedEnd.isBefore(plannedStart)) throw new BusinessException("Ngày kết thúc dự kiến phải sau ngày bắt đầu dự kiến.");
        if (actualStart != null && actualEnd != null && actualEnd.isBefore(actualStart))
            throw new BusinessException("Ngày kết thúc thực tế phải sau ngày bắt đầu thực tế.");
        BigDecimal progress;
        try {
            progress = new BigDecimal(progressText == null || progressText.isBlank() ? "0" : progressText.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new BusinessException("Tiến độ phải là số từ 0 đến 100.");
        }
        if (progress.compareTo(BigDecimal.ZERO) < 0 || progress.compareTo(new BigDecimal("100")) > 0)
            throw new BusinessException("Tiến độ phải nằm trong khoảng 0 – 100.");
        progress = progress.setScale(2, java.math.RoundingMode.HALF_UP);
        if (dao.siteCodeExists(code, id)) throw new BusinessException("Mã công trình đã tồn tại.");

        // Tự điền ngày thực tế theo trạng thái nếu người dùng chưa nhập.
        if (actualStart == null && ("IN_PROGRESS".equals(status) || "COMPLETED".equals(status))) actualStart = LocalDate.now();
        if (actualEnd == null && "COMPLETED".equals(status)) actualEnd = LocalDate.now();
        note = blankToNull(note);
        if (note != null && note.length() > 255) throw new BusinessException("Ghi chú tối đa 255 ký tự.");

        if (old == null) dao.insertSite(code, name, address, investor, plannedStart, plannedEnd, actualStart, actualEnd, status, statusDate, progress, progressDate, note);
        else { requireOwner(id, "sửa"); dao.updateSite(id, code, name, address, investor, plannedStart, plannedEnd, actualStart, actualEnd, status, statusDate, progress, progressDate, note); }
    }

    // ---------------- nhu cầu nhân lực ----------------
    public List<Demand> demands(Integer worksiteId) { return dao.demands(worksiteId); }

    /** Thêm (old == null) hoặc sửa một dòng nhu cầu nhân lực. */
    public void saveDemand(Demand old, Site site, LocalDate from, LocalDate to, String phase, Skill skill, Level level, int quantity) {
        if (site == null) throw new BusinessException("Hãy chọn công trình.");
        if (skill == null) throw new BusinessException("Hãy chọn kỹ năng.");
        if (level == null) throw new BusinessException("Kỹ năng này chưa có bậc thợ, hãy khai báo bậc thợ trước.");
        if (level.skillId() != skill.id()) throw new BusinessException("Bậc thợ không thuộc kỹ năng đã chọn.");
        phase = need(phase, "Giai đoạn thi công", 100);
        if (from == null || to == null) throw new BusinessException("Hãy nhập khoảng thời gian áp dụng.");
        if (to.isBefore(from)) throw new BusinessException("Ngày kết thúc phải sau ngày bắt đầu.");
        if (quantity <= 0) throw new BusinessException("Số lượng công nhân cần phải lớn hơn 0.");
        if (old == null) dao.addDemand(site.id(), from, to, phase, skill.id(), level.id(), quantity, Session.userId());
        else { Ownership.require(Kind.DEMAND, old.demandId(), "sửa"); dao.updateDemand(old.demandId(), old.planId(), site.id(), from, to, phase, skill.id(), level.id(), quantity, Session.userId()); }
    }

    public void deleteDemand(Demand d) { Ownership.require(Kind.DEMAND, d.demandId(), "xóa"); dao.deleteDemand(d.demandId(), d.planId()); }

    // ---------------- phân công ----------------
    public List<Assignment> assignments(String keyword, String status) { return dao.assignments(keyword, status); }

    public void assign(Worker worker, Site site, LocalDate start, LocalDate expectedEnd) {
        if (worker == null) throw new BusinessException("Hãy chọn công nhân.");
        if (site == null) throw new BusinessException("Hãy chọn công trình.");
        if (start == null) throw new BusinessException("Ngày bắt đầu không được để trống.");
        if (expectedEnd != null && expectedEnd.isBefore(start))
            throw new BusinessException("Ngày kết thúc dự kiến phải sau ngày bắt đầu.");
        dao.assign(worker.id(), site.id(), start, expectedEnd);
    }

    public void updateAssignment(Assignment a, Worker worker, Site site, LocalDate start, LocalDate expectedEnd) {
        Ownership.require(Kind.ASSIGNMENT, a.id(), "sửa");
        if (!"ACTIVE".equals(a.status())) throw new BusinessException("Chỉ sửa được phân công đang hoạt động.");
        if (worker == null || site == null || start == null) throw new BusinessException("Thiếu công nhân, công trình hoặc ngày bắt đầu.");
        if (expectedEnd != null && expectedEnd.isBefore(start)) throw new BusinessException("Ngày kết thúc dự kiến phải sau ngày bắt đầu.");
        dao.updateAssignment(a.id(), worker.id(), site.id(), start, expectedEnd);
    }

    public void finishAssignment(Assignment a, LocalDate endDate) {
        Ownership.require(Kind.ASSIGNMENT, a.id(), "kết thúc");
        if (!"ACTIVE".equals(a.status())) throw new BusinessException("Chỉ kết thúc được phân công đang hoạt động.");
        if (endDate == null || endDate.isBefore(a.startDate())) throw new BusinessException("Ngày kết thúc phải từ ngày bắt đầu phân công trở đi.");
        dao.finishAssignment(a.id(), endDate);
    }

    public void cancelAssignment(Assignment a, String reason) {
        Ownership.require(Kind.ASSIGNMENT, a.id(), "hủy");
        if (!"ACTIVE".equals(a.status())) throw new BusinessException("Chỉ hủy được phân công đang hoạt động.");
        if (reason == null || reason.isBlank()) throw new BusinessException("Hãy nhập lý do hủy phân công.");
        if (reason.trim().length() > 255) throw new BusinessException("Lý do tối đa 255 ký tự.");
        dao.cancelAssignment(a.id(), reason.trim());
    }

    // ---------------- cảnh báo ----------------
    public List<Object[]> shortage(Integer worksiteId) { return dao.shortage(worksiteId); }
    public List<Object[]> suggestWorkers(int worksiteId,int skillId,int levelId,LocalDate from,LocalDate to) { return dao.suggestWorkers(worksiteId,skillId,levelId,from,to); }

    private static String need(String v, String label, int max) {
        if (v == null || v.isBlank()) throw new BusinessException(label + " không được để trống.");
        v = v.trim();
        if (v.length() > max) throw new BusinessException(label + " tối đa " + max + " ký tự.");
        return v;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
