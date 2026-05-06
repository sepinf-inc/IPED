package iped.viewers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.UIManager;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.properties.ExtraProperties;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;
import iped.viewers.localization.Messages;

/**
 * Shows ExtraProperties.SUMMARY (array of strings) for the current item.
 * Extends HtmlViewer to reuse hit highlighting & WebView plumbing.
 */
public class SummaryViewer extends HtmlViewer {

    private static final String ANALYSIS_PREFIX = "ai:analysis:";
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    @Override
    public String getName() {
        return Messages.getString("SummaryViewer.TabName");
    }

    private static void addValues(ArrayList<String> values, Object value) {
        if (value instanceof Collection<?>) {
            for (Object v : (Collection<?>) value) {
                if (v != null) values.add(v.toString());
            }
        } else if (value instanceof Object[]) {
            for (Object v : Arrays.asList((Object[]) value)) {
                if (v != null) values.add(v.toString());
            }
        } else if (value instanceof String) {
            values.add((String) value);
        } else if (value != null) {
            values.add(value.toString());
        }
    }

    private static String humanizeAnalysisName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "Score";
        }
        String name = rawName.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) {
                sb.append(' ');
            }
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String renderSummaryMarkup(String text) {
        String html = SimpleHTMLEncoder.htmlEncode(text).replace("\n", "<br>");
        Matcher matcher = BOLD_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<strong>" + matcher.group(1) + "</strong>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public boolean isSupportedType(String contentType) {
        // we gate by data presence
        return true;
    }

    @Override
    public int getHitsSupported() {
        return 1;
    }

    /** Quick presence check so controller can decide tab visibility. */
    public boolean hasSummary(IStreamSource content) {
        if (!(content instanceof IItemReader)) return false;
        IItemReader item = (IItemReader) content;

        // Check extra attributes (preferred)
        Object v = item.getExtraAttribute(ExtraProperties.SUMMARY);
        if (v != null) return true;

        // Fallback: metadata bag
        String[] vals = item.getMetadata().getValues(ExtraProperties.SUMMARY);
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
            if (!(content instanceof IItemReader) || !hasSummary(content)) {
                webEngine.loadContent(UiUtil.getUIEmptyHtml("[" + Messages.getString("SummaryViewer.NoSummary") + "]"));
                return;
            }

            IItemReader item = (IItemReader) content;
            ArrayList<String> chunks = new ArrayList<>();
            Map<String, List<String>> analysisValues = new LinkedHashMap<>();

            Object value = item.getExtraAttribute(ExtraProperties.SUMMARY);
            addValues(chunks, value);

            // Fallback to metadata if we still have nothing
            if (chunks.isEmpty()) {
                String[] vals = item.getMetadata().getValues(ExtraProperties.SUMMARY);
                if (vals != null) {
                    for (String s : vals) {
                        if (s != null) chunks.add(s);
                    }
                }
            }

            Map<String, Object> extraAttributes = item.getExtraAttributeMap();
            if (extraAttributes != null) {
                for (Map.Entry<String, Object> entry : extraAttributes.entrySet()) {
                    String key = entry.getKey();
                    if (!key.startsWith(ANALYSIS_PREFIX)) {
                        continue;
                    }
                    ArrayList<String> values = new ArrayList<>();
                    addValues(values, entry.getValue());
                    if (!values.isEmpty()) {
                        analysisValues.put(humanizeAnalysisName(key.substring(ANALYSIS_PREFIX.length())), values);
                    }
                }
            }

            // Use theme colors
            Color background = UIManager.getColor("Viewer.background");
            if (background == null) {
                background = Color.white;
            }
            Color foreground = UIManager.getColor("Viewer.foreground");
            if (foreground == null) {
                foreground = Color.black;
            }

            // Simple, readable HTML; HtmlViewer will highlight search terms after load.
            StringBuilder html = new StringBuilder();
            html.append("<!doctype html><html><head><meta charset='utf-8'>")
                .append("<style>")
                .append("body {font:13px sans-serif; margin:8px;")
                .append("background-color:").append(UiUtil.getHexRGB(background)).append(";")
                .append("color:").append(UiUtil.getHexRGB(foreground)).append(";")
                .append("}")
                .append(".chunk {margin:8px 0; padding:10px; border:1px solid #ccc; border-radius:8px;}")
                .append(".chunk-top {display:flex; justify-content:flex-end; margin-bottom:8px;}")
                .append(".title {font-weight:bold; margin-bottom:4px;}")
                .append(".chunk-meta {font-size:11px; opacity:0.85; text-align:right; max-width:60%; line-height:1.35;}")
                .append("</style>")
                .append("</head><body>");

            html.append("<div class='title'>").append(Messages.getString("SummaryViewer.Title")).append("</div>");

            for (int i = 0; i < chunks.size(); i++) {
                String c = renderSummaryMarkup(chunks.get(i));
                html.append("<div class='chunk'>");
                if (!analysisValues.isEmpty()) {
                    ArrayList<String> chunkMeta = new ArrayList<>();
                    for (Map.Entry<String, List<String>> entry : analysisValues.entrySet()) {
                        if (i < entry.getValue().size()) {
                            String score = entry.getValue().get(i);
                            if (score != null && !score.isBlank()) {
                                chunkMeta.add(entry.getKey() + ": " + score);
                            }
                        }
                    }
                    if (!chunkMeta.isEmpty()) {
                        html.append("<div class='chunk-top'><div class='chunk-meta'>")
                            .append(SimpleHTMLEncoder.htmlEncode(String.join(" / ", chunkMeta)))
                            .append("</div></div>");
                    }
                }
                html.append("<div>")
                    .append("<div>").append(c).append("</div>")
                    .append("</div>")
                    .append("</div>");
            }
            html.append("</body></html>");

            webEngine.setJavaScriptEnabled(false); // not needed here
            webEngine.loadContent(html.toString());
        });
    }
}
