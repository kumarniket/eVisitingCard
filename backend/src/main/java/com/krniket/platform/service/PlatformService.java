package com.krniket.platform.service;

import com.krniket.platform.api.ApiModels;
import com.krniket.platform.data.FirestoreStore;
import com.krniket.platform.security.VerifiedUser;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformService {
    private final FirestoreStore store;

    public PlatformService(FirestoreStore store) {
        this.store = store;
    }

    public Map<String, Object> me(VerifiedUser user) {
        return store.get("users", user.uid()).orElseThrow(() -> missing("Vendor profile not found."));
    }

    public Map<String, Object> register(VerifiedUser user) {
        Optional<Map<String, Object>> existing = store.get("users", user.uid());
        if (existing.isPresent()) return existing.get();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("uid", user.uid());
        profile.put("displayName", user.displayName());
        profile.put("email", user.email());
        profile.put("role", user.superAdmin() ? "SUPER_ADMIN" : "VENDOR");
        profile.put("approvalStatus", user.superAdmin() ? "APPROVED" : "PENDING");
        profile.put("suspended", false);
        profile.put("createdAt", now());
        store.set("users", user.uid(), profile);
        return store.get("users", user.uid()).orElse(profile);
    }

    public Map<String, Object> updateProfile(VerifiedUser user, ApiModels.ProfilePatch patch) {
        Map<String, Object> values = new LinkedHashMap<>();
        add(values, "displayName", patch.displayName());
        add(values, "phone", patch.phone());
        store.patch("users", user.uid(), values);
        return me(user);
    }

    public Map<String, Object> createShop(VerifiedUser user, ApiModels.ShopCreate request) {
        ensureVendorActive(user.uid());
        if (!store.find("shops", Map.of("ownerUid", user.uid())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A vendor can manage one shop profile.");
        }
        String slug = uniqueSlug(request.name(), null);
        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("ownerUid", user.uid());
        shop.put("name", request.name().trim());
        shop.put("slug", slug);
        shop.put("category", request.category().trim());
        shop.put("description", "");
        shop.put("approvalStatus", "DRAFT");
        shop.put("approved", false);
        shop.put("published", false);
        shop.put("createdAt", now());
        shop.put("updatedAt", now());
        String id = store.create("shops", shop);
        return store.get("shops", id).orElseThrow(() -> missing("Shop could not be created."));
    }

    public Map<String, Object> myShop(VerifiedUser user) {
        return withLogoUrl(ownedShop(user.uid()));
    }

    public Map<String, Object> patchShop(VerifiedUser user, ApiModels.ShopPatch patch) {
        Map<String, Object> shop = ownedShop(user.uid());
        Map<String, Object> values = new LinkedHashMap<>();
        add(values, "name", patch.name());
        add(values, "category", patch.category());
        add(values, "description", patch.description());
        add(values, "address", patch.address());
        add(values, "city", patch.city());
        add(values, "phone", patch.phone());
        add(values, "email", patch.email());
        add(values, "website", patch.website());
        if (values.containsKey("name")) values.put("slug", uniqueSlug((String) values.get("name"), (String) shop.get("id")));
        values.put("updatedAt", now());
        store.patch("shops", (String) shop.get("id"), values);
        return store.get("shops", (String) shop.get("id")).orElseThrow(() -> missing("Shop not found."));
    }

    public Map<String, Object> submitShop(VerifiedUser user) {
        Map<String, Object> shop = ownedShop(user.uid());
        store.patch("shops", (String) shop.get("id"), Map.of("approvalStatus", "PENDING_REVIEW", "published", false, "updatedAt", now()));
        return store.get("shops", (String) shop.get("id")).orElseThrow(() -> missing("Shop not found."));
    }

    public Map<String, Object> shopStatus(VerifiedUser user) {
        Map<String, Object> shop = ownedShop(user.uid());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shopId", shop.get("id"));
        result.put("approvalStatus", shop.get("approvalStatus"));
        result.put("approved", shop.get("approved"));
        result.put("published", shop.get("published"));
        result.put("reviewNote", shop.get("reviewNote"));
        return result;
    }

    public ApiModels.PageResponse<Map<String, Object>> myProducts(VerifiedUser user, int page, int size) {
        Map<String, Object> shop = ownedShop(user.uid());
        Map<String, Object> filters = Map.of("shopId", shop.get("id"));
        return page("products", filters, page, size);
    }

    public Map<String, Object> createProduct(VerifiedUser user, ApiModels.ProductCreate request) {
        Map<String, Object> shop = ownedShop(user.uid());
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("ownerUid", user.uid());
        product.put("shopId", shop.get("id"));
        product.put("name", request.name().trim());
        product.put("description", request.description());
        product.put("price", request.price());
        product.put("category", request.category());
        product.put("imageUrl", request.imageUrl());
        product.put("active", true);
        product.put("createdAt", now());
        String id = store.create("products", product);
        return product(id);
    }

    public Map<String, Object> product(VerifiedUser user, String productId) {
        Map<String, Object> shop = ownedShop(user.uid());
        Map<String, Object> product = product(productId);
        if (!shop.get("id").equals(product.get("shopId"))) throw missing("Product not found.");
        return product;
    }

    public Map<String, Object> patchProduct(VerifiedUser user, String productId, ApiModels.ProductPatch patch) {
        Map<String, Object> product = product(user, productId);
        Map<String, Object> values = new LinkedHashMap<>();
        add(values, "name", patch.name());
        add(values, "description", patch.description());
        add(values, "price", patch.price());
        add(values, "category", patch.category());
        add(values, "imageUrl", patch.imageUrl());
        add(values, "active", patch.active());
        values.put("updatedAt", now());
        store.patch("products", (String) product.get("id"), values);
        return product(productId);
    }

    public void deleteProduct(VerifiedUser user, String productId) {
        Map<String, Object> product = product(user, productId);
        store.delete("products", (String) product.get("id"));
    }

    public ApiModels.PageResponse<Map<String, Object>> publicShops(int page, int size, String search, String category, String city) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("published", true);
        filters.put("approved", true);
        if (category != null && !category.isBlank()) filters.put("category", category);
        List<Map<String, Object>> result = store.find("shops", filters).stream()
                .filter(shop -> city == null || city.isBlank() || contains(shop.get("city"), city))
                .filter(shop -> search == null || search.isBlank() || contains(shop.get("name"), search)
                        || contains(shop.get("description"), search) || contains(shop.get("category"), search))
                .toList();
        return slice(result.stream().map(this::publicFields).toList(), page, size);
    }

    public Map<String, Object> publicShop(String slug) {
        return store.find("shops", Map.of("slug", slug)).stream().filter(this::isPublic).map(this::publicFields)
            .findFirst().orElseThrow(() -> missing("Published shop not found."));
    }

    public String publicLogoFileId(String slug) {
        return store.find("shops", Map.of("slug", slug)).stream().filter(this::isPublic)
                .map(shop -> (String) shop.get("logoFileId")).filter(id -> id != null && !id.isBlank())
                .findFirst().orElseThrow(() -> missing("Published shop logo not found."));
    }

    public List<Map<String, Object>> publicCategories() {
        return store.find("categories", Map.of("active", true));
    }

    public ApiModels.PageResponse<Map<String, Object>> publicProducts(String slug, int page, int size) {
        Map<String, Object> shop = publicShop(slug);
        Map<String, Object> filters = Map.of("shopId", shop.get("id"), "active", true);
        ApiModels.PageResponse<Map<String, Object>> result = page("products", filters, page, size);
        return new ApiModels.PageResponse<>(result.items().stream().map(this::publicFields).toList(),
            result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    public ApiModels.PageResponse<Map<String, Object>> adminVendors(int page, int size, String status) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("role", "VENDOR");
        if (status != null && !status.isBlank()) filters.put("approvalStatus", status.toUpperCase(Locale.ROOT));
        return page("users", filters, page, size);
    }

    public ApiModels.PageResponse<Map<String, Object>> adminShops(int page, int size, String status) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (status != null && !status.isBlank()) filters.put("approvalStatus", status.toUpperCase(Locale.ROOT));
        return page("shops", filters, page, size);
    }

    public Map<String, Object> approveVendor(String uid) { return vendorDecision(uid, "APPROVED", null); }
    public Map<String, Object> rejectVendor(String uid, String reason) { return vendorDecision(uid, "REJECTED", reason); }

    public Map<String, Object> approveShop(String shopId, VerifiedUser admin) {
        required("shops", shopId);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("approved", true);
        update.put("approvalStatus", "APPROVED");
        update.put("approvedBy", admin.uid());
        update.put("approvedAt", now());
        update.put("reviewNote", "");
        update.put("updatedAt", now());
        store.patch("shops", shopId, update);
        return required("shops", shopId);
    }

    public Map<String, Object> publishShop(String shopId) {
        Map<String, Object> shop = required("shops", shopId);
        if (!Boolean.TRUE.equals(shop.get("approved"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Approve the shop before publishing it.");
        Map<String, Object> vendor = required("users", (String) shop.get("ownerUid"));
        if (!"APPROVED".equals(vendor.get("approvalStatus")) || Boolean.TRUE.equals(vendor.get("suspended"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The vendor must be approved and active before publication.");
        }
        store.patch("shops", shopId, Map.of("published", true, "updatedAt", now()));
        return required("shops", shopId);
    }

    public Map<String, Object> unpublishShop(String shopId) {
        required("shops", shopId);
        store.patch("shops", shopId, Map.of("published", false, "updatedAt", now()));
        return required("shops", shopId);
    }

    public Map<String, Object> suspendVendor(String uid) {
        required("users", uid);
        store.patch("users", uid, Map.of("suspended", true, "updatedAt", now()));
        store.find("shops", Map.of("ownerUid", uid)).forEach(shop -> store.patch("shops", (String) shop.get("id"), Map.of("published", false, "updatedAt", now())));
        return required("users", uid);
    }

    public Map<String, Object> reactivateVendor(String uid) {
        required("users", uid);
        store.patch("users", uid, Map.of("suspended", false, "updatedAt", now()));
        return required("users", uid);
    }

    public Map<String, Object> dashboard() {
        return Map.of("vendors", store.count("users", Map.of("role", "VENDOR")),
                "pending", store.count("shops", Map.of("approvalStatus", "PENDING_REVIEW")),
                "published", store.count("shops", Map.of("published", true, "approved", true)));
    }

    public Map<String, Object> setShopLogo(String shopId, String fileId) {
        Map<String, Object> shop = required("shops", shopId);
        store.patch("shops", shopId, Map.of("logoFileId", fileId,
                "logoUrl", logoUrl((String) shop.get("slug")), "updatedAt", now()));
        return withLogoUrl(required("shops", shopId));
    }

    public Map<String, Object> setMyShopLogo(VerifiedUser user, String fileId) {
        Map<String, Object> shop = ownedShop(user.uid());
        return setShopLogo((String) shop.get("id"), fileId);
    }

    public Map<String, Object> removeShopLogo(String shopId) {
        required("shops", shopId);
        store.patch("shops", shopId, Map.of("logoFileId", "", "logoUrl", "", "updatedAt", now()));
        return required("shops", shopId);
    }

    public Map<String, Object> shopById(String shopId) {
        return withLogoUrl(required("shops", shopId));
    }

    public boolean isPublic(Map<String, Object> shop) {
        return Boolean.TRUE.equals(shop.get("published")) && Boolean.TRUE.equals(shop.get("approved"));
    }

    public static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private Map<String, Object> vendorDecision(String uid, String status, String reason) {
        required("users", uid);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("approvalStatus", status);
        update.put("reviewedAt", now());
        if (reason != null) update.put("reviewNote", reason);
        store.patch("users", uid, update);
        if ("REJECTED".equals(status)) {
            store.find("shops", Map.of("ownerUid", uid)).forEach(shop -> store.patch("shops",
                (String) shop.get("id"), Map.of("published", false, "approvalStatus", "REJECTED", "updatedAt", now())));
        }
        return required("users", uid);
    }

    private Map<String, Object> product(String id) { return required("products", id); }

    private Map<String, Object> ownedShop(String uid) {
        ensureVendorActive(uid);
        return store.find("shops", Map.of("ownerUid", uid)).stream().findFirst()
                .orElseThrow(() -> missing("Shop profile not found."));
    }

    private void ensureVendorActive(String uid) {
        Map<String, Object> profile = me(new VerifiedUser(uid, null, null, false));
        if (Boolean.TRUE.equals(profile.get("suspended"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This vendor account is suspended.");
    }

    private String uniqueSlug(String name, String currentId) {
        String slug = slugify(name);
        if (slug.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop name must contain letters or numbers.");
        boolean conflict = store.find("shops", Map.of("slug", slug)).stream()
                .anyMatch(existing -> !existing.get("id").equals(currentId));
        if (conflict) throw new ResponseStatusException(HttpStatus.CONFLICT, "That shop URL is already in use.");
        return slug;
    }

    private ApiModels.PageResponse<Map<String, Object>> page(String collection, Map<String, Object> filters, int page, int size) {
        long total = store.count(collection, filters);
        return ApiModels.PageResponse.of(store.page(collection, filters, page, size), page, size, total);
    }

    private ApiModels.PageResponse<Map<String, Object>> slice(List<Map<String, Object>> values, int page, int size) {
        int from = Math.min(page * size, values.size());
        int to = Math.min(from + size, values.size());
        return ApiModels.PageResponse.of(values.subList(from, to), page, size, values.size());
    }

    private Map<String, Object> required(String collection, String id) {
        return store.get(collection, id).orElseThrow(() -> missing("Resource not found."));
    }

    private Map<String, Object> publicFields(Map<String, Object> source) {
        Map<String, Object> result = withLogoUrl(source);
        result.remove("ownerUid");
        result.remove("logoFileId");
        result.remove("approvalStatus");
        result.remove("approvedBy");
        result.remove("approvedAt");
        result.remove("reviewedAt");
        result.remove("reviewNote");
        if (result.containsKey("logoUrl") && (!result.containsKey("coverUrl")
                || Objects.equals(result.get("coverUrl"), source.get("logoUrl")))) {
            result.put("coverUrl", result.get("logoUrl"));
        }
        return result;
    }

    private Map<String, Object> withLogoUrl(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>(source);
        Object fileId = source.get("logoFileId");
        Object slug = source.get("slug");
        if (fileId instanceof String id && !id.isBlank() && slug instanceof String shopSlug && !shopSlug.isBlank()) {
            Object updatedAt = source.get("updatedAt");
            String version = updatedAt instanceof String value && !value.isBlank()
                    ? "?v=" + URLEncoder.encode(value, StandardCharsets.UTF_8) : "";
            result.put("logoUrl", logoUrl(shopSlug) + version);
        }
        return result;
    }

    private String logoUrl(String slug) {
        return "/api/v1/public/shops/" + slug + "/logo";
    }

    private ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private String now() { return Instant.now().toString(); }
    private void add(Map<String, Object> target, String key, Object value) { if (value != null) target.put(key, value); }
    private boolean contains(Object value, String query) { return value != null && value.toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)); }
}
