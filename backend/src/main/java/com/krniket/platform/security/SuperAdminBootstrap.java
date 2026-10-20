package com.krniket.platform.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.util.HashMap;
import java.util.Map;

public final class SuperAdminBootstrap {
    private SuperAdminBootstrap() {}

    public static void main(String[] args) throws Exception {
        FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault()).build());
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        try {
            var user = auth.getUserByEmail(AdminAccessPolicy.ADMIN_EMAIL);
            if (!user.isEmailVerified()) {
                throw new IllegalStateException("The configured admin email must be verified in Firebase Authentication.");
            }
            Map<String, Object> claims = new HashMap<>(user.getCustomClaims());
            claims.put("superAdmin", true);
            auth.setCustomUserClaims(user.getUid(), claims);
            System.out.println("Super-admin claim assigned to " + AdminAccessPolicy.ADMIN_EMAIL
                    + ". Ask the user to sign out and back in to refresh the ID token.");
        } finally {
            app.delete();
        }
    }
}
