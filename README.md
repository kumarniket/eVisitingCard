# e-CardMitra | Multi-vendor visiting cards

e-CardMitra is a free, mobile-first directory for independent neighborhood businesses. Vendors get a shareable visiting card and product catalogue; visitors can find a shop, call or message it, open directions, download a contact card, or scan its QR code. There are no payments or subscriptions.

## Project layout

- `src/`: React 19, TypeScript, Vite, Tailwind CSS, React Router, Firebase Google sign-in, QR rendering, and the API client.
- `backend/`: Java 21 and Spring Boot REST API, Firebase Admin token verification, Firestore access, role enforcement, validation, OpenAPI, and Google Drive logo integration.
- `firestore.rules`: denies all browser-side Firestore access. Spring uses the Admin SDK.
- `firestore.indexes.json`: composite indexes for common directory and dashboard queries.
- `docs/maintainer-guide.md`: folder map, change recipes, UI screenshots, debugging, and ownership boundaries.
- `docs/`: API contract, Firestore model, deployment setup, and staged verification checklist.

For routine maintenance, start with the [Maintainer Guide](docs/maintainer-guide.md). It shows where to change branding, routes, forms, endpoints, security, data, and logo handling, with mobile UI references.

## Run with Docker Compose

Install and start Docker Desktop, then run these commands from the project root:

```powershell
docker compose up --build
```

Open `http://localhost:3000`. Nginx serves the frontend and forwards `/api/` requests to the backend. The API is available through the same address at `/swagger-ui.html`; Compose waits for the backend health check before starting the frontend. Stop both containers with `Ctrl+C`, or run `docker compose down` in another terminal.

This first Docker setup intentionally starts with Firebase and Google Drive disabled. Firebase web settings can be supplied as Docker Compose build variables. When those settings are in `.env.local`, pass it explicitly with `docker compose --env-file .env.local`; Compose does not load `.env.local` automatically. Firestore access also requires securely passing backend Application Default Credentials; configure that separately before enabling Firebase. Do not copy service-account credentials into an image or commit them to the repository.

### Run Docker Compose with Firestore

The default Compose setup leaves Firebase disabled. To use the existing Firebase project from the backend container:

1. In Google Cloud IAM, give the backend service account the **Cloud Datastore User** role (`roles/datastore.user`) on the Firebase project. Give your developer account **Service Account Token Creator** on that service account. Enable the Firestore and IAM Service Account Credentials APIs if they are not already enabled.
2. Sign in to Google Cloud CLI and create local Application Default Credentials by impersonating the backend service account. Do not create or commit a service-account key:

   ```powershell
   gcloud auth login
   gcloud auth application-default login --impersonate-service-account=ecardmitra-local-backend@e-cardmitra.iam.gserviceaccount.com
   ```

   If your Firebase project uses a different project ID or service account, substitute its ID and service-account email.
3. In the same PowerShell terminal, set the project ID and the path to the ADC file, then start Compose with the Firebase overlay:

   ```powershell
   $env:GOOGLE_CLOUD_PROJECT = "e-cardmitra"
   $env:GOOGLE_APPLICATION_CREDENTIALS = Join-Path $env:APPDATA "gcloud\application_default_credentials.json"
   docker compose --env-file .env.local -f compose.yaml -f compose.firebase.yaml up --build
   ```

   The overlay enables Firebase only for this run and mounts the ADC file read-only inside the backend container. It does not enable Google Drive. `--env-file .env.local` passes the frontend `VITE_FIREBASE_*` settings into the Docker build so browser sign-in is configured. Compose also sets the backend's `APP_CORS_ALLOWED_ORIGINS` to the local web origins, overriding any `app.cors.allowed-origins` value in the mounted local properties file. Keep using the same terminal for the command so Compose can read the backend credential environment variables too.
4. Open `http://localhost:3000`. If Firestore is connected but has no published and approved shop documents, the directory should load with no live listings; an empty collection is not itself an error. Stop with `Ctrl+C`, then run `docker compose down`.

If startup or API calls fail, inspect the backend output: server-side API exceptions now include the underlying exception and stack trace, while the response still contains only the safe public error message. Never paste credential files or access tokens into chat or commit them.

### Enable shop-logo uploads in Docker

Vendor and admin logo uploads use the backend's Google Drive integration, not Firestore storage. The base Compose setup deliberately keeps Drive disabled. The Firebase service account cannot own uploaded files because service accounts have no personal Drive storage quota. Use OAuth credentials for the administrator's Google account so uploads use that account's Drive storage:

1. In Google Cloud Console, create an OAuth client ID of type **Desktop app** for the project and download its client JSON to a private location outside the repository, for example `%APPDATA%\gcloud\drive-oauth-client.json`. If the OAuth consent screen is in Testing mode, add the administrator Google account as a test user.
2. In the same PowerShell window, back up the existing Firebase ADC file, sign in to Google Drive with the administrator account using that OAuth client, save its Drive-scoped ADC separately, and restore the original Firebase ADC file:

   ```powershell
   $adcPath = Join-Path $env:APPDATA "gcloud\application_default_credentials.json"
   $backupPath = Join-Path $env:APPDATA "gcloud\application_default_credentials.firebase-backup.json"
   $drivePath = Join-Path $env:APPDATA "gcloud\application_default_credentials.drive.json"
   $oauthClientPath = Join-Path $env:APPDATA "gcloud\drive-oauth-client.json"
   Copy-Item $adcPath $backupPath
   gcloud auth application-default login --client-id-file=$oauthClientPath --scopes=https://www.googleapis.com/auth/drive
   if ($LASTEXITCODE -ne 0) {
     Copy-Item $backupPath $adcPath
     throw "Google Drive sign-in failed; restored Firebase credentials."
   }
   Copy-Item $adcPath $drivePath
   Copy-Item $backupPath $adcPath
   Remove-Item $backupPath
   ```

   Complete the browser sign-in with the Google account whose Drive should own the uploaded files. Keep both ADC files private; neither belongs in the repository.
