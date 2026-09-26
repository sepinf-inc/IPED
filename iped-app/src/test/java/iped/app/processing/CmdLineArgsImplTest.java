package iped.app.processing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class CmdLineArgsImplTest {

    private static CmdLineArgsImpl parse(String... args) {
        CmdLineArgsImpl cmdArgs = new CmdLineArgsImpl();
        new JCommander(cmdArgs).parse(args);
        cmdArgs.checkReportTypeArgs();
        return cmdArgs;
    }

    @Test
    public void testReportTypeDefaultsCreateBoth() {
        CmdLineArgsImpl args = parse();
        assertFalse(args.isNoHtmlReport());
        assertFalse(args.isNoPortableCase());
    }

    @Test
    public void testNoHtmlReport() {
        assertTrue(parse("--nohtmlreport").isNoHtmlReport());
    }

    @Test
    public void testNoPortableCase() {
        assertTrue(parse("--noportablecase").isNoPortableCase());
    }

    @Test(expected = ParameterException.class)
    public void testNoHtmlReportAndNoPortableCaseAreExclusive() {
        parse("--nohtmlreport", "--noportablecase");
    }

    @Test(expected = ParameterException.class)
    public void testNoPortableCaseCannotAppend() {
        parse("--noportablecase", "--append");
    }
}
