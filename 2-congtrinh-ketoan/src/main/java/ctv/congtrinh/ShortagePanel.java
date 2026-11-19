package ctv.congtrinh;

import ctv.congtrinh.SiteModels.Site;
import ctv.gui.DataTable;
import ctv.gui.Responsive;
import ctv.gui.Ui;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

/** Cảnh báo thiếu / đủ nhân lực (chức năng 2.4): so nhu cầu (manpower_demands) với số công nhân đã phân công. */
public class ShortagePanel extends BorderPane {
    private static final String ALL = "Tất cả công trình";

    private final SiteService service = new SiteService();
    private final ComboBox<Object> cboCongTrinh = new ComboBox<>();
    private final Label lblDong = new Label("0"), lblThieu = new Label("0"), lblDu = new Label("0");
    private final DataTable table = Ui.table("#", "Công trình", "Thời gian", "Giai đoạn", "Kỹ năng",
            "Bậc tối thiểu", "Cần", "Đã phân công", "Thiếu", "Cảnh báo");
    private List<Object[]> current = List.of();
    private boolean loading;

    public ShortagePanel() {
        Ui.Content root = Ui.contentPanel();
        setCenter(root);

        Ui.translateCells(cboCongTrinh);
        Ui.size(cboCongTrinh, 300, 34);
        Button btnLamMoi = Ui.secondary("Cập nhật cảnh báo", "refresh-cw");
        Button btnDeXuat = Ui.secondary("Đề xuất công nhân phù hợp", "users");

        Responsive.Grid cards = new Responsive.Grid(3, 12, 215, 0);
        cards.add(Ui.statCard("layout-list", Ui.CARD_ACCENTS[0], "Dòng nhu cầu", lblDong, Ui.CARD_COLORS[0]));
        cards.add(Ui.statCard("x", Ui.CARD_ACCENTS[3], "Đang thiếu nhân lực", lblThieu, Ui.CARD_COLORS[3]));
        cards.add(Ui.statCard("circle-check", Ui.CARD_ACCENTS[2], "Đủ nhân lực", lblDu, Ui.CARD_COLORS[2]));

        root.north(Ui.stack(
                Ui.header("triangle-alert", "Cảnh báo nhân lực", "Theo dõi chênh lệch giữa nhu cầu và số công nhân đã phân công", btnLamMoi, btnDeXuat),
                Ui.card(Ui.flow(Pos.CENTER_LEFT, Ui.captionBold("Công trình:"), cboCongTrinh)), cards));
        root.center(Ui.scroll(table));

        // Dòng thiếu nhân lực: chữ đỏ ở cột "Thiếu" và "Cảnh báo"; đủ thì xanh.
        java.util.function.Function<Object[], Color> warn =
                row -> Integer.parseInt(String.valueOf(row[8])) > 0 ? Color.web("#B42318") : Color.web("#15803D");
        table.installColoredText(8, warn);
        table.installColoredText(9, warn);

        cboCongTrinh.setOnAction(e -> { if (!loading) reload(); });
        btnLamMoi.setOnAction(e -> reload());
        btnDeXuat.setOnAction(e -> suggest());
        reload();
    }

    private void suggest() {
        int row = table.getSelectedRow();
        if (row < 0) { Ui.error(this, "Hãy chọn dòng nhu cầu cần bổ sung."); return; }
        Object[] r = current.get(table.convertRowIndexToModel(row));
        Ui.safe(this, () -> {
            List<Object[]> people = service.suggestWorkers((Integer) r[10], (Integer) r[12], (Integer) r[13], (LocalDate) r[2], (LocalDate) r[3]);
            DataTable t = Ui.table("Mã CN", "Họ tên", "Điện thoại", "Bậc hiện có");
            for (Object[] x : people) t.addRow(x[1], x[2], x[3], x[5]);
            Ui.showContent(this, "Đề xuất công nhân - " + r[5] + " / " + r[6], Ui.scroll(t), 620, 300);
        });
    }

    private void reload() {
        Ui.safe(this, () -> {
            loading = true;
            Object keep = cboCongTrinh.getValue();
            List<Site> sites = service.allSites();
            List<Object> items = new ArrayList<>();
            items.add(ALL);
            items.addAll(sites);
            cboCongTrinh.getItems().setAll(items);
            cboCongTrinh.getSelectionModel().selectFirst();
            if (keep instanceof Site k) for (Site s : sites) if (s.id() == k.id()) cboCongTrinh.setValue(s);
            loading = false;

            Object sel = cboCongTrinh.getValue();
            table.setRowCount(0);
            int i = 1, lack = 0, ok = 0;
            current = service.shortage(sel instanceof Site s ? s.id() : null);
            for (Object[] r : current) {
                int missing = (Integer) r[9];
                if (missing > 0) lack++; else ok++;
                table.addRow(i++, r[0] + " - " + r[1], Ui.fmt((LocalDate) r[2]) + " - " + Ui.fmt((LocalDate) r[3]),
                        r[4], r[5], r[6], r[7], r[8], missing, missing > 0 ? "Thiếu " + missing + " người" : "Đủ nhân lực");
            }
            lblDong.setText(String.valueOf(lack + ok));
            lblThieu.setText(String.valueOf(lack));
            lblDu.setText(String.valueOf(ok));
        });
    }
}
