package iped.viewers;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.UIManager;

import org.w3c.dom.Document;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.localization.LocaleResolver;
import iped.properties.ExtraProperties;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;
import iped.viewers.api.MessageNavigator;
import iped.viewers.localization.Messages;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;

/**
 * Shows ExtraProperties.SUMMARY (array of strings) for the current item.
 * Extends HtmlViewer to reuse hit highlighting & WebView plumbing.
 *
 * This viewer also enriches the summary with:
 * - per-chunk analysis labels
 * - a link that navigates to the first message of the chunk in the Preview tab
 */
public class SummaryViewer extends HtmlViewer {

    // Extra attribute prefix used to store one analysis score per chunk.
    private static final String ANALYSIS_PREFIX = "ai:analysis:";
    // Minimal markdown support: convert **text** to <strong>text</strong>.
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    // Reuse the same bundle already used by the AI filters panel.
    private static final String ANALYSIS_BUNDLE = "iped-ai-filters";
    private static ResourceBundle analysisBundle;
    // Fallback analysis levels used when the controller does not inject levels from config.
    private List<AnalysisLevel> analysisLevels = List.of(
            new AnalysisLevel(800, "Analysis.Very High"),
            new AnalysisLevel(600, "Analysis.High"),
            new AnalysisLevel(300, "Analysis.Medium"),
            new AnalysisLevel(150, "Analysis.Low"),
            new AnalysisLevel(Integer.MIN_VALUE, "Analysis.Very Low"));
    private MessageNavigator messageNavigator;