3. Ensure that account has Editor access to the configured Drive folder in `backend/application-local.properties`, and that the Drive API is enabled. The folder can be in that user's My Drive.
4. Set the credential file path and start Compose with the Firebase and Drive overlays:

```powershell
$env:GOOGLE_CLOUD_PROJECT = "e-cardmitra"
$env:GOOGLE_APPLICATION_CREDENTIALS = Join-Path $env:APPDATA "gcloud\application_default_credentials.json"
$env:GOOGLE_DRIVE_CREDENTIALS = Join-Path $env:APPDATA "gcloud\application_default_credentials.drive.json"
docker compose --env-file .env.local -f compose.yaml -f compose.firebase.yaml -f compose.drive.yaml up --build
```

The Drive overlay enables the Drive client and mounts the ignored local properties file and the separate Google user credential read-only into the backend container. Set `DRIVE_CONFIG_FILE` if the properties file is elsewhere. Do not commit local properties or credential files. Uploads accept PNG/JPEG/GIF up to 10 MB and 16 megapixels. The backend stores a private Drive file ID in Firestore and serves the image through its public API proxy; the Drive file does not need a public link.

## Run locally

Prerequisites: Node.js 24.12+ (the installed Firebase SDK version requires it), JDK 21, and Maven 3.9+.

1. Install and start the frontend:

   ```powershell
   npm install
   Copy-Item .env.example .env.local
   npm run dev
   ```

2. Set only the `VITE_FIREBASE_*` values in `.env.local` from Firebase project settings. Enable Google as a Firebase Authentication provider and add `localhost` as an authorized domain. Without Firebase web configuration, the directory preview works and sign-in controls explain what is missing.

3. Authenticate local Application Default Credentials once with `gcloud auth application-default login --impersonate-service-account=ecardmitra-local-backend@e-cardmitra.iam.gserviceaccount.com`. Enable the Google Drive API, grant that service account access to the logo folder, then replace `REPLACE_WITH_GOOGLE_DRIVE_FOLDER_ID` in the ignored local file `backend/application-local.properties` with the folder ID from its Drive URL. This file enables Firebase and Drive for local development and is not committed.

   ```powershell
   mvn -f backend\pom.xml spring-boot:run
   ```

   Set `VITE_API_BASE_URL=http://localhost:8080` in `.env.local`. Firebase and Google credentials are server-only; never put service-account JSON, OAuth secrets, or refresh tokens in the frontend environment. Logo uploads store a private Drive file ID and a single API proxy `logoUrl` string in Firestore; the Drive file itself does not need to be public.

4. Open the API contract at `http://localhost:8080/swagger-ui.html` after the backend starts.

The public directory contains clearly marked sample listings when the API is unavailable. Firebase, Firestore, Drive, authorization, and publication still require project credentials and verification against a real Firebase project; see [staged verification](docs/staged-verification.md).

## Super-admin bootstrap

Use a trusted operator workstation with application-default credentials for the Firebase project. Never expose a public role-assignment endpoint or share ADC credentials.

```powershell
$env:GOOGLE_CLOUD_PROJECT = "e-cardmitra"
mvn -f backend/pom.xml -Dexec.mainClass=com.krniket.platform.security.SuperAdminBootstrap exec:java
```

Only the verified `niket2work@gmail.com` Firebase Authentication account is eligible for the `superAdmin` claim. The backend requires both this exact allowlisted email and the verified `superAdmin` Firebase custom claim; other accounts are not administrators even if they have a stale claim. After bootstrap, the account must sign out and back in (or refresh its ID token).

## Checks

```powershell
npm run build
npm run lint
mvn -f backend/pom.xml test
```

## Deploy

Build the frontend with `npm run build` and deploy `dist/` to Firebase Hosting or another static host. `firebase.json` includes history fallback and cache headers. Deploy the Java service separately to a Java 21 HTTPS host, set the backend environment variables from [deployment notes](docs/deployment.md), then set the exact frontend origin in `CORS_ALLOWED_ORIGINS` and the API URL in `VITE_API_BASE_URL` before rebuilding the frontend.

Deploy `firestore.rules` and `firestore.indexes.json` with the Firebase CLI after confirming the project ID and target project. Keep service-account credentials in the backend host's secret manager or workload identity, not in source control.

## Current implementation boundary

This repository is an implementation foundation, not a claim of production integration completion. Public directory/detail pages, contact actions, QR generation, Google sign-in wiring, authenticated REST helpers, vendor onboarding entry, API contracts, Firestore access, approval/publication logic, logo proxy delivery, and initial unit tests are present. The provided Firebase project, Google Drive account/folder, deployed HTTPS origins, and real-user workflows have not been supplied and therefore cannot be verified here. See the checklist before calling those integrations complete.
