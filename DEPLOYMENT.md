# PricePulse – Deployment Guide (public release)

This guide takes you from the project folder to a live service that anyone can use:

```text
Users' Chrome  ──HTTPS──▶  https://api.yourdomain.com  ──▶  Spring Boot backend  ──▶  MongoDB
      ▲                                                          │
      └── installed from the Chrome Web Store                    └── scheduler checks prices 24/7
```

Two things go live: **(A) the backend + database** on a server, and **(B) the extension** on the Chrome Web Store.
Project layout is in `docs/PROJECT_STRUCTURE.md`.

---

## 0. Read this first

**Honest status.** The backend was written but never started in the environment where this project was built (no Maven, MongoDB or Chrome there; see `docs/TEST_REPORT.md`). Do **not** publish before you finish Step 1 and Step 6 on your own machine. Expect to fix small issues.

**What "anyone can use it" means for this code:**

| Topic | Current state | What you should do |
|---|---|---|
| Identity | Each install gets a random ID. There are **no accounts**. Data is per install; a new device or reinstall starts empty. | Fine for a v1 launch. Say so in the store listing. Add accounts later if you want sync across devices. |
| Abuse | The server fetches URLs users submit. Protections: private-address blocking (SSRF guard), rate limits (120 req/min per install, 300/min per IP), 100 products per install. | Keep the limits. Watch logs after launch. |
| Always-on | The scheduler only runs while the backend runs. | Use an always-on host (not one that sleeps when idle). |
| Privacy | The extension sends product URLs, names and prices to your server. | Publish a privacy policy (template: `docs/PRIVACY_POLICY.md`). Required by the Web Store. |
| Data retention | Removing a product deletes its data. **There is no automatic purge of inactive installs.** | Either implement a cleanup job or remove the "inactive data is deleted after N months" sentence from your policy. Do not promise what the code doesn't do. |
| Price detection | Works on many pages with structured data. Some big sites block server fetches or render prices with JavaScript. | Tell users honestly (listing text below). |
| Cost | Hosting, MongoDB and a domain cost money. Free tiers change often. | Check current pricing pages before choosing. |

**Accounts and tools you need**

- A computer with JDK 21, Maven 3.9+, Docker Desktop (recommended), Git, Node 20+, Chrome.
- A GitHub account.
- A domain name (for example `pricepulse.app`) and access to its DNS settings.
- A hosting account: Render (Option A, easiest) **or** a VPS such as any Ubuntu 24.04 server (Option B, cheapest and most control).
- A MongoDB Atlas account (Option A) or nothing extra (Option B runs MongoDB itself).
- A Google account for the Chrome Web Store developer registration (one-time registration fee; confirm the amount on the dashboard).

---

## Step 1 – Run everything locally and prove it works

1. Open a terminal in the project folder (the one containing `README.md`).
2. Run the tests that need no infrastructure:
   ```bash
   ./run-tests.sh
   ```
   You should see `RESULT: 63 passed, 0 failed` and `# fail 0` for Node.
3. Start MongoDB + backend in containers:
   ```bash
   cp .env.example .env          # optional for local; defaults work
   docker compose up --build
   ```
   The first build downloads Maven dependencies (a few minutes). **If the build fails, this is where remaining code issues show up.** Read the error, fix, rebuild. (Or, without Docker: run `mongod`, then `cd backend && mvn spring-boot:run`.)
4. Smoke test the API (new terminal):
   ```bash
   curl -s http://localhost:8080/api/health
   # {"status":"UP","time":"..."}

   ID=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
   curl -s -X POST http://localhost:8080/api/products \
     -H "Content-Type: application/json" -H "X-Client-Id: $ID" \
     -d '{"url":"https://example.com/p/1?utm_source=x","name":"Test","price":3499,"currency":"INR","targetPrice":2999}'
   curl -s http://localhost:8080/api/products -H "X-Client-Id: $ID"
   # same POST again -> HTTP 409 DUPLICATE_TRACKING
   # price 0 or -5 -> HTTP 400/422 with a clear message
   ```
