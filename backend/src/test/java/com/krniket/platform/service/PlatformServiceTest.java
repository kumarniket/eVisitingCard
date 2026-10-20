package com.krniket.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.krniket.platform.data.FirestoreStore;
import com.krniket.platform.security.VerifiedUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlatformServiceTest {
    @Test
    void slugifyCreatesStableUrlSafeSlug() {
        assertEquals("morrow-coffee-and-bakes", PlatformService.slugify("  Morrow Coffee and Bakes! "));
    }

    @Test
    void slugifyReturnsEmptyForPunctuationOnlyNames() {
        assertEquals("", PlatformService.slugify("--- !!! ---"));
    }

    @Test
    void unpublishedShopCannotBePublishedBeforeApproval() {
        MemoryStore store = new MemoryStore();
        store.put("shops", "shop-1", Map.of("id", "shop-1", "approved", false));

        PlatformService service = new PlatformService(store);

        assertThrows(ResponseStatusException.class, () -> service.publishShop("shop-1"));
        assertFalse(store.wasPatched);
    }

    @Test
    void vendorCannotReadAnotherShopsProduct() {
        MemoryStore store = new MemoryStore();
        store.put("users", "vendor-a", Map.of("suspended", false));
        store.put("shops", "shop-a", Map.of("id", "shop-a", "ownerUid", "vendor-a"));
        store.put("products", "product-b", Map.of("id", "product-b", "shopId", "shop-b"));

        PlatformService service = new PlatformService(store);
        VerifiedUser vendor = new VerifiedUser("vendor-a", "vendor@example.test", "Vendor A", false);

        assertThrows(ResponseStatusException.class, () -> service.product(vendor, "product-b"));
    }

    @Test
    void publicShopIncludesLogoUrlWithoutExposingDriveFileId() {
        MemoryStore store = new MemoryStore();
        store.put("shops", "shop-1", Map.of(
                "id", "shop-1",
                "slug", "local-shop",
                "published", true,
                "approved", true,
                "logoUrl", "http://localhost:8080/api/v1/public/shops/local-shop/logo",
                "logoFileId", "private-drive-file-id"));

        Map<String, Object> shop = new PlatformService(store).publicShop("local-shop");

        assertEquals("/api/v1/public/shops/local-shop/logo", shop.get("logoUrl"));
        assertFalse(shop.containsKey("logoFileId"));
    }

    @Test
    void publicDirectoryUsesLogoUrlAsCardImageWhenCoverIsMissing() {
        MemoryStore store = new MemoryStore();
        store.put("shops", "shop-1", Map.of(
                "id", "shop-1",
                "slug", "local-shop",
                "published", true,
                "approved", true,
                "logoUrl", "http://localhost:8080/api/v1/public/shops/local-shop/logo",
                "logoFileId", "private-drive-file-id"));

        Map<String, Object> shop = new PlatformService(store).publicShops(0, 24, null, null, null)
                .items().get(0);

        assertEquals("/api/v1/public/shops/local-shop/logo", shop.get("coverUrl"));
        assertFalse(shop.containsKey("logoFileId"));
    }

    @Test
    void vendorCanSetLogoOnlyForTheirOwnShop() {
        MemoryStore store = new MemoryStore();
        store.put("users", "vendor-a", Map.of("suspended", false));
        store.put("shops", "shop-a", Map.of(
                "id", "shop-a",
                "slug", "shop-a",
                "ownerUid", "vendor-a"));
        store.put("shops", "shop-b", Map.of("id", "shop-b", "ownerUid", "vendor-b"));
        PlatformService service = new PlatformService(store);
        VerifiedUser vendor = new VerifiedUser("vendor-a", "vendor@example.test", "Vendor A", false);

        Map<String, Object> updated = service.setMyShopLogo(vendor, "file-a");

        assertEquals("file-a", updated.get("logoFileId"));
        assertTrue(updated.get("logoUrl").toString().matches("/api/v1/public/shops/shop-a/logo\\?v=.+"));
        assertThrows(ResponseStatusException.class,
                () -> service.setMyShopLogo(new VerifiedUser("vendor-b", null, null, false), "file-b"));
    }

    @Test
    void vendorShopNormalizesPreviouslyStoredAbsoluteLogoUrl() {
        MemoryStore store = new MemoryStore();
        store.put("users", "vendor-a", Map.of("suspended", false));
        store.put("shops", "shop-a", Map.of(
                "id", "shop-a",
                "slug", "shop-a",
                "ownerUid", "vendor-a",
                "logoFileId", "file-a",
                "logoUrl", "http://localhost:8080/api/v1/public/shops/shop-a/logo"));

        Map<String, Object> shop = new PlatformService(store).myShop(
                new VerifiedUser("vendor-a", "vendor@example.test", "Vendor A", false));

        assertEquals("/api/v1/public/shops/shop-a/logo", shop.get("logoUrl"));
    }

    private static final class MemoryStore extends FirestoreStore {
        private final Map<String, Map<String, Map<String, Object>>> collections = new HashMap<>();
        private boolean wasPatched;

        private MemoryStore() { super(null); }

        private void put(String collection, String id, Map<String, Object> value) {
            collections.computeIfAbsent(collection, ignored -> new HashMap<>()).put(id, new HashMap<>(value));
        }

        @Override
        public Optional<Map<String, Object>> get(String collection, String id) {
            return Optional.ofNullable(collections.getOrDefault(collection, Map.of()).get(id));
        }

        @Override
        public List<Map<String, Object>> find(String collection, Map<String, Object> filters) {
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Map<String, Object> value : collections.getOrDefault(collection, Map.of()).values()) {
                if (filters.entrySet().stream().allMatch(filter -> filter.getValue().equals(value.get(filter.getKey())))) {
                    matches.add(value);
                }
            }
            return matches;
        }

        @Override
        public void patch(String collection, String id, Map<String, Object> values) {
            wasPatched = true;
            collections.get(collection).get(id).putAll(values);
        }
    }
}
