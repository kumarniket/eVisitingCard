# REST API contract

Base path: `/api/v1`. JSON requests use `Content-Type: application/json`; authenticated requests send `Authorization: Bearer <Firebase ID token>`. Public endpoints do not require a token. The service derives UID and super-admin status only from a verified Firebase token. Pagination is zero-based (`page=0`), with `size` from 1 to 100. OpenAPI/Swagger is served at `/swagger-ui.html`.

## Account and vendor shop

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| POST | `/users/register` | Firebase user | Idempotently create vendor profile (`201` on create). |
| GET | `/users/me` | Firebase user | Current profile. |
| PATCH | `/users/me` | Firebase user | Update display name and phone. |
| POST | `/shops` | Vendor | Create a draft shop. |
| GET | `/shops/me` | Vendor | Read caller's shop. |
| PATCH | `/shops/me` | Vendor | Update permitted shop fields; logo/review/publication fields are excluded. |
| POST | `/shops/me/logo` | Vendor | Upload the caller's shop logo as multipart field `file`; requires configured Drive storage. |
| POST | `/shops/me/submit` | Vendor | Submit shop for administrator review. |
| GET | `/shops/me/status` | Vendor | Approval and publication state. |
| GET | `/shops/me/products?page=&size=` | Vendor | Paged owned catalogue. |
| POST | `/shops/me/products` | Vendor | Create a product. |
| GET | `/shops/me/products/{productId}` | Vendor | Read an owned product. |
| PATCH | `/shops/me/products/{productId}` | Vendor | Update an owned product. |
| DELETE | `/shops/me/products/{productId}` | Vendor | Delete an owned product (`204`). |

## Public directory

| Method | Path | Result |
| --- | --- | --- |
| GET | `/public/shops?page=&size=&q=&category=&city=` | Paged published and approved shops. |
| GET | `/public/shops/{slug}` | Published visiting card with public contact data. |
| GET | `/public/categories` | Active categories. |
| GET | `/public/shops/{slug}/products?page=&size=` | Active products for the published shop. |
| GET | `/public/shops/{slug}/logo` | Anonymous image bytes streamed through the API from Drive. |

## Super administrator

Every route below requires the verified `superAdmin: true` custom claim.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/admin/vendors?page=&size=&status=` | Filtered vendor page. |
| POST | `/admin/vendors/{uid}/approve` | Approve vendor. |
| POST | `/admin/vendors/{uid}/reject?reason=` | Reject vendor and unpublish its shops. |
| POST | `/admin/vendors/{uid}/suspend` | Suspend vendor and unpublish its shops. |
| POST | `/admin/vendors/{uid}/reactivate` | Reactivate vendor; publication remains an explicit admin action. |
| GET | `/admin/shops?page=&size=&status=` | Shop moderation queue. |
| POST | `/admin/shops/{shopId}/approve` | Approve shop. |
| POST | `/admin/shops/{shopId}/publish` | Publish only when shop and vendor are approved and vendor is active. |
| POST | `/admin/shops/{shopId}/unpublish` | Remove shop from public directory. |
| POST | `/admin/shops/{shopId}/logo` | Upload multipart field `file` (PNG/JPEG/GIF, at most 10 MB and 16 MP), assign Drive file, return proxied URL. |
| DELETE | `/admin/shops/{shopId}/logo` | Remove the shop's logo assignment. |
| GET | `/admin/dashboard` | Vendor, pending review, and published shop counts. |

## Responses and workflow

Create endpoints return `201`; successful reads/updates return `200`; deletes return `204`. Validation failures return `400`, unauthenticated requests `401`, role/ownership denial `403` or not-found `404`, state conflicts `409`, and unavailable integrations `503`. Error JSON is `{ "code", "message", "path", "traceId", "fields" }`; unexpected server errors do not expose exception details.

Vendor state is stored in Firestore. Vendor approval and shop approval are distinct gates. A shop must be submitted, reviewed, and explicitly published. Publication is rejected until its owner is approved and active. Admin privileges come from a trusted Firebase custom claim, not profile request data.

## Request Body Examples

Registration and workflow action endpoints do not require a JSON body. The server uses the Firebase UID from the verified token.

`PATCH /users/me` accepts only permitted profile fields:

```json
{
	"displayName": "Asha Rao",
	"phone": "+919876543210"
}
```

`POST /shops` creates a draft owned by the caller:

```json
{
	"name": "Morrow Coffee",
	"category": "Coffee & food"
}
```

`PATCH /shops/me` accepts shop presentation/contact fields. Omitted or `null` fields are left unchanged; owner UID, approval, publication, and logo fields are not accepted:

```json
{
	"name": "Morrow Coffee",
	"description": "Slow mornings and good beans.",
	"category": "Coffee & food",
	"address": "12 100 Feet Road",
	"city": "Indiranagar, Bengaluru",
	"phone": "+918045551201",
	"email": "hello@example.com",
	"website": "https://example.com"
}
```

`POST /shops/me/products`:

```json
{
	"name": "House espresso",
	"description": "Double shot, locally roasted.",
	"price": 180,
	"category": "Coffee",
	"imageUrl": "https://images.example.com/espresso.jpg"
}
```

`PATCH /shops/me/products/{productId}` accepts any subset of `name`, `description`, non-negative `price`, `category`, HTTP(S) `imageUrl`, and `active`. Product image hosting/upload is not implemented.

For `GET /admin/vendors`, `status` is a vendor approval state such as `PENDING`, `APPROVED`, or `REJECTED`. For `GET /admin/shops`, it is a shop state such as `DRAFT`, `PENDING_REVIEW`, `APPROVED`, or `REJECTED`.

Shop-logo upload is `multipart/form-data` with one `file` part. The API validates file type, readability, dimensions, and size before uploading through the backend Drive identity; it does not accept a client-supplied Drive ID or logo URL. The Drive API must be enabled and `DRIVE_ENABLED=true` and `DRIVE_LOGO_FOLDER_ID` must be configured. After upload, Firestore stores a private `logoFileId` and one `logoUrl` string pointing to the public API proxy. Drive configuration failures return `503` and do not write logo fields.
