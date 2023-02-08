package iped.parsers.evtx.model;

public class EvtxInvalidChunkHeaderException extends EvtxParseException {

    private static final long serialVersionUID = -55008009580772538L;
    String header;

    public EvtxInvalidChunkHeaderException(String header) {
        super("Invalid Chunk Header Signature");
        this.header = header;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

}
