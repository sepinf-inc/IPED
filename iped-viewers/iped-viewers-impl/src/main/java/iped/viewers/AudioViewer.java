package iped.viewers;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import javax.swing.UIManager;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.properties.ExtraProperties;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;
import iped.viewers.api.AbstractViewer;
import iped.viewers.localization.Messages;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;

public class AudioViewer extends AbstractViewer {
    private static final String transcriptionAttr = ExtraProperties.TRANSCRIPT_ATTR;
    private static final String transcriptionConfidenceAttr = ExtraProperties.CONFIDENCE_ATTR;;
    private static final String audioDurationAttr = ExtraProperties.AUDIO_META_PREFIX + "xmpDM:duration";

    private JFXPanel jfxPanel;
    private HtmlViewer htmlViewer;

    public AudioViewer() {
        super(new GridLayout());
        jfxPanel = new JFXPanel();
        htmlViewer = new HtmlViewer();

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                StackPane root = new StackPane();
                root.getChildren().add(htmlViewer.htmlViewer);
                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);
            }
        });

        this.getPanel().add(jfxPanel);
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.startsWith("audio/");
    }

    @Override
    public String getName() {
        return "Audio";
    }

    @Override
    public void init() {
        htmlViewer.init();
    }

    @Override
    public void dispose() {
        htmlViewer.dispose();
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {
        loadFile(content, null, terms);
    }

    @Override
    public void loadFile(final IStreamSource content, String contentType, final Set<String> terms) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                WebEngine webEngine = htmlViewer.webEngine;
                webEngine.loadContent(UiUtil.getUIEmptyHtml());

                if (content instanceof IItemReader) {
                    htmlViewer.highlightTerms = terms;
                    String preview = generatePreview((IItemReader) content);
                    try {
                        if (htmlViewer.tmpFile == null) {
                            htmlViewer.tmpFile = File.createTempFile("transcription", ".html");
                            htmlViewer.tmpFile.deleteOnExit();
                        }
                        Files.write(htmlViewer.tmpFile.toPath(), preview.getBytes("UTF-8"),
                                StandardOpenOption.TRUNCATE_EXISTING);
                        webEngine.load(htmlViewer.tmpFile.toURI().toURL().toString());

                    } catch (IOException e) {
                        webEngine.loadContent(preview);
                    }
                }
            }
        });
    }

    private String generatePreview(IItemReader item) {
        String transcription = item.getMetadata().get(transcriptionAttr);

        String strConfidence = null;
        if (transcription != null) {
            String conf = item.getMetadata().get(transcriptionConfidenceAttr);
            if (conf != null && !conf.isBlank()) {
                try {
                    double c = Double.parseDouble(conf);
                    strConfidence = String.format("%.0f%%", c * 100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        String strDuration = null;
        String duration = item.getMetadata().get(audioDurationAttr);
        if (duration != null && !duration.isBlank()) {
            try {
                double d = Double.parseDouble(duration);
                int seconds = (int) d;
                if (seconds > 0) {
                    int h = seconds / 3600;
                    int m = seconds % 3600 / 60;
                    int s = seconds % 60;
                    if (h > 0) {
                        strDuration = String.format("%dh %dm %ds", h, m, s);
                    } else if (m > 0) {
                        strDuration = String.format("%dm %ds", m, s);
                    } else {
                        strDuration = String.format("%ds", s);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String type = item.getType();

        Color color2 = new Color(0xD0D0D0);
        Color color3 = new Color(0xF4F4F4);
        Color background = UIManager.getColor("Viewer.background");
        if (background != null) {
            color3 = UiUtil.mix(background, Color.gray, 0.9);
            color2 = UiUtil.mix(background, Color.gray, 0.7);
        }
        String borderColor = "black";
        Color foreground = UIManager.getColor("Viewer.foreground");
        if (foreground != null && background != null)
            borderColor = UiUtil.getHexRGB(UiUtil.mix(background, foreground, 0.5));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n");
        sb.append(
                "<style>table {border-collapse: collapse; font-size:10pt; font-family: arial, verdana, sans-serif; width:100%; align:center; } ");
        sb.append("table.t {margin-bottom:20px;} td { padding: 2px; } ");
        sb.append("td.s1 {background-color:").append(UiUtil.getHexRGB(color2)).append("; ");
        sb.append("border: 1px solid ").append(borderColor).append("; text-align:left;} ");
        sb.append("td.s2 {background-color:").append(UiUtil.getHexRGB(color3)).append("; ");
        sb.append("border: 1px solid ").append(borderColor).append("; text-align:left;}\n");
        sb.append("textarea {readonly: readonly; height: 60px; width: 100%; resize: none;}\n");
        sb.append("*:focus {outline: none;}\n");
        sb.append("</style></head>\n");
        sb.append("<body style=\"");

        if (background != null)
            sb.append("background-color:").append(UiUtil.getHexRGB(background)).append(";");
        if (foreground != null)
            sb.append("color:").append(UiUtil.getHexRGB(foreground)).append(";");
        sb.append("\">\n");

        sb.append("<table class=\"t\">");
        sb.append("<tr>");

        sb.append("<td class=\"s1\">");
        sb.append("<b>").append(SimpleHTMLEncoder.htmlEncode(Messages.getString("AudioViewer.TranscriptionTitle"))).append("</b>");
        if (strConfidence != null) {
            sb.append(" [").append(strConfidence).append("]");
        }
        if (strDuration != null || type != null) {
            sb.append("<br>");
            if (strDuration != null) {
                sb.append(strDuration).append(' ');
            }
            if (type != null) {
                sb.append('(').append(type).append(')');
            }
        }
        sb.append("</td></tr>");

        sb.append("<tr>");
        sb.append("<td class=\"s2\">");
        if (transcription == null) {
            sb.append("<font color=\"gray\"> [");
            sb.append(SimpleHTMLEncoder.htmlEncode(Messages.getString("AudioViewer.NoTranscription")));
            sb.append("] </font>");
        } else {
            sb.append(SimpleHTMLEncoder.htmlEncode(transcription));
        }
        sb.append("</td></tr>");

        sb.append("</table>");
        sb.append("</body>");
        sb.append("</html>");

        return sb.toString();
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        htmlViewer.scrollToNextHit(forward);
    }
}
