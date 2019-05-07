package dpf.sp.gpinf.carver.api;

public class Signature {
    public byte[][] seqs = null;
    public int[] seqEndPos;
    SignatureType signatureType;
    String sigString;

    public SignatureType getSignatureType() {
        return signatureType;
    }

    CarverType carverType = null;
    int length;

    public Signature(CarverType carverType, String sigString, SignatureType sigType) {
        this.carverType = carverType;
        this.sigString = sigString;
        this.signatureType = sigType;
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

    public enum SignatureType {
        HEADER, FOOTER, ESCAPEFOOTER, LENGTHREF, CONTROL;
    }

    public String getSigString() {
        return sigString;
    }

    public void setSigString(String sigString) {
        this.sigString = sigString;
    }
}
