package dpf.sp.gpinf.indexer.parsers.util;

import java.util.Arrays;
import java.util.HashMap;

import dpf.sp.gpinf.indexer.util.HashValue;
import iped3.IHashValue;

public class LedHashes {

    public static HashMap<String, IHashValue[]> hashMap = null;

    public static boolean lookupHashDatabase(String mediaHash) {
        if (mediaHash == null)
            return false;
        String guessedAlgo = "";
        if (mediaHash.length() == 32)
            guessedAlgo = "md5";
        else if (mediaHash.length() == 40)
            guessedAlgo = "sha-1";
        else if (mediaHash.length() == 64)
            guessedAlgo = "sha-256";

        return lookupHashDatabase(guessedAlgo, mediaHash);
    }

    public static boolean lookupHashDatabase(String hashAlgo, String mediaHash) {
        if (LedHashes.hashMap != null && mediaHash != null) {
            if (LedHashes.hashMap.get(hashAlgo) != null) {
                HashValue hash = new HashValue(mediaHash);
                if (hash != null && Arrays.binarySearch(LedHashes.hashMap.get(hashAlgo), hash) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

}
