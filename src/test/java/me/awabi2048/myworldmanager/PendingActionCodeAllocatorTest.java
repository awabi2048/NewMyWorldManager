package me.awabi2048.myworldmanager;

import kotlin.random.Random;
import me.awabi2048.myworldmanager.service.PendingActionCodeAllocator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingActionCodeAllocatorTest {
    @Test
    void preservesLeadingZeroesAndAvoidsUsedCodesAfterCollisions() {
        var allocator = new PendingActionCodeAllocator(new ZeroRandom(), 3);
        var used = new HashSet<String>();
        used.add("0000");

        assertEquals("0001", allocator.allocate(used));
    }

    @Test
    void allocatesUniqueFourDigitCodesForOneRecipientNamespace() {
        var allocator = new PendingActionCodeAllocator();
        var used = new HashSet<String>();
        for (int i = 0; i < 250; i++) {
            String code = allocator.allocate(used);
            assertTrue(code.matches("^[0-9]{4}$"));
            assertTrue(used.add(code));
        }
    }

    @Test
    void reportsExhaustedCodeSpace() {
        Set<String> used = IntStream.range(0, 10_000)
            .mapToObj(PendingActionCodeAllocator.Companion::format)
            .collect(Collectors.toSet());
        assertNull(new PendingActionCodeAllocator().allocate(used));
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextBits(int bitCount) {
            return 0;
        }
    }
}
