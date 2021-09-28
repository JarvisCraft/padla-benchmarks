package ru.progrm_jarvis.benchmark.padla.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

@State(Scope.Benchmark)
public class UuidSerializationBenchmark {

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

    private UUID[] uuids;

    @Setup
    public void setUp() {
        uuids = new UUID[count];
        for (var i = 0; i < count; i++) uuids[i] = UUID.randomUUID();
    }

    @Benchmark
    public void byteBuffer(final Blackhole blackhole) {
        for (final var uuid : uuids) blackhole.consume(uuidToBytes_ByteBuffer(uuid));
    }

    @Benchmark
    public void varHandle(final Blackhole blackhole) {
        for (final var uuid : uuids) blackhole.consume(uuidToBytes_VarHandle(uuid));
    }

    @Benchmark
    public void unsafe(final Blackhole blackhole) {
        for (final var uuid : uuids) blackhole.consume(uuidToBytes_Unsafe(uuid));
    }

    public byte[] uuidToBytes_ByteBuffer(final UUID uuid) {
        if (uuid == null) throw new NullPointerException("uuid is null");

        final var buffer = ByteBuffer.wrap(new byte[UUID_BYTES]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        return buffer.array();
    }

    public byte[] uuidToBytes_VarHandle(final UUID uuid) {
        if (uuid == null) throw new NullPointerException("uuid is null");

        final byte[] bytes;
        BYTE_ARRAY_LONG_VIEW_BIG_ENDIAN.set(bytes = new byte[UUID_BYTES], 0, uuid.getMostSignificantBits());
        BYTE_ARRAY_LONG_VIEW_BIG_ENDIAN.set(bytes, Long.BYTES, uuid.getLeastSignificantBits());

        return bytes;
    }

    public byte[] uuidToBytes_Unsafe(final UUID uuid) {
        if (uuid == null) throw new NullPointerException("uuid is null");

        final byte[] bytes;
        UNSAFE.putLong(
                bytes = new byte[UUID_BYTES], UNSAFE.ARRAY_BYTE_BASE_OFFSET,
                tryReverseBytes(uuid.getMostSignificantBits())
        );
        UNSAFE.putLong(
                bytes, UNSAFE.ARRAY_BYTE_BASE_OFFSET + Long.BYTES,
                tryReverseBytes(uuid.getLeastSignificantBits())
        );

        return bytes;
    }

    private static long tryReverseBytes(final long value) {
        return SHOULD_REVERSE_UNSAFE_BYTES ? Long.reverseBytes(value) : value;
    }
}