5. Load the extension: `chrome://extensions` → **Developer mode** → **Load unpacked** → select the `extension/` folder → pin PricePulse.
6. Open a real product page that shows its price → click PricePulse → confirm the detected price → enter a target → **Track Price**. Open **Tracked products** (menu icon) and **Price history**.
7. Force a server check: in Tracked products click **Check now**. On a page with structured price data you will see the price update; on others you'll see a clear "could not read a price" message (expected, see Step 0).

Only continue when steps 3–6 work.

---

## Step 2 – Put the code on GitHub

```bash
git init
git add .
git commit -m "PricePulse v1.0.0"
# create an empty PRIVATE repository on github.com, then:
git remote add origin https://github.com/<you>/pricepulse.git
git branch -M main
git push -u origin main
```

Check that `.env` is **not** in the commit (`.gitignore` excludes it). If you ever commit a secret by mistake, rotate that secret immediately.

---

## Step 3 – Choose a hosting path

| | **Option A: Render + MongoDB Atlas** | **Option B: One VPS (Docker + Caddy)** |
|---|---|---|
| Effort | Lowest (clicks) | Medium (a terminal session) |
| HTTPS | Automatic | Automatic (Caddy) |
| Database | Managed, backups included (Atlas) | Runs on your server, you back it up |
| Always-on | Needs a paid always-on instance type | Yes |
| Network allow-list for DB | Hard (changing IPs) | Easy |

Pick one and follow only that part.

---

## Step 4A – Deploy with Render + MongoDB Atlas

### 4A.1 Create the database (MongoDB Atlas)

1. Sign in at mongodb.com/atlas → **Create** a cluster (the smallest/free shared tier is fine to start; check current limits).
2. **Database Access** → **Add New Database User** → username `pricepulse`, generate a long random password, role **Read and write to any database** (or restrict to the `pricepulse` database). Save the password in a password manager.
3. **Network Access** → **Add IP Address**.
   - Render's outgoing IPs are not fixed on basic plans, so the common approach is `0.0.0.0/0` (anywhere). This is only acceptable because the database still requires the strong password and TLS. For a fixed-IP setup, use Option B.
4. **Connect** → **Drivers** → copy the connection string. It looks like:
   `mongodb+srv://pricepulse:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority`
   Put the password in and add the database name before the `?`:
   `mongodb+srv://pricepulse:PASSWORD@cluster0.xxxxx.mongodb.net/pricepulse?retryWrites=true&w=majority`
   (URL-encode special characters in the password, e.g. `@` → `%40`.)

### 4A.2 Create the web service (Render)

1. Sign in at render.com → **New** → **Web Service** → connect your GitHub repository.
2. Settings:
   - **Language/Runtime:** Docker
   - **Root directory:** `backend`  (so `backend/Dockerfile` is used)
   - **Instance type:** choose an **always-on paid** type. Free/idle types spin down when unused, which stops the price scheduler and makes the first request slow.
   - **Health check path:** `/api/health`
3. **Environment variables** (Environment tab):

   | Key | Value |
   |---|---|
   | `MONGODB_URI` | the Atlas string from 4A.1 |
   | `PRICEPULSE_CORS_ORIGINS` | `chrome-extension://*` for now (tightened in Step 8) |
   | `PRICEPULSE_CHECK_INTERVAL_MINUTES` | `60` |

   Do not set `PORT` (Render sets it; the app reads it).
4. **Create Web Service.** Wait for the build to finish. Open `https://<name>.onrender.com/api/health`; you should see `{"status":"UP",...}`.

### 4A.3 Use your own domain

1. Render → your service → **Settings** → **Custom Domains** → add `api.yourdomain.com`.
2. At your DNS provider add the record Render shows (a `CNAME` from `api` to your `onrender.com` host).
3. Wait until Render shows the certificate as issued. Test `https://api.yourdomain.com/api/health`.

Continue at **Step 5**.

---

## Step 4B – Deploy on a single VPS (Docker Compose + Caddy)

Runs three containers: Caddy (HTTPS reverse proxy, the only public one), the backend, and MongoDB (private, with a password).

### 4B.1 Prepare the server

