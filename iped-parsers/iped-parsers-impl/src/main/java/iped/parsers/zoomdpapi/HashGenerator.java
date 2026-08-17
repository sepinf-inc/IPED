package iped.parsers.zoomdpapi;

/**
 * Generates hashcat-compatible $DPAPImk$ hash strings from
 * DPAPI master key file data. Used for password cracking.
 *
 * @author Calil Khalil (Hakal)
 */
public class HashGenerator {

    public String generateHash(byte[] masterKeyFileData, String sid, String context) {
        try {
            DataReader reader = new DataReader(masterKeyFileData);

            reader.readDword(); // version
            reader.readDword();
            reader.readDword();
            reader.readStringUtf16(72); // guid
            reader.readDword();
            reader.readDword();
            reader.readDword(); // policy

            long masterkeyLen = reader.readQword();
            reader.readQword(); // backupkeyLen
            reader.readQword(); // credhistLen
            reader.readQword(); // domainkeyLen

            if (masterkeyLen > 0) {
                DataReader subReader = new DataReader(reader.readBytes((int) masterkeyLen));
                return generateMasterKeyHash(subReader, sid, context);
            }
        } catch (Exception e) { /* corrupt file */ }
        return null;
    }

    private String generateMasterKeyHash(DataReader data, String sid, String context) {
        data.skip(4); // version
        byte[] salt = data.readBytes(16);
        long rounds = data.readDword();
        String hashAlgo = getHashAlgoName(data.readDword());
        String cipherAlgo = getCipherAlgoName(data.readDword());
        byte[] ciphertext = data.readBytes(data.remaining());

        String hexCiphertext = CryptoUtil.bytesToHex(ciphertext);

        return "$DPAPImk$2*" + getContextValue(context) + "*" + sid + "*" +
               cipherAlgo + "*" + hashAlgo + "*" + rounds + "*" +
               CryptoUtil.bytesToHex(salt) + "*" + hexCiphertext.length() + "*" + hexCiphertext;
    }

    private String getHashAlgoName(long algId) {
        switch ((int) algId) {
            case 0x8004: return "sha1";
            case 0x800c: return "sha256";
            case 0x800d: return "sha384";
            case 0x800e: return "sha512";
            default: return "unknown";
        }
    }

    private String getCipherAlgoName(long algId) {
        switch ((int) algId) {
            case 0x6603: return "des3";
            case 0x6610:
            case 0x6611: return "aes256";
            case 0x660e: return "aes128";
            case 0x660f: return "aes192";
            default: return "unknown";
        }
    }

    private int getContextValue(String context) {
        switch (context.toLowerCase()) {
            case "local": return 1;
            case "domain":
            case "domain1607-": return 2;
            case "domain1607+": return 3;
            default: return 1;
        }
    }
}
