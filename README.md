# PricePulse

A Chrome extension (Manifest V3) that tracks product prices, keeps real price history, draws a line chart, detects increases and decreases, and notifies you when a price reaches your target. The backend is Java 21 + Spring Boot with MongoDB. Separate small Java modules cover the academic syllabus (sockets, threads, JDBC, servlets/JSP, REST client).

> Read **Known limitations** before relying on it. Nothing here claims to work on every website.

**Deploying for public use?** Follow [`DEPLOYMENT.md`](DEPLOYMENT.md). Folder layout: [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md).

## Architecture

```text
Chrome Extension (popup / options / service worker, injected extractor)
        |  HTTP + JSON  (X-Client-Id header)
        v
Spring Boot REST controllers  ->  Services  ->  Repositories  ->  MongoDB
                                     ^
      @Scheduled tick -> due products -> bounded ExecutorService -> page fetch + parse -> record observation
```

| Part | What it does |
|---|---|
| `extension/popup` | Detects the product on the active tab, shows price, sets target, tracks, shows status. |
| `extension/content/extractor.js` | Injected on demand (activeTab + scripting). Structured data first, then DOM heuristics, then site adapters. Never invents a price. |
| `extension/background` | Service worker + `chrome.alarms`: pulls alerts queued by the backend and shows notifications. |
| `extension/options` | Tracked products, price history (chart, stats, table), settings. |
| `backend` | REST API, validation, MongoDB persistence, scheduler, concurrent price checks. |
| `academic/*` | Runnable syllabus modules, kept out of production code. See `docs/CONCEPT_MAPPING.md`. |

**Detection levels:** (1) JSON-LD / Schema.org `Product`+`Offer`, meta tags; (2) `itemprop`, price-like elements (hidden and struck-through prices are ignored, currency symbol required); (3) adapters for Amazon and eBay (best-effort, see limitations). If confidence is insufficient the popup says *Unable to detect a valid product price.* and offers manual entry.

**Price checks come from two places:**
1. **Your browser** – opening the popup on a tracked page records the price you actually see (works on JavaScript-rendered and login-gated pages).
2. **The server scheduler** – fetches the page and reads structured data only. Sites that render prices with JavaScript, or block bots, will show "Server check failing"; the extension then relies on (1).

## Setup

Quick start with Docker: `docker compose up --build` (MongoDB + backend). Manual setup:

Requirements: JDK 21, Maven 3.9+, MongoDB 6+ (local or Atlas), Chrome 116+.

```bash
# 1. MongoDB
mongod --dbpath ./data            # or use a hosted URI

# 2. Backend
cd backend
export MONGODB_URI="mongodb://localhost:27017/pricepulse"   # keep credentials in the environment
mvn spring-boot:run               # http://localhost:8080/api/health

# 3. Extension
# chrome://extensions -> Developer mode -> Load unpacked -> select the extension/ folder -> pin PricePulse
```

Then: open a real product page -> click PricePulse -> check the detected price -> enter a target price -> **Track Price**.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/pricepulse` | MongoDB connection (never put credentials in the extension) |
| `PORT` | `8080` | HTTP port |
| `PRICEPULSE_CORS_ORIGINS` | `chrome-extension://*` | Allowed origins (comma separated). Pin it to your extension id in production. |
| `PRICEPULSE_CHECK_INTERVAL_MINUTES` | `60` | Time between server checks per product |
| `PRICEPULSE_SCHEDULER_ENABLED` | `true` | Turn the scheduler off |
| `PRICEPULSE_ALLOW_PRIVATE_HOSTS` | `false` | Testing only: lets the server fetch localhost pages (disables the SSRF guard) |

### Using a non-local backend
Settings -> Server. Chrome asks for permission for that one origin (`optional_host_permissions`). Use HTTPS for anything that is not localhost.

## API

