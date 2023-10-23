package iped.engine.task.regex.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlacaRegexValidatorServiceTest {

    PlacaRegexValidatorService service = new PlacaRegexValidatorService();

    @Test
    public void testCarLicensePlateService() {

        String plate = "Jhl-2330";
        assertEquals("JHL2330", service.format(plate));
    }

    @Test
    public void testValidCarLicensePlateService() {

        assertTrue(service.validate("CAR PLATE JJJ-2442"));
    }
}
