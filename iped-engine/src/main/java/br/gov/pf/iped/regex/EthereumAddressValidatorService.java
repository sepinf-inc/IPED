package br.gov.pf.iped.regex;

import java.io.File;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import dpf.sp.gpinf.indexer.process.task.regex.BasicAbstractRegexValidatorService;

/**
 * Validate Ethereum address encoded as in EIP-55
 * 
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 *
 */
public class EthereumAddressValidatorService extends BasicAbstractRegexValidatorService {

    private static final MessageDigest digest;
    private static final int[] MASKS = { 128, 8 };

    static {
        Security.addProvider(new BouncyCastleProvider());
        digest = new Keccak.Digest256();
    }

    @Override
    public void init(File confDir) {
        // Nothing to do.
    }

    @Override
    public List<String> getRegexNames() {
        return Arrays.asList("CRIPTOCOIN_ETHEREUM");
    }

    @Override
    protected boolean validate(String hit) {
        return validateEthereumAddress(hit);
    }

    public boolean validateEthereumAddress(String addr) {
        // remove the 0x prefix
        addr = addr.substring(2);
        byte[] keccak = null;

        synchronized (digest) {
            digest.reset();
            digest.update(addr.toLowerCase().getBytes());
            keccak = digest.digest();
        }

        for (int i = 0; i < addr.length(); i++) {
            char c = addr.charAt(i);
            int bit = keccak[i / 2] & MASKS[i % 2];

            if (Character.isLetter(c)) {
                if (Character.isLowerCase(c)) {
                    if (bit != 0) {
                        return false;
                    }
                } else {
                    if (bit == 0) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
