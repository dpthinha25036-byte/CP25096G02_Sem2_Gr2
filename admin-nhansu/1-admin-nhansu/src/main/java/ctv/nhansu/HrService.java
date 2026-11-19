package ctv.nhansu;

import ctv.nhansu.HrDAO;
import ctv.nhansu.HrModels.*;
import ctv.util.BusinessException;
import ctv.util.Ownership;
import ctv.util.Ownership.Kind;
import ctv.util.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Nhóm 1 – nghiệp vụ công nhân, kỹ năng, bậc thợ, đơn giá, báo cáo nhân lực. */
public class HrService {
    private final HrDAO dao = new HrDAO();

    // ---------------- công nhân ----------------
    public List<WorkerType> workerTypes() { return dao.workerTypes(); }
    public List<Worker> searchWorkers(String keyword, String status, Integer typeId, Integer skillId, Integer levelId) {
        return dao.searchWorkers(keyword, status, typeId, skillId, levelId);
    }
    public List<Worker> workingWorkers() { return dao.workingWorkers(); }

    /** Thêm (old == null) hoặc sửa công nhân. Ngày hiệu lực trạng thái do người dùng nhập (1.2.4); bỏ trống thì lấy hôm nay. */
    public void saveWorker(Worker old, String code, String name, LocalDate birth, String gender, String idCard,
                           String phone, String address, WorkerType type, LocalDate hire, String status,
                           LocalDate statusEffective, String bankName, String bankAccountNo, String bankAccountHolder) {
        int id = old == null ? 0 : old.id();
        code = need(code, "Mã công nhân", 20);
        name = need(name, "Họ tên", 100);
        idCard = need(idCard, "Số CCCD", 12);
        if (!idCard.matches("\\d{12}")) throw new BusinessException("Số CCCD phải gồm đúng 12 chữ số.");
        if (birth == null) throw new BusinessException("Ngày sinh không được để trống.");
        if (hire == null) throw new BusinessException("Ngày vào làm không được để trống.");
        if (!birth.isBefore(LocalDate.now())) throw new BusinessException("Ngày sinh phải trước ngày hôm nay.");
        if (hire.isBefore(birth)) throw new BusinessException("Ngày vào làm không được trước ngày sinh.");
        if (gender == null || gender.isBlank()) throw new BusinessException("Hãy chọn giới tính.");
        if (type == null) throw new BusinessException("Hãy chọn loại công nhân.");
        phone = blankToNull(phone);
        if (phone != null && phone.length() > 15) throw new BusinessException("Số điện thoại tối đa 15 ký tự.");
        address = blankToNull(address);
        if (address != null && address.length() > 255) throw new BusinessException("Địa chỉ tối đa 255 ký tự.");
        // Tài khoản ngân hàng: để trống số tài khoản = lãnh lương tay (khi đó bỏ luôn tên ngân hàng / chủ tài khoản).
        bankName = blankToNull(bankName);
        bankAccountNo = blankToNull(bankAccountNo);
        bankAccountHolder = blankToNull(bankAccountHolder);
        if (bankAccountNo != null) {
            bankAccountNo = bankAccountNo.replaceAll("[\\s.\\-]", "");
            if (!bankAccountNo.matches("\\d{6,20}")) throw new BusinessException("Số tài khoản ngân hàng chỉ gồm 6–20 chữ số.");
            if (bankAccountHolder == null) bankAccountHolder = name;   // chủ tài khoản mặc định = tên công nhân
            if (bankAccountHolder != null && bankAccountHolder.length() > 100) throw new BusinessException("Tên chủ tài khoản tối đa 100 ký tự.");
        } else {
            if (bankAccountHolder != null && !bankAccountHolder.equals(name))
                throw new BusinessException("Hãy nhập số tài khoản, hoặc xóa thông tin ngân hàng để công nhân lãnh lương tay.");
            bankName = null; bankAccountHolder = null;
        }
        if (dao.workerCodeExists(code, id)) throw new BusinessException("Mã công nhân đã tồn tại.");
        if (dao.idCardExists(idCard, id)) throw new BusinessException("Số CCCD đã tồn tại.");

        LocalDate statusDate = statusEffective != null ? statusEffective
                : (old != null && old.workStatus().equals(status) && old.statusEffectiveDate() != null)
                ? old.statusEffectiveDate() : LocalDate.now();
        if (statusDate.isBefore(hire)) throw new BusinessException("Ngày hiệu lực trạng thái không được trước ngày vào làm.");
        if (statusDate.isAfter(LocalDate.now())) throw new BusinessException("Ngày hiệu lực trạng thái không được ở tương lai.");
        if (old == null) dao.insertWorker(code, name, birth, gender, idCard, phone, address, type.id(), hire, status, statusDate,
                bankName, bankAccountNo, bankAccountHolder);
        else { Ownership.require(Kind.WORKER, id, "sửa"); dao.updateWorker(id, code, name, birth, gender, idCard, phone, address, type.id(), hire, status, statusDate,
                bankName, bankAccountNo, bankAccountHolder); }
    }

