import com.pricepulse.util.UrlNormalizer;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** java.net.URL parsing plus the product-URL normalisation PricePulse uses to prevent duplicate tracking. */
public class UrlDemo {
    public static void main(String[] args) throws Exception {
        String raw = args.length > 0 ? args[0]
                : "HTTPS://Shop.Example.com:8443/p/trail%20shoe/?utm_source=mail&color=red&size=9#reviews";
        URL url = new URI(raw).toURL();
        System.out.println("protocol : " + url.getProtocol());
        System.out.println("host     : " + url.getHost());
        System.out.println("port     : " + url.getPort() + " (default " + url.getDefaultPort() + ")");
        System.out.println("path     : " + url.getPath() + "  -> decoded: " + URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8));
        System.out.println("query    : " + url.getQuery());
        System.out.println("fragment : " + url.getRef());

        URI normalized = UrlNormalizer.normalize(raw);
        System.out.println("normalized product URL : " + normalized);
        System.out.println("domain                 : " + UrlNormalizer.domain(normalized));

        for (String bad : new String[]{"ftp://example.com/a", "javascript:alert(1)", "https://user:pw@example.com/"}) {
            try { UrlNormalizer.normalize(bad); System.out.println("UNEXPECTED accept: " + bad); }
            catch (IllegalArgumentException e) { System.out.println("rejected '" + bad + "': " + e.getMessage()); }
        }
    }
}
