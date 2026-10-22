package dev.hookrelay.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

    @Test
    void hashesDeterministicallyWithPepper() {
        ApiKeyHasher hasher = new ApiKeyHasher("pepper-one");
        String hash = hasher.hash("hr_live_example");
        assertEquals(64, hash.length());
        assertEquals(hash, hasher.hash("hr_live_example"));
    }

    @Test
    void differentPeppersProduceDifferentHashes() {
        String raw = "hr_live_example";
        String left = new ApiKeyHasher("pepper-a").hash(raw);
        String right = new ApiKeyHasher("pepper-b").hash(raw);
        assertNotEquals(left, right);
    }

    @Test
    void rejectsBlankRawKey() {
        ApiKeyHasher hasher = new ApiKeyHasher("");
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(" "));
    }
}
