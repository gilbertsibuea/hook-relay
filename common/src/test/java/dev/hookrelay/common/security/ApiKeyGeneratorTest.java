package dev.hookrelay.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatesHighEntropyKeyWithStablePrefixShape() {
        GeneratedApiKey first = generator.generate();
        GeneratedApiKey second = generator.generate();

        assertTrue(first.rawKey().startsWith(ApiKeyGenerator.KEY_PREFIX_MARKER));
        assertEquals(ApiKeyGenerator.PREFIX_LENGTH, first.keyPrefix().length());
        assertTrue(first.rawKey().startsWith(first.keyPrefix()));
        assertNotEquals(first.rawKey(), second.rawKey());
        assertFalse(first.toString().contains(first.rawKey()));
    }
}
