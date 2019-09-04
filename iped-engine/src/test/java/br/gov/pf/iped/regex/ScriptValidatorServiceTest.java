package br.gov.pf.iped.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;

import org.junit.Test;

public class ScriptValidatorServiceTest {

    @Test
    public void testRegisterScript() throws Exception {
        File file = Paths.get(getClass().getClassLoader().getResource("TestValidator.js").toURI()).normalize().toFile();
        ScriptValidatorService service = new ScriptValidatorService();
        service.registerScript(file);

        assertEquals(1, service.getRegexNames().size());
        assertEquals("TEST", service.getRegexNames().get(0));
    }

    @Test
    public void testScriptValidate() throws Exception {
        File file = Paths.get(getClass().getClassLoader().getResource("TestValidator.js").toURI()).normalize().toFile();
        ScriptValidatorService service = new ScriptValidatorService();
        service.registerScript(file);

        assertFalse(service.validate("TEST", "boloks"));
        assertTrue(service.validate("TEST", "amigo"));
    }

    @Test
    public void testScriptFormat() throws Exception {
        File file = Paths.get(getClass().getClassLoader().getResource("TestValidator.js").toURI()).normalize().toFile();
        ScriptValidatorService service = new ScriptValidatorService();
        service.registerScript(file);

        assertEquals("AMIGO", service.format("TEST", "amigo"));
    }

}
