package iped.engine.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;

public class DirectMemory {

    private static VarHandle varHandle;

    static {
        varHandle = MethodHandles.byteBufferViewVarHandle(byte[].class, ByteOrder.LITTLE_ENDIAN);
    }

    public static final void putByteVolatile(MappedByteBuffer bb, long pos, byte val) {
        varHandle.setVolatile(bb, pos, val);
    }

    public static final byte getByteVolatile(MappedByteBuffer bb, long pos) {
        return (byte) varHandle.getVolatile(bb, pos);
    }
}
