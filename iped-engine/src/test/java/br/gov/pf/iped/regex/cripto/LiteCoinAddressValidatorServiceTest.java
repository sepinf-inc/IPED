package br.gov.pf.iped.regex.cripto;

import static org.junit.Assert.*;

import org.junit.Test;

import br.gov.pf.iped.regex.cripto.BCHAddressValidatorService;

public class LiteCoinAddressValidatorServiceTest {

    @Test
    public void testValidLiteCoinService() {

        LiteCoinAddressValidatorService service = new LiteCoinAddressValidatorService();

        assertTrue(service.validate("LT1ftmvF7AXsy912AWkcRMrLnoYiay2DmV"));
        assertTrue(service.validate("M9NGjPmTEKGWFZhZ7nTidfDeqrewiWHYpG"));
        assertTrue(service.validate("ltc1qxj2jvw2jlf9m84fshrm5045ydjytk24m0cjjlz"));
        assertTrue(service.validate("ltc1q4slde0705ja35qy782528c32wl8cr7wek0gt62"));
        assertFalse(service.validate("LT1ftmvF7AXsy912AWkcRMrLnnYiay2DmV"));
        assertFalse(service.validate("M9NGjPmTEKGWFZhZ7nTidfDeqrewiWHYiG"));
        assertFalse(service.validate("ltc1qxj2jvw2jlf9m84fshrm5045ydjykk24m0cjjlz"));
        assertFalse(service.validate("1A ltc1q4slde0705ja35qy782528c32wl8cr7wek0gt62"));
        assertFalse(service.validate("BZbvjr"));
        assertFalse(service.validate("i55j"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn3#!"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3xdczflputqfjl7pz"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62izz"));
        assertFalse(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nJ9"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
    }

}
