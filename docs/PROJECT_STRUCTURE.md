# Project structure

```text
PricePulse/
├── README.md                     Overview, local setup, API, limitations
├── DEPLOYMENT.md                 Step-by-step guide to deploy for public use (start here to go live)
├── run-tests.sh                  Runs every test that needs only JDK 21 + Node
├── docker-compose.yml            Local dev: MongoDB + backend in containers
├── .env.example                  Template for environment variables (copy to .env, never commit .env)
├── .gitignore
│
├── extension/                    THE CHROME EXTENSION (what users install)
│   ├── manifest.json             MV3 manifest, permissions
│   ├── popup/                    Popup UI: detect product, set target, track, remove
│   │   ├── popup.html / popup.css / popup.js
│   ├── options/                  Full page: tracked products, price history + chart, settings
│   │   ├── options.html / options.css / options.js
│   ├── background/
│   │   └── service-worker.js     Alarms + notifications (MV3 service worker)
│   ├── content/
│   │   └── extractor.js          Product/price detection, injected on demand
│   ├── charts/
│   │   └── lineChart.js          SVG price-history line chart
│   ├── shared/
│   │   ├── pricecore.js          Price parsing + validation (mirrors Java)
│   │   ├── api.js                Backend client: timeout, retry with backoff
│   │   ├── storage.js            Settings, install id
│   │   └── format.js             Formatting + safe DOM helper
│   ├── styles/base.css           Shared design tokens, buttons, cards
│   └── assets/                   Icons 16/32/48/128
│
├── backend/                      JAVA 21 + SPRING BOOT API
│   ├── pom.xml
│   ├── Dockerfile                Multi-stage build -> small runtime image
│   └── src/
│       ├── main/java/com/pricepulse/
│       │   ├── PricePulseApplication.java
│       │   ├── web/              Controllers, exception handler, client-id check, rate limit filter, CORS
│       │   ├── service/          ProductService, price checks, scheduler, page fetcher/parser
│       │   ├── repo/             Spring Data MongoDB repositories
│       │   ├── model/            MongoDB documents (tracked_products, price_history) + enums
│       │   ├── dto/              Request/response objects (persistence objects are never exposed)
│       │   ├── config/           Executor pool, Mongo config, typed properties
│       │   └── util/             Framework-free logic: parser, validator, price change, URL rules, SSRF guard, rate limiter
│       ├── main/resources/application.yml   Config (values come from environment variables)
│       └── test/java/.../CoreLogicSelfTest.java
│
├── deploy/                       PRODUCTION FILES
│   ├── docker-compose.prod.yml   Caddy (HTTPS) + backend + MongoDB on one server
│   └── Caddyfile
│
├── scripts/
│   └── package-extension.sh      Builds the Chrome Web Store zip pointed at your production API
│
├── docs/
│   ├── PROJECT_STRUCTURE.md      This file
│   ├── PRIVACY_POLICY.md         Template you must publish before Web Store submission
│   ├── CONCEPT_MAPPING.md        Syllabus concept -> code
│   └── TEST_REPORT.md            What was run vs not run
│
├── academic/                     SYLLABUS MODULES (not part of production)
│   ├── networking/               TCP, UDP, URL, InetAddress demos
│   ├── concurrency/              Threads, lifecycle, sync, wait/notify, executors
│   ├── jdbc/                     JDBC + DAO (PostgreSQL)
│   ├── servlets/                 Servlets, filter, listener, session, JSP/JSTL MVC (WAR for Tomcat 10.1)
│   └── rest-client/              Java HTTP client + Jackson
│
├── database/                     SQL for the academic JDBC module only (production uses MongoDB)
└── tests/                        Node tests for the extension logic
```
