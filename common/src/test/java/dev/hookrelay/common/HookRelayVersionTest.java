package dev.hookrelay.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HookRelayVersionTest {

    @Test
    void exposesProjectIdentity() {
        assertNotNull(HookRelayVersion.PROJECT);
        assertFalse(HookRelayVersion.PROJECT.isBlank());
        assertNotNull(HookRelayVersion.VERSION);
        assertFalse(HookRelayVersion.VERSION.isBlank());
    }
}
