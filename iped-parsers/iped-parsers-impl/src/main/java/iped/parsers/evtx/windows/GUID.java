package iped.parsers.evtx.windows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import iped.parsers.evtx.template.ByteArrayFormatted;

public class GUID {
    byte[] timelower = new byte[4];
    byte[] timemiddle = new byte[2];
    byte[] timeupper = new byte[2];
    byte[] middle = new byte[2];
    byte[] node = new byte[6];

    String guid;

    public GUID(byte[] b) {
        ByteBuffer bf = ByteBuffer.wrap(b);
        bf.get(timelower);
        bf.get(timemiddle);
        bf.get(timeupper);
        middle[1] = bf.get();
        middle[0] = bf.get();
        node[5] = bf.get();
        node[4] = bf.get();
        node[3] = bf.get();
        node[2] = bf.get();
        node[1] = bf.get();
        node[0] = bf.get();
    }

    @Override
    public String toString() {
        if (guid == null) {
            guid = (new ByteArrayFormatted(timelower)).toString().substring(2) + "-" + (new ByteArrayFormatted(timemiddle)).toString().substring(2) + "-" + (new ByteArrayFormatted(timeupper)).toString().substring(2) + "-"
                    + (new ByteArrayFormatted(middle)).toString().substring(2) + "-" + (new ByteArrayFormatted(node)).toString().substring(2);
        }
        return guid;

    }

}
