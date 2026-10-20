# Deployment guide

## Target architecture

- The React/Vite site is published to GitHub Pages at `https://kumarniket.github.io/eVisitingCard/`.
- The Java 21 API runs as a Docker web service on Render.
- GitHub Actions builds the frontend and backend from `main`; the backend workflow publishes a Linux AMD64 image to GitHub Container Registry (GHCR), then calls the Render deploy hook.
- Firebase Authentication remains in the browser. Firestore and Firebase Admin token verification remain server-side. Google Drive remains server-side and stores private logo files.

This guide assumes a public repository for the free GitHub Pages plan. Before making the repository public, review the full Git history and verify that no credentials or private data were ever committed. Frontend Firebase web configuration is public by design; service-account keys, OAuth refresh tokens, and deploy hooks are not.

The chosen repository name is `eVisitingCard`, so its project Pages URL is `/eVisitingCard/`. The account homepage `https://kumarniket.github.io/` is not redirected or changed. GitHub Pages is being used here as a non-commercial demo host; review GitHub's current Pages terms before using it for a business-facing production service.

## 1. Prepare GitHub

1. In **Settings → General → Danger Zone**, make the repository public if the account plan does not support Pages for private repositories. Keep backend credentials in Render secret files and Actions secrets; never add them to the repository.
2. In **Settings → Pages**, set the build/deployment source to **GitHub Actions**.
3. In **Settings → Secrets and variables → Actions → Variables**, add the four Firebase web values:
   - `VITE_FIREBASE_API_KEY`
   - `VITE_FIREBASE_AUTH_DOMAIN`
   - `VITE_FIREBASE_PROJECT_ID`
   - `VITE_FIREBASE_APP_ID`
4. Add the Render deploy hook as the Actions **secret** `RENDER_DEPLOY_HOOK_URL` after creating the Render service.
5. Merge deployment changes into `main` only after reviewing the pull request. Deploy workflows intentionally publish production from `main`, not feature branches.

Firebase web values are embedded in the downloaded JavaScript bundle and are not secrets. Firebase Auth rules, authorized domains, and backend authorization must still be configured correctly.

The Render API URL is not known until the backend image has been published and the Render service created. On the first `main` run, the Pages workflow will build but deliberately skip publishing Pages until `VITE_API_BASE_URL` is set. The backend workflow can publish the image independently.

## 2. Publish the backend image and create the Render service

The first merge to `main` builds and pushes `ghcr.io/kumarniket/evisitingcard-backend` with `main` and commit-SHA tags. Until the Render deploy-hook secret is configured, the workflow leaves the image in GHCR and emits a warning.

1. After the first backend image is published, open the repository owner's **Packages** page and confirm the package name `evisitingcard-backend`. Keep it private unless you explicitly want to publish the backend image.
2. Create a GitHub personal access token (classic) with **read:packages** only. Add it to Render under **Workspace Settings → Container Registry Credentials**, using GitHub username `kumarniket`.
3. In Render, create a **Web Service → Existing Image**:
   - Image: `ghcr.io/kumarniket/evisitingcard-backend:main`
   - Registry credential: the GHCR credential just created
   - Health check path: `/actuator/health`
   - Do not configure a Render build command; GitHub Actions builds the image.
4. Create the service and note its HTTPS origin, e.g. `https://ecardmitra-api.onrender.com`. Set this as the repository Actions variable `VITE_API_BASE_URL` with no trailing slash.
5. In the Render service **Settings**, copy its deploy hook URL and add it to GitHub as `RENDER_DEPLOY_HOOK_URL`.
6. Manually run **Actions → Deploy frontend to GitHub Pages → Run workflow** on `main` after setting the API URL. Subsequent merges to `main` deploy automatically.

The Render image service must use the same GHCR image repository as the workflow. The workflow passes the commit-SHA image tag to the deploy hook, so each Render deployment uses the exact image built from that commit.

## 3. Configure Render environment and secret files

Set the following environment variables in the Render service:

| Variable | Value |
| --- | --- |
| `FIREBASE_ENABLED` | `true` |
| `GOOGLE_CLOUD_PROJECT` | Firebase/GCP project ID, e.g. `e-cardmitra` |
| `GOOGLE_APPLICATION_CREDENTIALS` | `/etc/secrets/firebase-service-account.json` |
| `DRIVE_ENABLED` | `true` |
| `GOOGLE_DRIVE_CREDENTIALS` | `/etc/secrets/google-drive-user-adc.json` |
| `DRIVE_LOGO_FOLDER_ID` | ID of the administrator-owned Drive logo folder |
| `CORS_ALLOWED_ORIGINS` | `https://kumarniket.github.io` |

Render supplies `PORT`; Spring Boot reads it automatically. CORS values are **origins only**: no `/eVisitingCard/` path and no trailing slash.

