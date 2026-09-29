# Test report

Environment: Linux sandbox, JDK 21.0.10, Node 22. **No Maven, MongoDB, PostgreSQL, Tomcat or Chrome**, and Maven Central was not reachable, so nothing that needs those could be run. `./run-tests.sh` reproduces every PASS below.

## Executed

| Area | Result | How |
|---|---|---|
| Price parsing (₹1,299 / $29.99 / €49,99 / 1.299,50 € / lakh format / text noise), Java and JS | PASS | same case table in `CoreLogicSelfTest` and `tests/core.test.mjs` |
| Price validation (zero, negative, NaN, Infinity, exponent, 3 decimals, JPY decimals, huge, bad currency) | PASS | Java 63/63 checks; JS |
| Price change (3,499 -> 3,199 = -300.00 / -8.57%; 2,999 -> 3,499 = +500.00 / +16.67%; unchanged; first observation) | PASS | Java |
| URL normalisation and rejection (tracking params, fragment, default port, Amazon /dp/ASIN, ftp, javascript:, credentials) | PASS | Java, JS |
| SSRF address classification (loopback, 10/8, 192.168/16, 169.254.169.254, CGNAT, IPv6 ULA blocked; public allowed) | PASS | Java |
| Product detection: JSON-LD, @graph, malformed JSON-LD, meta tags, DOM heuristics ignoring hidden/struck prices, Amazon adapter, no-price page, price without currency, zero price | PASS | jsdom fixtures (synthetic pages, not live sites) |
| Extension API client: headers, retry with backoff on 5xx, bounded retries, POST not retried, 409/422 mapping, 404 lookup, 204, offline, timeout | PASS | local HTTP server |
| Chart data: sorting, min/max, trend, flat series, invalid points dropped | PASS | Node (SVG rendering itself not exercised) |
| TCP, UDP, URL, InetAddress demos | PASS | ran, exit 0 |
| Thread lifecycle (all six states observed), synchronization (unsynchronized lost updates: 1,373,336 of 2,000,000 in one run; synchronized/atomic exact), wait/notify (capacity never exceeded), executor/concurrency utilities | PASS | ran, exit 0 |
| JDBC module | PASS (compiles) | `javac`; not run |
| Rate limiter (per-key limits, independent keys, window reset, retry-after) | PASS | Java, fake clock (7 checks; total now 63) |
| Production packaging script (rewrites host permissions, removes optional hosts, sets backend URL, 27-file zip) | PASS | ran `scripts/package-extension.sh` and inspected the output |
| manifest.json references, JS syntax of every extension file | PASS | script |

## Not executed (written, unverified)

| Area | Status |
|---|---|
| Spring Boot startup, MongoDB connection and indexes, REST endpoints, validation responses, scheduler, concurrent price checks, duplicate handling under load | **NOT RUN.** Only a syntax-level check found no parse errors; type-checking against Spring was impossible. |
| ProductPageParser (jsoup + Jackson) and PageFetcher against real pages | NOT RUN |
| JDBC demos (Statement, PreparedStatement, CallableStatement, metadata, transactions, batch) | NOT RUN (needs PostgreSQL) |
| Servlets, filter, listener, cookies, session, dispatcher, JSP/JSTL/EL, MVC | NOT RUN (needs Tomcat 10.1; JSP untested) |
| Java REST client | NOT RUN |
| Popup, options page, service worker, alarms, notifications, chart rendering in a real browser; installation; browser/laptop restart behaviour; extension reload | **NOT RUN** (no Chrome) |
| Real product page end-to-end flow; live-site adapters | NOT RUN |
| Console errors / UI polish pass | NOT DONE (no browser to look at) |

Also not executed: `RateLimitFilter`, `Dockerfile`, both compose files and the Caddyfile (no Docker here), and every step of `DEPLOYMENT.md` on a real host.

## Final checklist

Items from the build brief that are **not** yet verified: real product detection on live sites, MongoDB persistence, notifications, scheduler, restart behaviour, Java modules needing external runtimes, REST integration. Treat the project as "implemented, partially tested" until you have run the steps in the README and the items above are checked off.
