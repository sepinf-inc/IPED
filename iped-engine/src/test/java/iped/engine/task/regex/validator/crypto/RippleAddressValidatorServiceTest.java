package iped.engine.task.regex.validator.crypto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import iped.engine.task.regex.validator.crypto.RippleAddressValidatorService;

public class RippleAddressValidatorServiceTest {
    RippleAddressValidatorService service = new RippleAddressValidatorService();

    @Test
    public void testValidRippleAddressService() {
        assertTrue(service.validate("rMCa3o92WJDHDw7GLZiP85Pp3ZRkb87xUp"));
        assertTrue(service.validate("rfhzYEqyDBiKkdpPUbSdnuEenqbmfJCys5"));
        assertTrue(service.validate("rLedgerMAX4v6YjDTbKdccP2wPbN1XhdcX"));
        assertTrue(service.validate("rLedgerppMCmjqdtH873nBA35hcFicnKMg"));
        assertTrue(service.validate("XV5sbjUmgPpvXv4ixFWZ5ptAYZ6PD28Sq49uo34VyjnmK5H"));
        assertFalse(service.validate("edgerppMCmjqd2nBA35hcFicnKMg"));
        assertFalse(service.validate("rLedgerMAX442KdccP2wPbN1XhdcX"));
        assertFalse(service.validate("rfhzYEqyDBiKkdpPUbSd##nuEenqbmfJCys5"));
        assertFalse(service.validate("XV5sbjUmgPpvXv4ixFWZ5ptAYZ6PD28Sq49uo34VyjmmK5H"));
        assertFalse(service.validate("1A NrfhzYEqyDBiKkdpPUbSdnuEenqbmfJCys5gFiqJ2i7Z2DPU2J6hW62i"));
        assertFalse(service.validate("jDTbKdccP2w"));
        assertFalse(service.validate("LedgerM"));
        assertFalse(service.validate("rMCa3o92WJDHDw7GLZiP85Pp3ZRkb87xUp!"));
        assertFalse(service.validate("rMCa3o92##7GLZiP85Pp3ZRkb87xUp"));
        assertFalse(service.validate("rMCa3o$Pp3ZRkb87xUp"));
        assertFalse(service.validate("rM!@GLZiP85Pp3ZRkb87xUp"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
    }

}
