package com.krniket.platform.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {}

    public record ProfilePatch(@Size(max = 100) String displayName, @Size(max = 30) String phone) {}
    public record ShopCreate(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 60) String category) {}
    public record ShopPatch(@Size(min = 2, max = 100) String name, @Size(max = 1000) String description,
            @Size(max = 60) String category, @Size(max = 240) String address, @Size(max = 100) String city,
                @Size(max = 30) String phone, @Email @Size(max = 254) String email,
                @Pattern(regexp = "https?://[^\\s]+") @Size(max = 2048) String website) {}
    public record ProductCreate(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                @PositiveOrZero Double price, @Size(max = 60) String category,
                @Pattern(regexp = "https?://[^\\s]+") @Size(max = 2048) String imageUrl) {}
    public record ProductPatch(@Size(min = 1, max = 120) String name, @Size(max = 1000) String description,
                @PositiveOrZero Double price, @Size(max = 60) String category,
                @Pattern(regexp = "https?://[^\\s]+") @Size(max = 2048) String imageUrl,
            Boolean active) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
            return new PageResponse<>(items, page, size, total, (int) Math.ceil((double) total / size));
        }
    }
    public record ApiError(String code, String message, String path, String traceId, Map<String, String> fields) {}
}