    public SummaryViewer() {
        enableJavascript = true;
        fileHandler = new SummaryViewerFileHandler();
        javafx.application.Platform.runLater(() -> {
            // The JS bridge must be reattached at multiple WebEngine stages because
            // this viewer uses loadContent(...), which is less predictable than file-based loads.
            webEngine.documentProperty().addListener(new ChangeListener<Document>() {
                @Override
                public void changed(ObservableValue<? extends Document> observable, Document oldValue,
                        Document newValue) {
                    addJavascriptListener(webEngine);
                }
            });
            webEngine.getLoadWorker().progressProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue,
                        Number newValue) {
                    addJavascriptListener(webEngine);
                }
            });
            webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                @Override
                public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue,
                        Worker.State newValue) {
                    if (newValue == Worker.State.SUCCEEDED) {
                        addJavascriptListener(webEngine);
                    }
                }
            });
        });
    }

    @Override
    public String getName() {
        return Messages.getString("SummaryViewer.TabName");
    }

    // Normalizes extra-attribute values to a flat list of strings.
    private static void appendStringValues(ArrayList<String> values, Object value) {
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

    // Turns technical names like "fraudScore" into "Fraud Score".
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

    // Escapes the summary text as HTML and then applies the supported markdown subset.
    private static String renderSummaryMarkup(String text) {
        String html = SimpleHTMLEncoder.htmlEncode(text).replace("\n", "<br>");
        Matcher matcher = BOLD_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement("<strong>" + matcher.group(1) + "</strong>"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Escapes a value so it can be safely embedded inside a single-quoted JS string.
    private static String escapeJsSingleQuotedString(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    // Represents one score threshold mapped to a localization key.
    public static final class AnalysisLevel {
        private final int minimumScore;
        private final String bundleKey;

        public AnalysisLevel(int minimumScore, String bundleKey) {
            this.minimumScore = minimumScore;
            this.bundleKey = bundleKey;
        }

        public int getMinimumScore() {
            return minimumScore;
        }

        public String getBundleKey() {
            return bundleKey;
        }
    }

    // Uses the same external bundle as the AI filters tree so labels follow the active IPED language.
    private static ResourceBundle getAnalysisBundle() {
        if (analysisBundle == null) {
            synchronized (SummaryViewer.class) {
                if (analysisBundle == null) {
                    analysisBundle = iped.localization.Messages.getExternalBundle(ANALYSIS_BUNDLE,
                            LocaleResolver.getLocale());
                }
            }
        }
        return analysisBundle;
    }

    // Converts the raw numeric score stored in the item to a localized analysis label.
    private String getAnalysisLevelLabel(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        try {
            int numericScore = Integer.parseInt(score.trim());
            for (AnalysisLevel level : analysisLevels) {
                if (numericScore >= level.getMinimumScore()) {
                    return getAnalysisBundle().getString(level.getBundleKey());
                }
            }
            return score;
        } catch (NumberFormatException | MissingResourceException e) {
            return score;
        }
    }

    // Injected by the app/controller side to avoid coupling this viewer to iped-app classes.
    public void setMessageNavigator(MessageNavigator messageNavigator) {
        this.messageNavigator = messageNavigator;
    }

    // Injected by the controller after reading AIFiltersConfig.
    public void setAnalysisLevels(List<AnalysisLevel> analysisLevels) {
        if (analysisLevels == null || analysisLevels.isEmpty()) {
            return;
        }
        this.analysisLevels = List.copyOf(analysisLevels);
    }

    // Object exposed to the WebView as "app", allowing the HTML to call back into Java.
    public class SummaryViewerFileHandler extends FileHandler {
        public void navigateToMessage(String messageId) {
            if (messageNavigator != null && messageId != null && !messageId.isBlank()) {
                messageNavigator.navigateToMessage(messageId);
            }
        }
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
        this.highlightTerms = terms;

        javafx.application.Platform.runLater(() -> {
            if (!(content instanceof IItemReader) || !hasSummary(content)) {
                webEngine.loadContent(UiUtil.getUIEmptyHtml("[" + Messages.getString("SummaryViewer.NoSummary") + "]"));
                return;
            }

            IItemReader item = (IItemReader) content;
            ArrayList<String> chunks = new ArrayList<>();
            ArrayList<String> chunkIds = new ArrayList<>();
            Map<String, List<String>> analysisValues = new LinkedHashMap<>();

            // Summaries are stored as one value per chunk.
            Object value = item.getExtraAttribute(ExtraProperties.SUMMARY);
            appendStringValues(chunks, value);

            // Fallback to metadata if we still have nothing
            if (chunks.isEmpty()) {
                String[] vals = item.getMetadata().getValues(ExtraProperties.SUMMARY);
                if (vals != null) {
                    for (String s : vals) {
                        if (s != null) chunks.add(s);
                    }
                }
            }

            // Each chunk id points to the first message of that chunk in the generated chat HTML.
            appendStringValues(chunkIds, item.getExtraAttribute(ExtraProperties.CHUNK_IDS));

            // Analysis attributes are stored as parallel arrays, aligned with the chunk order.
            Map<String, Object> extraAttributes = item.getExtraAttributeMap();
            if (extraAttributes != null) {
                for (Map.Entry<String, Object> entry : extraAttributes.entrySet()) {
                    String key = entry.getKey();
                    if (!key.startsWith(ANALYSIS_PREFIX)) {
                        continue;
                    }
                    ArrayList<String> values = new ArrayList<>();
                    appendStringValues(values, entry.getValue());
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
                .append(".chunk-top {display:flex; justify-content:space-between; align-items:flex-start; gap:12px; margin-bottom:8px;}")
                .append(".title {font-weight:bold; margin-bottom:4px;}")
                .append(".chunk-link {font-size:11px; line-height:1.35; flex:0 0 auto;}")
                .append(".chunk-link a {text-decoration:none;}")
                .append(".chunk-link a:hover {text-decoration:underline;}")
                .append(".chunk-meta {font-size:11px; opacity:0.85; text-align:right; max-width:60%; line-height:1.35; margin-left:auto;}")
                .append("</style>")
                .append("</head><body>");

            html.append("<div class='title'>").append(Messages.getString("SummaryViewer.Title")).append("</div>");

            for (int i = 0; i < chunks.size(); i++) {
                String c = renderSummaryMarkup(chunks.get(i));
                html.append("<div class='chunk'>");
                ArrayList<String> chunkMeta = new ArrayList<>();
                if (!analysisValues.isEmpty()) {
                    // Build the human-readable metadata line for this chunk, such as
                    // "Fraud: High / Grooming: Medium".
                    for (Map.Entry<String, List<String>> entry : analysisValues.entrySet()) {
                        if (i < entry.getValue().size()) {
                            String score = entry.getValue().get(i);
                            if (score != null && !score.isBlank()) {
                                chunkMeta.add(entry.getKey() + ": " + getAnalysisLevelLabel(score));
                            }
                        }
                    }
                }
                boolean hasChunkId = i < chunkIds.size() && !chunkIds.get(i).isBlank();
                if (hasChunkId || !chunkMeta.isEmpty()) {
                    html.append("<div class='chunk-top'>");
                    if (hasChunkId) {
                        String chunkId = escapeJsSingleQuotedString(chunkIds.get(i));
                        html.append("<div class='chunk-link'><a href=\"javascript:void(0)\" onclick=\"app.navigateToMessage('")
                            .append(chunkId)
                            .append("');\">")
                            .append(Messages.getString("SummaryViewer.GoToMessage"))
                            .append("</a></div>");
                    }
                    if (!chunkMeta.isEmpty()) {
                        html.append("<div class='chunk-meta'>")
                            .append(SimpleHTMLEncoder.htmlEncode(String.join(" / ", chunkMeta)))
                            .append("</div>");
                    }
                    html.append("</div>");
                }
                html.append("<div>")
                    .append("<div>").append(c).append("</div>")
                    .append("</div>")
                    .append("</div>");
            }
            html.append("</body></html>");

            webEngine.setJavaScriptEnabled(true);
            webEngine.setUserStyleSheetLocation(UiUtil.getUIHtmlStyle());
            try {
                if (tmpFile == null) {
                    tmpFile = File.createTempFile("summary", ".html");
                    tmpFile.deleteOnExit();
                }
                // Load from a UTF-8 temp file instead of loadContent(...) because some
                // supplementary Unicode characters were rendered incorrectly in WebView.
                Files.write(tmpFile.toPath(), html.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.TRUNCATE_EXISTING);
                webEngine.load(tmpFile.toURI().toURL().toString());
            } catch (IOException e) {
                webEngine.loadContent(html.toString());
            }
        });
    }
}
