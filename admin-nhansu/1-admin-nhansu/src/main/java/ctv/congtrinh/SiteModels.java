package ctv.congtrinh;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Model nhóm 2 – công trình, nhu cầu nhân lực, phân công (ánh xạ bảng trong Form 06). */
public final class SiteModels {
    private SiteModels() {}

    /** Thông tin công trình đầy đủ (bảng worksites). */
    public record Site(int id, String code, String name, String address, String investor,
                       LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd,
                       String status, BigDecimal progress,
                       Integer createdBy, String creatorName, String creatorRole) {
        /** "Họ tên (Chức vụ)" của người đã thêm công trình; rỗng nếu công trình cũ chưa ghi nhận. */
        public String creatorLabel() {
            if (creatorName == null) return "";
            return creatorRole == null ? creatorName : creatorName + " (" + creatorRole + ")";
        }
        @Override public String toString() { return code + " - " + name; }
    }

    /** Nhu cầu nhân lực = manpower_demands + manpower_plans (khoảng ngày) + tên công trình/kỹ năng/bậc. */
    public record Demand(int demandId, int planId, int worksiteId, String worksiteName,
                         LocalDate fromDate, LocalDate toDate, String phase,
                         int skillId, String skillName, int levelId, String levelName, int quantity) {}

    /** Phân công công nhân vào công trình (bảng assignments). */
    public record Assignment(int id, int workerId, String workerCode, String workerName,
                             int worksiteId, String worksiteName, LocalDate startDate,
                             LocalDate expectedEnd, LocalDate actualEnd, String status, String cancelReason) {}
}
