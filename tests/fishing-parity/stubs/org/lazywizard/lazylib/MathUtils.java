package org.lazywizard.lazylib;

import java.util.Random;

public class MathUtils {

    public static Random rng = new Random(0);

    public static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }

    public static float getRandomNumberInRange(float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }
}
