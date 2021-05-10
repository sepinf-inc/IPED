package br.gov.pf.iped.regex;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AltcoinBase58CheckValidator {
    private static final Base58CheckedValidator base58validator = new Base58CheckedValidator(
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray());

    private Map<String, Set<Integer>> prefixToVersionMap = new HashMap<>();
    private Set<Integer> prefixSizes = new LinkedHashSet<>();

    public AltcoinBase58CheckValidator() {
    }
    
    public void setVersionForPrefix(String prefix, int version) {
        Set<Integer> versions = prefixToVersionMap.get(prefix);
        if (versions == null) {
            versions = new HashSet<>();
            prefixToVersionMap.put(prefix, versions);
        }
        versions.add(version);
        prefixSizes.add(prefix.length());
    }
    
    public boolean validate(String addr) {
        int addressHeader = -1;
        try {
            addressHeader = getAddressHeader(addr);
        } catch (Exception x) {
        }
        
        if (addressHeader >= 0 ) {
            for (int size : prefixSizes) {
                String addrPrefix = addr.substring(0, size);
                Set<Integer> versions = prefixToVersionMap.get(addrPrefix);
                if (versions != null) {
                    return versions.contains(addressHeader);
                }
            }
        }
        
        return false;
    }

    private static int getAddressHeader(String address) throws IOException {
        byte[] tmp = base58validator.decodeChecked(address);
        return tmp[0] & 0xFF;
    }
}
