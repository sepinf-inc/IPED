package iped.engine.task.regex.validator.swift;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import iped.engine.task.regex.validator.swift.SwiftCodeRegexValidatorService;

public class SwiftCodeRegexValidatorServiceTest {

    private SwiftCodeRegexValidatorService service = new SwiftCodeRegexValidatorService();

    @Test
    public void testValidSwift() {
        String swift = "BACAADAD";
        assertTrue(service.validate(swift));
    }

    @Test
    public void testInvalidSwift() {
        String swift = "XXXXADXX";
        assertFalse(service.validate(swift));
    }

}