package ru.progrm_jarvis.benchmark.padla.misc;

import org.openjdk.jmh.annotations.Benchmark;

public class ArrayAllocation {

    private static final int SIZE = 16;

    private static final byte[] BASE = new byte[SIZE];

    @Benchmark
    public byte[] create() {
        return new byte[SIZE];
    }

    @Benchmark
    public byte[] copy() {
        return BASE.clone();
    }
}
