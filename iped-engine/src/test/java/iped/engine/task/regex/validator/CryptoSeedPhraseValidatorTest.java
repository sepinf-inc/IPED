package iped.engine.task.regex.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class CryptoSeedPhraseValidatorTest {

    private CryptoSeedPhraseValidator service;

    @Before
    public void setUp() {
        service = new CryptoSeedPhraseValidator();
        // To make the test environment-independent, we locate the project root dynamically.
        // The test is typically run from the 'iped-engine' module directory.
        String userDir = System.getProperty("user.dir");
        File projectRoot = new File(userDir).getParentFile(); // Navigate up from iped-engine to the project root
        // Now, construct the path to the 'config' directory needed by the validator's init method.
        File testDir = new File(projectRoot, "iped-app/resources/config/conf");
        service.init(testDir);
    }

    // --- BIP-39 Tests ---
    @Test
    public void testValidBIP39_12WordPhrase() {
        String phrase = "legal winner thank year wave sausage worth useful legal winner thank yellow";
        assertTrue("A valid 12-word BIP-39 phrase should return true", service.validate(null, phrase));
    }

    @Test
    public void testValidBIP39_15WordPhrase() {
        String phrase = "candy maple cake sugar pudding cream honey rich smooth crumble sweet treat treat treat indoor";
        assertTrue("A valid 15-word phrase should return true", service.validate(null, phrase));
    }

    @Test
    public void testValidBIP39_18WordPhrase() {
        String phrase = "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal will";
        assertTrue("A valid 18-word BIP-39 phrase should return true", service.validate(null, phrase));
    }

    @Test
    public void testValidBIP39_21WordPhrase() {
        String phrase = "lyrics artist frog frog tooth perfect project drama soldier glue prevent polar syrup dynamic absorb hockey fever wet royal ribbon focus";
        assertTrue("A valid 21-word phrase should return true", service.validate(null, phrase));
    }

    @Test
    public void testValidBIP39_24WordPhrase() {
        String phrase = "tool eager remind during glimpse wild vocal wise crisp lonely bean vote silver use silver light stomach chronic absorb clean bean wall demise oxygen";
        assertTrue("A valid 24-word phrase should return true", service.validate(null, phrase));
    }

    // --- Electrum Tests ---
    @Test
    public void testValidElectrum12WordPhrase() {
        String phrase = "lounge jungle legal sibling scout learn fly famous need liar upgrade target";
        assertTrue("A valid 12-word Electrum phrase should return true", service.validate(null, phrase));
    }

    @Test
    public void testInvalidElectrumPhrase() {
        // This phrase has valid words but will fail the version check
        String phrase = "witch collapse practice feed shame open despair creek road again ice wrong";
        assertFalse("An Electrum phrase with an invalid version/checksum should be false", service.validate(null, phrase));
    }

    // --- General Invalidity Tests ---
    @Test
    public void testInvalidWordCount() {
        String phrase = "legal winner thank year wave sausage worth useful legal winner"; // 10 words
        assertFalse("A phrase with 10 words should be invalid", service.validate(null, phrase));
    }

    @Test
    public void testInvalidWord() {
        String phrase = "legal winner thank year wave sausage worth useful legal winner thank xyz"; // 'xyz' is not a valid word
        assertFalse("A phrase containing a word not in the BIP-39 list should be invalid", service.validate(null, phrase));
    }

    @Test
    public void testFormat() {
        String phrase = "a valid phrase should not be changed by the format method";
        assertEquals(phrase, service.format(null, phrase));
    }

}
