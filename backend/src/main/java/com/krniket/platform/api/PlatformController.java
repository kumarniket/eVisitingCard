package com.krniket.platform.api;

import com.krniket.platform.integration.DriveLogoService;
import com.krniket.platform.security.VerifiedUser;
import com.krniket.platform.service.PlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Validated
public class PlatformController {
    private static final Logger logger = LoggerFactory.getLogger(PlatformController.class);
    private final PlatformService platform;
    private final DriveLogoService logos;

    public PlatformController(PlatformService platform, DriveLogoService logos) {
        this.platform = platform;
        this.logos = logos;
    }

    @GetMapping("/users/me")
    public Map<String, Object> me(Authentication authentication) { return platform.me(actor(authentication)); }

    @PostMapping("/users/register")
    public ResponseEntity<Map<String, Object>> register(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platform.register(actor(authentication)));
    }

    @PatchMapping("/users/me")
    public Map<String, Object> updateProfile(Authentication authentication, @Valid @RequestBody ApiModels.ProfilePatch patch) {
        return platform.updateProfile(actor(authentication), patch);
    }

    @PostMapping("/shops")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> createShop(Authentication authentication, @Valid @RequestBody ApiModels.ShopCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platform.createShop(actor(authentication), request));
    }

    @GetMapping("/shops/me")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> myShop(Authentication authentication) { return platform.myShop(actor(authentication)); }

    @PatchMapping("/shops/me")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> patchShop(Authentication authentication, @Valid @RequestBody ApiModels.ShopPatch patch) {
        return platform.patchShop(actor(authentication), patch);
    }

    @PostMapping("/shops/me/submit")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> submitShop(Authentication authentication) { return platform.submitShop(actor(authentication)); }

    @PostMapping(value = "/shops/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> uploadMyShopLogo(Authentication authentication, @RequestPart("file") MultipartFile file) {
        VerifiedUser user = actor(authentication);
        Map<String, Object> shop = platform.myShop(user);
        DriveLogoService.Upload upload = logos.upload((String) shop.get("slug"), file);
        Map<String, Object> updated = platform.setMyShopLogo(user, upload.fileId());
        return cleanupSupersededLogos(shop, upload.fileId(), updated);
    }

    @GetMapping("/shops/me/status")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> shopStatus(Authentication authentication) { return platform.shopStatus(actor(authentication)); }

    @GetMapping("/shops/me/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ApiModels.PageResponse<Map<String, Object>> myProducts(Authentication authentication,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return platform.myProducts(actor(authentication), page, Math.max(1, size));
    }

    @PostMapping("/shops/me/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> createProduct(Authentication authentication, @Valid @RequestBody ApiModels.ProductCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platform.createProduct(actor(authentication), request));
    }

    @GetMapping("/shops/me/products/{productId}")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> product(Authentication authentication, @PathVariable String productId) {
        return platform.product(actor(authentication), productId);
    }

    @PatchMapping("/shops/me/products/{productId}")
    @PreAuthorize("hasRole('VENDOR')")
    public Map<String, Object> patchProduct(Authentication authentication, @PathVariable String productId,
            @Valid @RequestBody ApiModels.ProductPatch patch) {
        return platform.patchProduct(actor(authentication), productId, patch);
    }

    @DeleteMapping("/shops/me/products/{productId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteProduct(Authentication authentication, @PathVariable String productId) {
        platform.deleteProduct(actor(authentication), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/shops")
    public ApiModels.PageResponse<Map<String, Object>> publicShops(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "24") @PositiveOrZero @Max(100) int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String category,
            @RequestParam(required = false) String city) {
        return platform.publicShops(page, Math.max(1, size), q, category, city);
    }

    @GetMapping("/public/shops/{slug}")
    public Map<String, Object> publicShop(@PathVariable String slug) { return platform.publicShop(slug); }

    @GetMapping("/public/categories")
    public List<Map<String, Object>> publicCategories() { return platform.publicCategories(); }

    @GetMapping("/public/shops/{slug}/products")
    public ApiModels.PageResponse<Map<String, Object>> publicProducts(@PathVariable String slug,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return platform.publicProducts(slug, page, Math.max(1, size));
    }

    @GetMapping("/public/shops/{slug}/logo")
    public ResponseEntity<byte[]> publicLogo(@PathVariable String slug) {
        DriveLogoService.Download download = logos.download(platform.publicLogoFileId(slug));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(download.contentType())).body(download.bytes());
    }

    @GetMapping("/admin/vendors")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiModels.PageResponse<Map<String, Object>> adminVendors(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        return platform.adminVendors(page, Math.max(1, size), status);
    }

    @GetMapping("/admin/shops")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiModels.PageResponse<Map<String, Object>> adminShops(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        return platform.adminShops(page, Math.max(1, size), status);
    }

    @PostMapping("/admin/vendors/{uid}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> approveVendor(@PathVariable String uid) { return platform.approveVendor(uid); }

    @PostMapping("/admin/vendors/{uid}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> rejectVendor(@PathVariable String uid, @RequestParam(required = false) String reason) {
        return platform.rejectVendor(uid, reason);
    }

    @PostMapping("/admin/shops/{shopId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> approveShop(@PathVariable String shopId, Authentication authentication) {
        return platform.approveShop(shopId, actor(authentication));
    }

    @PostMapping("/admin/shops/{shopId}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> publishShop(@PathVariable String shopId) { return platform.publishShop(shopId); }

    @PostMapping("/admin/shops/{shopId}/unpublish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> unpublishShop(@PathVariable String shopId) { return platform.unpublishShop(shopId); }

    @PostMapping("/admin/vendors/{uid}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> suspendVendor(@PathVariable String uid) { return platform.suspendVendor(uid); }

    @PostMapping("/admin/vendors/{uid}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> reactivateVendor(@PathVariable String uid) { return platform.reactivateVendor(uid); }

    @PostMapping(value = "/admin/shops/{shopId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> assignLogo(@PathVariable String shopId, @RequestPart("file") MultipartFile file) {
        Map<String, Object> shop = platform.shopById(shopId);
        DriveLogoService.Upload upload = logos.upload((String) shop.get("slug"), file);
        Map<String, Object> updated = platform.setShopLogo(shopId, upload.fileId());
        return cleanupSupersededLogos(shop, upload.fileId(), updated);
    }

    @DeleteMapping("/admin/shops/{shopId}/logo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> removeLogo(@PathVariable String shopId) {
        Map<String, Object> shop = platform.shopById(shopId);
        Map<String, Object> updated = platform.removeShopLogo(shopId);
        return cleanupSupersededLogos(shop, null, updated);
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> adminDashboard() { return platform.dashboard(); }

    private VerifiedUser actor(Authentication authentication) {
        return (VerifiedUser) authentication.getPrincipal();
    }

    private Map<String, Object> cleanupSupersededLogos(Map<String, Object> shop, String keepFileId,
            Map<String, Object> updated) {
        String slug = (String) shop.get("slug");
        try {
            logos.deleteSuperseded(slug, keepFileId);
            return updated;
        } catch (ResponseStatusException exception) {
            logger.warn("logo_cleanup_failed shopId={} slug={}", shop.get("id"), slug, exception);
            Map<String, Object> response = new LinkedHashMap<>(updated);
            response.put("logoCleanupWarning", true);
            return response;
        }
    }
}
