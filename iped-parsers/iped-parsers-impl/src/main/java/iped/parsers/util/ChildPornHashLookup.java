package iped.parsers.util;

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
        return lookupHashAndMerge(mediaHash, null);
    }

    /**
     * Lookup a hash in the database and merge the result with a optionally provided
     * list (returned from a previous call to hash lookup). The hash algorithm (md5,
     * sha-1 or sha-256) is guessed from the hash length.
     */
    public static List<String> lookupHashAndMerge(String mediaHash, List<String> prev) {
        if (mediaHash == null || mediaHash.isEmpty() || lookupProviders.isEmpty())
            return prev != null ? prev : Collections.emptyList();
        String guessedAlgo = "";
        if (mediaHash.length() == 32)
            guessedAlgo = "md5";
        else if (mediaHash.length() == 40)
            guessedAlgo = "sha-1";
        else if (mediaHash.length() == 64)
            guessedAlgo = "sha-256";

        return lookupHashAndMerge(guessedAlgo, mediaHash, prev);
    }

    public static List<String> lookupHash(String algorithm, String hash) {
        return lookupHashAndMerge(algorithm, hash, null);
    }

    /**
     * Lookup a hash in the database and merge the result with a optionally provided
     * list (returned from a previous call to hash lookup), using a defined
     * algorithm.
     */
    public static List<String> lookupHashAndMerge(String algorithm, String hash, List<String> prev) {
        Set<String> hashsets = null;
        if (hash != null) {
            for (LookupProvider provider : lookupProviders) {
                List<String> sets = provider.lookupHash(algorithm, hash);
                if (sets != null) {
                    if (hashsets == null) {
                        hashsets = new HashSet<String>();
                    }
                    hashsets.addAll(sets);
                }
            }
        }
        if (hashsets == null || hashsets.isEmpty()) {
            return prev != null ? prev : Collections.emptyList();
        }
        if (prev != null) {
            hashsets.addAll(prev);
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
