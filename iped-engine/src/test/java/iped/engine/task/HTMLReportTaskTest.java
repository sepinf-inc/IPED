package iped.engine.task;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocaleConfig;

public class HTMLReportTaskTest {

    @BeforeClass
    public static void setUpConfig() {
        ConfigurationManager.createInstance(null).addObject(new LocaleConfig());
    }

    @Test
    public void testRemoveSearchHelpLink() {
        String contents = "<td>\n                %BOOKMARKS%\n\n                <p>\n"
                + "                    <a href=\"../iped/help/Help.htm\" class=\"SmallText2\" target=\"ReportPage\">Search Help</a>\n"
                + "                </p>\n\n            </td>";
        assertEquals("<td>\n                %BOOKMARKS%\n\n            </td>", HTMLReportTask.removeSearchHelpLink(contents));
    }

    @Test
    public void testRemoveLocalizedSearchHelpLink() {
        String contents = "<td>\n                %BOOKMARKS%\n\n                <p>\n"
                + "                    <a href=\"../iped/help/Help_pt-BR.htm\" class=\"SmallText2\" target=\"ReportPage\">Busca por palavras-chave</a>\n"
                + "                </p>\n\n            </td>";
        assertEquals("<td>\n                %BOOKMARKS%\n\n            </td>", HTMLReportTask.removeSearchHelpLink(contents));
    }

    @Test
    public void testRemoveSearchHelpLinkWithWindowsLineEndings() {
        String contents = "<td>\r\n                %BOOKMARKS%\r\n\r\n                <p>\r\n"
                + "                    <a href=\"../iped/help/Help.htm\" class=\"SmallText2\" target=\"ReportPage\">Search Help</a>\r\n"
                + "                </p>\r\n\r\n            </td>";
        assertEquals("<td>\r\n                %BOOKMARKS%\r\n\r\n            </td>", HTMLReportTask.removeSearchHelpLink(contents));
    }

    @Test
    public void testRemoveSearchHelpLinkKeepsContentsWithoutLink() {
        String contents = "<td>\n                %BOOKMARKS%\n            </td>";
        assertEquals(contents, HTMLReportTask.removeSearchHelpLink(contents));
    }
}
