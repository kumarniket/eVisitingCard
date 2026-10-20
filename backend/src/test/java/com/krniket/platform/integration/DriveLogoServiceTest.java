package com.krniket.platform.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DriveLogoServiceTest {
    @Test
    void cleanupKeepsReplacementAndDeletesAllOlderCopies() {
        assertEquals(List.of("old-logo-a", "old-logo-b"),
                DriveLogoService.supersededFileIds(
                        List.of("old-logo-a", "replacement-logo", "old-logo-b"), "replacement-logo"));
    }

    @Test
    void cleanupDeletesAllCopiesWhenLogoIsRemoved() {
        assertEquals(List.of("old-logo-a", "old-logo-b"),
                DriveLogoService.supersededFileIds(List.of("old-logo-a", "old-logo-b"), null));
    }
}
