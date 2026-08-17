package iped.parsers.zoomdpapi;

/**
 * Parses and represents a $DPAPImk$ hash string used for
 * password cracking against DPAPI master keys.
 *
 * Format: $DPAPImk$version*context*sid*cipher*hash*rounds*salt*len*ciphertext
 *
 * @author Calil Khalil (Hakal)
 */
public class DPAPIHash {

    int version;
    int context;
    String sid;
    String cipherAlgo;
    String hashAlgo;
    long rounds;
    byte[] salt;
    byte[] ciphertext;

    public static DPAPIHash parse(String hashLine) {
        if (hashLine == null || !hashLine.startsWith("$DPAPImk$")) return null;

        String[] parts = hashLine.split("\\*");
        if (parts.length < 9) return null;

        try {
            DPAPIHash hash = new DPAPIHash();
            hash.version = Integer.parseInt(parts[0].substring(9));
            hash.context = Integer.parseInt(parts[1]);
            hash.sid = parts[2];
            hash.cipherAlgo = parts[3];
            hash.hashAlgo = parts[4];
            hash.rounds = Long.parseLong(parts[5]);
            hash.salt = CryptoUtil.hexToBytes(parts[6]);
            hash.ciphertext = CryptoUtil.hexToBytes(parts[8]);
            return hash;
        } catch (Exception e) {
            return null;
        }
    }

    int getCipherKeyLength() {
        switch (cipherAlgo) {
            case "aes256": return 256;
            case "aes192": return 192;
            case "aes128": return 128;
            case "des3": return 192;
            default: return 256;
        }
    }

    int getIVLength() {
        return cipherAlgo.startsWith("aes") ? 128 : 64;
    }

    int getHashDigestLength() {
        switch (hashAlgo) {
            case "sha512": return 64;
            case "sha384": return 48;
            case "sha256": return 32;
            case "sha1": return 20;
            default: return 32;
        }
    }
}
