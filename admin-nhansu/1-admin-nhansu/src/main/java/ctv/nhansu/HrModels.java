package ctv.nhansu;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Model nhóm 1 – nhân sự, kỹ năng, đơn giá (ánh xạ bảng trong Form 06). */
public final class HrModels {
    private HrModels() {}

    public record WorkerType(int id, String name) {
        @Override public String toString() { return name; }
    }

    /** Hồ sơ công nhân (bảng workers). typeName lấy từ worker_types. */
    public record Worker(int id, String code, String fullName, LocalDate birthDate, String gender, String idCardNo,
                         String phone, String address, int typeId, String typeName, LocalDate hireDate,
                         String workStatus, LocalDate statusEffectiveDate,
                         String bankName, String bankAccountNo, String bankAccountHolder, boolean hasActiveAssignment) {
        @Override public String toString() { return code + " - " + fullName; }

        /** Trạng thái hiển thị: đang làm việc mà chưa được phân công vào công trình nào = "WAITING" (Chờ phân công). */
        public String displayStatus() {
            return "WORKING".equals(workStatus) && !hasActiveAssignment ? "WAITING" : workStatus;
        }

        /** Tên chủ tài khoản: nếu chưa nhập thì lấy tên công nhân. */
        public String holderName() {
            return bankAccountHolder == null || bankAccountHolder.isBlank() ? fullName : bankAccountHolder;
        }

        /** Có tài khoản ngân hàng nhận lương hay không (không có = lãnh lương tay). */
        public boolean hasBankAccount() { return bankAccountNo != null && !bankAccountNo.isBlank(); }

        /** Hình thức trả lương: có số tài khoản = Chuyển khoản, không có = Lãnh lương tay. */
        public String payMethodLabel() { return hasBankAccount() ? "Chuyển khoản" : "Lãnh lương tay"; }

        /** Nhãn ngắn hiển thị ở danh sách. */
        public String receiveLabel() {
            if (!hasBankAccount()) return "Lãnh lương tay";
            return (bankName == null || bankName.isBlank() ? "" : bankName + " - ") + bankAccountNo;
        }
    }

    public record Skill(int id, String code, String name, String description) {
        @Override public String toString() { return name; }
    }

    /** Bậc thợ – thuộc một kỹ năng (bảng skill_levels). */
    public record Level(int id, String code, String name, int skillId, String skillName, String description) {
        @Override public String toString() { return name; }
    }

    /** Một dòng gán kỹ năng + bậc thợ cho công nhân (bảng worker_skills). */
    public record WorkerSkillRow(int id, int workerId, String workerCode, String workerName,
                                 int skillId, String skillName, int levelId, String levelName) {}

    /** Đơn giá theo ca (bảng shift_rates). */
    public record Rate(int id, int skillId, String skillName, int levelId, String levelName,
                       String shiftType, BigDecimal rate, LocalDate effectiveDate) {}
}
