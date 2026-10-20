# Staged verification

Do not call an integration complete based only on compilation. Record project IDs, environment names, test identity UIDs, and evidence in the release tracker without putting secrets here.

## Stage 1: Local source checks

- [x] React/TypeScript production build runs.
- [x] Spring Boot compiles and unit tests run.
- [x] Run and resolve `npm run lint` findings.
- [x] Run the frontend at desktop and mobile widths; confirm directory filtering, direct shop link, QR rendering, and contact links.
- [ ] Exercise the downloadable vCard in a browser and verify the downloaded contact fields.

## Stage 2: Firebase identity and authorization

- [ ] Configure a non-production Firebase project and Google provider; verify new and returning Google sign-in.
- [ ] Verify Bearer ID token reaches Java and its Firebase UID, email, and custom claims are read only after signature/audience/expiry verification.
- [ ] Verify missing, malformed, expired, and revoked tokens receive 401; a vendor receives 403 from every admin endpoint.
- [ ] Bootstrap one admin through `SuperAdminBootstrap`; verify only that account receives `ROLE_SUPER_ADMIN` after refreshing its token.
- [ ] Verify registration is idempotent and vendor approval is server-managed.

## Stage 3: Firestore ownership and workflow

- [ ] Deploy deny-all browser rules and confirm unauthenticated and vendor Firebase web SDK reads/writes are denied.
- [ ] Exercise profile/shop/product create, read, patch, and delete with two vendor UIDs; cross-vendor reads/updates/deletes must return not found.
- [ ] Confirm client-supplied UID, role, approval, publication, and logo fields cannot alter ownership or workflow.
- [ ] Verify rejected/suspended vendors are unpublished and cannot manage shop or product data.
- [ ] Verify a shop cannot publish before both vendor and shop approval, then verify unpublish and republish.
- [ ] Seed categories and validate filtering, stable pagination, and category indexes against realistic data volumes.

## Stage 4: Drive logo delivery

- [ ] Enable the Drive API, configure `DRIVE_ENABLED=true`, the Drive folder ID, and backend identity access; test upload MIME/magic/dimension/size rejection.
- [ ] Upload a logo as the owning vendor and assign/replace/remove a logo as super-admin; verify Firestore stores a private Drive file ID plus one API proxy URL string.
- [ ] Fetch the proxy URL in a signed-out browser and a clean session with no Google cookies; inspect image bytes, content type, cache behavior, and QR card rendering.
- [ ] Confirm vendors cannot call admin logo routes or patch logo fields through the shop details endpoint.

## Stage 5: Release

- [ ] Deploy API and frontend separately with HTTPS, exact CORS origins, secrets, health checks, logs, indexes, and rollback plan.
- [ ] Run end-to-end checks on the deployed origins and confirm no private vendor data leaks from public endpoints.
- [ ] Verify browser contact actions, map, vCard download, QR sharing, small-screen layout, keyboard navigation, and error/loading/empty states.

Current workspace verification: frontend production build and backend unit tests pass. Real Firebase, Firestore, Drive, CORS, and deployed publication workflows remain unverified because project credentials and deployment targets have not been provided.
