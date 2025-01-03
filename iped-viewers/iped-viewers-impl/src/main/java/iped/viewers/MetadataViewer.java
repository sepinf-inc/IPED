package iped.viewers;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.UIManager;

import org.apache.commons.codec.binary.Hex;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.localization.LocalizedProperties;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.DateUtil;
import iped.utils.EmojiUtil;
import iped.utils.LocalizedFormat;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.UiUtil;
import iped.viewers.api.AbstractViewer;
import iped.viewers.localization.Messages;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;

public abstract class MetadataViewer extends AbstractViewer {

    private final DecimalFormat df = LocalizedFormat.getDecimalInstance("#,###.############"); //$NON-NLS-1$
    private final DateFormat dateFormat = new SimpleDateFormat(Messages.getString("MetadataViewer.DateFormat"));

    private TabPane tabPane;
    private JFXPanel jfxPanel;
    private List<HtmlViewer> htmlViewers = new ArrayList<>();
    
    private static final int minLenToCollapse = 128;
    private static final int collapsedSubstringLen = 32;

    public static class FieldComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            a = LocalizedProperties.getLocalizedField(a);
            b = LocalizedProperties.getLocalizedField(b);
            return a.compareToIgnoreCase(b);
        }
    };

    private FieldComparator comparator = new FieldComparator();

    public MetadataViewer() {
        super(new GridLayout());

        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        jfxPanel = new JFXPanel();
        for (int i = 0; i < 3; i++)
            htmlViewers.add(new HtmlViewer());

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                tabPane = new TabPane();
                tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
                tabPane.setSide(Side.RIGHT);

                Tab tab0 = new Tab();
                tab0.setText(Messages.getString("MetadataViewer.BasicProps"));
                tab0.setContent(htmlViewers.get(0).htmlViewer);
                tabPane.getTabs().add(tab0);

                Tab tab1 = new Tab();
                tab1.setText(Messages.getString("MetadataViewer.AdvancedProps"));
                tab1.setContent(htmlViewers.get(1).htmlViewer);
                tabPane.getTabs().add(tab1);

                Tab tab2 = new Tab();
                tab2.setText(Messages.getString("MetadataViewer.Metadata"));
                tab2.setContent(htmlViewers.get(2).htmlViewer);
                tabPane.getTabs().add(tab2);

                StackPane root = new StackPane();
                root.getChildren().add(tabPane);
                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);
            }
        });

        this.getPanel().add(jfxPanel);

    }

    public abstract boolean isNumeric(String field);

    @Override
    public String getName() {
        return Messages.getString("MetadataViewer.TabTitle"); //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return true;
    }

    public boolean isFixed() {
        return false;
    }

    @Override
    public void init() {
        for (HtmlViewer viewer : htmlViewers)
            viewer.init();
    }

    private void selectTab(int tabIdx) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                tabPane.getSelectionModel().select(tabIdx);
            }
        });
    }

    public boolean isMetadataEntry(String contentType) {
        return MediaTypes.isMetadataEntryType(MediaType.parse(contentType));
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

                for (HtmlViewer viewer : htmlViewers) {
                    WebEngine webEngine = viewer.webEngine;
                    webEngine.loadContent(UiUtil.getUIEmptyHtml());

                    if (content instanceof IItemReader) {
                        viewer.highlightTerms = terms;
                        String preview = generatePreview((IItemReader) content, htmlViewers.indexOf(viewer));
                        try {
                            if (viewer.tmpFile == null) {
                                viewer.tmpFile = File.createTempFile("metadata", ".html"); //$NON-NLS-1$ //$NON-NLS-2$
                                viewer.tmpFile.deleteOnExit();
                            }
                            Files.write(viewer.tmpFile.toPath(), preview.getBytes("UTF-8"), //$NON-NLS-1$
                                    StandardOpenOption.TRUNCATE_EXISTING);
                            webEngine.load(viewer.tmpFile.toURI().toURL().toString());

                        } catch (IOException e) {
                            webEngine.loadContent(preview);
                        }
                    }
                }

                if (!isFixed() && isMetadataEntry(contentType)) {
                    selectTab(2);
                }

            }
        });
    }

    private String generatePreview(IItemReader item, int tabIndex) {
        Color color1 = new Color(0xD7D7D7); 
        Color color2 = new Color(0xF2F2F2);
        Color color3 = new Color(0xF2F2F2);
        Color background = UIManager.getColor("Viewer.background"); //$NON-NLS-1$
        if (background != null) {
            color3 = UiUtil.mix(background, Color.gray, 0.9);
            color2 = UiUtil.mix(background, Color.gray, 0.7);
            color1 = UiUtil.mix(background, Color.gray, 0.5);
        }
        String borderColor = "black"; //$NON-NLS-1$
        Color foreground = UIManager.getColor("Viewer.foreground"); //$NON-NLS-1$
        if (foreground != null && background != null)
            borderColor = UiUtil.getHexRGB(UiUtil.mix(background, foreground, 0.5));
        
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n"); //$NON-NLS-1$
        sb.append("<html>\n"); //$NON-NLS-1$
        sb.append("<head>\n"); //$NON-NLS-1$
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n"); //$NON-NLS-1$
        sb.append("<style>table {border-collapse: collapse; font-size:11pt; font-family: arial, verdana, sans-serif; width:100%; align:center; } table.t {margin-bottom:20px;} td { padding: 2px; } th {");
        sb.append("background-color:").append(UiUtil.getHexRGB(color1)).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("border: 1px solid ").append(borderColor).append("; padding: 3px; text-align: left; font-weight: normal;} td.s1 {font-size:10pt; "); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("background-color:").append(UiUtil.getHexRGB(color2)).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("width:170px; border: 1px solid ").append(borderColor).append("; text-align:left;} td.s2 {font-size:10pt; "); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("background-color:").append(UiUtil.getHexRGB(color3)).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("border: 1px solid ").append(borderColor).append("; word-break: break-all; word-wrap: break-word; text-align:left;}\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sb.append("textarea {readonly: readonly; height: 60px; width: 100%; resize: none;}\n"); //$NON-NLS-1$
        sb.append("*:focus {outline: none;}\n"); //$NON-NLS-1$
        sb.append("</style></head>\n"); //$NON-NLS-1$
        sb.append("<body style=\"");//$NON-NLS-1$

        if (background != null)  
            sb.append("background-color:").append(UiUtil.getHexRGB(background)).append(";"); //$NON-NLS-1$  //$NON-NLS-2$
        if (foreground != null)  
            sb.append("color:").append(UiUtil.getHexRGB(foreground)).append(";"); //$NON-NLS-1$  //$NON-NLS-2$
        sb.append("\">\n"); //$NON-NLS-1$

        if (tabIndex == 0)
            fillBasicProps(sb, item);
        if (tabIndex == 1)
            fillAdvancedProps(sb, item);
        if (tabIndex == 2)
            fillMetadata(sb, item.getMetadata());

        sb.append("</body>"); //$NON-NLS-1$
        sb.append("</html>"); //$NON-NLS-1$

        return EmojiUtil.replaceByImages(sb.toString());
    }

    private void fillMetadata(StringBuilder sb, Metadata metadata) {

        String[] metas = metadata.names();
        if (metas.length == 0)
            return;
        sb.append("<table class=\"t\">"); //$NON-NLS-1$
        sb.append("<tr><th colspan=2>" + Messages.getString("MetadataViewer.Metadata") + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Arrays.sort(metas, comparator);
        for (String meta : metas) {
            if (MetadataUtil.ignorePreviewMetas.contains(meta))
                continue;
            sb.append("<tr><td class=\"s1\">"); //$NON-NLS-1$
            sb.append(LocalizedProperties.getLocalizedField(meta));
            sb.append("</td><td class=\"s2\">"); //$NON-NLS-1$
            String val = null;
            if (!metadata.isMultiValued(meta)) {
                val = metadata.get(meta);
                if (isNumeric(meta)) {
                    val = df.format(Double.valueOf(val));
                } else {
                    try {
                        Date date = DateUtil.stringToDate(val);
                        val = dateFormat.format(date);
                    } catch (Exception e) {
                    }
                }
            } else {
                String[] vals = metadata.getValues(meta);
                if (isNumeric(meta)) {
                    for (int i = 0; i < vals.length; i++) {
                        vals[i] = df.format(Double.valueOf(vals[i]));
                    }
                } else {
                    boolean isDate = true;
                    for (int i = 0; i < vals.length; i++) {
                        try {
                            DateUtil.stringToDate(vals[i]);
                        } catch (Exception e) {
                            isDate = false;
                            break;
                        }
                    }
                    if (isDate) {
                        for (int i = 0; i < vals.length; i++) {
                            try {
                                Date date = DateUtil.stringToDate(vals[i]);
                                vals[i] = dateFormat.format(date);
                            } catch (Exception e) {
                            }
                        }
                    }
                }
                val = Arrays.asList(vals).toString();
            }
            appendCollapsibleString(sb, val);
            sb.append("</td></tr>"); //$NON-NLS-1$
        }
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillBasicProps(StringBuilder sb, IItemReader item) {
        sb.append("<table class=\"t\">"); //$NON-NLS-1$
        sb.append("<tr><th colspan=2>" + Messages.getString("MetadataViewer.BasicProps") + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        fillProp(sb, BasicProps.NAME, item.getName());
        fillProp(sb, BasicProps.LENGTH, item.getLength());
        fillProp(sb, BasicProps.EXT, item.getExt());
        fillProp(sb, BasicProps.TYPE, item.getType());
        fillProp(sb, BasicProps.DELETED, item.isDeleted());
        fillProp(sb, BasicProps.CATEGORY, item.getCategorySet());
        fillProp(sb, BasicProps.CREATED, formatDate(item.getCreationDate()));
        fillProp(sb, BasicProps.MODIFIED, formatDate(item.getModDate()));
        fillProp(sb, BasicProps.ACCESSED, formatDate(item.getAccessDate()));
        fillProp(sb, BasicProps.CHANGED, formatDate(item.getChangeDate()));
        fillProp(sb, BasicProps.HASH, item.getHash());
        fillProp(sb, BasicProps.PATH, item.getPath());
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillAdvancedProps(StringBuilder sb, IItemReader item) {
        sb.append("<table class=\"t\">"); //$NON-NLS-1$
        sb.append("<tr><th colspan=2>" + Messages.getString("MetadataViewer.AdvancedProps") + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        fillProp(sb, BasicProps.CONTENTTYPE, item.getMediaType());
        fillProp(sb, BasicProps.ID, item.getId());
        fillProp(sb, BasicProps.PARENTID, item.getParentId());
        fillProp(sb, BasicProps.EVIDENCE_UUID, item.getDataSource());
        fillProp(sb, BasicProps.SUBITEM, item.isSubItem());
        fillProp(sb, BasicProps.SUBITEMID, item.getSubitemId());
        fillProp(sb, BasicProps.CARVED, item.isCarved());
        fillProp(sb, BasicProps.ISDIR, item.isDir());
        fillProp(sb, BasicProps.HASCHILD, item.hasChildren());
        fillProp(sb, BasicProps.ISROOT, item.isRoot());
        fillProp(sb, BasicProps.TIMEOUT, item.isTimedOut());
        String[] keys = item.getExtraAttributeMap().keySet().toArray(new String[0]);
        Arrays.sort(keys, comparator);
        for (String key : keys) {
            fillProp(sb, key, item.getExtraAttributeMap().get(key));
        }
        fillProp(sb, ExtraProperties.TIKA_PARSER_USED, item.getMetadata().get(ExtraProperties.TIKA_PARSER_USED));
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillProp(StringBuilder sb, String key, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            sb.append("<tr><td class=\"s1\">");
            sb.append(LocalizedProperties.getLocalizedField(key));
            sb.append("</td>");

            String val = null;
            if (value instanceof Collection) {
                ArrayList<Object> formattedVals = new ArrayList<>();
                for (Object v : (Collection<?>) value) {
                    formattedVals.add(format(v));
                }
                val = formattedVals.toString();
            } else {
                val = format(value);
            }

            sb.append("<td class=\"s2\">");
            appendCollapsibleString(sb, val);
            sb.append("</td></tr>");
        }
    }
    
    private void appendCollapsibleString(StringBuilder sb, String str) {
        if (str.length() >= minLenToCollapse) {
            sb.append("<details tabindex=\"-1\"><summary><b>[");
            sb.append(df.format(str.length()));
            sb.append(" chars]</b> ");
            sb.append(SimpleHTMLEncoder.htmlEncode(str.substring(0, collapsedSubstringLen)));
            sb.append("<b>...</b>");
            sb.append("</summary><span>");
            sb.append(SimpleHTMLEncoder.htmlEncode(str));
            sb.append("</span></details>");
        } else {
            sb.append(SimpleHTMLEncoder.htmlEncode(str));
        }
    }

    private String formatDate(Date date) {
        return date == null ? null : dateFormat.format(date);
    }

    private String format(Object value) {
        if (value instanceof Number) {
            return df.format(Double.valueOf(((Number) value).doubleValue()));
        } else if (value instanceof byte[]) {
            return new String(Hex.encodeHex((byte[]) value));
        }
        return value.toString();
    }

    @Override
    public void dispose() {
        for (HtmlViewer viewer : htmlViewers)
            viewer.dispose();
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        int tabIndex = tabPane.getSelectionModel().getSelectedIndex();
        htmlViewers.get(tabIndex).scrollToNextHit(forward);
    }

}
