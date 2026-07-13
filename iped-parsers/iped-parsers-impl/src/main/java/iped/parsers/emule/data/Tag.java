package iped.parsers.emule.data;

import java.nio.ByteBuffer;

public class Tag {
    public static final byte FT_FILENAME = 0x01;
    public static final byte FT_SIZE = 0x02;
    public static final byte FT_SIZE_HI = 0x3A;
    public static final byte FT_COLLECTIONAUTHOR = 0x31;
    public static final byte FT_COLLECTIONAUTHORKEY = 0x32;
    public static final byte FT_AICH_HASH = 0x27;
    public static final byte FT_FILE_HASH = 0x28;

    private static final byte TAGTYPE_STRING = 0x02;
    private static final byte TAGTYPE_UINT32 = 0x03;
    private static final byte TAGTYPE_UINT64 = 0x0B;
    private static final byte TAGTYPE_UINT16 = 0x08;
    private static final byte TAGTYPE_UINT8 = 0x09;
    private static final byte TAGTYPE_FLOAT32 = 0x04;
    private static final byte TAGTYPE_STR1 = 0x11;
    private static final byte TAGTYPE_STR16 = 0x20;
    private static final long TAGTYPE_HASH = 0x01;
    private static final long TAGTYPE_BOOL = 0x05;
    private static final long TAGTYPE_BOOLARRAY = 0x06;
    private static final long TAGTYPE_BLOB = 0x07;
    long m_uType;
    private byte m_uName;
    private byte[] m_pszName;
    private int m_nBlobSize;
    private byte[] m_pstrVal;
    private Object m_uVal;
    private byte[] m_fVal;
    byte[] m_pData;

    public byte getNameID() {
        return m_uName;
    }

    static public Tag createTag(ByteBuffer data, boolean bOptUTF8)
    {
        Tag t = new Tag();
        t.m_uType = data.get();
        if ((t.m_uType & 0x80) != 0)
        {
            t.m_uType &= 0x7F;
            t.m_uName = data.get();
            t.m_pszName = null;
        }
        else
        {
            int length = data.getShort();
            if (length == 1)
            {
                t.m_uName = data.get();
                t.m_pszName = null;
            }
            else
            {
                t.m_uName = 0;
                t.m_pszName = new byte[length+1];
                try{
                    data.get(t.m_pszName);
                }
                catch(Exception ex){
                    t.m_pszName = null;
                    throw ex;
                }
                t.m_pszName[length] = '\0';
            }
        }
        
        t.m_nBlobSize = 0;

        // NOTE: It's very important that we read the *entire* packet data, even if we do
        // not use each tag. Otherwise we will get troubles when the packets are returned in 
        // a list - like the search results from a server.
        if (t.m_uType == TAGTYPE_STRING)
        {
            int length = data.getShort();
            t.m_pstrVal = new byte[length];
            data.get(t.m_pstrVal); //new CString(data->ReadString(bOptUTF8));
        }
        else if (t.m_uType == TAGTYPE_UINT32)
        {
            t.m_uVal = data.getInt();
        }
        else if (t.m_uType == TAGTYPE_UINT64)
        {
            t.m_uVal = data.getLong();
        }
        else if (t.m_uType == TAGTYPE_UINT16)
        {
            t.m_uVal = data.getShort();
            t.m_uType = TAGTYPE_UINT32;
        }
        else if (t.m_uType == TAGTYPE_UINT8)
        {
            t.m_uVal = data.get();
            t.m_uType = TAGTYPE_UINT32;
        }
        else if (t.m_uType == TAGTYPE_FLOAT32)
        {
            t.m_fVal = new byte[4];
            data.get(t.m_fVal);
        }
        else if (t.m_uType >= TAGTYPE_STR1 && t.m_uType <= TAGTYPE_STR16)
        {
            int length = (int) t.m_uType - TAGTYPE_STR1 + 1;
            t.m_pstrVal = new byte[length];
            data.get(t.m_pstrVal);
            t.m_uType = TAGTYPE_STRING;
        }
        else if (t.m_uType == TAGTYPE_HASH)
        {
            t.m_pData = new byte[16];
            try{
                data.get(t.m_pData);
            }
            catch(Exception ex){
                t.m_pData = null;
                throw ex;
            }
        }
        else if (t.m_uType == TAGTYPE_BOOL)
        {
            data.get();
        }
        else if (t.m_uType == TAGTYPE_BOOLARRAY)
        {
            int len = data.getShort();
            data.position(data.position() + (len / 8) + 1);
        }
        else if (t.m_uType == TAGTYPE_BLOB)
        {
            t.m_nBlobSize = data.getInt();
            t.m_pData = new byte[t.m_nBlobSize];
            data.get(t.m_pData);
        }
        else
        {
            t.m_uVal = 0;
        }

        return t;
    }

    public String getStr() {
        return new String(m_pstrVal);
    }

    public Object getValObject() {
        return m_uVal;
    }
    
    
}
