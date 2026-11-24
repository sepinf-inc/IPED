package iped.app.ui;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WordReportDocumentTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path fixture(String document, Map<String, String> extra) throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("word/document.xml", document);
        parts.put("word/_rels/document.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        parts.put("word/styles.xml", "<styles>untouched styles</styles>");
        parts.put("word/header1.xml", "<header>untouched header</header>");
        parts.put("word/custom.xml", "<opaque>untouched custom part</opaque>");
        parts.putAll(extra);
        Path file = temporary.newFile("source-" + System.nanoTime() + ".docx").toPath();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private Path fixture() throws Exception {
        return fixture("<w:document xmlns:w=\"" + WordReportDocument.W + "\"><w:body>"
                + "<w:p><w:r><w:t>Before</w:t></w:r></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Existing table</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "<w:p><w:r><w:t>After</w:t></w:r></w:p><w:sectPr/></w:body></w:document>", Collections.emptyMap());
    }

    private WordReportDocument.Evidence evidence() {
        WordReportDocument.Evidence item = new WordReportDocument.Evidence();
        item.title = "Image & metadata <review>";
        item.text = "Extracted text\nSecond line 😀";
        item.image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        item.imageDescription = "Synthetic image";
        item.metadata.put("IPED source ID", "3");
        item.metadata.put("IPED item ID", "42");
        item.metadata.put("Hash", "fixture-hash");
        return item;
    }

    @Test
    public void insertsAtSelectedBlockAndPreservesExistingPackage() throws Exception {
        Path source = fixture();
        byte[] original = Files.readAllBytes(source);
        WordReportDocument report = new WordReportDocument(source);
        assertEquals(4, report.getLocations().size());
        assertEquals("Existing table", report.getLocationText(2));
        report.insert(2, Collections.singletonList(evidence()), true, true);
        Path output = temporary.getRoot().toPath().resolve("result.docx");
        report.write(output);
        assertArrayEquals(original, Files.readAllBytes(source));
        try (ZipFile zip = new ZipFile(output.toFile()); ZipFile input = new ZipFile(source.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("Existing table") < xml.indexOf("Image &amp; metadata"));
            assertTrue(xml.indexOf("Image &amp; metadata") < xml.indexOf(">After<"));
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document parsed = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertTrue(parsed.getDocumentElement().getTextContent().contains("😀"));
            assertEquals(1, zip.stream().filter(e -> e.getName().startsWith("word/media/")).count());
            for (String name : Arrays.asList("word/styles.xml", "word/header1.xml", "word/custom.xml")) {
                assertArrayEquals(input.getInputStream(input.getEntry(name)).readAllBytes(), zip.getInputStream(zip.getEntry(name)).readAllBytes());
            }
            assertTrue(new String(zip.getInputStream(zip.getEntry("word/_rels/document.xml.rels")).readAllBytes(), StandardCharsets.UTF_8).contains("/image"));
        }
    }

    @Test
    public void appendsBeforeFinalSectionAndSupportsMetadataAndTextOptions() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        report.insert(3, Collections.singletonList(evidence()), false, false);
        Path output = temporary.getRoot().toPath().resolve("append.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("Image &amp; metadata") < xml.indexOf("<w:sectPr"));
            assertFalse(xml.contains("fixture-hash"));
            assertFalse(xml.contains("Extracted text"));
        }
    }

    @Test
    public void refusesOverwriteAndChangedInput() throws Exception {
        Path source = fixture();
        WordReportDocument report = new WordReportDocument(source);
        assertTrue(report.isSourceCurrent());
        try {
            report.write(source);
            fail("Input was overwritten");
        } catch (java.io.IOException expected) {
        }
        Path output = temporary.newFile("existing.docx").toPath();
        try {
            report.write(output);
            fail("Existing output was overwritten");
        } catch (java.io.IOException expected) {
        }
        Files.writeString(source, "changed");
        assertFalse(report.isSourceCurrent());
        try {
            report.write(temporary.getRoot().toPath().resolve("changed.docx"));
            fail("Changed input accepted");
        } catch (java.io.IOException expected) {
        }
    }

    @Test
    public void savesExactlyTheReviewedDocumentAndChecksSourceAgain() throws Exception {
        Path source = fixture();
        WordReportDocument report = new WordReportDocument(source);
        report.insert(3, Collections.singletonList(evidence()), true, true);
        Path prepared = temporary.getRoot().toPath().resolve("reviewed.docx");
        report.write(prepared);
        Path saved = temporary.getRoot().toPath().resolve("saved.docx");
        report.savePreparedCopy(prepared, saved);
        assertArrayEquals(Files.readAllBytes(prepared), Files.readAllBytes(saved));
        try {
            report.savePreparedCopy(prepared, saved);
            fail("Existing output was overwritten");
        } catch (java.io.IOException expected) {
        }
        Files.writeString(source, "source changed after review");
        Path refused = temporary.getRoot().toPath().resolve("refused.docx");
        try {
            report.savePreparedCopy(prepared, refused);
            fail("Changed source was accepted after review");
        } catch (java.io.IOException expected) {
            assertFalse(Files.exists(refused));
        }
    }

    @Test
    public void updatesTheSameReportAcrossBatchesAndCanContinueInASaveAsCopy() throws Exception {
        Path reportFile = fixture();
        WordReportDocument report = new WordReportDocument(reportFile);
        int cursor = report.insert(2, Collections.singletonList(evidence()), true, true);
        Path reviewed = temporary.getRoot().toPath().resolve("reviewed-first.docx");
        report.write(reviewed);
        report.savePreparedReport(reviewed);
        assertArrayEquals(Files.readAllBytes(reviewed), Files.readAllBytes(reportFile));

        report = new WordReportDocument(reportFile);
        WordReportDocument.Evidence second = evidence();
        second.title = "Second saved batch";
        report.insert(cursor, Collections.singletonList(second), true, true);
        Path next = temporary.getRoot().toPath().resolve("reviewed-second.docx");
        report.write(next);
        report.savePreparedReport(next);
        assertArrayEquals(Files.readAllBytes(next), Files.readAllBytes(reportFile));
        try (ZipFile zip = new ZipFile(reportFile.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("Image &amp; metadata") < xml.indexOf("Second saved batch"));
        }

        byte[] original = Files.readAllBytes(reportFile);
        WordReportDocument saved = new WordReportDocument(reportFile);
        Path copy = temporary.getRoot().toPath().resolve("save-as.docx");
        saved.savePreparedCopy(reportFile, copy);
        assertArrayEquals(original, Files.readAllBytes(copy));
        WordReportDocument continued = new WordReportDocument(copy);
        continued.insert(1, Collections.singletonList(evidence()), true, false);
        Path last = temporary.getRoot().toPath().resolve("reviewed-copy.docx");
        continued.write(last);
        continued.savePreparedReport(last);
        assertArrayEquals(Files.readAllBytes(last), Files.readAllBytes(copy));
        assertArrayEquals(original, Files.readAllBytes(reportFile));
    }

    @Test
    public void refusesToUpdateAReportChangedAfterReview() throws Exception {
        Path reportFile = fixture();
        WordReportDocument report = new WordReportDocument(reportFile);
        report.insert(1, Collections.singletonList(evidence()), true, true);
        Path reviewed = temporary.getRoot().toPath().resolve("stale-review.docx");
        report.write(reviewed);
        byte[] externalChange = "External edits after review".getBytes(StandardCharsets.UTF_8);
        Files.write(reportFile, externalChange);
        try {
            report.savePreparedReport(reviewed);
            fail("External edits were overwritten");
        } catch (java.io.IOException expected) {
            assertArrayEquals(externalChange, Files.readAllBytes(reportFile));
        }
    }

    @Test
    public void failedUpdateKeepsTheReportAndRemovesTheStagingFile() throws Exception {
        Path reportFile = fixture();
        byte[] original = Files.readAllBytes(reportFile);
        WordReportDocument report = new WordReportDocument(reportFile);
        try {
            report.savePreparedReport(temporary.getRoot().toPath().resolve("missing-preview.docx"));
            fail("Missing preview was accepted");
        } catch (java.io.IOException expected) {
            assertArrayEquals(original, Files.readAllBytes(reportFile));
            try (java.util.stream.Stream<Path> files = Files.list(reportFile.getParent())) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".iped-report-")));
            }
        }
    }

    @Test
    public void remembersTheDocumentBetweenSelectionsWithoutPersistingSharedLinks() throws Exception {
        String previousHome = System.getProperty("user.home");
        Path profile = temporary.newFolder("recent-profile").toPath();
        Path another = temporary.newFolder("other-profile").toPath();
        Path source = fixture();
        try {
            System.setProperty("user.home", profile.toString());
            WordReportFiles.rememberSelection(0, source.toString());
            System.setProperty("user.home", another.toString());
            assertNull(WordReportFiles.lastSelection());
            System.setProperty("user.home", profile.toString());
            assertEquals(source.toString(), WordReportFiles.lastSelection().value);
            String link = "https://example.test/report.docx?token=private-fixture";
            WordReportFiles.rememberSelection(1, link);
            assertEquals(link, WordReportFiles.lastSelection().value);
            Path settings = profile.resolve(".iped/word-report/last-document.properties");
            assertFalse(Files.readString(settings).contains("private-fixture"));
            System.setProperty("user.home", another.toString());
            WordReportFiles.lastSelection();
            System.setProperty("user.home", profile.toString());
            assertEquals(0, WordReportFiles.lastSelection().type);
            assertEquals(source.toString(), WordReportFiles.lastSelection().value);
            WordReportFiles.rememberSelection(2, "");
            assertEquals(2, WordReportFiles.lastSelection().type);
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void repeatedEvidenceCanBeLocatedAfterItsEarlierExport() throws Exception {
        WordReportDocument first = new WordReportDocument(fixture());
        first.insert(1, Collections.singletonList(evidence()), true, true);
        Path output = temporary.getRoot().toPath().resolve("earlier.docx");
        first.write(output);
        WordReportDocument next = new WordReportDocument(output);
        String anchor = WordReportDocument.FILE_NAME + ": " + evidence().title;
        assertEquals(0, next.countTextOccurrencesBefore(0, anchor));
        assertEquals(1, next.countTextOccurrencesBefore(next.getLocations().size() - 1, anchor));
    }

    @Test
    public void pairedChatPreviewStartsAtTheReplacedTableWhenTheFileNameIsRepeated() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence excerpt = chat("repeated");
        WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
        audio.text = "Transcription of the selected audio.";
        excerpt.transcripts.add(audio);
        int after = report.insert(3, Arrays.asList(excerpt, excerpt), true, true);
        Path output = temporary.getRoot().toPath().resolve("repeated-chat.docx");
        report.write(output);

        WordReportDocument saved = new WordReportDocument(output);
        String anchor = WordReportDocument.FILE_NAME + ": " + excerpt.title;
        assertTrue(saved.hasPreviousChat(after));
        assertEquals(4, saved.countTextOccurrencesBefore(after, anchor, false));
        assertEquals(2, saved.countTextOccurrencesBefore(after, anchor, true));
    }

    @Test
    public void continuesAfterSavedBatchWithoutReplacingEarlierEvidence() throws Exception {
        Path source = fixture();
        byte[] original = Files.readAllBytes(source);
        WordReportDocument first = new WordReportDocument(source);
        WordReportDocument.Evidence second = evidence();
        second.title = "Second batch";
        int cursor = first.insert(2, Arrays.asList(evidence(), evidence(), evidence()), true, true, 2);
        assertEquals(7, cursor);
        Path prepared = temporary.getRoot().toPath().resolve("prepared-batch.docx");
        first.write(prepared);
        Path saved = temporary.getRoot().toPath().resolve("saved-batch.docx");
        first.savePreparedCopy(prepared, saved);
        byte[] firstBatch = Files.readAllBytes(saved);
        WordReportDocument next = new WordReportDocument(saved);
        assertEquals(cursor + 2, next.insert(cursor, Collections.singletonList(second), true, true, 1));
        Path complete = temporary.getRoot().toPath().resolve("complete.docx");
        next.write(complete);
        try (ZipFile zip = new ZipFile(complete.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("Existing table") < xml.indexOf("Image &amp; metadata"));
            assertTrue(xml.lastIndexOf("Image &amp; metadata") < xml.indexOf("Second batch"));
            assertTrue(xml.indexOf("Second batch") < xml.indexOf(">After<"));
            assertEquals(4, zip.stream().filter(e -> e.getName().startsWith("word/media/")).count());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(firstBatch, Files.readAllBytes(saved));
    }

    @Test
    public void rejectsExternalEntitiesAndMacros() throws Exception {
        Path entity = fixture("<!DOCTYPE doc [<!ENTITY x SYSTEM 'file:///should-not-be-read'>]><doc>&x;</doc>", Collections.emptyMap());
        try {
            new WordReportDocument(entity);
            fail("DTD accepted");
        } catch (org.xml.sax.SAXException expected) {
        }
        Path macro = fixture("<doc/>", Collections.singletonMap("word/vbaProject.bin", "macro"));
        try {
            new WordReportDocument(macro);
            fail("Macro document accepted");
        } catch (java.io.IOException expected) {
        }
    }

    @Test
    public void validatesLinksWithoutLeakingCredentials() throws Exception {
        assertEquals("example.test", WordReportFiles.httpsUri("https://example.test/report.docx").getHost());
        for (String invalid : Arrays.asList("http://example.test", "file:///private/report.docx", "https://user:secret@example.test/report")) {
            try {
                WordReportFiles.httpsUri(invalid);
                fail("Unsafe URL accepted");
            } catch (java.io.IOException expected) {
                assertFalse(expected.getMessage().contains("secret"));
            }
        }
    }

    @Test
    public void savedTemplatesPersistWithoutReplacingPreviousCopies() throws Exception {
        String previous = System.getProperty("user.home");
        Path source = fixture();
        byte[] original = Files.readAllBytes(source);
        try {
            System.setProperty("user.home", temporary.newFolder("profile").toString());
            Path first = WordReportFiles.saveTemplate(source);
            Path second = WordReportFiles.saveTemplate(source);
            assertNotEquals(first, second);
            assertEquals(2, WordReportFiles.templates().size());
            assertTrue(WordReportFiles.templates().contains(first));
            assertArrayEquals(original, Files.readAllBytes(source));
            assertArrayEquals(original, Files.readAllBytes(first));
            assertArrayEquals(original, Files.readAllBytes(second));
        } finally {
            System.setProperty("user.home", previous);
        }
    }

    @Test
    public void mediaFiltersUseExistingIPEDImageAndVideoTypes() {
        assertEquals(1, WordReportEvidence.mediaType("image/jpeg"));
        assertEquals(1, WordReportEvidence.mediaType("application/coreldraw"));
        assertEquals(2, WordReportEvidence.mediaType("application/vnd.rn-realmedia"));
        assertEquals(3, WordReportEvidence.mediaType("audio/ogg"));
        assertEquals(4, WordReportEvidence.mediaType("application/x-whatsapp-message"));
        assertEquals(5, WordReportEvidence.mediaType("application/pdf"));
    }

    @Test
    public void separateEvidenceKeepsOneBlankParagraphOnEitherSideOfAnExistingFrame() throws Exception {
        WordReportDocument first = new WordReportDocument(fixture());
        int after = first.insert(3, Collections.singletonList(evidence()), true, true);
        Path output = temporary.getRoot().toPath().resolve("spaced.docx");
        first.write(output);
        for (int position : new int[] { after - 2, after - 1 }) {
            WordReportDocument reopened = new WordReportDocument(output);
            reopened.insert(position, Collections.singletonList(chat("excerpt")), true, true);
            Path saved = temporary.getRoot().toPath().resolve("position-" + position + ".docx");
            reopened.write(saved);
            org.w3c.dom.NodeList frames = documentXml(saved).getElementsByTagNameNS(WordReportDocument.W, "tbl");
            org.w3c.dom.Node left = frames.item(1), right = frames.item(2);
            assertEquals("p", left.getNextSibling().getLocalName());
            assertTrue(left.getNextSibling().getTextContent().trim().isEmpty());
            assertSame(right, left.getNextSibling().getNextSibling());
            org.w3c.dom.Element gap = (org.w3c.dom.Element) left.getNextSibling();
            org.w3c.dom.Element spacing = (org.w3c.dom.Element) gap.getElementsByTagNameNS(WordReportDocument.W, "spacing").item(0);
            assertEquals("240", spacing.getAttributeNS(WordReportDocument.W, "line"));
            assertEquals("exact", spacing.getAttributeNS(WordReportDocument.W, "lineRule"));
            assertTrue((position == after - 2 ? right : left).getTextContent().contains(evidence().title));
        }
    }

    @Test
    public void navigationUsesHeadingsAndReadablePositionsWithExactInsertionIndexes() throws Exception {
        Path source = fixture("<w:document xmlns:w=\"" + WordReportDocument.W + "\"><w:body>"
                + "<w:p/><w:p><w:r><w:drawing/></w:r></w:p>"
                + "<w:sdt><w:sdtContent><w:p><w:pPr><w:pStyle w:val=\"TOC1\"/></w:pPr><w:r><w:t>Summary</w:t></w:r></w:p></w:sdtContent></w:sdt>"
                + "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Dados obtidos</w:t></w:r></w:p>"
                + "<w:p/><w:p><w:r><w:t>First readable paragraph</w:t></w:r></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Existing table</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p/>"
                + "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Conclusão</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Closing paragraph</w:t></w:r></w:p><w:sectPr/></w:body></w:document>", Collections.emptyMap());
        WordReportDocument report = new WordReportDocument(source);
        assertEquals(3, report.getSections().size());
        WordReportDocument.Section section = report.getSections().get(1);
        assertEquals("Dados obtidos", section.title);
        java.util.List<WordReportDocument.Location> positions = report.getPositions(section);
        assertEquals(5, positions.size());
        assertEquals(8, positions.get(0).index);
        assertEquals(4, positions.get(1).index);
        assertEquals(6, positions.get(2).index);
        assertTrue(positions.get(2).label.contains("First readable paragraph"));
        assertEquals(6, positions.get(3).index);
        assertEquals(7, positions.get(4).index);
        assertEquals(2, report.getPositions(report.getSections().get(0)).size());
        report.insert(positions.get(2).index, Collections.singletonList(evidence()), true, true);
        Path output = temporary.getRoot().toPath().resolve("navigation.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("First readable paragraph") < xml.indexOf("Image &amp; metadata"));
            assertTrue(xml.indexOf("Image &amp; metadata") < xml.indexOf("Existing table"));
        }
    }

    @Test
    public void fileInformationUsesOnlyTheThreeReferenceFieldsInOrder() {
        iped.engine.data.Item item = new iped.engine.data.Item();
        item.setName("sample.jpg");
        item.setPath("sample/sample.jpg");
        item.setMediaTypeStr("image/jpeg");
        item.setLength(123L);
        item.setHash("fixture-hash");
        item.getMetadata().set("image:ICC:Blue TRC", "0 ".repeat(6000));
        item.setExtraAttribute("test:extra", new int[] { 1, 2 });
        Map<String, String> fields = WordReportEvidence.readMetadata(item);
        assertEquals(Arrays.asList("Hash", "Nome", "Caminho"), new java.util.ArrayList<>(fields.keySet()));
        assertEquals("fixture-hash", fields.get("Hash"));
        assertEquals("sample.jpg", fields.get("Nome"));
        assertEquals("sample/sample.jpg", fields.get("Caminho"));
    }

    @Test
    public void includesCompleteTextAndTranscriptionBeyondTheFormerCharacterLimit() throws Exception {
        iped.engine.data.Item item = new iped.engine.data.Item();
        String complete = "Conteúdo de exemplo com acentuação. ".repeat(2000) + "FINAL DO CONTEÚDO";
        item.setParsedTextCache(complete);
        assertEquals(complete, WordReportEvidence.readText(item));
        String transcription = "Transcrição completa. ".repeat(2000) + "FINAL DA TRANSCRIÇÃO";
        item.setExtraAttribute(iped.properties.ExtraProperties.TRANSCRIPT_ATTR, transcription);
        assertEquals(transcription, WordReportEvidence.readText(item));
        item.dispose();
    }

    @Test
    public void pairedEvidenceKeepsEachImageAndMetadataInItsOwnColumn() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence second = evidence();
        second.title = "Second evidence";
        second.metadata.put("Hash", "second-hash");
        report.insert(3, Arrays.asList(evidence(), second), true, true, 2);
        Path output = temporary.getRoot().toPath().resolve("paired.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
            org.w3c.dom.Element frame = (org.w3c.dom.Element) xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
            org.w3c.dom.NodeList rows = frame.getElementsByTagNameNS(WordReportDocument.W, "tr");
            assertEquals(3, rows.getLength());
            org.w3c.dom.NodeList content = ((org.w3c.dom.Element) rows.item(0)).getElementsByTagNameNS(WordReportDocument.W, "tc");
            assertEquals(2, content.getLength());
            org.w3c.dom.NodeList metadata = ((org.w3c.dom.Element) rows.item(2)).getElementsByTagNameNS(WordReportDocument.W, "tc");
            assertTrue(metadata.item(0).getTextContent().contains("fixture-hash"));
            assertFalse(metadata.item(0).getTextContent().contains("second-hash"));
            assertTrue(metadata.item(1).getTextContent().contains("second-hash"));
            assertEquals(2, zip.stream().filter(e -> e.getName().startsWith("word/media/")).count());
        }
    }

    @Test
    public void referenceImageFramesHaveNoAdditionalHeadingOrVisibleCaptionInEnglish() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence item = evidence();
        item.imageDescription = "IPED item image, resized for the report.";
        item.metadata.put("IPED source ID", "should-not-be-rendered");
        report.insert(3, Arrays.asList(item, evidence()), true, false, 2);
        Path output = temporary.getRoot().toPath().resolve("reference-frame.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
            org.w3c.dom.Element frame = (org.w3c.dom.Element) xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
            String visible = frame.getTextContent();
            assertFalse(visible.contains("resized for the report"));
            assertFalse(visible.contains("Evidence"));
            assertFalse(visible.contains("should-not-be-rendered"));
            assertFalse(visible.contains("Hash (IPED)"));
            org.w3c.dom.NodeList rows = frame.getElementsByTagNameNS(WordReportDocument.W, "tr");
            assertEquals(3, rows.getLength());
            assertEquals("Informações do arquivoInformações do arquivo", rows.item(1).getTextContent());
            org.w3c.dom.NodeList fields = ((org.w3c.dom.Element)rows.item(2)).getElementsByTagNameNS(WordReportDocument.W, "tc");
            for (int i = 0; i < 2; i++) {
                String value = fields.item(i).getTextContent();
                assertTrue(value.indexOf("Hash:") < value.indexOf("Nome:"));
                assertTrue(value.indexOf("Nome:") < value.indexOf("Caminho:"));
            }
            assertTrue(new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8).contains("w:fill=\"DDD9C3\""));
        }
    }

    @Test
    public void messagesUseTheOriginalNumberedColumnsAndKeepMixedBatchOrder() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence message = evidence();
        message.message = true;
        message.participants = "Remetente de exemplo\nDestinatário de exemplo";
        message.title = "mensagem.txt";
        WordReportDocument.Evidence last = evidence();
        last.title = "Última imagem";
        int next = report.insert(2, Arrays.asList(evidence(), message, message, last), true, true, 2);
        assertEquals(9, next);
        Path output = temporary.getRoot().toPath().resolve("reference-messages.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
            org.w3c.dom.Element table = (org.w3c.dom.Element)xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(2);
            org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
            assertEquals(3, rows.getLength());
            assertEquals("InterlocutoresConteúdo da mensagemInformações do arquivo", rows.item(0).getTextContent());
            for (int i = 1; i < 3; i++) {
                org.w3c.dom.NodeList cells = ((org.w3c.dom.Element)rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc");
                assertEquals(4, cells.getLength());
                assertEquals(Integer.toString(i), cells.item(0).getTextContent());
                assertTrue(cells.item(1).getTextContent().contains("Remetente de exemplo"));
                assertTrue(cells.item(3).getTextContent().contains("Caminho:"));
            }
            String all = xml.getDocumentElement().getTextContent();
            assertTrue(all.indexOf("mensagem.txt") < all.indexOf("Última imagem"));
            assertTrue(all.indexOf("Última imagem") < all.indexOf("After"));
        }
    }

    @Test
    public void croppedChatUsesScreenshotTranscriptionAndOriginalFileColumns() throws Exception {
        WordReportDocument.Evidence chat = evidence();
        chat.chatCapture = true;
        chat.title = "Chat_WhatsApp_exemplo.html";
        chat.text = "WHOLE CHAT MUST NOT BE INCLUDED";
        chat.metadata.put("Nome", chat.title);
        chat.metadata.put("Caminho", "extracao/conversa.html");
        WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
        audio.text = "Somente a transcrição visível aprovada.";
        audio.metadata.put("Hash", "audio-hash");
        chat.transcripts.add(audio);
        WordReportDocument report = new WordReportDocument(fixture());
        report.insert(3, Collections.singletonList(chat), true, true, 2);
        Path output = temporary.getRoot().toPath().resolve("chat-crop.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
            org.w3c.dom.Element table = (org.w3c.dom.Element) xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
            org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
            assertEquals(3, rows.getLength());
            for (int i = 0; i < 3; i++) {
                assertEquals(2, ((org.w3c.dom.Element) rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
            }
            org.w3c.dom.NodeList cells = ((org.w3c.dom.Element) rows.item(0)).getElementsByTagNameNS(WordReportDocument.W, "tc");
            assertEquals(1, ((org.w3c.dom.Element) cells.item(0)).getElementsByTagNameNS(WordReportDocument.W, "drawing").getLength());
            assertTrue(cells.item(1).getTextContent().contains("Transcrição do áudio"));
            assertTrue(cells.item(1).getTextContent().contains(audio.text));
            assertFalse(cells.item(1).getTextContent().contains("audio-hash"));
            assertFalse(table.getTextContent().contains(chat.text));
            assertFalse(table.getTextContent().contains("Conteúdo da mensagem"));
            for (int i = 0; i < 2; i++) {
                org.w3c.dom.Node cell = ((org.w3c.dom.Element) rows.item(2)).getElementsByTagNameNS(WordReportDocument.W, "tc").item(i);
                assertTrue(cell.getTextContent().contains("Hash: fixture-hash"));
                assertTrue(cell.getTextContent().contains("Nome: " + chat.title));
                assertTrue(cell.getTextContent().contains("Caminho: extracao/conversa.html"));
            }
        }
    }

    @Test
    public void enlargedAttachmentUsesChatFileInformationWithOrWithoutAudio() throws Exception {
        WordReportDocument.Evidence excerpt = chat("example");
        excerpt.attachment = evidence();
        excerpt.attachment.title = "receipt.png";
        excerpt.attachment.image = new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB);
        excerpt.attachment.metadata.put("Caminho", "media/receipt.png");
        WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
        audio.text = "Selected transcription";
        audio.metadata.put("Hash", "audio-hash");
        excerpt.transcripts.add(audio);
        for (boolean includeText : new boolean[] { true, false }) {
            WordReportDocument report = new WordReportDocument(fixture());
            int after = report.insert(3, Collections.singletonList(excerpt), true, includeText);
            Path output = temporary.getRoot().toPath().resolve("attachment-" + includeText + ".docx");
            report.write(output);
            assertFalse(new WordReportDocument(output).hasPreviousChat(after));
            org.w3c.dom.Element table = (org.w3c.dom.Element) documentXml(output).getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
            org.w3c.dom.NodeList cells = table.getElementsByTagNameNS(WordReportDocument.W, "tc");
            assertEquals(6, cells.getLength());
            assertEquals(includeText, cells.item(1).getTextContent().contains(audio.text));
            assertEquals(1, ((org.w3c.dom.Element) cells.item(1)).getElementsByTagNameNS(WordReportDocument.W, "drawing").getLength());
            assertTrue(cells.item(4).getTextContent().contains("example-hash"));
            assertFalse(cells.item(4).getTextContent().contains("receipt.png"));
            assertTrue(cells.item(4).getTextContent().contains(excerpt.title));
            assertTrue(cells.item(4).getTextContent().contains(excerpt.metadata.get("Caminho")));
            assertEquals(cells.item(4).getTextContent(), cells.item(5).getTextContent());
            assertFalse(table.getTextContent().contains("fixture-hash"));
            assertFalse(table.getTextContent().contains("audio-hash"));
        }
    }

    @Test
    public void readsTheMetadataTranscriptionUsedByTheAudioViewer() throws Exception {
        iped.engine.data.Item item = new iped.engine.data.Item();
        String text = "Transcrição completa com acentuação. ".repeat(1500);
        item.getMetadata().set(iped.properties.ExtraProperties.TRANSCRIPT_ATTR, text);
        item.setParsedTextCache("Other indexed metadata");
        assertEquals(text, WordReportEvidence.readText(item));
        item.getMetadata().remove(iped.properties.ExtraProperties.TRANSCRIPT_ATTR);
        item.getMetadata().set(iped.properties.ExtraProperties.MESSAGE_BODY, "Corpo da mensagem");
        assertEquals("Corpo da mensagem", WordReportEvidence.readText(item));
        item.dispose();
    }

    private WordReportDocument.Evidence chat(String name) {
        WordReportDocument.Evidence chat = evidence();
        chat.chatCapture = true;
        chat.title = "Chat_WhatsApp_" + name + ".html";
        chat.metadata.put("Nome", chat.title);
        chat.metadata.put("Hash", name + "-hash");
        chat.metadata.put("Caminho", "extracao/" + name + ".html");
        return chat;
    }

    private org.w3c.dom.Document documentXml(Path file) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
        }
    }

    @Test
    public void loneChatHasOneColumnWithoutDuplicatedInformation() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        report.insert(3, Collections.singletonList(chat("first")), true, true, 2);
        Path file = temporary.getRoot().toPath().resolve("single-chat.docx");
        report.write(file);
        org.w3c.dom.Element table = (org.w3c.dom.Element) documentXml(file).getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
        assertEquals(3, rows.getLength());
        for (int i = 0; i < rows.getLength(); i++) {
            assertEquals(1, ((org.w3c.dom.Element) rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
        }
        assertFalse(table.getTextContent().contains("Transcrição do áudio"));
        assertTrue(new WordReportDocument(file).hasPreviousChat(5));
    }

    @Test
    public void newChatCanUseSavedPreviousColumnWithoutDuplicatingItsImageOrMetadata() throws Exception {
        WordReportDocument first = new WordReportDocument(fixture());
        first.insert(3, Collections.singletonList(chat("first")), true, true);
        Path saved = temporary.getRoot().toPath().resolve("first-chat.docx");
        first.write(saved);
        byte[] original = Files.readAllBytes(saved);
        WordReportDocument next = new WordReportDocument(saved);
        WordReportDocument.Evidence second = chat("second");
        second.besidePrevious = true;
        assertEquals(5, next.insert(5, Collections.singletonList(second), true, true));
        Path output = temporary.getRoot().toPath().resolve("paired-chats.docx");
        next.write(output);
        assertArrayEquals(original, Files.readAllBytes(saved));
        org.w3c.dom.Element table = (org.w3c.dom.Element) documentXml(output).getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
        for (int i = 0; i < 3; i++) {
            assertEquals(2, ((org.w3c.dom.Element) rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
        }
        org.w3c.dom.NodeList images = ((org.w3c.dom.Element) rows.item(0)).getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip");
        assertEquals(2, images.getLength());
        assertNotEquals(((org.w3c.dom.Element) images.item(0)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed"),
                ((org.w3c.dom.Element) images.item(1)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed"));
        org.w3c.dom.NodeList metadata = ((org.w3c.dom.Element) rows.item(2)).getElementsByTagNameNS(WordReportDocument.W, "tc");
        assertTrue(metadata.item(0).getTextContent().contains("first-hash"));
        assertFalse(metadata.item(0).getTextContent().contains("second-hash"));
        assertTrue(metadata.item(1).getTextContent().contains("second-hash"));
        assertFalse(metadata.item(1).getTextContent().contains("first-hash"));
        assertFalse(new WordReportDocument(output).hasPreviousChat(5));
    }

    @Test
    public void savedDocumentExcerptsKeepOnlyTheApprovedPixelsAndOriginalMetadata() throws Exception {
        BufferedImage page = new BufferedImage(120, 180, BufferedImage.TYPE_INT_RGB);
        page.setRGB(11, 20, 0xff44ee);
        page.setRGB(61, 100, 0xaaff33);
        iped.engine.data.Item item = new iped.engine.data.Item();
        item.setName("sample.pdf");
        item.setPath("/documents/sample.pdf");
        item.setHash("AABBCC");
        WordReportChatSelection selection = WordReportChatSelection.document(page);
        WordReportDocument.Evidence first = WordReportEvidence.capturedEvidence(item,
                selection.crop(new java.awt.Rectangle(10, 20, 36, 48)));
        WordReportDocument.Evidence second = WordReportEvidence.capturedEvidence(item,
                selection.crop(new java.awt.Rectangle(60, 100, 15, 35)));
        assertFalse(first.chatCapture);
        assertNull(first.text);
        assertEquals(0xffff44ee, first.image.getRGB(1, 0));
        WordReportDocument report = new WordReportDocument(fixture());
        int location = report.insert(3, Collections.singletonList(first), true, true);
        Path saved = temporary.getRoot().toPath().resolve("document-excerpt.docx");
        report.write(saved);
        report = new WordReportDocument(saved);
        assertTrue(report.hasPreviousImage(location));
        second.besidePrevious = true;
        report.insert(location, Collections.singletonList(second), true, true);
        Path pair = temporary.getRoot().toPath().resolve("document-pair.docx");
        report.write(pair);
        try (ZipFile zip = new ZipFile(pair.toFile())) {
            java.util.List<BufferedImage> images = new ArrayList<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.getName().startsWith("word/media/iped-report-")) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        images.add(javax.imageio.ImageIO.read(stream));
                    }
                }
            }
            assertEquals(2, images.size());
            assertTrue(images.stream().anyMatch(i -> i.getWidth() == 36 && i.getHeight() == 48 && i.getRGB(1, 0) == 0xffff44ee));
            assertTrue(images.stream().anyMatch(i -> i.getWidth() == 15 && i.getHeight() == 35 && i.getRGB(1, 0) == 0xffaaff33));
        }
        String text = documentXml(pair).getDocumentElement().getTextContent();
        assertTrue(text.contains("AABBCC") && text.contains("sample.pdf") && text.contains("/documents/sample.pdf"));
        assertFalse(text.contains("Transcrição do áudio"));
    }

    @Test
    public void imageCanPairWithTheSavedSingleFrameAfterReopening() throws Exception {
        WordReportDocument first = new WordReportDocument(fixture());
        WordReportDocument.Evidence left = evidence();
        left.title = "first-image.png";
        left.metadata.put("Nome", left.title);
        left.metadata.put("Hash", "first-image-hash");
        first.insert(3, Collections.singletonList(left), true, true);
        Path saved = temporary.getRoot().toPath().resolve("first-image.docx");
        first.write(saved);
        byte[] original = Files.readAllBytes(saved);
        WordReportDocument next = new WordReportDocument(saved);
        assertTrue(next.hasPreviousImage(5));
        assertFalse(next.hasPreviousChat(5));
        WordReportDocument.Evidence right = evidence();
        right.title = "second-image.png";
        right.metadata.put("Nome", right.title);
        right.metadata.put("Hash", "second-image-hash");
        right.image.setRGB(0, 0, 0xff00aa);
        right.besidePrevious = true;
        assertEquals(5, next.insert(5, Collections.singletonList(right), true, true));
        Path output = temporary.getRoot().toPath().resolve("paired-images.docx");
        next.write(output);
        assertArrayEquals(original, Files.readAllBytes(saved));
        org.w3c.dom.Element table = (org.w3c.dom.Element) documentXml(output)
                .getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
        assertEquals(3, rows.getLength());
        for (int i = 0; i < 3; i++) {
            assertEquals(2, ((org.w3c.dom.Element) rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
        }
        org.w3c.dom.NodeList metadata = ((org.w3c.dom.Element) rows.item(2)).getElementsByTagNameNS(WordReportDocument.W, "tc");
        assertTrue(metadata.item(0).getTextContent().contains("first-image-hash"));
        assertFalse(metadata.item(0).getTextContent().contains("second-image-hash"));
        assertTrue(metadata.item(1).getTextContent().contains("second-image-hash"));
        assertFalse(metadata.item(1).getTextContent().contains("first-image-hash"));
        WordReportDocument paired = new WordReportDocument(output);
        assertFalse(paired.hasPreviousImage(5));
        WordReportDocument.Evidence third = evidence();
        third.besidePrevious = true;
        try {
            paired.insert(5, Collections.singletonList(third), true, true);
            fail("A complete pair must not accept a third image");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void explicitImagePairAndSeparatePlacementOverrideTheTwoColumnDefault() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence first = evidence(), second = evidence();
        second.besidePrevious = true;
        assertEquals(5, report.insert(3, Arrays.asList(first, second), true, true, 2));
        Path paired = temporary.getRoot().toPath().resolve("pending-image-pair.docx");
        report.write(paired);
        assertEquals(2, documentXml(paired).getElementsByTagNameNS(WordReportDocument.W, "tbl").getLength());

        report = new WordReportDocument(fixture());
        second.besidePrevious = false;
        second.separateRow = true;
        assertEquals(7, report.insert(3, Arrays.asList(first, second), true, true, 2));
        Path separate = temporary.getRoot().toPath().resolve("separate-images.docx");
        report.write(separate);
        org.w3c.dom.NodeList tables = documentXml(separate).getElementsByTagNameNS(WordReportDocument.W, "tbl");
        assertEquals(3, tables.getLength());
        for (int i = 1; i < tables.getLength(); i++) {
            assertEquals(3, ((org.w3c.dom.Element) tables.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
        }
    }

    @Test
    public void imagesDoNotPairWithChatFramesOrUnrelatedTables() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        assertFalse(report.hasPreviousImage(2));
        report.insert(3, Collections.singletonList(chat("first")), true, true);
        Path file = temporary.getRoot().toPath().resolve("chat-before-image.docx");
        report.write(file);
        report = new WordReportDocument(file);
        assertFalse(report.hasPreviousImage(5));
        assertTrue(report.hasPreviousChat(5));
        WordReportDocument.Evidence image = evidence();
        image.besidePrevious = true;
        try {
            report.insert(5, Collections.singletonList(image), true, true);
            fail("An image must not silently replace a chat transcription column");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void pairingAnEarlierChatOmitsItsLegacyAudioHashAndKeepsTheSourceHashes() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence first = chat("first");
        WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
        audio.text = "Transcrição aprovada.";
        first.transcripts.add(audio);
        int after = report.insert(3, Collections.singletonList(first), true, true);
        Path original = temporary.getRoot().toPath().resolve("earlier-chat.docx");
        report.write(original);
        org.w3c.dom.Document xml = documentXml(original);
        org.w3c.dom.Element table = (org.w3c.dom.Element) xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        org.w3c.dom.Element row = (org.w3c.dom.Element) table.getElementsByTagNameNS(WordReportDocument.W, "tr").item(0);
        org.w3c.dom.Element cell = (org.w3c.dom.Element) row.getElementsByTagNameNS(WordReportDocument.W, "tc").item(1);
        org.w3c.dom.Element p = xml.createElementNS(WordReportDocument.W, "w:p");
        org.w3c.dom.Element run = xml.createElementNS(WordReportDocument.W, "w:r");
        org.w3c.dom.Element text = xml.createElementNS(WordReportDocument.W, "w:t");
        text.setTextContent("Hash: legacy-audio-hash");
        run.appendChild(text);
        p.appendChild(run);
        cell.appendChild(p);
        String wp = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
        String drawing = "http://schemas.openxmlformats.org/drawingml/2006/main";
        ((org.w3c.dom.Element) table.getElementsByTagNameNS(wp, "extent").item(0)).setAttribute("cy", "200");
        org.w3c.dom.Element blip = (org.w3c.dom.Element) table.getElementsByTagNameNS(drawing, "blip").item(0);
        blip.appendChild(xml.createElementNS(drawing, "a:extLst")).appendChild(xml.createElementNS(drawing, "a:ext"));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                new javax.xml.transform.dom.DOMSource(xml), new javax.xml.transform.stream.StreamResult(bytes));
        Path legacy = temporary.getRoot().toPath().resolve("legacy-chat.docx");
        try (ZipFile source = new ZipFile(original.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(legacy))) {
            for (ZipEntry entry : Collections.list(source.entries())) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals("word/document.xml")) {
                    out.write(bytes.toByteArray());
                } else {
                    try (InputStream in = source.getInputStream(entry)) {
                        in.transferTo(out);
                    }
                }
                out.closeEntry();
            }
        }
        report = new WordReportDocument(legacy);
        WordReportDocument.Evidence second = chat("second");
        second.besidePrevious = true;
        report.insert(after, Collections.singletonList(second), true, true);
        Path paired = temporary.getRoot().toPath().resolve("paired-legacy-chat.docx");
        report.write(paired);
        org.w3c.dom.Document pairedXml = documentXml(paired);
        org.w3c.dom.Element frame = (org.w3c.dom.Element) pairedXml.getElementsByTagNameNS(wp, "extent").item(0);
        org.w3c.dom.Element transform = (org.w3c.dom.Element) pairedXml.getElementsByTagNameNS(drawing, "xfrm").item(0);
        org.w3c.dom.Element shape = (org.w3c.dom.Element) transform.getElementsByTagNameNS(drawing, "ext").item(0);
        assertEquals(2 * Long.parseLong(frame.getAttribute("cy")), Long.parseLong(frame.getAttribute("cx")));
        assertEquals(frame.getAttribute("cx"), shape.getAttribute("cx"));
        assertEquals(frame.getAttribute("cy"), shape.getAttribute("cy"));
        assertFalse(((org.w3c.dom.Element) pairedXml.getElementsByTagNameNS(drawing, "ext").item(0)).hasAttribute("cx"));
        String content = documentXml(paired).getDocumentElement().getTextContent();
        assertFalse(content.contains("legacy-audio-hash"));
        assertTrue(content.contains(audio.text));
        assertTrue(content.contains("first-hash"));
        assertTrue(content.contains("second-hash"));
    }

    @Test
    public void pendingChatPairRetainsEachApprovedTranscriptionUnderItsOwnImage() throws Exception {
        WordReportDocument report = new WordReportDocument(fixture());
        WordReportDocument.Evidence first = chat("first"), second = chat("second");
        WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
        audio.text = "Transcrição do primeiro trecho.";
        first.transcripts.add(audio);
        second.besidePrevious = true;
        assertEquals(5, report.insert(3, Arrays.asList(first, second), true, true));
        Path output = temporary.getRoot().toPath().resolve("pending-chat-pair.docx");
        report.write(output);
        org.w3c.dom.Element table = (org.w3c.dom.Element) documentXml(output).getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        org.w3c.dom.Element row = (org.w3c.dom.Element) table.getElementsByTagNameNS(WordReportDocument.W, "tr").item(0);
        org.w3c.dom.NodeList cells = row.getElementsByTagNameNS(WordReportDocument.W, "tc");
        assertEquals(2, cells.getLength());
        assertTrue(cells.item(0).getTextContent().contains(audio.text));
        assertFalse(cells.item(1).getTextContent().contains(audio.text));
        assertEquals(1, ((org.w3c.dom.Element) cells.item(0)).getElementsByTagNameNS(WordReportDocument.W, "drawing").getLength());
        assertEquals(1, ((org.w3c.dom.Element) cells.item(1)).getElementsByTagNameNS(WordReportDocument.W, "drawing").getLength());
    }

    @Test
    public void fourCropsCanExtendASavedPairAndShareTheirFileInformation() throws Exception {
        Path source = fixture();
        int after = 3;
        final String hash = "abc123";
        for (int i = 0; i < 4; i++) {
            WordReportDocument report = new WordReportDocument(source);
            if (i > 0) {
                assertEquals(i, report.previousChatCount(after, hash));
                assertEquals(0, report.previousChatCount(after, "different"));
            }
            WordReportDocument.Evidence item = chat("same");
            item.metadata.put("Hash", "ABC123");
            item.imageDescription = "Crop " + (i + 1);
            item.image = new BufferedImage(120 + 15 * i, 480, BufferedImage.TYPE_INT_RGB);
            item.besidePrevious = i > 0;
            after = report.insert(after, Collections.singletonList(item), true, true);
            source = temporary.getRoot().toPath().resolve("chat-" + i + ".docx");
            report.write(source);
            assertEquals(i == 3 ? 1 : 0, documentXml(source).getElementsByTagNameNS(WordReportDocument.W, "br").getLength());
        }
        org.w3c.dom.Element grid = (org.w3c.dom.Element) documentXml(source)
                .getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
        assertEquals(4, grid.getElementsByTagNameNS(WordReportDocument.W, "tr").getLength());
        String wp = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
        for (int i = 0; i < 4; i++) {
            org.w3c.dom.Element drawing = (org.w3c.dom.Element) grid.getElementsByTagNameNS(wp, "docPr").item(i);
            assertEquals("Crop " + (i + 1), drawing.getAttribute("descr"));
            org.w3c.dom.Element bounds = (org.w3c.dom.Element) grid.getElementsByTagNameNS(wp, "extent").item(i);
            assertEquals((120 + 15 * i) / 480.0, Double.parseDouble(bounds.getAttribute("cx"))
                    / Double.parseDouble(bounds.getAttribute("cy")), 0.00001);
        }
        assertEquals(1, grid.getTextContent().split("Hash:", -1).length - 1);
        WordReportDocument full = new WordReportDocument(source);
        assertEquals(0, full.previousChatCount(after, hash));
        WordReportDocument continued = new WordReportDocument(source);
        continued.insert(after, Collections.singletonList(chat("next")), true, true);
        Path next = temporary.getRoot().toPath().resolve("continued.docx");
        continued.write(next);
        assertEquals(2, documentXml(next).getElementsByTagNameNS(WordReportDocument.W, "br").getLength());
        WordReportDocument.Evidence fifth = chat("same");
        fifth.metadata.put("Hash", hash);
        fifth.besidePrevious = true;
        try {
            full.insert(after, Collections.singletonList(fifth), true, true);
            fail("A full chat grid must not accept a fifth image");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void chatGridPreservesDistinctPathsAndRejectsUnrelatedOrAnnotatedCrops() throws Exception {
        List<WordReportDocument.Evidence> crops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            WordReportDocument.Evidence item = chat("source-" + i);
            item.metadata.put("Hash", "shared");
            item.besidePrevious = i > 0;
            crops.add(item);
        }
        WordReportDocument report = new WordReportDocument(fixture());
        report.insert(3, crops, true, true);
        Path output = temporary.getRoot().toPath().resolve("distinct-paths.docx");
        report.write(output);
        String xmlText = documentXml(output).getDocumentElement().getTextContent();
        for (WordReportDocument.Evidence item : crops) {
            assertTrue(xmlText.contains(item.metadata.get("Caminho")));
        }
        for (String invalid : new String[] { "different", "", "transcript", "attachment" }) {
            WordReportDocument.Evidence third = chat("third");
            third.besidePrevious = true;
            third.metadata.put("Hash", invalid);
            if ("transcript".equals(invalid) || "attachment".equals(invalid)) {
                third.metadata.put("Hash", "shared");
                if ("attachment".equals(invalid)) {
                    third.attachment = evidence();
                } else {
                    third.transcripts.add(evidence());
                }
            }
            try {
                new WordReportDocument(fixture()).insert(3, Arrays.asList(crops.get(0), crops.get(1), third), true, true);
                fail("Ineligible crop was grouped: " + invalid);
            } catch (java.io.IOException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }

    @Test
    public void pairingWithoutPreviousChatIsRejected() throws Exception {
        WordReportDocument.Evidence chat = chat("first");
        chat.besidePrevious = true;
        try {
            new WordReportDocument(fixture()).insert(3, Collections.singletonList(chat), true, true);
            fail("A pair requires an eligible previous chat");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void workingDocumentsAreRememberedPerCaseWithoutPersistingSharedLinks() throws Exception {
        String previous = System.getProperty("user.home");
        Path home = temporary.newFolder("case-profile").toPath();
        try {
            System.setProperty("user.home", home.toString());
            WordReportFiles.rememberSelection("case-a", 0, "report-a.docx");
            WordReportFiles.rememberSelection("case-b", 2, "template-b.docx");
            assertEquals("report-a.docx", WordReportFiles.lastSelection("case-a").value);
            assertEquals("template-b.docx", WordReportFiles.lastSelection("case-b").value);
            assertNull(WordReportFiles.lastSelection("case-c"));
            String shared = "https://example.invalid/shared?token=private";
            WordReportFiles.rememberSelection("case-a", 1, shared);
            assertEquals(shared, WordReportFiles.lastSelection("case-a").value);
            try (java.util.stream.Stream<Path> files = Files.walk(home)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    assertFalse(Files.readString(file).contains("token=private"));
                }
            }
        } finally {
            System.setProperty("user.home", previous);
        }
    }

    @Test
    public void chatHtmlPreservesMessageOrderSendersAndTimesWithoutRunningResources() throws Exception {
        String html = "<html><head><style>ignored</style><script>ignored()</script></head><body>"
                + "<div class='linha'><div class='bbl'><div class='incoming from'><span class='name'>Pessoa A</span><br>"
                + "Primeira &amp; completa<br><span class='time'>2026-01-01 10:00</span></div></div></div>"
                + "<div class='linha'><div class='bbr'><div class='outgoing to'><span class='name'>Pessoa B</span><br>"
                + "Resposta com acentuação<span class='time'>2026-01-01 10:01</span></div></div></div>"
                + "<img src='https://example.invalid/do-not-load'><script>doNotRun()</script></body></html>";
        WordReportDocument.Evidence chat = evidence();
        java.util.List<WordReportDocument.Evidence> rows = WordReportChat.read(new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), chat);
        assertEquals(2, rows.size());
        assertEquals("Pessoa A", rows.get(0).participants);
        assertTrue(rows.get(0).text.contains("Primeira & completa"));
        assertTrue(rows.get(0).text.contains("2026-01-01 10:00"));
        assertTrue(rows.get(1).text.contains("acentuação\n2026-01-01 10:01"));
        assertFalse(rows.get(0).text.contains("Pessoa A"));
        assertFalse(rows.get(0).text.contains("ignored"));
        assertEquals("Pessoa B", rows.get(1).participants);
        assertTrue(rows.get(1).text.contains("Resposta com acentuação"));
        assertEquals(chat.metadata, rows.get(0).metadata);
        chat.messages.addAll(rows);
        chat.message = true;
        WordReportDocument report = new WordReportDocument(fixture());
        report.insert(3, Collections.singletonList(chat), true, true);
        Path output = temporary.getRoot().toPath().resolve("chat-html.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.indexOf("Pessoa A") < xml.indexOf("Pessoa B"));
            assertTrue(xml.contains("2026-01-01 10:01"));
            assertFalse(xml.contains("doNotRun"));
        }
    }

    @Test
    public void legacyUfedSenderUsesTheOriginalInlineStyle() throws Exception {
        String html = "<html><body><div class='linha'><div class='bbl'><div class='incoming from'>"
                + "<span style='font-family: Arial; color: #b4c74b;'>Pessoa A</span><br/>"
                + "Mensagem completa<span class='time'>2026-01-01 10:00</span></div></div></div></body></html>";
        java.util.List<WordReportDocument.Evidence> rows = WordReportChat.read(
                new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), evidence());
        assertEquals(1, rows.size());
        assertEquals("Pessoa A", rows.get(0).participants);
        assertEquals("Mensagem completa\n2026-01-01 10:00", rows.get(0).text);
    }

    @Test
    public void videoSamplingKeepsChronologicalFramesWithoutFillingWithDuplicates() {
        java.util.List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            BufferedImage frame = new BufferedImage(40, 80, BufferedImage.TYPE_INT_RGB);
            frame.setRGB(0, 0, i);
            frames.add(frame);
        }
        java.util.List<BufferedImage> selected = WordReportVideo.sample(frames, 20);
        assertEquals(20, selected.size());
        assertSame(frames.get(0), selected.get(0));
        assertSame(frames.get(29), selected.get(19));
        for (int i = 1; i < selected.size(); i++) {
            assertTrue(selected.get(i - 1).getRGB(0, 0) < selected.get(i).getRGB(0, 0));
        }
        assertEquals(2, WordReportVideo.sample(frames.subList(0, 2), 20).size());
        BufferedImage grid = WordReportVideo.contactSheet(selected);
        assertEquals(5 * 42 + 2, grid.getWidth());
        assertEquals(4 * 82 + 2, grid.getHeight());
    }

    @Test
    public void videoUsesSixFragmentsMergedContactGridAndOneFileInformationBlock() throws Exception {
        WordReportDocument.Evidence video = evidence();
        video.video = true;
        video.title = "video-exemplo.mp4";
        video.text = "Transcrição integral do vídeo.";
        for (int i = 0; i < 20; i++) {
            video.videoFrames.add(new BufferedImage(80, 140, BufferedImage.TYPE_INT_RGB));
        }
        video.image = WordReportVideo.contactSheet(video.videoFrames);
        WordReportDocument report = new WordReportDocument(fixture());
        assertEquals(5, report.insert(3, Collections.singletonList(video), true, true, 2));
        Path output = temporary.getRoot().toPath().resolve("video.docx");
        report.write(output);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
            org.w3c.dom.Element table = (org.w3c.dom.Element)xml.getElementsByTagNameNS(WordReportDocument.W, "tbl").item(1);
            org.w3c.dom.NodeList rows = table.getElementsByTagNameNS(WordReportDocument.W, "tr");
            assertEquals(5, rows.getLength());
            for (int i = 0; i < 3; i++) {
                assertEquals(3, ((org.w3c.dom.Element)rows.item(i)).getElementsByTagNameNS(WordReportDocument.W, "tc").getLength());
            }
            assertEquals(7, table.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip").getLength());
            assertEquals("Informações do arquivo", rows.item(3).getTextContent());
            assertTrue(table.getTextContent().contains("Vídeo separado em frames"));
            assertTrue(table.getTextContent().contains("Áudio constante no vídeo"));
            assertTrue(table.getTextContent().contains(video.text));
            assertTrue(rows.item(4).getTextContent().contains("Caminho:"));
        }
    }

    @Test
    public void neutralTemplateSupportsRepeatedExportWithoutBundledDocuments() throws Exception {
        assertNull(getClass().getResourceAsStream("word-report-template.docx"));
        Path model = WordReportFiles.defaultTemplate(temporary.newFolder().toPath());
        try (ZipFile zip = new ZipFile(model.toFile())) {
            assertEquals(6, zip.size());
            assertFalse(zip.stream().anyMatch(e -> e.getName().startsWith("word/media/")
                    || e.getName().startsWith("docProps/") || e.getName().startsWith("customXml/")));
        }
        WordReportDocument report = new WordReportDocument(model);
        int data = -1;
        for (int i = 0; i < report.getLocations().size(); i++) {
            if (report.getLocationText(i).equals("Dados obtidos")) {
                data = i;
            }
        }
        assertTrue(data > 0);
        report.insert(data, Collections.singletonList(evidence()), true, true);
        Path first = temporary.getRoot().toPath().resolve("first.docx");
        report.write(first);
        WordReportDocument next = new WordReportDocument(first);
        next.insert(next.getLocations().size() - 1, Collections.singletonList(evidence()), true, true);
        Path second = temporary.getRoot().toPath().resolve("second.docx");
        next.write(second);
        try (ZipFile zip = new ZipFile(second.toFile())) {
            assertEquals(2, zip.stream().filter(e -> e.getName().startsWith("word/media/iped-report-")).count());
        }
        String artifact = System.getProperty("iped.test.wordReportOutput");
        if (artifact != null) {
            Files.copy(first, Path.of(artifact));
        }
    }
}