1. Create an Ubuntu 24.04 server at any provider (1 vCPU / 2 GB RAM is a reasonable start). Note its public IP.
2. In your DNS panel create an **A record**: `api.yourdomain.com` → the server IP. Wait for it to resolve: `dig +short api.yourdomain.com`.
3. Connect and secure the machine:
   ```bash
   ssh root@SERVER_IP
   apt update && apt -y upgrade
   adduser deploy && usermod -aG sudo deploy
   ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw --force enable
   ```
   Log in as `deploy` from now on (`ssh deploy@SERVER_IP`), preferably with SSH keys and password login disabled.
4. Install Docker:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker deploy && newgrp docker
   docker --version && docker compose version
   ```

### 4B.2 Get the code onto the server

```bash
git clone https://github.com/<you>/pricepulse.git
cd pricepulse
cp .env.example .env
nano .env
```

Set in `.env`:

```bash
MONGO_USER=pricepulse
MONGO_PASSWORD=<long random string>          # e.g. output of: openssl rand -base64 32
PRICEPULSE_CORS_ORIGINS=chrome-extension://*  # tightened in Step 8
PRICEPULSE_CHECK_INTERVAL_MINUTES=60
DOMAIN=api.yourdomain.com
```

Protect it: `chmod 600 .env`.

### 4B.3 Start

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build
docker compose -f deploy/docker-compose.prod.yml ps
docker compose -f deploy/docker-compose.prod.yml logs -f backend     # Ctrl+C to stop following
```

Caddy obtains the HTTPS certificate automatically once DNS points at the server and ports 80/443 are open. Test from your computer: `curl https://api.yourdomain.com/api/health`.

### 4B.4 Backups (do this now, not later)

```bash
mkdir -p ~/backups
docker compose -f deploy/docker-compose.prod.yml exec -T mongo \
  mongodump -u "$MONGO_USER" -p "$MONGO_PASSWORD" --authenticationDatabase admin --db pricepulse --archive --gzip \
  > ~/backups/pricepulse-$(date +%F).gz
```

Automate it with `crontab -e` (daily), copy the files off the server (object storage or another machine), and **test a restore** with `mongorestore --archive --gzip < file.gz` on a scratch instance.

### 4B.5 Updating later

```bash
cd ~/pricepulse && git pull
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build
```

Continue at **Step 5**.

---

## Step 5 – Verify production

From your own computer (replace the domain):

```bash
curl -s https://api.yourdomain.com/api/health

ID=$(uuidgen)   # any random 16-64 char id
curl -s -i -X POST https://api.yourdomain.com/api/products \
  -H "Content-Type: application/json" -H "X-Client-Id: $ID" \
  -d '{"url":"https://example.com/p/1","name":"Prod test","price":100,"currency":"USD","targetPrice":90}'
curl -s -i https://api.yourdomain.com/api/products                     # 401: no X-Client-Id
```

Checklist:

- [ ] `https://` works and `http://` redirects (Caddy/Render do this).
- [ ] Requests without `X-Client-Id` return 401; invalid prices return validation errors.
- [ ] Rate limit works: send more than 300 requests in a minute from one IP → HTTP 429 with `Retry-After`.
- [ ] Logs show no stack traces returned to clients.
- [ ] After one scheduler interval, `docker compose ... logs backend` (or Render logs) shows "Price check batch finished".
- [ ] Delete your test product: `curl -X DELETE https://api.yourdomain.com/api/products/<id> -H "X-Client-Id: $ID"`.

---

## Step 6 – Build the production extension and test it

1. Build the store package (points the extension at your API, removes `localhost` and the broad optional host permissions, hides the "Server" setting):
   ```bash
   scripts/package-extension.sh https://api.yourdomain.com
   ```
   Output: `dist/pricepulse-extension-v1.0.0.zip`.
2. Test that exact build before uploading: unzip it to a folder, then `chrome://extensions` → **Load unpacked** on that folder (remove the dev copy first).
3. Track a real product; confirm it appears in your MongoDB (Atlas → Browse Collections, or `mongosh` on the VPS) in `tracked_products` and `price_history`.
4. Test notifications: **Settings → Send test notification**. If nothing shows, allow Chrome notifications in your OS settings.
5. Test offline behaviour: stop the backend and open the popup. You should see *PricePulse server is currently unavailable.* Start it again.
6. Restart Chrome and confirm tracked products are still there.

Increase the `version` in `extension/manifest.json` for every new upload (Web Store rejects a repeated version).

---

