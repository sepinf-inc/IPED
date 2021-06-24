package dpf.sp.gpinf.carver.api;

import java.io.Serializable;

public class Signature implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    CarverType carverType = null;
    public byte[][] seqs = null;
    public int[] seqEndPos;
    SignatureType signatureType;
    String sigString;
    int length;

    public Signature(CarverType carverType, String sigString, SignatureType sigType) {
        this.carverType = carverType;
        this.sigString = sigString;
        this.signatureType = sigType;
    }

    public SignatureType getSignatureType() {
        return signatureType;
    }

    public CarverType getCarverType() {
        return carverType;
    }

    public boolean isFooter() {
        return signatureType == SignatureType.FOOTER;
    }

    public boolean isHeader() {
        return signatureType == SignatureType.HEADER;
    }

    public int getLength() {
        return length;
    }

    public enum SignatureType implements Serializable {
        HEADER, FOOTER, ESCAPEFOOTER, LENGTHREF, CONTROL;
    }

    public String getSigString() {
        return sigString;
    }

    public void setSigString(String sigString) {
        this.sigString = sigString;
    }
}
