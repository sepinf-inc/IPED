package dpf.sp.gpinf.carver.api;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.carver.api.Signature.SignatureType;

import java.util.ArrayList;
import java.util.Iterator;

public class CarverType {
    String name;
    String carverClass = null;
    String carverScript = null;
    MediaType mimeType;
    // public Signature[] sigs = new Signature[2];
    boolean bigendian;
    int sizePos = -1, sizeBytes;
    ArrayList<Signature> signatures = new ArrayList<Signature>();
    private Long minLength = null, maxLength = null;
    boolean hasFooter = false;
    boolean hasLengthRef = false;

    public CarverType() {

    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CarverType && ((CarverType) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    private Signature decodeSig(String str, SignatureType sigType) throws DecoderException {
        ArrayList<Byte> sigArray = new ArrayList<Byte>();
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) != '\\') {
                sigArray.add((byte) str.charAt(i));
            } else {
                char[] hex = { str.charAt(i + 1), str.charAt(i + 2) };
                byte[] hexByte = Hex.decodeHex(hex);
                sigArray.add(hexByte[0]);
                i += 2;
            }
        }

        // divide assinaturas com coringas em várias sem coringa
        Signature sig = new Signature(this, str, sigType);
        int i = 0;
        ArrayList<byte[]> seqs = new ArrayList<byte[]>();
        ArrayList<Integer> seqEnd = new ArrayList<Integer>();
        ArrayList<Byte> seq = new ArrayList<Byte>();
        for (Byte b : sigArray) {
            if (b != '?') {
                seq.add(b);
            }

            if (seq.size() > 0 && (b == '?' || (i + 1 == sigArray.size()))) {
                byte[] array = new byte[seq.size()];
                int j = 0;
                for (Byte b1 : seq) {
                    array[j++] = b1;
                }

                seqs.add(array);
                if (i + 1 == sigArray.size() && b != '?') {
                    seqEnd.add(i + 1);
                } else {
                    seqEnd.add(i);
                }
                seq = new ArrayList<Byte>();
            }
            i++;
        }

        sig.length = sigArray.size();
        sig.seqs = seqs.toArray(new byte[0][]);
        int[] seqEndPos = new int[seqEnd.size()];
        i = 0;
        for (int end : seqEnd) {
            seqEndPos[i++] = end;
        }
        sig.seqEndPos = seqEndPos;

        return sig;
    }

    public void addHeader(String sigString) throws DecoderException {
        this.signatures.add(decodeSig(sigString, SignatureType.HEADER));
    }

    public void addFooter(String sigString) throws DecoderException {
        this.signatures.add(decodeSig(sigString, SignatureType.FOOTER));
        hasFooter = true;
    }

    public void addSignature(String sigString, SignatureType sigType) throws DecoderException {
        this.signatures.add(decodeSig(sigString, sigType));
        if (sigType.equals(SignatureType.FOOTER)) {
            hasFooter = true;
        }
        if (sigType.equals(SignatureType.LENGTHREF)) {
            hasLengthRef = true;
        }
    }

    public MediaType getMimeType() {
        return mimeType;
    }

    public void setMimeType(MediaType mimeType) {
        this.mimeType = mimeType;
    }

    public Long getMinLength() {
        return minLength;
    }

    public void setMinLength(Long minLength) {
        this.minLength = minLength;
    }

    public void setMinLength(long minLength) {
        this.minLength = minLength;
    }

    public Long getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Long maxLength) {
        this.maxLength = maxLength;
    }

    public void setMaxLength(long maxLength) {
        this.maxLength = maxLength;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCarverClass() {
        return carverClass;
    }

    public void setCarverClass(String carverClass) {
        this.carverClass = carverClass;
    }

    public String getCarverScript() {
        return carverScript;
    }

    public void setCarverScript(String carverScript) {
        this.carverScript = carverScript;
    }

    public boolean isBigendian() {
        return bigendian;
    }

    public void setBigendian(boolean bigendian) {
        this.bigendian = bigendian;
    }

    public int getSizePos() {
        return sizePos;
    }

    public void setSizePos(int sizePos) {
        this.sizePos = sizePos;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public ArrayList<Signature> getSignatures() {
        return signatures;
    }

    public boolean addSignature(Signature sig) {
        return signatures.add(sig);
    }

    public boolean hasFooter() {
        return hasFooter;
    }

    public boolean hasLengthRef() {
        return hasLengthRef;
    }

}