import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class JsoupTest {
    public static void main(String[] args) {
        String html = "<h1>Hello <!-- inline comment --> World Of HTML</h1>";
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        System.out.println("Result: '" + doc.select("h1").first().html() + "'");
        System.out.println("Full: '" + doc.html() + "'");
    }
}
