package ctv.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 3.4.9 – xuất báo cáo ra Excel (Apache POI) hoặc PDF (OpenPDF). */
public final class Exporter {
    private Exporter() {}

    public static void toExcel(File file, String title, List<String> headers, List<List<Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Sheet sh = wb.createSheet(I18n.t("Báo cáo"));
            CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            sh.createRow(0).createCell(0).setCellValue(I18n.t(title));
            sh.getRow(0).getCell(0).setCellStyle(bold);
            Row h = sh.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(I18n.t(headers.get(i)));
                c.setCellStyle(bold);
            }
            int r = 3;
            for (List<Object> row : rows) {
                Row xr = sh.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Object v = row.get(i);
                    Cell c = xr.createCell(i);
                    if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else c.setCellValue(v == null ? "" : I18n.t(v.toString()));
                }
            }
            for (int i = 0; i < headers.size(); i++) sh.autoSizeColumn(i);
            wb.write(out);
        } catch (IOException e) {
            throw new BusinessException("Không ghi được file Excel: " + e.getMessage(), e);
        }
    }

    public static void toPdf(File file, String title, List<String> headers, List<List<Object>> rows) {
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(unicodeFont(), 16, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font head = new com.lowagie.text.Font(unicodeFont(), 10, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font body = new com.lowagie.text.Font(unicodeFont(), 10);
            doc.add(new Paragraph(I18n.t(title), titleFont));
            doc.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(headers.size());
            t.setWidthPercentage(100);
            for (String h : headers) t.addCell(new PdfPCell(new Phrase(I18n.t(h), head)));
            for (List<Object> row : rows)
                for (Object v : row) t.addCell(new PdfPCell(new Phrase(v == null ? "" : I18n.t(v.toString()), body)));
            doc.add(t);
            doc.close(); // phải đóng tài liệu trước khi luồng ghi file được đóng
        } catch (IOException | DocumentException e) {
            throw new BusinessException("Không ghi được file PDF: " + e.getMessage(), e);
        }
    }

    /** Font có dấu tiếng Việt: thử các font Windows/Linux phổ biến, nếu không có thì dùng Helvetica. */
    private static BaseFont unicodeFont() {
        String[] candidates = {
            "C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/Library/Fonts/Arial Unicode.ttf"};
        for (String path : candidates) {
            if (new File(path).exists()) {
                try { return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED); }
                catch (Exception ignored) { }
            }
        }
        try { return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false); }
        catch (Exception e) { throw new BusinessException("Không tạo được font PDF", e); }
    }
}
