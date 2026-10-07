package iped.engine.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;

import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import iped.engine.config.RegexTaskConfig.RegexEntry;

public class RegexTaskConfigTest {

    // same compilation path used by RegexTask
    private static RunAutomaton compile(String regex) {
        return toRunAutomaton(RegexTaskConfig.replace(regex));
    }

    private static RunAutomaton toRunAutomaton(String replacedRegex) {
        return new RunAutomaton(new RegExp(replacedRegex).toAutomaton(new DatatypesAutomatonProvider()));
    }

    private static RunAutomaton compileFromShippedConfig(String regexName) throws IOException {
        RegexTaskConfig config = new RegexTaskConfig();
        config.processTaskConfig(Paths.get("../iped-app/resources/config/conf/RegexConfig.txt"));
        for (RegexEntry entry : config.getRegexList()) {
            if (entry.getRegexName().equals(regexName)) {
                return toRunAutomaton(entry.getRegex());
            }
        }
        throw new IllegalArgumentException(regexName);
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

    @Test
    public void testBrCarPlateMatchesOldAndMercosurPatterns() throws IOException {
        RunAutomaton pattern = compileFromShippedConfig("BR_CAR_PLATE");
        assertTrue(pattern.run(" ABC-1234 "));
        assertTrue(pattern.run(" ABC1234 "));
        assertTrue(pattern.run(" Placa ABC 1234 "));
        assertTrue(pattern.run(" ABC1D23 "));
        assertTrue(pattern.run(" ABC-1D23 "));
        assertTrue(pattern.run(" Placa ABC 1D23 "));
        assertFalse(pattern.run(" ABCD123 "));
        assertFalse(pattern.run(" AB11D23 "));
        assertFalse(pattern.run(" ABC1DD3 "));
        assertFalse(pattern.run(" ABC1D234 "));
    }

}
