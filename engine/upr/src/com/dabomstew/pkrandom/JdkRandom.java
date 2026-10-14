package com.dabomstew.pkrandom;

import java.util.Random;

// [web] Added file. A java.util.Random whose every method follows the
// OpenJDK algorithms exactly. TeaVM's own Random produces different values
// for the same seed, which would make seeds incompatible with the PC version.
public class JdkRandom extends Random {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;
    private static final double DOUBLE_UNIT = 0x1.0p-53;
    private static long seedUniquifier = 8682522807148012L;

    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;

    public JdkRandom() {
        this(nextSeedUniquifier() ^ System.nanoTime() ^ (long) (Math.random() * Long.MAX_VALUE));
    }

    public JdkRandom(long seed) {
        super(0);
        setSeed(seed);
    }

    private static synchronized long nextSeedUniquifier() {
        seedUniquifier *= 1181783497276652981L;
        return seedUniquifier;
    }

    @Override
    public synchronized void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        haveNextNextGaussian = false;
    }

    @Override
    protected int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    @Override
    public void nextBytes(byte[] bytes) {
        for (int i = 0, len = bytes.length; i < len; ) {
            for (int rnd = nextInt(), n = Math.min(len - i, Integer.SIZE / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE) {
                bytes[i++] = (byte) rnd;
            }
        }
    }

    @Override
    public int nextInt() {
        return next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // retry
            }
        }
        return r;
    }

    @Override
    public long nextLong() {
        return ((long) (next(32)) << 32) + next(32);
    }

    @Override
    public boolean nextBoolean() {
        return next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    @Override
    public double nextDouble() {
        return (((long) (next(26)) << 27) + next(27)) * DOUBLE_UNIT;
    }

    @Override
    public synchronized double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1, v2, s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        nextNextGaussian = v2 * multiplier;
        haveNextNextGaussian = true;
        return v1 * multiplier;
    }
}
