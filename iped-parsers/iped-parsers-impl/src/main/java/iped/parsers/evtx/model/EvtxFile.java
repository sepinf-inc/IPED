package iped.parsers.evtx.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.util.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.parsers.evtx.template.TemplateData;

public class EvtxFile {

    private static final Logger logger = LoggerFactory.getLogger(EvtxFile.class);

    HashMap<Integer, TemplateData> templateDatas = new HashMap<Integer, TemplateData>();
    HashMap<Integer, EvtxXmlFragment> templateXmls = new HashMap<Integer, EvtxXmlFragment>();

    byte[] header = new byte[4096];
    byte[] curChunk = new byte[64 * 1024];
    int chunckCount = 0;
    String name;

    boolean dirty = false;

    long totalCount = 0;

    ArrayList<Object> templateValues = new ArrayList<Object>();

    EvtxRecordConsumer evtxRecordConsumer;
    private InputStream is;

    public EvtxFile(InputStream is) {
        this.is = is;
    }

    public void processFile() throws IOException, EvtxParseException {
        BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);

        bis.readNBytes(header, 0, header.length);

        ByteBuffer bb = ByteBuffer.wrap(header);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        String sig = new String(ArrayUtil.copyOfSubArray(header, 0, 8));

        if (!sig.equals("ElfFile\0")) {
            throw new EvtxParseException("Invalid header signature");
        }
        long firstChunckNumber = bb.asLongBuffer().get(1);
        long lastChunckNumber = bb.asLongBuffer().get(2);
        long nextRecordIdentifier = bb.asLongBuffer().get(2);
        int flags = bb.asIntBuffer().get(30);
        chunckCount = bb.asShortBuffer().get(21);
        if (flags == 0x0001) {
            // isDirty (not commited) so try to parse another chunk
            dirty = true;
        }

        boolean eof = false;
        for (int i = 0; !eof; i++) {
            int read = bis.readNBytes(curChunk, 0, curChunk.length);
            if (read == curChunk.length) {
                try {
                    EvtxChunk chunk = new EvtxChunk(this, curChunk);
                    chunk.processChunk();
                } catch (EvtxParseException e) {
                    if (e instanceof EvtxInvalidChunkHeaderException) {
                        if (i < chunckCount) {
                            if (!dirty) {
                                logger.warn("Invalid chunk header found on non dirty evtx file: {}", ((EvtxInvalidChunkHeaderException) e).getHeader());
                            } else {
                                logger.warn("Invalid chunk header found before end of chunckcount on evtx file: {}", ((EvtxInvalidChunkHeaderException) e).getHeader());
                            }
                        }
                        // if the file is dirty ignores parsing with no error because it can be normal
                        // to occur
                    } else {
                        e.printStackTrace();
                    }
                } finally {
                    templateXmls.clear();
                }
            } else {
                eof = true;
            }
        }
    }

    public EvtxRecordConsumer getEvtxRecordConsumer() {
        return evtxRecordConsumer;
    }

    public void setEvtxRecordConsumer(EvtxRecordConsumer evtxRecordConsumer) {
        this.evtxRecordConsumer = evtxRecordConsumer;
    }

    public void addTemplateData(int offset, TemplateData templateDefinition) {
        templateDatas.put(offset, templateDefinition);
    }

    public TemplateData getTemplateData(int offset) {
        return templateDatas.get(offset);
    }

    public void addTemplateXml(int offset, EvtxXmlFragment templateXml) {
        templateXmls.put(offset, templateXml);
    }

    public EvtxXmlFragment getTemplateXml(int offset) {
        return templateXmls.get(offset);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getRecordCount() {
        return totalCount;
    }

    public boolean isDirty() {
        return dirty;
    }
}