Add both credential files through Render **Secret Files**, not environment text, Docker build arguments, or GitHub:

- `firebase-service-account.json`: a dedicated, least-privilege Firebase/GCP service identity with the Firestore permissions the backend needs and Firebase Admin access required for token verification.
- `google-drive-user-adc.json`: the authorized-user OAuth credential for the Google account that can edit the My Drive logo folder. The Drive OAuth client/consent screen must be owned by this project.

Do not use the local desktop application's files as committed files or Docker build inputs. The local OAuth app is currently in **Testing**. Testing-mode refresh tokens for sensitive scopes can expire after seven days. Before production, complete Google's OAuth publishing/verification requirements for the Drive scope, then create and test a production refresh credential. Keep the credential in Render Secret Files and plan a secure rotation procedure. A Firebase service account cannot upload into a personal My Drive because it has no personal storage quota.

After setting the environment and secret files, deploy the service and verify `https://<render-service>.onrender.com/actuator/health` returns HTTP 200. Health does not prove that Firestore or Drive credentials work; perform the authenticated and logo acceptance checks before launch.

## 4. Configure GitHub Pages, Firebase Auth, and the API

The Pages workflow builds from `frontend/` with `VITE_BASE_PATH=/eVisitingCard/`. It validates the four Firebase Actions variables, writes output to `frontend/dist`, and deploys only when `VITE_API_BASE_URL` is configured.

1. Confirm the Pages workflow is enabled and its first deployment succeeds.
2. In Firebase Console → **Authentication → Settings → Authorized domains**, add `kumarniket.github.io`. Do not include the `/eVisitingCard` path.
3. Confirm Google sign-in is enabled for the Firebase Authentication project and that the Firebase web app values in the Actions variables match the same project used by the backend.
4. Confirm `VITE_API_BASE_URL` is the HTTPS Render API origin with no path or trailing slash. The frontend resolves API-served logo URLs against this API origin.
5. Confirm Render `CORS_ALLOWED_ORIGINS` is exactly `https://kumarniket.github.io`. Browser origin excludes the Pages path.

The frontend has a GitHub Pages `404.html` route fallback and a matching React Router basename, so direct links such as `/eVisitingCard/shops/nk-shop` can load. The vendor's **Copy shop link** and **Preview card** include the repository path.

## 5. Firestore and rules

Firestore remains private behind the Spring API. The browser must not read Firestore directly. Deploy the rules and indexes to the intended project from the repository root:

```powershell
firebase use e-cardmitra
firebase deploy --only firestore:rules,firestore:indexes
```

The `firebase.json` Hosting target is `frontend/dist` for anyone who intentionally uses Firebase Hosting instead. The GitHub Pages workflow does not deploy to Firebase Hosting.

## Local development after the folder split

- Frontend: `Copy-Item frontend/.env.example frontend/.env.local`, configure the public Firebase values, then run `npm ci` and `npm run dev` from `frontend/`.
- Backend: from the repository root run `mvn -f backend\pom.xml spring-boot:run`.
- Docker Compose remains rooted at the repository root. Copy the root `.env.example` to the ignored root `.env.local`, set local Firebase web values, and pass `--env-file .env.local`; enable the Firebase and Drive overlays only after setting their local credential paths.
- The Vite development server proxies `/api` to `http://localhost:8080`. Compose serves both containers through `http://localhost:3000`.

## CORS, API URL, and secret handling

- `VITE_API_BASE_URL` is a build-time, public URL. It is a GitHub Actions **variable**, not a secret.
- `CORS_ALLOWED_ORIGINS` is a backend runtime setting containing exact origins. Never use `*` for this API.
- Firebase web configuration is public; Firebase ID tokens are sent to the API at request time.
- Service-account JSON, Google Drive OAuth refresh credentials, GHCR read tokens, and Render deploy-hook URLs are private. Keep them in Render Secret Files/credentials or GitHub Actions secrets, as appropriate.
- Never put backend secrets in `frontend/.env*`, build arguments, `VITE_*` variables, image labels, or logs.
- Image changes are deployed to Render only from `main`; pull requests build the backend image without publishing or deploying it.

## Release acceptance checks

1. `https://<render-service>.onrender.com/actuator/health` returns HTTP 200.
2. Pages loads at `https://kumarniket.github.io/eVisitingCard/`, and a direct shop URL survives refresh.
3. Google sign-in succeeds from `kumarniket.github.io`; vendor and admin authorization remain enforced by the backend.
4. Public shop list/detail and authenticated vendor/admin API calls succeed without browser CORS errors.
5. Firestore reads/writes succeed through the API; direct browser Firestore access remains denied.
6. Upload, replace, retrieve, and remove a Drive logo. Confirm replacement cleanup leaves only the active logo file for that shop.
7. Test one PR build and one `main` deployment. Confirm the Render image tag matches the commit SHA and keep a previous GHCR image tag for rollback.
