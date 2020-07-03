package dpf.sp.gpinf.indexer.util;

import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class DirectMemory {

    private static Unsafe unsafe;

    static {
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe"); //$NON-NLS-1$
            singleoneInstanceField.setAccessible(true);
            unsafe = (Unsafe) singleoneInstanceField.get(null);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static final void putByteVolatile(MappedByteBuffer bb, long pos, byte val) {
        unsafe.putByteVolatile(null, ((sun.nio.ch.DirectBuffer) bb).address() + pos, val);
    }

    public static final byte getByteVolatile(MappedByteBuffer bb, long pos) {
        return unsafe.getByteVolatile(null, ((sun.nio.ch.DirectBuffer) bb).address() + pos);
    }
}
