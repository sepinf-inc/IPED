package br.gov.pf.iped.regex;

import static org.junit.Assert.*;

import org.junit.Test;

public class RippleAddressValidatorServiceTest {
    RippleAddressValidatorService service = new RippleAddressValidatorService();

    @Test
    public void testValidRippleAddressService() {
        assertTrue(service.validate("rMCa3o92WJDHDw7GLZiP85Pp3ZRkb87xUp"));
        assertTrue(service.validate("rfhzYEqyDBiKkdpPUbSdnuEenqbmfJCys5"));
        assertTrue(service.validate("rLedgerMAX4v6YjDTbKdccP2wPbN1XhdcX"));
        assertTrue(service.validate("rLedgerppMCmjqdtH873nBA35hcFicnKMg"));
        assertFalse(service.validate("edgerppMCmjqd2nBA35hcFicnKMg"));
        assertFalse(service.validate("rLedgerMAX442KdccP2wPbN1XhdcX"));
        assertFalse(service.validate("rfhzYEqyDBiKkdpPUbSd##nuEenqbmfJCys5"));
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
