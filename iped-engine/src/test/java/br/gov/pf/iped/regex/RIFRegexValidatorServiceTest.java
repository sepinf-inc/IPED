package br.gov.pf.iped.regex;

import static org.junit.Assert.*;

import org.junit.Test;

public class RIFRegexValidatorServiceTest {
    RIFRegexValidatorService service = new RIFRegexValidatorService();

    @Test
    public void testValidRIFFormatService() {

        String rif = "RIF |xxY 123456789";
        assertEquals("RIF 123456789", service.format(rif));
    }

    @Test
    public void testValidRIFService() {

        assertTrue(service.validate("RIf 100029430"));
    }

}
