package dpf.sp.gpinf.indexer.parsers.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChildPornHashLookup {
    
    private static ArrayList<LookupProvider> lookupProviders = new ArrayList<>();

    public static void addLookupProvider(LookupProvider provider) {
        lookupProviders.add(provider);
    }

    public static void dispose() {
        lookupProviders.clear();
    }

    public static List<String> lookupHash(String mediaHash) {
        if (mediaHash == null || mediaHash.isEmpty() || lookupProviders.isEmpty())
            return Collections.EMPTY_LIST;
        String guessedAlgo = "";
        if (mediaHash.length() == 32)
            guessedAlgo = "md5";
        else if (mediaHash.length() == 40)
            guessedAlgo = "sha-1";
        else if (mediaHash.length() == 64)
            guessedAlgo = "sha-256";

        return lookupHash(guessedAlgo, mediaHash);
    }

    public static List<String> lookupHash(String algorithm, String hash) {
        ArrayList<String> hashsets = new ArrayList<>();
        if (hash != null) {
            for (LookupProvider provider : lookupProviders) {
                String hashSet = provider.lookupHash(algorithm, hash);
                if (hashSet != null) {
                    hashsets.add(hashSet);
                }

            }
        }
        return hashsets;
    }

    public static abstract class LookupProvider {

        /**
         * @param hashAlgo
         *            hash algorithm
         * @param hash
         *            hash to look up
         * @return hashset id where hash was found.
         */
        public abstract String lookupHash(String hashAlgo, String hash);

    }

}
