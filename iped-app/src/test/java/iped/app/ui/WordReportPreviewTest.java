package iped.app.ui;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;

public class WordReportPreviewTest {

    private PDDocument document(String... pages) throws Exception {
        PDDocument document = new PDDocument();
        for (String text : pages) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(40, 700);
                content.setLeading(16);
                for (String line : text.split("\n")) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
        }
        return document;
    }

    @Test
    public void findsRepeatedFileNamesInTheSamePageAndAcrossPages() throws Exception {
        try (WordReportPreview preview = new WordReportPreview(document("Nome: first\nNome: first", "Nome: first", "Other content"))) {
            assertEquals(3, preview.getPageCount());
            assertEquals(0, preview.findPageContaining("Nome: first", 0));
            assertEquals(0, preview.findPageContaining("Nome: first", 1));
            assertEquals(1, preview.findPageContaining("Nome: first", 2));
            assertEquals(-1, preview.findPageContaining("Nome: first", 3));
            assertEquals(0, preview.findPageContaining("Nome: first", 0));
        }
    }

    @Test
    public void retainsHeadingAndWrappedTextNavigation() throws Exception {
        try (WordReportPreview preview = new WordReportPreview(document("Cover", "4. Dados obtidos", "Nome: Chat_\nWhatsApp_example"))) {
            assertEquals(1, preview.findPage("Dados obtidos"));
            assertEquals(2, preview.findPageContaining("Nome: Chat_WhatsApp_example", 0));
            assertEquals(2, preview.findPage("Nome: Chat_WhatsApp_example"));
            assertEquals(0, preview.findPage(""));
            assertEquals(0, preview.findPage("Missing item"));
        }
    }

    @Test
    public void revisitingAPageReusesItsRenderedImage() throws Exception {
        try (WordReportPreview preview = new WordReportPreview(document("First page", "Second page"))) {
            BufferedImage first = preview.render(0);
            assertSame(first, preview.render(0));
            BufferedImage second = preview.render(1);
            assertNotSame(first, second);
            assertSame(first, preview.render(0));
            assertSame(second, preview.render(1));
            assertTrue(first.getWidth() > 0);
            assertTrue(first.getHeight() > 0);
        }
    }
}
