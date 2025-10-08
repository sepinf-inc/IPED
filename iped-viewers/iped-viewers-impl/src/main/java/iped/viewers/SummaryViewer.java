package iped.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.properties.ExtraProperties;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;

/**
 * Shows ExtraProperties.SUMMARIES (array of strings) for the current item.
 * Extends HtmlViewer to reuse hit highlighting & WebView plumbing.
 */
public class SummaryViewer extends HtmlViewer {

    public SummaryViewer() {
        super(); // delegates to HtmlViewer constructor
    }

    @Override public String getName() { return "Summary"; } // or wire into i18n later
    @Override public boolean isSupportedType(String contentType) { return true; } // we gate by data presence
    @Override public int getHitsSupported() { return -1; } // no Prev/Next hit buttons for this tab

    /** Quick presence check so controller can decide tab visibility. */
    public boolean hasSummaries(IStreamSource content) {
        if (!(content instanceof IItemReader)) return false;
        IItemReader item = (IItemReader) content;

        // Check extra attributes (preferred)
        Object v = item.getExtraAttributeMap().get(ExtraProperties.SUMMARIES);
        if (v instanceof Collection && !((Collection<?>) v).isEmpty()) return true;
        if (v instanceof Object[] && ((Object[]) v).length > 0) return true;
        if (v instanceof String && !((String) v).isEmpty()) return true;
        if (v != null) return true; // some non-null single value

        // Fallback: metadata bag
        String[] vals = item.getMetadata().getValues(ExtraProperties.SUMMARIES);
        return vals != null && vals.length > 0;
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        loadFile(content, null, terms);
    }


    @Override
    public void loadFile(final IStreamSource content, String contentType, final Set<String> terms) {
        // Reuse HtmlViewer's highlighter: set highlightTerms and load HTML directly in the WebEngine.
        this.highlightTerms = terms;
        this.tmpFile = null; // ensure the "location endsWith(tmpFile)" early-return never triggers

        javafx.application.Platform.runLater(() -> {
            if (!(content instanceof IItemReader) || !hasSummaries(content)) {
                webEngine.loadContent(UiUtil.getUIEmptyHtml());
                return;
            }

            IItemReader item = (IItemReader) content;
            //List<String> chunks = new ArrayList<>();
            //ArrayList<Object> chunks = new ArrayList<>();
            ArrayList<String> chunks = new ArrayList<>();
            

            Object value = item.getExtraAttributeMap().get(ExtraProperties.SUMMARIES);
            if (value instanceof Collection<?>) {
                for (Object v : (Collection<?>) value) {
                    if (v != null) chunks.add(v.toString());
                }
            } else if (value instanceof Object[]) {
                for (Object v : Arrays.asList((Object[]) value)) {
                    if (v != null) chunks.add(v.toString());
                }
            } else if (value instanceof String) {
                chunks.add((String) value);
            } else if (value != null) {
                chunks.add(value.toString());
            }

            // Fallback to metadata if we still have nothing
            if (chunks.isEmpty()) {
                String[] vals = item.getMetadata().getValues(ExtraProperties.SUMMARIES);
                if (vals != null) {
                    for (String s : vals) {
                        if (s != null) chunks.add(s);
                    }
                }
            }



            // Simple, readable HTML; HtmlViewer will highlight search terms after load.
            StringBuilder html = new StringBuilder();
            html.append("<!doctype html><html><head><meta charset='utf-8'>")
                .append("<style>")
                .append("body{font:13px sans-serif;margin:8px}")
                .append(".chunk{margin:8px 0;padding:10px;border:1px solid #ccc;border-radius:8px}")
                .append(".title{font-weight:600;margin-bottom:4px}")
                .append("</style>")
                .append("</head><body>");

            html.append("<div class='title'>AI-generated summaries. Check all information").append("</div>");

            for (int i = 0; i < chunks.size(); i++) {
                //String c = SimpleHTMLEncoder.htmlEncode(chunks.get(i));
                String c = SimpleHTMLEncoder.htmlEncode(chunks.get(i)).replaceAll("\n","<br>");
                html.append("<div class='chunk'>")
                    //.append("<div class='title'>Chunk ").append(i + 1).append("</div>")
                    .append("<div>").append(c).append("</div>")
                    .append("</div>");
            }
            html.append("</body></html>");

            // Keep IPED HTML style for consistent colors (HtmlViewer uses this).
            webEngine.setUserStyleSheetLocation(UiUtil.getUIHtmlStyle());
            webEngine.setJavaScriptEnabled(false); // not needed here
            webEngine.loadContent(html.toString());
        });
    }
}
