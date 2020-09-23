package br.gov.pf.iped.regex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IPLRegexValidatorServiceTest {

    private IPLRegexValidatorService service = new IPLRegexValidatorService();

    @Test
    public void testFormat() {
        String ipl = "RE_180-2018-01";
        assertEquals("RE 180-2018-01", service.format(ipl));
    }

    @Test
    public void testFormatRE() {
        String ipl = "IPL_2910-2013-01";
        assertEquals("IPL 2910-2013-01", service.format(ipl));
    }

    @Test
    public void testSimplerFormat() {
        String ipl = "IPL 2910-2013-01";
        assertEquals("IPL 2910-2013-01", service.format(ipl));
    }

}
