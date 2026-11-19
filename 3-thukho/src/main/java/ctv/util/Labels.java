package ctv.util;

import ctv.model.Models.Option;
import java.util.List;

/** Nhãn tiếng Việt cho các giá trị trạng thái lưu trong DB (mã tiếng Anh theo Form 06). */
public final class Labels {
    private Labels() {}

    // ----- thiết bị (nhóm 3) -----
    public static final List<Option> CONDITIONS = List.of(
            new Option("GOOD", "Tốt"), new Option("NEED_REPAIR", "Cần sửa chữa"), new Option("BROKEN", "Hỏng"));
    /** 5 tình trạng hiển thị của thiết bị (Form 02A 3.2.8): suy ra từ condition_status, usage_status và vị trí tồn kho. */
    public static final List<Option> STATUSES = List.of(
            new Option("READY", "Sẵn sàng"), new Option("IN_USE", "Đang sử dụng"), new Option("MAINT", "Bảo trì"),
            new Option("REPAIR", "Cần sửa chữa"), new Option("BROKEN", "Hỏng"));
    public static final List<Option> USAGES = List.of(
            new Option("IN_STOCK", "Trong kho"), new Option("IN_USE", "Đang sử dụng"), new Option("MAINTENANCE", "Bảo trì"));

    // ----- nhân sự (nhóm 1) -----
    public static final List<Option> WORK_STATUSES = List.of(
            new Option("WORKING", "Đang làm việc"), new Option("ON_LEAVE", "Tạm nghỉ"), new Option("RESIGNED", "Đã nghỉ việc"));
    /** Trạng thái hiển thị/lọc: thêm "Chờ phân công" (suy ra: đang làm việc nhưng chưa có phân công ACTIVE, không lưu trong DB). */
    public static final String WAITING = "WAITING";
    public static final List<Option> DISPLAY_WORK_STATUSES = List.of(
            new Option("WORKING", "Đang làm việc"), new Option(WAITING, "Chờ phân công"),
            new Option("ON_LEAVE", "Tạm nghỉ"), new Option("RESIGNED", "Đã nghỉ việc"));
    public static final List<Option> GENDERS = List.of(
            new Option("Nam", "Nam"), new Option("Nữ", "Nữ"), new Option("Khác", "Khác"));
    public static final List<Option> SHIFT_TYPES = List.of(
            new Option("REGULAR", "Ca thường"), new Option("OVERTIME", "Ca tăng ca"));

    // ----- công trình, phân công (nhóm 2) -----
    public static final List<Option> WORKSITE_STATUSES = List.of(
            new Option("PREPARING", "Chuẩn bị"), new Option("IN_PROGRESS", "Đang thi công"),
            new Option("PAUSED", "Tạm dừng"), new Option("COMPLETED", "Hoàn thành"));
    public static final List<Option> ASSIGNMENT_STATUSES = List.of(
            new Option("ACTIVE", "Đang phân công"), new Option("FINISHED", "Đã kết thúc"), new Option("CANCELLED", "Đã hủy"));

    public static String condition(String code) { return find(CONDITIONS, code); }
    public static String status(String code) { return find(STATUSES, code); }
    public static String usage(String code) { return find(USAGES, code); }
    public static String workStatus(String code) { return find(DISPLAY_WORK_STATUSES, code); }
    public static String shiftType(String code) { return find(SHIFT_TYPES, code); }
    public static String worksiteStatus(String code) { return find(WORKSITE_STATUSES, code); }
    public static String assignmentStatus(String code) { return find(ASSIGNMENT_STATUSES, code); }

    /** Tìm Option theo mã để chọn sẵn trong ComboBox; không thấy thì trả phần tử đầu. */
    public static Option option(List<Option> list, String code) {
        for (Option o : list) if (o.code().equals(code)) return o;
        return list.get(0);
    }

    private static String find(List<Option> list, String code) {
        if (code == null) return "";
        for (Option o : list) if (o.code().equals(code)) return o.label();
        return code;
    }
}