All `/api/products/**` and `/api/notifications/**` calls need `X-Client-Id` (a random id the extension generates once).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | no header needed |
| GET | `/api/products` | list yours |
| POST | `/api/products` | body: `url, name, price, currency, targetPrice?` -> 201; 409 `DUPLICATE_TRACKING` (includes `productId`) |
| GET | `/api/products/lookup?url=` | 404 when not tracked |
| GET | `/api/products/{id}` | |
| PATCH | `/api/products/{id}` | `targetPrice`, `clearTarget`, `status` (ACTIVE/PAUSED), `name` |
| DELETE | `/api/products/{id}` | 204; deletes history |
| GET | `/api/products/{id}/history?limit=` | points (oldest first) + stats |
| POST | `/api/products/{id}/observations` | browser-observed price |
| POST | `/api/products/{id}/check` | server-side check now; 422 `EXTRACTION_FAILED` if unreadable |
| GET | `/api/notifications/pending` | queued alerts |
| POST | `/api/notifications/ack` | `{"productIds":[...]}` |

Errors are JSON: `{timestamp,status,code,message,fieldErrors,productId}`; 400 malformed, 401 missing client id, 404, 409, 422 validation, 500 generic (with a log reference, never a stack trace).

## Data model (MongoDB)

* `tracked_products` – unique index `(userId, normalizedUrl)` blocks duplicates; `(status, nextCheckAt)` serves the scheduler; embedded `notification` state.
* `price_history` – one document per **successful** observation, index `(productId, observedAt)`. Failed extractions never create history, and nothing is back-filled.

Price change is `current - previous` (signed), with percentage to 2 decimals: 3,499 -> 3,199 is a 300 decrease (8.57%).

## Scheduler and concurrency

`PriceCheckScheduler.tick()` runs every minute, loads products whose `nextCheckAt` has passed (batch of 50), and submits one task each to a **bounded** `ThreadPoolExecutor` (4 workers, queue of 100, caller-runs back-pressure). Each check fetches with timeouts, up to 3 attempts with exponential backoff, manual redirect handling and an SSRF guard. Failures back off per product (interval x 2^n, capped at 16x). `ProductService` serialises updates per product with a lock so `previous/current/direction` stay consistent when browser observations and server checks race.

## Notifications

The backend decides *what* deserves an alert and stores it (target reached, drop, increase). The extension's alarm (default every 15 min) fetches pending alerts, shows `chrome.notifications`, and acknowledges them. Spam control: target alerts fire once until the price rises back above the target; unchanged prices never notify; one pending alert per product (highest priority wins). Notification settings (all/drops/increases) are in Settings.

**When Chrome is closed:** extension JavaScript cannot run. The backend keeps checking and queuing alerts; Chrome shows them shortly after it starts (`onStartup` syncs immediately). No notification can appear while Chrome is fully closed or the computer is off.

## Permissions

| Permission | Why |
|---|---|
| `storage` | settings, install id |
| `alarms` | periodic sync while Chrome runs |
| `notifications` | price alerts |
| `activeTab` + `scripting` | read the product page only when you open the popup (no always-on content script) |
| host `localhost:8080` | default backend |
| optional hosts | requested only if you point the extension at another server |

## Security

No secrets in the extension; MongoDB credentials live in backend environment variables. Requests are validated in the popup and again on the server (positive, finite, precision, supported currency). URL normalisation rejects non-http(s) and credentialed URLs. The server only fetches public addresses (SSRF guard, redirects re-checked). CORS is restricted, errors never expose stack traces, and logs contain no request bodies.
**`X-Client-Id` scopes data per install; it is not user authentication.** Anyone who learns an id can read that install's data. Add real accounts before hosting this for other people.

## Testing

```bash
./run-tests.sh      # JDK 21 + Node 20 only
```

See `docs/TEST_REPORT.md` for exactly what was executed and what was not.

## Known limitations

* The Spring Boot backend and everything that needs MongoDB, PostgreSQL, Tomcat or Chrome was **written but not executed** in the environment where this was built (see the test report). Expect to fix small compile/runtime issues on first run.
* Server-side checks read structured data only; JavaScript-rendered prices and bot-blocked sites (Amazon, Flipkart and others often block) will fail server checks. Browser observations cover this while you visit the page.
* Amazon/eBay adapters are best-effort and untested against live sites; selectors on shopping sites change.
* Prices seen in your browser can be personalised (region, login, coupons) and may differ from the server's view.
* DNS-rebinding is not fully covered by the SSRF guard (host is resolved before, not pinned during, the request).
* Not "real-time": checks run on intervals (default 60 min server, 15 min alert sync).
* Single backend instance assumed (locks are in-process).
