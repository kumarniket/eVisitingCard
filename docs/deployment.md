# Deployment notes

## Firebase project

1. Create a Firebase project and enable Google Authentication.
2. Create a Firestore database in the intended region; confirm the region cannot be changed later.
3. Deploy `firestore.rules` and `firestore.indexes.json` to that exact project. Rules deny all browser access because all business data is served by Spring.
4. Set the Firebase web app values in the frontend host as build-time `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, and `VITE_FIREBASE_APP_ID` values. These identify the public web app; they are not service credentials.
5. Add the final frontend domain to Firebase Authentication's authorized domains.
6. Admin access is restricted in the API to the verified `niket2work@gmail.com` account with the Firebase `superAdmin` custom claim. The email check is enforced server-side; changing frontend code or setting a Firestore user role does not grant admin access.

## Java API host

Deploy `backend/` as a Java 21 Spring Boot service with HTTPS and a persistent Java-compatible host. Configure:

- `FIREBASE_ENABLED=true`
- `GOOGLE_CLOUD_PROJECT` set to the Firebase project ID
- `GOOGLE_APPLICATION_CREDENTIALS` or workload identity for a least-privilege service identity
- `CORS_ALLOWED_ORIGINS` as a comma-separated allowlist of exact HTTPS frontend origins
- `PUBLIC_BASE_URL` as the public HTTPS API origin
- `PORT` if required by the host
- `DRIVE_ENABLED=true` and `DRIVE_LOGO_FOLDER_ID` after Drive access is configured
- `GOOGLE_DRIVE_CREDENTIALS` pointing to a securely mounted Google user OAuth credential with the Drive scope when storing uploads in that user's My Drive. A service account has no personal Drive storage quota; use a Workspace Shared Drive with the appropriate API support or a Drive user OAuth identity.

Never expose service-account files through image assets, frontend environment variables, logs, or source control. Restrict the service identity to required Firestore and Drive operations.

## Drive logo delivery

The API does not use a Google Drive share link as an image URL. It stores the Drive file ID internally and returns a stable URL on the API: `/api/v1/public/shops/{slug}/logo`. The Spring service streams the file using its server-side Drive credentials, so visitors do not need Google accounts and the Drive file need not be made public.

For the initial ADC integration, enable the Drive API and share the administrator-owned logo folder with the backend service identity with only the required write/read access. Confirm this arrangement matches the administrator's Google Workspace policy. If an administrator-owned OAuth user flow is required instead, keep its client secret and refresh token only in the backend secret manager and implement a credential provider before production.

Verify with an anonymous browser request that the assigned logo returns the expected image MIME type and content, then test assignment, replacement, and removal. The upload accepts PNG/JPEG/GIF up to 10 MB and 16 MP; product-image hosting is not included yet.

## Frontend hosting

Run `npm run build` and publish `dist/`. `firebase.json` configures SPA fallback and static asset cache headers. Set `VITE_API_BASE_URL` to the deployed Java API before building. The browser talks only to the HTTPS REST API; it must never initialize the Firestore or Drive SDK.

## API and health

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- Health probe: `/actuator/health`

Use the host's secret manager for credentials and HTTPS termination. Confirm CORS origins, TLS, logs, health checks, backups/retention, alerting, and Firestore indexes in the release checklist.
