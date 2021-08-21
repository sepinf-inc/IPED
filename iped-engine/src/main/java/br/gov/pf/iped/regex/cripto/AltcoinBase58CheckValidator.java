package br.gov.pf.iped.regex.cripto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AltcoinBase58CheckValidator {
    private static final Base58CheckedValidator base58validator = new Base58CheckedValidator(
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray());

    private Map<String, Set<ByteBuffer>> prefixToVersionMap = new HashMap<>();
    private int maxPrefixSize = 0;

    public AltcoinBase58CheckValidator() {
    }
    
    
    public void setVersionForPrefix(String prefix, byte ... version) {
        Set<ByteBuffer> versions = prefixToVersionMap.get(prefix);
        if (versions == null) {
            versions = new HashSet<>();
            prefixToVersionMap.put(prefix, versions);
        }
        versions.add(ByteBuffer.wrap(version));
        maxPrefixSize = Integer.max(maxPrefixSize, version.length);
    }
    
    public boolean validate(String addr) {
        byte [] addressHeader = null;
        try {
            addressHeader = getAddressHeader(addr, maxPrefixSize);
            
            for (Map.Entry<String, Set<ByteBuffer>> entry : prefixToVersionMap.entrySet()) {
                if (addr.startsWith(entry.getKey())) {
                    for (ByteBuffer validHeaderBuffer : entry.getValue()) {
                        ByteBuffer addrHeaderBuff = ByteBuffer.wrap(addressHeader, 0, validHeaderBuffer.limit());
                        if (addrHeaderBuff.equals(validHeaderBuffer)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        } catch (Exception x) {
        }
        
        return false;
    }

    public static byte[] getAddressHeader(String address, int size) throws IOException {
        byte[] tmp = base58validator.decodeChecked(address);
        byte[] result = new byte[size];
        System.arraycopy(tmp, 0, result, 0, size);
        return result;
    }
}
