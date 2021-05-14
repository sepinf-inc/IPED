package br.gov.pf.iped.regex.cripto;

import static org.junit.Assert.*;

import org.junit.Test;

import br.gov.pf.iped.regex.cripto.BCHAddressValidatorService;

public class DashAddressValidatorServiceTest {

    @Test
    public void testValidDashAddressService() {

        DashAddressValidatorService service = new DashAddressValidatorService();

        assertTrue(service.validate("XuA85gm2KNBxbJfRXQ3SWURKVtFcbUeDm8"));
        assertTrue(service.validate("XjLdaXaTfEWUv6CtKzd31es5RZyLn8xvgw"));
        assertTrue(service.validate("XmkJKvFwqAzFdV7afGkMU3fcs6Zz4fwQ3x"));
        assertTrue(service.validate("XbYC7U4k72Dz1LM5MGpuXTXhEeHmSoasGF"));
        assertFalse(service.validate("XjLdaXaTfEWvv6CtKzd31es5RZyLn8xvgw"));
        assertFalse(service.validate("XmkJKvFwqAzFdV7afGkMU3fcc6Zz4fwQ3x"));
        assertFalse(service.validate("XuA85gm2KNBxbJfRXQ3sWURKVtFcbUeDm8"));
        assertFalse(service.validate("1A XbYC7U4k72Dz1LM5MGpuXTXhEeHmSoasGF"));
        assertFalse(service.validate("BZbvjr"));
        assertFalse(service.validate("i55j"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn3#!"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3xdczflputqfjl7pz"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62izz"));
        assertFalse(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nJ9"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
    }

}
