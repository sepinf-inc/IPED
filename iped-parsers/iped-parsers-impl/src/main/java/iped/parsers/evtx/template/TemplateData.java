package iped.parsers.evtx.template;

import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.lucene.util.ArrayUtil;

import iped.parsers.evtx.model.BinXmlToken;
import iped.parsers.evtx.model.EvtxFile;
import iped.parsers.evtx.model.EvtxParseException;
import iped.parsers.evtx.model.EvtxXmlFragment;
import iped.parsers.evtx.windows.GUID;
import iped.parsers.evtx.windows.SID;

public class TemplateData {

    ArrayList<Object> tds = new ArrayList<>();
    EvtxFile evtxFile;
    TemplateInstance templateInstance;

    static final byte NULL_TYPE = 0x00;
    static final byte STRING_TYPE = 0x01;
    static final byte ANSISTRING_TYPE = 0x02;
    static final byte INT8_TYPE = 0x03;
    static final byte UINT8_TYPE = 0x04;
    static final byte INT16_TYPE = 0x05;
    static final byte UINT16_TYPE = 0x06;
    static final byte INT32_TYPE = 0x07;
    static final byte UINT32_TYPE = 0x08;
    static final byte INT64_TYPE = 0x09;
    static final byte UINT64_TYPE = 0x0a;
    static final byte REAL32_TYPE = 0x0b;
    static final byte REAL64_TYPE = 0x0c;
    static final byte BOOL_TYPE = 0x0d;
    static final byte BINARY_TYPE = 0x0e;
    static final byte GUID_TYPE = 0x0f;
    static final byte SIZET_TYPE = 0x10;
    static final byte FILETIME_TYPE = 0x11;
    static final byte SYSTIME_TYPE = 0x12;
    static final byte SID_TYPE = 0x13;
    static final byte HEXINT32_TYPE = 0x14;
    static final byte HEXINT64_TYPE = 0x15;
    static final byte BINXML_TYPE = 0x21;

