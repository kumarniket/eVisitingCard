package com.krniket.platform.security;

import java.util.Map;

public final class AdminAccessPolicy {
    public static final String ADMIN_EMAIL = "niket2work@gmail.com";

    private AdminAccessPolicy() {}

    public static boolean allows(String email, Map<String, Object> claims) {
        return email != null
                && ADMIN_EMAIL.equalsIgnoreCase(email.trim())
                && Boolean.TRUE.equals(claims.get("email_verified"))
                && Boolean.TRUE.equals(claims.get("superAdmin"));
    }
}
