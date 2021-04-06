package br.gov.pf.iped.regex;

import static org.junit.Assert.*;

import org.junit.Test;

public class BCHAddressValidatorServiceTest {

    @Test
    public void testValidBHCAddressService() {
        
        BCHAddressValidatorService service = new BCHAddressValidatorService();

        assertTrue(service.validate("bitcoincash:pqkh9ahfj069qv8l6eysyufazpe4fdjq3u4hna323j"));
        assertTrue(service.validate("qrd9khmeg4nqag3h5gzu8vjt537pm7le85lcauzezc"));
        assertTrue(service.validate("qrr60v3ld0vchz402fa7kujdm22xpvdudcxjzpagu0"));
        assertTrue(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn33czflputqfjl7pr"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn33czflputqfjl7pz"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn33czflputqfjl7J"));
        assertFalse(service.validate("qrr60v3ld0vchz4427kujdm22xpvdudcxjzpagu0"));
        assertFalse(service.validate("1A qpkvl5wdksmcsuu9xms3ufrmpn33czflputqfjl7pz"));
        assertFalse(service.validate("BZbvjr"));
        assertFalse(service.validate("i55j"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn3#!"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3xdczflputqfjl7pz"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62izz"));
        assertFalse(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nJ9"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
        
        assertTrue(service.validateBitcoinCashAddress("qr4wp5humgezrr302vcarrpscawzesunav7tgsc2xl"));
        assertTrue(service.validateBitcoinCashAddress("qrsp7hvfsatc7763cdwwzd8jrjpa55r3gqeldy3gcy"));
        assertTrue(service.validateBitcoinCashAddress("qqpjhzesn9eaj028h9wj6ped9dqvev6w5yj57jwk9n"));
        assertFalse(service.validateBitcoinCashAddress("qr4wp5humgezrr3023rrpscawzesunav7tgsc2xl"));
        assertFalse(service.validateBitcoinCashAddress("qr4wp5humgezrr30 23rrpsca!esunav7tgsc2xl"));
        assertFalse(service.validateBitcoinCashAddress("qr4wp5humgezrr3023rrpscaw!sunav7tgsc2xl"));
        assertFalse(service.validateBitcoinCashAddress("qr4wp5humgezrr3#qrsp7hvfsatc7763cdwwzd8jrjpa55r3gqeldy3gcy"));
        assertFalse(service.validateBitcoinCashAddress("###"));

    }

}
