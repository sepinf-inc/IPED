package dpf.sp.gpinf.indexer.parsers.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<String> hashsets = new HashSet<String>();
        if (hash != null) {
            for (LookupProvider provider : lookupProviders) {
                List<String> sets = provider.lookupHash(algorithm, hash);
                if (sets != null) {
                    hashsets.addAll(sets);
                }
            }
        }
        List<String> l = new ArrayList<String>(hashsets);
        Collections.sort(l);
        return l;
    }

    public static abstract class LookupProvider {

        /**
         * @param hashAlgo Hash algorithm.
         * @param hash Hash to look up
         * @return A list of hash sets in which the hash was found.
         */
        public abstract List<String> lookupHash(String hashAlgo, String hash);

    }

}
