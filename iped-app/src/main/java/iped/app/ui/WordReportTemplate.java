package iped.app.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a neutral starter document without distributing a user's template. */
final class WordReportTemplate {

    private static final String W = WordReportDocument.W;
    private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOCUMENT_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";

    private WordReportTemplate() {
    }

    static void create(Path destination) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/settings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", "<Relationships xmlns=\"" + REL + "\"><Relationship Id=\"rId1\" Type=\""
                + DOCUMENT_REL + "officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        parts.put("word/_rels/document.xml.rels", "<Relationships xmlns=\"" + REL + "\">"
                + "<Relationship Id=\"rIdStyles\" Type=\"" + DOCUMENT_REL + "styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rIdSettings\" Type=\"" + DOCUMENT_REL + "settings\" Target=\"settings.xml\"/>"
                + "</Relationships>");
        parts.put("word/styles.xml", styles());
        parts.put("word/settings.xml", "<w:settings xmlns:w=\"" + W + "\"><w:updateFields w:val=\"true\"/></w:settings>");
        parts.put("word/document.xml", document());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + part.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String styles() {
        return "<w:styles xmlns:w=\"" + W + "\">"
                + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/>"
                + "<w:color w:val=\"000000\"/><w:sz w:val=\"22\"/><w:lang w:val=\"pt-BR\"/>"
                + "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>"
                + "</w:pPr></w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/>"
                + "<w:pPr><w:jc w:val=\"center\"/><w:spacing w:before=\"2400\" w:after=\"480\"/></w:pPr>"
                + "<w:rPr><w:b/><w:sz w:val=\"44\"/></w:rPr></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"TOCHeading\"><w:name w:val=\"TOC Heading\"/>"
                + "<w:basedOn w:val=\"Normal\"/><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"360\"/></w:pPr>"
                + "<w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style>"
                + headingStyle("Heading1", "heading 1", 0, 28)
                + headingStyle("Heading2", "heading 2", 1, 24) + "</w:styles>";
    }

    private static String headingStyle(String id, String name, int level, int size) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + name + "\"/>"
                + "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:pPr><w:keepNext/>"
                + "<w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"" + level + "\"/></w:pPr>"
                + "<w:rPr><w:b/><w:sz w:val=\"" + size + "\"/></w:rPr></w:style>";
    }

    private static String document() {
        StringBuilder body = new StringBuilder("<w:document xmlns:w=\"").append(W).append("\"><w:body>");
        paragraph(body, "Title", "RELATÓRIO DE ANÁLISE", false);
        paragraph(body, "Normal", "Número e ano: [PREENCHER]", false);
        paragraph(body, "Normal", "Data: [PREENCHER]", false);
        paragraph(body, "Normal", "Referência: [PREENCHER]", false);
        paragraph(body, "Normal", "Assunto: [PREENCHER]", false);
        paragraph(body, "Normal", "Responsável: [PREENCHER]", false);
        paragraph(body, "TOCHeading", "SUMÁRIO", true);
        body.append("<w:p><w:r><w:fldChar w:fldCharType=\"begin\" w:dirty=\"true\"/></w:r>"
                + "<w:r><w:instrText xml:space=\"preserve\"> TOC \\o &quot;1-2&quot; \\h \\z \\u </w:instrText></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>"
                + "<w:r><w:t>Atualize o sumário após editar o relatório.</w:t></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>");
        paragraph(body, "Heading1", "Introdução", true);
        paragraph(body, "Normal", "[CONTEXTO E FINALIDADE DO RELATÓRIO]", false);
        paragraph(body, "Heading1", "Identificação", false);
        paragraph(body, "Normal", "[REFERÊNCIA DO PROCEDIMENTO]", false);
        paragraph(body, "Heading2", "Vestígios", false);
        paragraph(body, "Normal", "[IDENTIFICAÇÃO DO MATERIAL E ORIGEM]", false);
        paragraph(body, "Normal", "[EXTRAÇÃO, FERRAMENTAS E VERIFICAÇÃO DE INTEGRIDADE]", false);
        paragraph(body, "Heading1", "Objetivo e metodologia", false);
        paragraph(body, "Normal", "[OBJETIVO, ESCOPO, LIMITAÇÕES E MÉTODO UTILIZADO]", false);
        paragraph(body, "Heading1", "Dados obtidos", false);
        paragraph(body, "Normal", "[INSERIR EVIDÊNCIAS DO IPED NESTA SEÇÃO]", false);
        paragraph(body, "Heading1", "Análise", false);
        paragraph(body, "Normal", "[ANÁLISE E CONTEXTUALIZAÇÃO DAS EVIDÊNCIAS]", false);
        paragraph(body, "Heading1", "Conclusão", false);
        paragraph(body, "Normal", "[CONCLUSÃO, LOCAL, DATA E RESPONSÁVEL]", false);
        paragraph(body, "Heading1", "Anexos", false);
        paragraph(body, "Normal", "[RELAÇÃO DE ANEXOS]", false);
        return body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"720\" w:footer=\"720\"/>"
                + "</w:sectPr></w:body></w:document>").toString();
    }

    private static void paragraph(StringBuilder body, String style, String text, boolean pageBreak) {
        body.append("<w:p><w:pPr><w:pStyle w:val=\"").append(style).append("\"/>");
        if (pageBreak) {
            body.append("<w:pageBreakBefore/>");
        }
        body.append("</w:pPr><w:r><w:t>").append(text).append("</w:t></w:r></w:p>");
    }
}
