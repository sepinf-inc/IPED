package iped.parsers.evtx.model;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class EvtxValue {
    int size;
    public static final int LIBFWEVT_VALUE_TYPE_NULL = 0x00;
    public static final int LIBFWEVT_VALUE_TYPE_STRING_UTF16 = 0x01;
    public static final int LIBFWEVT_VALUE_TYPE_STRING_int_STREAM = 0x02;
    public static final int LIBFWEVT_VALUE_TYPE_INTEGER_8BIT = 0x03;
    public static final int LIBFWEVT_VALUE_TYPE_UNSIGNED_INTEGER_8BIT = 0x04;
    public static final int LIBFWEVT_VALUE_TYPE_INTEGER_16BIT = 0x05;
    public static final int LIBFWEVT_VALUE_TYPE_UNSIGNED_INTEGER_16BIT = 0x06;
    public static final int LIBFWEVT_VALUE_TYPE_INTEGER_32BIT = 0x07;
    public static final int LIBFWEVT_VALUE_TYPE_UNSIGNED_INTEGER_32BIT = 0x08;
    public static final int LIBFWEVT_VALUE_TYPE_INTEGER_64BIT = 0x09;
    public static final int LIBFWEVT_VALUE_TYPE_UNSIGNED_INTEGER_64BIT = 0x0a;
    public static final int LIBFWEVT_VALUE_TYPE_FLOATING_POINT_32BIT = 0x0b;
    public static final int LIBFWEVT_VALUE_TYPE_FLOATING_POINT_64BIT = 0x0c;
    public static final int LIBFWEVT_VALUE_TYPE_BOOLEAN = 0x0d;
    public static final int LIBFWEVT_VALUE_TYPE_BINARY_DATA = 0x0e;
    public static final int LIBFWEVT_VALUE_TYPE_GUID = 0x0f;
    public static final int LIBFWEVT_VALUE_TYPE_SIZE = 0x10;
    public static final int LIBFWEVT_VALUE_TYPE_FILETIME = 0x11;
    public static final int LIBFWEVT_VALUE_TYPE_SYSTEMTIME = 0x12;
    public static final int LIBFWEVT_VALUE_TYPE_NT_SECURITY_IDENTIFIER = 0x13;
    public static final int LIBFWEVT_VALUE_TYPE_HEXADECIMAL_INTEGER_32BIT = 0x14;
    public static final int LIBFWEVT_VALUE_TYPE_HEXADECIMAL_INTEGER_64BIT = 0x15;
    public static final int LIBFWEVT_VALUE_TYPE_BINARY_XML = 0x21;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_STRING_UTF16 = 0x81;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_STRING_int_STREAM = 0x82;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_INTEGER_8BIT = 0x83;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_UNSIGNED_INTEGER_8BIT = 0x84;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_INTEGER_16BIT = 0x85;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_UNSIGNED_INTEGER_16BIT = 0x86;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_INTEGER_32BIT = 0x87;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_UNSIGNED_INTEGER_32BIT = 0x88;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_INTEGER_64BIT = 0x89;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_UNSIGNED_INTEGER_64BIT = 0x8a;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_FLOATING_POINT_32BIT = 0x8b;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_FLOATING_POINT_64BIT = 0x8c;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_GUID = 0x8f;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_SIZE = 0x90;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_FILETIME = 0x91;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_SYSTEMTIME = 0x92;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_NT_SECURITY_IDENTIFIER = 0x93;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_HEXADECIMAL_INTEGER_32BIT = 0x94;
    public static final int LIBFWEVT_VALUE_TYPE_ARRAY_OF_HEXADECIMAL_INTEGER_64BIT = 0x95;
    private byte type;
    Object value;

    public EvtxValue(EvtxFile evtxFile, ByteBuffer bb) {
        this.type = bb.get();
        switch (this.type) {
            case LIBFWEVT_VALUE_TYPE_STRING_UTF16:
                int strSize = bb.getShort() * 2;
                this.size += strSize + 2;
                byte[] b = new byte[strSize];
                bb.get(b);
                try {
                    value = new String(b, "UTF-16LE");
                } catch (UnsupportedEncodingException e) {
                    value = null;
                    e.printStackTrace();
                }
                break;
            case 0x0d:
            case 0x0e:
                // read substitution
            case 0x08:
            case 0x48:
                // char entity reference
            case 0x09:
            case 0x49:
                // entity reference
            default:
                // throw new Exception("Bad attribute type : " + this.type);
                break;
        }
        this.size += 1;
    }

    @Override
    public String toString() {
        return value.toString();
    }

}
