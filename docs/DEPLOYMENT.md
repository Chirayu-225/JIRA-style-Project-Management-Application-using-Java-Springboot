# Deploying the Issue Tracker for ₹0 (no credit card)

You'll create 4 free accounts: **GitHub**, **Neon**, **Render**, **Vercel**. Sign in to the last three *with GitHub*, which makes it quicker.
Total time: about 30–45 minutes the first time.

```
Browser ──► Vercel (React site) ──► Render (Spring Boot API, Docker) ──► Neon (PostgreSQL)
```

> Free-tier facts to know (they can change, so check each site's pricing page):
> - **Render free** web services **sleep after ~15 min with no traffic**; the next visit takes ~30–60 s to wake.
>   The app shows a "Waking up the free-tier server…" banner so it doesn't look broken.
> - **Neon free** gives a small serverless Postgres that also auto-suspends when idle (wakes in ~1 s).
> - **Vercel Hobby** is free for personal, non-commercial projects.

---

## Step 1: Put the code on GitHub

```bash
cd issue-tracker
git init
git add .
git commit -m "Issue Tracker: initial commit"
```

On github.com: **New repository** → name it `issue-tracker` → **Public** (recruiters can read it) → Create. Then:

```bash
git remote add origin https://github.com/<your-username>/issue-tracker.git
git branch -M main
git push -u origin main
```

Open the **Actions** tab on GitHub: the **CI** workflow compiles and tests everything. Wait for the green ✓.
If it's red, click it to read the error (see Troubleshooting).

## Step 2: Create the database on Neon

1. Go to **neon.tech** → Sign up with GitHub → **Create project**.
   - Name: `issue-tracker` · Postgres version: **16** · Region: **AWS Asia Pacific (Singapore)** (closest to India)
2. On the dashboard click **Connect**. You'll see a connection string like:
   ```
   postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
3. Split it into the 3 values Spring Boot needs (keep this tab open):

   | Variable | Value (from the example above) |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | `neondb_owner` |
   | `DB_PASSWORD` | `AbC123xyz` |

   Notice: replace `postgresql://user:password@` with `jdbc:postgresql://` and move the user and password out.

You don't need to create any tables: **Flyway creates them automatically** on the API's first start.

## Step 3: Deploy the API on Render

1. Go to **render.com** → Sign up with GitHub.
2. **New +** → **Blueprint** → connect your `issue-tracker` repo. Render finds `render.yaml` and proposes a service called `issue-tracker-api` (plan: Free).
3. It asks for the values marked `sync: false`:
   - `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD` → from Step 2
   - `CORS_ALLOWED_ORIGINS` → for now type `http://localhost:5173` (you'll update it in Step 5)
   - `JWT_SECRET` is generated for you automatically.
4. Click **Apply**. The first Docker build takes ~5–8 minutes. Watch the **Logs** tab and wait for `Started IssueTrackerApplication`.
5. Copy your API URL, e.g. `https://issue-tracker-api-xxxx.onrender.com`, and test it:
   - `https://issue-tracker-api-xxxx.onrender.com/actuator/health` → `{"status":"UP"}`
   - `https://issue-tracker-api-xxxx.onrender.com/swagger-ui.html` → interactive API docs

*(No Blueprint? Use **New + → Web Service** → your repo → Runtime **Docker** → Root directory `backend` →
Instance type **Free** → add the same environment variables by hand, with `JWT_SECRET` = any random 48+ character string.)*

## Step 4: Deploy the frontend on Vercel

1. Go to **vercel.com** → Sign up with GitHub → **Add New… → Project** → import `issue-tracker`.
2. Settings:
   - **Root Directory:** `frontend` (click Edit and pick the folder)
   - Framework preset: **Vite** (auto-detected)
   - **Environment Variables:** `VITE_API_URL` = your Render URL from Step 3 (no trailing slash)
3. **Deploy**. You get a URL like `https://issue-tracker-yourname.vercel.app`.

## Step 5: Allow the frontend to call the API (CORS)

Back on Render → `issue-tracker-api` → **Environment** → edit `CORS_ALLOWED_ORIGINS`:

```
https://issue-tracker-yourname.vercel.app,https://*.vercel.app,http://localhost:5173
```

Save; Render redeploys automatically. (`https://*.vercel.app` also allows Vercel's preview deployments.)

## Step 6: Try it

Open your Vercel URL → **Explore the demo workspace**. Open it in two browser windows, drag a card in one, and watch the other update live.

Put the URL in your README, resume and LinkedIn.

---

## Updating the app later

Just `git push`. GitHub Actions runs the tests, Render rebuilds the API, and Vercel rebuilds the site, all automatically.
Need a DB change? Add a new file `backend/src/main/resources/db/migration/V2__describe_change.sql`. Never edit V1 after it has run.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Browser console: *blocked by CORS policy* | `CORS_ALLOWED_ORIGINS` on Render must exactly match your Vercel URL (https, no trailing slash). |
| Login does nothing / "Cannot reach the server" | Check `VITE_API_URL` on Vercel, then **redeploy** (Vite bakes env vars in at build time). |
| Render logs: `Connection refused` / `password authentication failed` | Re-check the 3 DB variables. URL must start with `jdbc:postgresql://` and end with `?sslmode=require`. |
| Render logs: `JWT_SECRET must be at least 32 characters` | Set a longer secret. |
| Render: *Out of memory* | The Dockerfile already caps the heap at 75 % of 512 MB. Keep `DB_POOL_SIZE` at 5. |
| First load takes ~1 min | Normal on the free tier (server waking up). Optional: a free uptime pinger (e.g. UptimeRobot) hitting `/actuator/health` every 10 min keeps it awake, but check Render's current fair-use rules first. |
| CI red on `backend` job | Open the log and find the first `[ERROR]` line, which names the file and line. Paste it to me and we'll fix it. |
