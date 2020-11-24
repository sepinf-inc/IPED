package dpf.sp.gpinf.indexer.parsers.util;

import java.util.ArrayList;

public class ChildPornHashLookup {
    
    private static ArrayList<LookupHashSet> hashSets = new ArrayList<>();

    public static void addLookupHashSet(LookupHashSet hashSet) {
        hashSets.add(hashSet);
    }

    public static void dispose() {
        hashSets.clear();
    }

    public static boolean lookupHash(String mediaHash) {
        if (mediaHash == null || hashSets.isEmpty())
            return false;
        String guessedAlgo = "";
        if (mediaHash.length() == 32)
            guessedAlgo = "md5";
        else if (mediaHash.length() == 40)
            guessedAlgo = "sha-1";
        else if (mediaHash.length() == 64)
            guessedAlgo = "sha-256";

        return lookupHash(guessedAlgo, mediaHash);
    }

    public static boolean lookupHash(String algorithm, String hash) {
        if (hash != null) {
            for (LookupHashSet hashSet : hashSets) {
                if (hashSet.lookupHash(algorithm, hash)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static abstract class LookupHashSet {

        public abstract boolean lookupHash(String hashAlgo, String hash);

    }

}
