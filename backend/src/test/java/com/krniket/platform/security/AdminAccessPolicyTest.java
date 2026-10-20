package com.krniket.platform.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminAccessPolicyTest {
    @Test
    void allowsOnlyConfiguredEmailWithVerifiedAdminClaim() {
        assertTrue(AdminAccessPolicy.allows("niket2work@gmail.com",
                Map.of("email_verified", true, "superAdmin", true)));
    }

    @Test
    void rejectsOtherEmailsEvenWhenTheyHaveAdminClaim() {
        assertFalse(AdminAccessPolicy.allows("someone@example.com",
                Map.of("email_verified", true, "superAdmin", true)));
    }

    @Test
    void rejectsUnverifiedAdminEmail() {
        assertFalse(AdminAccessPolicy.allows("niket2work@gmail.com",
                Map.of("email_verified", false, "superAdmin", true)));
    }

    @Test
    void rejectsAllowedEmailWithoutAdminClaim() {
        assertFalse(AdminAccessPolicy.allows("niket2work@gmail.com",
                Map.of("email_verified", true)));
    }
}
