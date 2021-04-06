package br.gov.pf.iped.regex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BitcoinAddressValidatorServiceTest {

        BitcoinAddressValidatorService service = new BitcoinAddressValidatorService();

        @Test
        public void testValidAddresses() {
        assertTrue(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i"));
        assertTrue(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nK9"));
        assertTrue(service.validate("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"));
        assertTrue(service.validate("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62j"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62X"));
        assertFalse(service.validate("1ANNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i"));
        assertFalse(service.validate("1A Na15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i"));
        assertFalse(service.validate("BZbvjr"));
        assertFalse(service.validate("i55j"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62!"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62iz"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62izz"));
        assertFalse(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nJ9"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
    }

}
