package ru.progrm_jarvis.benchmark.padla.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

@State(Scope.Benchmark)
public class UuidDeserializationBenchmark {

    private static final int UUID_BYTES = Long.BYTES << 1;

    private static final boolean SHOULD_REVERSE_UNSAFE_BYTES = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final VarHandle BYTE_ARRAY_LONG_VIEW_BIG_ENDIAN
            = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    private static final Unsafe UNSAFE;

    static {
        final Field theUnsafe;
        try {
            theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        } catch (final NoSuchFieldException e) {
            throw new AssertionError("Unsafe is unavailable", e);
        }
        theUnsafe.setAccessible(true);
        try {
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (final IllegalAccessException e) {
            throw new AssertionError("Unsafe is unavailable", e);
        }
    }

    @Param("1000000")
    private int count;

    private byte[][] uuidBytes;

    @Setup
    public void setUp() {VarHandle.storeStoreFence();
        uuidBytes = new byte[count][];
        for (var i = 0; i < count; i++) {
            final var uuid = UUID.randomUUID();
            try (final var bytes = new ByteArrayOutputStream(UUID_BYTES); final var out = new DataOutputStream(bytes)) {
                out.writeLong(uuid.getLeastSignificantBits());
                out.writeLong(uuid.getMostSignificantBits());
                uuidBytes[i] = bytes.toByteArray();
            } catch (final IOException e) {
                throw new AssertionError("Unexpected IOException", e);
            }
        }
    }

    @Benchmark
    public void byteBuffer(final Blackhole blackhole) {
        for (final var bytes : uuidBytes) blackhole.consume(uuidFromBytes_ByteBuffer(bytes));
    }

    @Benchmark
    public void varHandle(final Blackhole blackhole) {
        for (final var bytes : uuidBytes) blackhole.consume(uuidFromBytes_VarHandle(bytes));
    }

    @Benchmark
    public void unsafe(final Blackhole blackhole) {
        for (final var bytes : uuidBytes) blackhole.consume(uuidFromBytes_Unsafe(bytes));
    }

    public static UUID uuidFromBytes_ByteBuffer(final byte [] bytes) {
        if (bytes == null) throw new NullPointerException("bytes is null");
        if (bytes.length != UUID_BYTES) throw new IllegalArgumentException("Length of bytes should be " + UUID_BYTES);

        final var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static UUID uuidFromBytes_VarHandle(final byte [] bytes) {
        if (bytes == null) throw new NullPointerException("bytes is null");
        if (bytes.length != UUID_BYTES) throw new IllegalArgumentException(
                "Length of bytes length should be " + UUID_BYTES
        );

        return new UUID(
                (long) BYTE_ARRAY_LONG_VIEW_BIG_ENDIAN.get(bytes, 0),
                (long) BYTE_ARRAY_LONG_VIEW_BIG_ENDIAN.get(bytes, Long.BYTES)
        );
    }

    public static UUID uuidFromBytes_Unsafe(final byte [] bytes) {
        if (bytes == null) throw new NullPointerException("bytes is null");
        if (bytes.length != UUID_BYTES) throw new IllegalArgumentException(
                "Length of bytes length should be " + UUID_BYTES
        );

        return new UUID(
                tryReverseBytes(UNSAFE.getLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET)),
                tryReverseBytes(UNSAFE.getLong(bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET + Long.BYTES))
        );
    }

    private static long tryReverseBytes(final long value) {
        return SHOULD_REVERSE_UNSAFE_BYTES ? Long.reverseBytes(value) : value;
    }
}
