package ru.example.piratechesttoken.util;

import java.util.random.RandomGenerator;

/** Чистая логика броска шанса — покрыта юнит-тестами. */
public final class ChanceRoller {

    private ChanceRoller() {
    }

    /** Зажать шанс в диапазон [0; 1]. */
    public static double clamp(double chance) {
        if (!Double.isFinite(chance) || chance < 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, chance);
    }

    /** Бросок на находку: 0.0 — никогда, 1.0 — всегда. */
    public static boolean roll(RandomGenerator random, double chance) {
        double clamped = clamp(chance);
        if (clamped <= 0.0D) {
            return false;
        }
        if (clamped >= 1.0D) {
            return true;
        }
        return random.nextDouble() < clamped;
    }
}
