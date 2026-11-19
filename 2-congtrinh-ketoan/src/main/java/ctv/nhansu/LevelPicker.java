package ctv.nhansu;

import ctv.gui.Ui;
import ctv.nhansu.HrModels.Level;
import ctv.nhansu.HrModels.Skill;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

/** Bậc thợ thuộc từng kỹ năng (Form 06): khi đổi kỹ năng thì ComboBox bậc thợ nạp lại đúng các bậc của kỹ năng đó. */
public final class LevelPicker {
    private LevelPicker() {}

    public static void bind(Node parent, HrService service, ComboBox<Skill> skill, ComboBox<Level> level) {
        Ui.translateCells(level);
        Runnable fill = () -> {
            Skill s = skill.getValue();
            List<Level> levels = s == null ? List.of() : service.levels(s.id());
            level.getItems().setAll(levels);
            if (!levels.isEmpty()) level.getSelectionModel().selectFirst();
        };
        skill.setOnAction(e -> Ui.safe(parent, fill));
        Ui.safe(parent, fill);
    }

    /** Chọn sẵn bậc có id cho trước (nếu có trong danh sách). */
    public static void select(ComboBox<Level> level, int levelId) {
        for (Level l : level.getItems())
            if (l.id() == levelId) { level.setValue(l); return; }
    }
}
