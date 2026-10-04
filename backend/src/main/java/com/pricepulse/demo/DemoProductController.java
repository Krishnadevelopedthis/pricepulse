package com.pricepulse.demo;

import com.pricepulse.model.TrackedProduct;
import com.pricepulse.repo.TrackedProductRepository;
import com.pricepulse.service.PriceCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo product page served by the backend itself (viva only; enabled with PRICEPULSE_DEMO_ENABLED=true).
 * The price is stored in MongoDB and written into the page's JSON-LD, so the server's normal price
 * check reads exactly what the page shows. Changing the price triggers an immediate server check of
 * every tracked copy of this page, so PricePulse updates without opening the popup.
 */
@RestController
@ConditionalOnProperty(name = "pricepulse.demo.enabled", havingValue = "true")
public class DemoProductController {
    private static final Logger log = LoggerFactory.getLogger(DemoProductController.class);
    private static final String STATE_ID = "headphones";
    private static final long DEFAULT_PRICE = 30000;
    private static final String PATH = "/demo/headphones";

    private final DemoStateRepository state;
    private final TrackedProductRepository products;
    private final PriceCheckService checks;

    public DemoProductController(DemoStateRepository state, TrackedProductRepository products,
                                 PriceCheckService checks) {
        this.state = state;
        this.products = products;
        this.checks = checks;
    }

    public record PriceBody(Long price) {}

    @GetMapping(value = PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page() {
        long price = currentPrice();
        String html = PAGE.replace("__PRICE__", Long.toString(price));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(html);
    }

    @PostMapping(value = "/demo/price", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> setPrice(@RequestBody PriceBody body) {
        if (body == null || body.price() == null || body.price() < 1 || body.price() > 9_999_999L) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Price must be a whole number between 1 and 9999999."));
        }
        long price = body.price();
        state.save(new DemoState(STATE_ID, price));
        // Re-check every tracked copy of this page right now (off the request thread).
        for (TrackedProduct p : products.findByNormalizedUrlContaining(PATH)) {
            String id = p.getId();
            CompletableFuture.runAsync(() -> {
                try {
                    checks.check(id);
                } catch (RuntimeException e) {
                    log.warn("Demo re-check failed for {}", id, e);
                }
            });
        }
        return ResponseEntity.ok(Map.of("price", price));
    }

    private long currentPrice() {
        return state.findById(STATE_ID).map(DemoState::getPrice).orElse(DEFAULT_PRICE);
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>SoundMax Wireless Headphones</title>
            <script type="application/ld+json" id="product-jsonld">
            {"@context":"https://schema.org","@type":"Product","name":"SoundMax Wireless Headphones",
             "offers":{"@type":"Offer","price":"__PRICE__","priceCurrency":"INR","availability":"https://schema.org/InStock"}}
            </script>
            <style>
              body { font-family: Arial, sans-serif; background: #f4f4f4; margin: 0; padding: 20px; }
              .box { max-width: 520px; margin: 0 auto; background: #fff; padding: 20px; border: 1px solid #ddd; }
              h1 { font-size: 22px; margin-top: 0; }
              .price { font-size: 32px; font-weight: bold; color: #b12704; margin: 10px 0; }
              .admin { background: #fff8c5; border: 1px solid #e0d27a; padding: 12px; margin-top: 20px; }
              input { padding: 6px; width: 120px; }
              button { padding: 6px 12px; cursor: pointer; }
              #msg { margin-top: 8px; font-size: 14px; }
            </style>
            </head>
            <body>
            <div class="box">
              <h1>SoundMax Wireless Headphones</h1>
              <p>Bluetooth over-ear headphones, 40 hours battery, noise cancellation.</p>
              <div class="price" id="price">Rs. __PRICE__</div>
              <div class="admin">
                <strong>Change price (for demo)</strong><br><br>
                <input id="newPrice" type="number" min="1" placeholder="e.g. 27000">
                <button id="setBtn" type="button">Set price</button>
                <div id="msg"></div>
              </div>
            </div>
            <script>
              document.getElementById('setBtn').addEventListener('click', function () {
                var v = parseInt(document.getElementById('newPrice').value, 10);
                var msg = document.getElementById('msg');
                if (!v || v < 1) { msg.textContent = 'Enter a valid price.'; return; }
                fetch('/demo/price', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ price: v })
                }).then(function (r) { return r.json(); }).then(function (d) {
                  if (d.price) {
                    document.getElementById('price').textContent = 'Rs. ' + d.price;
                    msg.textContent = 'Price saved. PricePulse server is checking it now.';
                  } else { msg.textContent = d.error || 'Failed.'; }
                }).catch(function () { msg.textContent = 'Request failed.'; });
              });
            </script>
            </body>
            </html>
            """;
}