    public TemplateData(EvtxFile evtxFile, ByteBuffer bb, TemplateInstance templateInstance) throws EvtxParseException {
        this.evtxFile = evtxFile;
        int tdValuesCount = bb.getInt();
        this.templateInstance = templateInstance;

        try {
            TemplateValueDescriptor[] vds = new TemplateValueDescriptor[tdValuesCount];

            for (int i = 0; i < tdValuesCount; i++) {
                TemplateValueDescriptor vd = new TemplateValueDescriptor();
                short shortVal = bb.getShort();
                if (shortVal < 0) {
                    vd.size = 0x10000 + (int) shortVal;
                } else {
                    vd.size = shortVal;
                }

                byte bval = bb.get();
                if (bval >= (short) 0) {
                    vd.type = (short) bval;
                } else {
                    vd.type = (short) ((0x100) + bval);
                }
                bb.get();
                vds[i] = vd;
            }

            for (int i = 0; i < tdValuesCount; i++) {
                TemplateValueDescriptor vd = vds[i];
                switch (vd.type) {
                    case 0x01:
                        byte[] strb = new byte[vd.size];
                        bb.get(strb);
                        if (strb.length > 0 && strb[strb.length - 1] == 0 && strb[strb.length - 2] == 0) {
                            strb = ArrayUtil.copyOfSubArray(strb, 0, strb.length - 2);
                        }
                        try {
                            tds.add(new String(strb, "UTF-16LE"));
                        } catch (Exception e) {
                            tds.add(e.getMessage());
                        }
                        break;
                    case 0x02:
                        strb = new byte[vd.size];
                        bb.get(strb);
                        tds.add(new String(strb));
                        break;
                    case 0x03:
                        short v = bb.get();
                        if (v < 0) {
                            v = (short) ((0x100) + v);
                        }
                        tds.add(v);
                        break;
                    case 0x04:
                        v = bb.get();
                        if (v < 0) {
                            v = (short) ((0x100) + v);
                        }
                        tds.add(v);
                        break;
                    case 0x05:
                        int vi = bb.getShort();
                        if (vi < 0) {
                            vi = (int) ((0x10000) + vi);
                        }
                        tds.add(vi);
                        break;
                    case 0x06:
                        vi = bb.getShort();
                        if (vi < 0) {
                            vi = (int) ((0x10000) + vi);
                        }
                        tds.add(vi);
                        break;
                    case 0x07:
                        long vl = bb.getInt();
                        if (vl < 0) {
                            vl = (long) ((0x100000000l) + vl);
                        }
                        tds.add(vl);
                        break;
                    case 0x08:
                        vl = bb.getInt();
                        if (vl < 0) {
                            vl = (long) ((0x100000000l) + vl);
                        }
                        tds.add(vl);
                        break;
                    case 0x09:
                        BigInteger bi = BigInteger.valueOf(bb.getLong());
                        if (bi.longValue() < 0) {
                            bi = bi.add(BigInteger.valueOf(Long.MAX_VALUE));
                            bi = bi.add(BigInteger.valueOf(Long.MAX_VALUE));
                            bi = bi.add(BigInteger.valueOf(2));
                        }
                        tds.add(bi);
                        break;
                    case 0x0A:
                        bi = BigInteger.valueOf(bb.getLong());
                        if (bi.longValue() < 0) {
                            bi = bi.add(BigInteger.valueOf(Long.MAX_VALUE));
                            bi = bi.add(BigInteger.valueOf(Long.MAX_VALUE));
                            bi = bi.add(BigInteger.valueOf(2));
                        }
                        tds.add(bi);
                        break;
                    case 0x0B:
                        tds.add(bb.getFloat());
                        break;
                    case 0x0C:
                        tds.add(bb.getDouble());
                        break;
                    case 0x0D:
                        tds.add(bb.getInt() == 1);
                        break;
                    case BINARY_TYPE:
                        byte[] vb = new byte[vd.size];
                        bb.get(vb);
                        tds.add(new ByteArrayFormatted(vb));
                        break;
                    case GUID_TYPE:
                        vb = new byte[16];
                        bb.get(vb);
                        tds.add(new GUID(vb));
                        break;
                    case 0x11:
                        long filetime = bb.getLong();
                        long javatime = filetime - 0x19db1ded53e8000L;
                        javatime /= 10000;
                        Date d = new Date(javatime);
                        tds.add(d);
                        break;
                    case SYSTIME_TYPE:
                        int year = bb.getShort();
                        int month = bb.getShort();
                        int dayOfWeek = bb.getShort();
                        int day = bb.getShort();
                        int hour = bb.getShort();
                        int minute = bb.getShort();
                        int second = bb.getShort();
                        int millisecond = bb.getShort();
                        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
                        c.set(Calendar.YEAR, year);
                        c.set(Calendar.MONTH, month);
                        c.set(Calendar.DAY_OF_MONTH, day);
                        c.set(Calendar.HOUR, hour);
                        c.set(Calendar.MINUTE, minute);
                        c.set(Calendar.SECOND, second);
                        c.set(Calendar.MILLISECOND, millisecond);
                        tds.add(c.getTime());
                        break;
                    case SID_TYPE:
                        vb = new byte[vd.size];
                        bb.get(vb);
                        tds.add(new SID(vb));
                        break;
                    case 0x14:
                        vb = new byte[vd.size];
                        bb.get(vb);
                        tds.add(new ByteArrayFormatted(vb));
                        break;
                    case 0x15:
                        vb = new byte[vd.size];
                        bb.get(vb);
                        tds.add(new ByteArrayFormatted(vb));
                        break;
                    case 0x21:
                        BinXmlToken bt = new BinXmlToken(evtxFile, bb);
                        if (bt.type == BinXmlToken.LIBFWEVT_XML_TOKEN_TEMPLATE_INSTANCE) {
                            TemplateInstance subTemplateInstance = new TemplateInstance(evtxFile, bb);
                            tds.add(subTemplateInstance);
                        }
                        if (bt.type == BinXmlToken.LIBFWEVT_XML_TOKEN_FRAGMENT_HEADER) {
                            EvtxXmlFragment evtxXmlFragment = new EvtxXmlFragment(evtxFile, this, bb);
                            tds.add(evtxXmlFragment);
                        }
                        break;
                    case 0x00:
                        vb = new byte[vd.size];
                        bb.get(vb);
                        tds.add(new ByteArrayFormatted(vb));
                        break;
                    default:
                        try {
                            vb = new byte[vd.size];
                            bb.get(vb);
                            tds.add(new ByteArrayFormatted(vb));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        } catch (BufferUnderflowException e) {
            throw new EvtxParseException("Not enough data to read template value descriptor in " + evtxFile.getName());
        }

    }

}
