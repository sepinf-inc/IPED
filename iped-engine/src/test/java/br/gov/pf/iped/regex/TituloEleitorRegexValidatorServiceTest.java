package br.gov.pf.iped.regex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TituloEleitorRegexValidatorServiceTest {

    private TituloEleitorRegexValidatorService service = new TituloEleitorRegexValidatorService();

    @Test
    public void testValidTituloEleitor() {
        String tituloEleitor = "270343380159";
        assertTrue(service.validate(tituloEleitor));
    }

    @Test
    public void testInvalidTituloEleitor() {
        String tituloEleitor = "270343380151";
        assertFalse(service.validate(tituloEleitor));
    }

}
