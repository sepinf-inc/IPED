package iped.engine.task.regex.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RENAVAMRegexValidatorServiceTest {

    private final RENAVAMRegexValidatorService service = new RENAVAMRegexValidatorService();

    // --- valid ---

    @Test
    public void testValid11Digits() {
        // sum=156, rem=2, check=9
        assertTrue(service.validate("00123456789"));
    }

    @Test
    public void testValid9DigitsNormalized() {
        // normalizes to 00123456789
        assertTrue(service.validate("123456789"));
    }

    @Test
    public void testValid10DigitsNormalized() {
        // normalizes to 00123456789
        assertTrue(service.validate("0123456789"));
    }

    @Test
    public void testValidSecondExample() {
        // base=0098765432, sum=284, rem=9, check=2
        assertTrue(service.validate("00987654322"));
    }

    @Test
    public void testValidRemainderZeroGivesCheckZero() {
        // base=0000000031, sum=11, rem=0, check=0
        assertTrue(service.validate("00000000310"));
    }

    // --- invalid check digit ---

    @Test
    public void testInvalidWrongCheckDigit() {
        assertFalse(service.validate("00123456780"));
    }

    @Test
    public void testInvalidWrongCheckDigitSecondExample() {
        assertFalse(service.validate("00987654321"));
    }

    // --- invalid structure ---

    @Test
    public void testNullIsInvalid() {
        assertFalse(service.validate(null));
    }

    @Test
    public void testEmptyIsInvalid() {
        assertFalse(service.validate(""));
    }

    @Test
    public void testTooShortIsInvalid() {
        assertFalse(service.validate("12345678"));
    }

    @Test
    public void testTooLongIsInvalid() {
        assertFalse(service.validate("001234567890"));
    }

    @Test
    public void testAllZerosIsInvalid() {
        // degenerate: all same base digits — sum=0, rem=0, check=0 matches, but must reject
        assertFalse(service.validate("00000000000"));
    }

    @Test
    public void testAllSameBaseDigitIsInvalid() {
        // base=1111111111, all same
        assertFalse(service.validate("11111111116"));
    }

    // --- format ---

    @Test
    public void testFormat9DigitsPadsTo11() {
        assertEquals("00123456789", service.format("123456789"));
    }

    @Test
    public void testFormat11DigitsUnchanged() {
        assertEquals("00123456789", service.format("00123456789"));
    }

    @Test
    public void testFormatStripsNonDigits() {
        assertEquals("00123456789", service.format("00123456789"));
    }

}
