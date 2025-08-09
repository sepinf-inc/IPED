package iped.engine.task.regex.validator.crypto;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RunAutomaton;
import dk.brics.automaton.RunAutomaton;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.AutomatonMatcher;

import org.junit.Test;

public class TronAddressValidatorServiceTest {

    TronAddressValidatorService service = new TronAddressValidatorService();

    @Test
    public void testMatchDefaultRegex() {
        RegExp regex = new RegExp("\bT[a-km-zA-HJ-NP-Z1-9]{33}\b");
        Automaton automaton = regex.toAutomaton(new DatatypesAutomatonProvider());
        RunAutomaton pattern = new RunAutomaton(automaton);
        AutomatonMatcher fullMatcher = pattern.newMatcher("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
        assertTrue(fullMatcher.find());
    }

    @Test
    public void testValidTronAddress() {
        String tronAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
        assertTrue(service.validateTronAddress(tronAddress));
    }

    @Test
    public void testNotValidTronAddress() {
        String tronAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjkj8t";
        assertFalse(service.validateTronAddress(tronAddress));
    }

}