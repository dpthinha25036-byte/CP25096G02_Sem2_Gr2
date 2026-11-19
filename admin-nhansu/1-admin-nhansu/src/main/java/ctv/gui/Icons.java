package ctv.gui;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Icon SVG (bộ Lucide, 24x24, nét viền) trong resources/icons/{name}.svg, vẽ bằng hình JavaFX và tô theo màu chỉ định. */
public final class Icons {
    private Icons() {}

    private static final Map<String, Document> CACHE = new HashMap<>();

    /** Trả về một Node vuông size x size; mỗi lần gọi tạo Node mới (một Node không thể nằm ở hai nơi). */
    public static Node icon(String name, Color color, double size) {
        Group g = new Group();
        Document doc = load(name);
        if (doc != null) {
            NodeList kids = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (!(kids.item(i) instanceof Element e)) continue;
                Shape s = shape(e);
                if (s == null) continue;
                s.setFill(Color.TRANSPARENT);
                s.setStroke(color);
                s.setStrokeWidth(2);
                s.setStrokeLineCap(StrokeLineCap.ROUND);
                s.setStrokeLineJoin(StrokeLineJoin.ROUND);
                g.getChildren().add(s);
            }
        }
        double k = size / 24.0;
        g.getTransforms().add(new Scale(k, k, 0, 0));
        Pane box = new Pane(g);
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    private static Shape shape(Element e) {
        switch (e.getTagName()) {
            case "path": {
                SVGPath p = new SVGPath();
                p.setContent(e.getAttribute("d"));
                return p;
            }
            case "circle":
                return new Circle(num(e, "cx"), num(e, "cy"), num(e, "r"));
            case "rect": {
                Rectangle r = new Rectangle(num(e, "x"), num(e, "y"), num(e, "width"), num(e, "height"));
                r.setArcWidth(num(e, "rx") * 2);
                r.setArcHeight((e.hasAttribute("ry") ? num(e, "ry") : num(e, "rx")) * 2);
                return r;
            }
            case "polyline": {
                Polyline pl = new Polyline();
                String[] pts = e.getAttribute("points").trim().split("[\\s,]+");
                for (String v : pts) if (!v.isEmpty()) pl.getPoints().add(Double.parseDouble(v));
                return pl;
            }
            default:
                return null;
        }
    }

    private static double num(Element e, String a) {
        String v = e.getAttribute(a);
        return v == null || v.isEmpty() ? 0 : Double.parseDouble(v);
    }

    private static synchronized Document load(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        Document doc = null;
        try (InputStream in = Icons.class.getResourceAsStream("/icons/" + name + ".svg")) {
            if (in != null) doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        } catch (Exception ignored) { /* thiếu icon: bỏ qua, không làm sập giao diện */ }
        CACHE.put(name, doc);
        return doc;
    }
}