## Step 7 – Publish on the Chrome Web Store

Dashboard wording changes over time; follow the current labels on the dashboard and the "Publish in the Chrome Web Store" documentation if something differs.

### 7.1 Prepare assets and pages

| Item | Requirement / suggestion |
|---|---|
| Extension zip | `dist/pricepulse-extension-v1.0.0.zip` from Step 6 |
| Icon | 128×128 (already in the package) |
| Screenshots | At least one, 1280×800 or 640×400 recommended; capture the popup on a product page, the tracked list, and the history chart |
| Small promo tile | 440×280 (optional but improves the listing) |
| Privacy policy | Fill in `docs/PRIVACY_POLICY.md`, host it at a public HTTPS URL (GitHub Pages works: enable Pages on a repo and publish the file as a page) |
| Support contact | An email you will read |

### 7.2 Register and create the item

1. Go to the Chrome Web Store Developer Dashboard and sign in with your Google account.
2. Pay the one-time registration fee and complete account verification if asked.
3. **Add new item** → upload the zip.

### 7.3 Fill in the listing

**Name:** PricePulse – Price Tracker

**Short description (≤132 chars):** Track product prices, see price history and get alerts when a price reaches your target.

**Detailed description (honest, no exaggeration):**
> PricePulse tracks the price of products you choose. Open a product page, click PricePulse, set a target price and start tracking. You get a price-history chart, increase/decrease details and browser notifications when the price reaches your target.
>
> How it works: the extension reads the price on the page when you open it. Our server also re-checks tracked pages periodically. Some websites block automated checks or show prices only in your browser; for those, the price updates when you visit the page. PricePulse does not work on every website and does not guarantee a price.
>
> Notifications appear while Chrome is running. Data is tied to this browser install; there is no account.

**Category:** Shopping. **Language:** English.

### 7.4 "Privacy practices" tab

- **Single purpose:** *Track prices of products the user chooses and notify them about price changes.*
- **Permission justifications:**

  | Permission | Justification to enter |
  |---|---|
  | `storage` | Saves settings and the anonymous install ID locally. |
  | `alarms` | Periodically syncs price alerts while the browser is running. |
  | `notifications` | Shows price-drop and target-price alerts. |
  | `activeTab` | Reads the product name and price from the current page only when the user opens the popup. |
  | `scripting` | Runs the price-detection script on the active tab after the user opens the popup. |
  | Host permission (your API domain) | Communicates with the PricePulse server that stores tracked products and price history. |

- **Data usage disclosure:** tick the categories that apply. For this extension: *Website content* (product page URL, name, price) is transmitted to the server. Do **not** tick categories you don't collect. Certify that data is not sold, not used for unrelated purposes, and not used for creditworthiness/lending.
- **Remote code:** answer that you do **not** use remote code (all code is in the package).
- Add your **privacy policy URL**.

### 7.5 Distribution and submit

1. **Visibility:** choose **Unlisted** first (only people with the link can install) to test the real install flow with a few users, then switch to **Public** later.
2. **Regions:** all regions, or restrict if your policy requires.
3. **Submit for review.** Review time varies from hours to days or longer for new developers. If rejected, the email lists the reason; the usual causes are missing privacy policy, unclear single purpose, or permissions not justified. Fix and resubmit with a higher version number.
4. When approved, note the **extension ID** (in the dashboard/URL). You need it in Step 8.

---

## Step 8 – Lock down CORS to your extension ID

1. Set the allowed origin to your published extension only:
   - Render: Environment → `PRICEPULSE_CORS_ORIGINS` = `chrome-extension://<EXTENSION_ID>` → save (redeploys).
   - VPS: edit `.env` → same value → `docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d`.
2. Re-test the installed extension from the Web Store (track, history, notifications).

Note: the extension's requests are permitted by its host permission regardless of CORS; CORS pinning is an extra layer against other sites' scripts calling your API from browsers.

---

## Step 9 – Operate it

**Uptime monitoring.** Add a free uptime monitor (any service) that requests `https://api.yourdomain.com/api/health` every 5 minutes and emails you on failure.

**Logs.** Render: Logs tab. VPS: `docker compose -f deploy/docker-compose.prod.yml logs --since 1h backend`. Watch for repeated `RATE_LIMITED`, `EXTRACTION_FAILED`, and 500s (each 500 has a short reference code in the response you can search in logs).

