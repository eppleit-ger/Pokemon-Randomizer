package com.dabomstew.pkrandom;

// [web] Added file. Math.round with Java semantics (round half up);
// TeaVM rounds negative halves away from zero (Math.round(-2.5) == -3).
public final class JavaMath {
    private JavaMath() {
    }

    public static long round(double a) {
        if (Double.isNaN(a)) {
            return 0;
        }
        return (long) Math.floor(a + 0.5d);
    }

    public static int round(float a) {
        if (Float.isNaN(a)) {
            return 0;
        }
        return (int) Math.floor(a + 0.5f);
    }
}
