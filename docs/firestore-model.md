# Firestore model

All documents are accessed by the Spring Boot service using Firebase Admin SDK. Browser access is denied by `firestore.rules`; do not add client exceptions without a separate security review.

## Collections

### `users/{firebaseUid}`

| Field | Type | Notes |
| --- | --- | --- |
| `uid` | string | Verified Firebase UID; document ID is the same UID. |
| `displayName`, `email`, `phone` | string | Profile/contact fields. Email is never returned by public shop endpoints. |
| `role` | string | `VENDOR` or `SUPER_ADMIN`; admin authority is independently checked from the verified token claim. |
| `approvalStatus` | string | Vendor workflow: `PENDING`, `APPROVED`, `REJECTED`. |
| `suspended` | boolean | Suspended vendors cannot manage shops; their shop is unpublished. |
| `createdAt`, `updatedAt`, `reviewedAt` | ISO-8601 string | Server-generated timestamps. |

### `shops/{shopId}`

Includes `ownerUid`, `name`, globally unique `slug`, `category`, `description`, `address`, `city`, `phone`, `email`, `website`, `logoFileId`, `logoUrl`, `approvalStatus`, `approved`, `published`, `reviewNote`, `createdAt`, and `updatedAt`. `logoFileId` is the private Google Drive object ID; `logoUrl` is one string containing the stable API proxy URL, not an array or a public Drive link. The backend writes both fields only after a successful Drive upload. Vendor patch DTOs do not accept owner, logo, approval, publication, or review fields; logo fields can only be written by the authenticated logo upload and admin assignment endpoints. The public response removes owner UID, Drive file ID, approval internals, and review notes.

A shop is visible publicly only when both `approved == true` and `published == true`. Publishing also checks that its owner is approved and not suspended.

When a shop has `logoUrl` and no `coverUrl`, the public API uses that logo URL as the card image. Logo uploads require the backend Drive integration to be enabled and configured with an accessible folder; Firebase/Firestore credentials alone are not sufficient.

### `products/{productId}`

Includes `shopId`, `ownerUid`, `name`, `description`, `price`, `category`, `imageUrl`, `active`, and timestamps. Vendor operations resolve the caller's shop and verify product ownership before returning, patching, or deleting a product. Public product lists include only active products belonging to a published shop.

### `categories/{categoryId}`

Includes `name`, `slug`, and `active`. The public categories endpoint returns active categories only. Seed category documents before enabling production directory filters.

## Operational notes

- `firestore.indexes.json` contains the composite indexes needed for the current equality-filter query patterns. Deploy them before using those filters in production.
- `FirestoreStore` is the repository boundary. Its blocking Future calls are suitable for the initial small app; production traffic should add async composition, retry/timeouts, and cursor-based pagination.
- The directory search currently applies text matching in the service after a bounded Firestore read. For a large dataset, add normalized search tokens or a dedicated search index and switch to cursor pagination.
- Admin SDK access bypasses Firestore Rules by design, so authorization must remain in Spring Security and service ownership checks.
