package iped.engine.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

public class RegexTaskConfigTest {

    // same compilation path used by RegexTask
    private static RunAutomaton compile(String regex) {
        String replaced = RegexTaskConfig.replace(regex);
        return new RunAutomaton(new RegExp(replaced).toAutomaton(new DatatypesAutomatonProvider()));
    }

    @Test
    public void testNonWhitespaceClassDoesNotAbsorbRestOfRegex() {
        RunAutomaton pattern = compile("foo\\S\\d");
        assertTrue(pattern.run("fooX1"));
        assertFalse(pattern.run("foo 1"));
        assertFalse(pattern.run("fooX"));
    }

    @Test
    public void testPredefinedCharacterClasses() {
        assertTrue(compile("a\\sb").run("a b"));
        assertFalse(compile("a\\sb").run("axb"));

        assertTrue(compile("a\\Sb").run("axb"));
        assertFalse(compile("a\\Sb").run("a\tb"));

        assertTrue(compile("a\\db").run("a1b"));
        assertFalse(compile("a\\db").run("axb"));

        assertTrue(compile("a\\Db").run("axb"));
        assertFalse(compile("a\\Db").run("a1b"));

        assertTrue(compile("a\\wb").run("a_b"));
        assertFalse(compile("a\\wb").run("a-b"));

        assertTrue(compile("a\\Wb").run("a-b"));
        assertFalse(compile("a\\Wb").run("a_b"));
    }

}
