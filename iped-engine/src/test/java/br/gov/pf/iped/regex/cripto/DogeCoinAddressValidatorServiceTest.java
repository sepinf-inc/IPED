package br.gov.pf.iped.regex.cripto;

import static org.junit.Assert.*;

import org.junit.Test;

import br.gov.pf.iped.regex.cripto.BCHAddressValidatorService;

public class DogeCoinAddressValidatorServiceTest {

    @Test
    public void testValidDogeCoinService() {

        DogeCoinAddressValidatorService service = new DogeCoinAddressValidatorService();

        assertTrue(service.validate("DCAjVmVVZZG3KcEhUGRC2kF3caxB7huvP7"));
        assertTrue(service.validate("ACSbgj91BsjdpuG6pBkG9LXtCTFaH4mn5a"));
        assertTrue(service.validate("9rUiQiJG7L4X1UCT7UaTVuZHiWvqLQ5S43"));
        assertTrue(service.validate("9wsyK52K9j8kzVyx2pm4cNZk4YxgERu6Ju"));
        assertTrue(service.validate("A6m6JuB7fRQmeubCKZKy6PEYQrsZa8EzTr"));
        assertFalse(service.validate("DCAjVmVVZZG3ccEhUGRC2kF3caxB7huvP7"));
        assertFalse(service.validate("ACSbgj91BsjduuG6pBkG9LXtCTFaH4mn5a"));
        assertFalse(service.validate("9wsyK52K9j8kzVyx4pm4cNZk4YxgERu6Ju"));
        assertFalse(service.validate("1A A6m6JuB7fRQmeubCKZKy6PEYQrsZa8EzTr"));
        assertFalse(service.validate("BZbvjr"));
        assertFalse(service.validate("i55j"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3ufrmpn3#!"));
        assertFalse(service.validate("qpkvl5wdksmcsuu9xms3xdczflputqfjl7pz"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62izz"));
        assertFalse(service.validate("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nJ9"));
        assertFalse(service.validate("1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62I"));
    }

}
