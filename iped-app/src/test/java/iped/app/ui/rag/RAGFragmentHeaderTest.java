package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.localization.LocaleResolver;
import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;

public class RAGFragmentHeaderTest {

    private static final String HEADER = StandardParser.METADATA_HEADER;

    private Locale previous;

    @Before
    public void setUp() {
        previous = LocaleResolver.getLocale();
        LocaleResolver.setLocale(new Locale("pt", "BR"));
    }

    @After
    public void tearDown() {
        LocaleResolver.setLocale(previous);
    }

    @Test
    public void testEmailHeaderHasSubjectSenderRecipientsDateAndPath() {
        Metadata m = new Metadata();
        m.set(ExtraProperties.MESSAGE_SUBJECT, "Reunião de quinta");
        m.set(ExtraProperties.COMMUNICATION_FROM, "ana@example.com");
        m.add(ExtraProperties.COMMUNICATION_TO, "bruno@example.com");
        m.add(ExtraProperties.COMMUNICATION_TO, "carla@example.com");
        m.set(ExtraProperties.COMMUNICATION_DATE.getName(), "2001-10-25T03:49:53Z");
        m.set(common(TikaCoreProperties.CREATOR), "ana@example.com");
        m.set(common(TikaCoreProperties.TITLE), "Reunião de quinta");

        assertEquals("[7] Documento: 90_\n" //
                + "Assunto: Reunião de quinta\n" //
                + "De: ana@example.com\n" //
                + "Para: bruno@example.com; carla@example.com\n" //
                + "Data: 2001-10-25T03:49:53Z\n" //
                + "Caminho: mail/inbox/90_\n" //
                + "---\n", RAGAssistantPanel.fragmentHeader("7", "90_", "mail/inbox/90_", m));
    }

    @Test
    public void testDocumentHeaderHasTitleAuthorAndCreation() {
        Metadata m = new Metadata();
        m.set(common(TikaCoreProperties.TITLE), "Relatório anual");
        m.set(common(TikaCoreProperties.CREATOR), "Fulano de Tal");
        m.set(common(TikaCoreProperties.CREATED), "2023-09-13T20:43:58Z");

        assertEquals("[3] Documento: rel.pdf\n" //
                + "Título: Relatório anual\n" //
                + "Autor: Fulano de Tal\n" //
                + "Criado em: 2023-09-13T20:43:58Z\n" //
                + "Caminho: docs/rel.pdf\n" //
                + "---\n", RAGAssistantPanel.fragmentHeader("3", "rel.pdf", "docs/rel.pdf", m));
    }

    @Test
    public void testTitleEqualToNameAndMissingFieldsAreOmitted() {
        Metadata m = new Metadata();
        m.set(common(TikaCoreProperties.TITLE), "rel.pdf");
        assertEquals("[3] Documento: rel.pdf\n---\n", RAGAssistantPanel.fragmentHeader("3", "rel.pdf", null, m));
    }

    @Test
    public void testLongDetailIsCutAndWhitespaceCollapsed() {
        Metadata m = new Metadata();
        m.set(ExtraProperties.MESSAGE_SUBJECT, "a\n b " + "x".repeat(500));
        String header = RAGAssistantPanel.fragmentHeader("1", null, null, m);
        assertTrue(header.startsWith("[1]\nAssunto: a b xxx"));
        assertEquals("Assunto: ".length() + RAGAssistantPanel.MAX_DETAIL_CHARS + "(...)".length(),
                header.split("\n")[1].length());
    }

    @Test
    public void testIdenticalCopiesAreListedInTheHeader() {
        String header = RAGAssistantPanel.fragmentHeader("7", "90_");
        assertSame(header, RAGAssistantPanel.withIdenticalCopies(header, java.util.Collections.emptyList()));
        assertEquals("[7] Documento: 90_\nMesmo conteúdo em: [8] msgstore_1.db; [9]\n---\n",
                RAGAssistantPanel.withIdenticalCopies(header, java.util.Arrays.asList("[8] msgstore_1.db", "[9]")));
    }

    @Test
    public void testLabelsFollowThePromptLanguage() {
        LocaleResolver.setLocale(Locale.ENGLISH);
        Metadata m = new Metadata();
        m.set(ExtraProperties.MESSAGE_SUBJECT, "Thursday meeting");
        m.set(ExtraProperties.COMMUNICATION_FROM, "ana@example.com");
        assertEquals("[7] Document: 90_\n" //
                + "Subject: Thursday meeting\n" //
                + "From: ana@example.com\n" //
                + "Path: mail/inbox/90_\n" //
                + "---\n", RAGAssistantPanel.fragmentHeader("7", "90_", "mail/inbox/90_", m));
        assertEquals("[7] Document: 90_\nSame content in: [8]\n---\n", RAGAssistantPanel.withIdenticalCopies(
                RAGAssistantPanel.fragmentHeader("7", "90_"), java.util.Arrays.asList("[8]")));
    }

    @Test
    public void testTrailingMetadataBlocksAreRemoved() {
        String body = "Texto do e-mail.\nAssinatura";
        String content = body + "\n\n" + HEADER + "\n\n" + HEADER + "\nMessage-Subject: OOO\n"
                + "Message:Raw-Header:X-Folder: \\Pasta\\Itens\n" + StandardParser.METADATA_FOOTER + "\n";
        assertEquals(body, RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testBlockCutAtFragmentBoundaryIsRemoved() {
        String content = "Corpo\n" + HEADER + "\nMessage-Subject: OOO\nMessage:Raw-Hea";
        assertEquals("Corpo", RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testValuesSpanningLinesAreRemoved() {
        String content = "Corpo\n" + HEADER + "\nCommunication:From: ana@example.com\n"
                + "Message-Body: Ana\nAnalista\nRamal 1234\nMessage-Subject: OOO\nMessage:AttachmentCount: 0\n";
        assertEquals("Corpo", RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testHeaderFollowedByRegularTextIsKept() {
        String content = "Início\n" + HEADER + "\ncampo: valor\nTexto comum sem dois pontos\nfim do trecho";
        assertSame(content, RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testHeaderInsideLineIsKept() {
        String content = "Veja os " + HEADER + "\ncampo: valor\n";
        assertSame(content, RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testFragmentWithOnlyMetadataIsKeptWhole() {
        String content = HEADER + "\nModel: Camera X\nGPS Latitude: -15.79\n";
        assertSame(content, RAGAssistantPanel.stripTrailingMetadata(content));
    }

    @Test
    public void testTextWithoutMetadataIsUnchanged() {
        String content = "Somente texto.\n";
        assertSame(content, RAGAssistantPanel.stripTrailingMetadata(content));
    }

    private static String common(org.apache.tika.metadata.Property p) {
        return ExtraProperties.COMMON_META_PREFIX + p.getName();
    }
}