    public void deleteWorker(int id) { Ownership.require(Kind.WORKER, id, "xóa"); dao.deleteWorker(id); }

    // ---------------- kỹ năng / bậc thợ ----------------
    public List<Skill> skills(String keyword) { return dao.skills(keyword); }
    public List<Level> levels(Integer skillId) { return dao.levels(skillId); }

    public void saveSkill(Skill old, String code, String name, String desc) {
        int id = old == null ? 0 : old.id();
        code = need(code, "Mã kỹ năng", 20);
        name = need(name, "Tên kỹ năng", 100);
        desc = blankToNull(desc);
        if (dao.skillCodeExists(code, id)) throw new BusinessException("Mã kỹ năng đã tồn tại.");
        if (old == null) dao.insertSkill(code, name, desc, Session.userId());
        else { Ownership.require(Kind.SKILL, id, "sửa"); dao.updateSkill(id, code, name, desc, Session.userId()); }
    }

    public void deleteSkill(int id) { Ownership.require(Kind.SKILL, id, "xóa"); dao.deleteSkill(id, Session.userId()); }

    public void saveLevel(Level old, Skill skill, String code, String name, String desc) {
        int id = old == null ? 0 : old.id();
        if (skill == null) throw new BusinessException("Hãy chọn kỹ năng cho bậc thợ.");
        code = need(code, "Mã bậc thợ", 20);
        name = need(name, "Tên bậc thợ", 50);
        desc = blankToNull(desc);
        if (dao.levelCodeExists(code, id)) throw new BusinessException("Mã bậc thợ đã tồn tại.");
        if (old == null) dao.insertLevel(code, name, skill.id(), desc, Session.userId());
        else { Ownership.require(Kind.LEVEL, id, "sửa"); dao.updateLevel(id, code, name, desc, Session.userId()); }
    }

    public void deleteLevel(int id) { Ownership.require(Kind.LEVEL, id, "xóa"); dao.deleteLevel(id, Session.userId()); }

    // ---------------- gán kỹ năng ----------------
    public List<WorkerSkillRow> workerSkills(String keyword, Integer skillId) { return dao.workerSkills(keyword, skillId); }

    public void assignSkill(Worker worker, Skill skill, Level level) {
        if (worker == null) throw new BusinessException("Hãy chọn công nhân.");
        if (skill == null) throw new BusinessException("Hãy chọn kỹ năng.");
        if (level == null) throw new BusinessException("Kỹ năng này chưa có bậc thợ, hãy khai báo bậc thợ trước.");
        if (level.skillId() != skill.id()) throw new BusinessException("Bậc thợ không thuộc kỹ năng đã chọn.");
        dao.assignSkill(worker.id(), skill.id(), level.id(), Session.userId());
    }

    public void removeWorkerSkill(int id) { Ownership.require(Kind.WORKER_SKILL, id, "xóa"); dao.removeWorkerSkill(id, Session.userId()); }

    // ---------------- đơn giá ----------------
    public List<Rate> rates(Integer skillId) { return dao.rates(skillId); }
    public List<Object[]> rateHistories(Integer skillId) { return dao.rateHistories(skillId); }

    public void saveRate(Skill skill, Level level, String shiftType, String rateText, LocalDate effective) {
        if (skill == null) throw new BusinessException("Hãy chọn kỹ năng.");
        if (level == null) throw new BusinessException("Hãy chọn bậc thợ.");
        if (level.skillId() != skill.id()) throw new BusinessException("Bậc thợ không thuộc kỹ năng đã chọn.");
        if (effective == null) throw new BusinessException("Ngày hiệu lực không được để trống.");
        BigDecimal rate;
        String t = rateText == null ? "" : rateText.trim();
        if (t.matches("\\d{1,3}([.,]\\d{3})+")) t = t.replace(".", "").replace(",", "");   // 350.000 hoặc 350,000
        try {
            rate = new BigDecimal(t).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BusinessException("Đơn giá phải là số (ví dụ 350000).");
        }
        if (rate.signum() <= 0) throw new BusinessException("Đơn giá phải lớn hơn 0.");
        if (rate.compareTo(new BigDecimal("9999999999999")) > 0) throw new BusinessException("Đơn giá quá lớn.");
        dao.saveRate(skill.id(), level.id(), shiftType, rate, effective, Session.userId());
    }

    // ---------------- báo cáo ----------------
    public List<Object[]> countBy(String dimension) { return dao.countBy(dimension); }
    public Map<String, Integer> statusCounts() { return dao.statusCounts(); }

    // ---------------- tiện ích ----------------
    private static String need(String v, String label, int max) {
        if (v == null || v.isBlank()) throw new BusinessException(label + " không được để trống.");
        v = v.trim();
        if (v.length() > max) throw new BusinessException(label + " tối đa " + max + " ký tự.");
        return v;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
