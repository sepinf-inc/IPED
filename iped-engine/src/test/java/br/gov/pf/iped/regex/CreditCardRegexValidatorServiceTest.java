package br.gov.pf.iped.regex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CreditCardRegexValidatorServiceTest {

    private CreditCardRegexValidatorService service = new CreditCardRegexValidatorService();

    @Test
    public void testValidCreditCard() {
        String creditCard = "4369811367191811";
        assertTrue(service.validate(creditCard));
    }

    @Test
    public void testInvalidCreditCard() {
        String creditCard = "4369811367191812";
        assertFalse(service.validate(creditCard));
    }

}
