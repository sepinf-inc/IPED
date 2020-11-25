package dpf.sp.gpinf.indexer.parsers.util;

import java.util.ArrayList;

public class ChildPornHashLookup {
    
    private static ArrayList<LookupProvider> lookupProviders = new ArrayList<>();

    public static void addLookupProvider(LookupProvider provider) {
        lookupProviders.add(provider);
    }

    public static void dispose() {
        lookupProviders.clear();
    }

    public static boolean lookupHash(String mediaHash) {
        if (mediaHash == null || lookupProviders.isEmpty())
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
            for (LookupProvider provider : lookupProviders) {
                if (provider.lookupHash(algorithm, hash)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static abstract class LookupProvider {

        public abstract boolean lookupHash(String hashAlgo, String hash);

    }

}
