package com.krniket.platform.security;

public record VerifiedUser(String uid, String email, String displayName, boolean superAdmin) {}
