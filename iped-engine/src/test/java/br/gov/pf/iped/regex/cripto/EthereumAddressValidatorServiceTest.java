package br.gov.pf.iped.regex.cripto;

import static org.junit.Assert.*;

import org.junit.Test;

import br.gov.pf.iped.regex.cripto.EthereumAddressValidatorService;

public class EthereumAddressValidatorServiceTest {

    EthereumAddressValidatorService service = new EthereumAddressValidatorService();

    @Test
    public void testValidEthereumAddress() {
        String EthereumAddress = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        assertEquals(true, service.validateEthereumAddress(EthereumAddress));
    }

    @Test
    public void testNotValidEthereumAddress() {
        String EthereumAddress = "0xfB6916095ca1df60bB7e92cE3Ea74c37c5d359";
        assertEquals(false, service.validateEthereumAddress(EthereumAddress));
    }

}
