package ctv.gui;

import java.time.LocalDate;
import javafx.scene.control.DatePicker;

/** Ô ngày dd/MM/yyyy có nút mở lịch chọn ngày (DatePicker của JavaFX, hiển thị và nhập theo dd/MM/yyyy). */
public final class DatePopup {
    private DatePopup() {}

    /**
     * Ô nhập ngày kèm nút lịch bên phải. initial = null → để trống. Cho phép gõ tay; đọc giá trị bằng Ui.parseDate / Ui.dateOf
     * (báo lỗi nếu sai dạng). clearable chỉ để giữ chữ ký cũ: DatePicker luôn cho xóa trắng ô nhập.
     */
    public static DatePicker field(LocalDate initial, boolean clearable) {
        DatePicker p = new DatePicker();
        p.setConverter(Ui.dateConverter());
        p.setPromptText("dd/MM/yyyy");
        p.setEditable(true);
        p.getStyleClass().add("ctv-input");
        if (initial != null) {
            p.setValue(initial);
            // Skin của DatePicker chưa tạo nên ô nhập chưa tự có chữ; ghi thẳng để đọc ngày ngay khi khởi tạo màn hình
            p.getEditor().setText(Ui.dateConverter().toString(initial));
        }
        // Gõ tay xong rồi bấm ra ngoài: ghi nhận giá trị (chuỗi sai dạng vẫn nằm trong ô nhập để parseDate báo lỗi).
        p.getEditor().focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                LocalDate d = Ui.dateConverter().fromString(p.getEditor().getText());
                if (d != null) p.setValue(d);
            }
        });
        p.setMaxWidth(Double.MAX_VALUE);
        return p;
    }
}