**Backups.** Atlas: enable its backups if your tier includes them. VPS: the cron job from 4B.4, with off-server copies and a tested restore.

**Releasing an update.**
1. Change code, run `./run-tests.sh`.
2. Deploy the backend first (Render auto-deploys on push; VPS: `git pull` + compose up). Keep the API backward-compatible for old extension versions.
3. Bump `version` in `extension/manifest.json`, run `scripts/package-extension.sh https://api.yourdomain.com`, upload the new zip in the dashboard, submit for review. Users receive updates automatically after approval.

**Capacity guide.** One small instance handles thousands of tracked products because checks run on a bounded pool (4 workers) every minute in batches of 50. If checks fall behind, raise `pool-size` and `batch-size` in `application.yml` and use a larger instance. Do not run two backend instances until you replace the in-memory rate limiter and the per-product locks with shared ones (Redis or database locks).

**Costs to expect:** domain (yearly), server or Render instance (monthly), MongoDB (free tier at first, then paid), Chrome developer registration (one-time). Check each provider's current pricing.

---

## Step 10 – Before you invite a lot of users

- [ ] Decide on a retention rule and either implement the cleanup or edit the privacy policy.
- [ ] Add user accounts if you want data to survive reinstalls or sync across devices. Until then, the install ID in Settings is the only key to a user's data.
- [ ] Monitor the outbound fetch volume: your server requests third-party sites on users' behalf. Some sites may block your server's IP; that is why browser observations exist.
- [ ] Respect target sites' terms. Keep the interval conservative (default 60 minutes).
- [ ] Prepare a support email and a short FAQ ("Why can't PricePulse read my product?").

---

## Alternative: share without the Web Store

For friends or a class: send `dist/pricepulse-extension-v1.0.0.zip` (built for your live API). They unzip it, open `chrome://extensions`, enable **Developer mode**, click **Load unpacked**, and pick the folder. Chrome shows a developer-mode warning on each start and won't auto-update it, so use the Web Store for real users.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `docker compose up --build` fails while compiling | Untested Spring code has an error | Read the first error line, fix that file, rebuild |
| Backend exits at startup: `MongoTimeoutException` / auth failed | Wrong `MONGODB_URI`, password characters not URL-encoded, Atlas network access blocks the host | Recheck URI (`/pricepulse?authSource=admin` for the compose Mongo), encode special characters, allow the host in Atlas |
| `https://api...` certificate error | DNS not pointing at the server yet, or ports 80/443 closed | `dig`, firewall rules, wait a few minutes, check `docker compose logs caddy` |
| Extension says *server is currently unavailable* | Wrong URL in the build, backend down, mixed content | Run `curl https://api.../api/health`; rebuild the package with the right domain |
| Requests fail with 401 `CLIENT_ID_REQUIRED` | Missing/invalid `X-Client-Id` (only for manual curl tests) | Send a 16–64 char id of letters, digits, dashes |
| 429 `RATE_LIMITED` | Too many requests from one IP/install | Wait for `Retry-After`; raise limits in `application.yml` if legitimate |
| CORS errors in a browser console | `PRICEPULSE_CORS_ORIGINS` doesn't include the extension ID | Set `chrome-extension://<ID>` |
| Server check: *No structured price found* | Page renders price with JavaScript or has no structured data | Expected; open the page so the extension records the price |
| Server check: *HTTP 403/503* | Site blocks automated requests | Expected on some sites; rely on browser observations |
| No notifications | Notifications off in Settings, OS/Chrome blocks them, or no pending alert | Use "Send test notification"; check OS notification settings |
| Web Store rejection | Missing/vague privacy policy, unjustified permission, wrong data-use disclosure | Follow Step 7.4 exactly; resubmit with a higher version |
| Render service is slow after idle / scheduler doesn't run | Instance type sleeps when idle | Use an always-on instance type |

---

## Quick reference

```bash
./run-tests.sh                                              # tests that need no infrastructure
docker compose up --build                                   # local dev stack
scripts/package-extension.sh https://api.yourdomain.com     # Web Store zip
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build   # VPS deploy/update
```
