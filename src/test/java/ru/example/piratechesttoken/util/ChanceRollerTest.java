package ru.example.piratechesttoken.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ChanceRollerTest {

    @Test
    void clampBoundsChanceToUnitInterval() {
        assertEquals(0.0D, ChanceRoller.clamp(-1.5D));
        assertEquals(0.0D, ChanceRoller.clamp(Double.NaN));
        assertEquals(0.0D, ChanceRoller.clamp(Double.POSITIVE_INFINITY));
        assertEquals(1.0D, ChanceRoller.clamp(3.0D));
        assertEquals(0.25D, ChanceRoller.clamp(0.25D));
    }

    @Test
    void rollZeroChanceNeverDrops() {
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            assertFalse(ChanceRoller.roll(random, 0.0D));
            assertFalse(ChanceRoller.roll(random, -0.5D));
        }
    }

    @Test
    void rollFullChanceAlwaysDrops() {
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            assertTrue(ChanceRoller.roll(random, 1.0D));
            assertTrue(ChanceRoller.roll(random, 2.0D));
        }
    }

    @Test
    void rollDistributionIsCloseToChance() {
        Random random = new Random(42);
        int drops = 0;
        int attempts = 10_000;
        for (int i = 0; i < attempts; i++) {
            if (ChanceRoller.roll(random, 0.3D)) {
                drops++;
            }
        }
        assertTrue(drops > 2_500 && drops < 3_500, "drops=" + drops);
    }
}
