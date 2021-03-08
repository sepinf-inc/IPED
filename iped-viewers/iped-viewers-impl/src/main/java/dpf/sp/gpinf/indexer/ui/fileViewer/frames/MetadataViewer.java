package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;
import iped3.io.IItemBase;
import iped3.io.IStreamSource;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;

public class MetadataViewer extends Viewer {

    private TabPane tabPane;
    private JFXPanel jfxPanel;
    private List<HtmlViewer> htmlViewers = new ArrayList<>();;

    private Collator collator;

    public MetadataViewer() {
        super(new GridLayout());

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

        collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
    }

    @SuppressWarnings("restriction")
    private void selectTab(int tabIdx) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                tabPane.getSelectionModel().select(tabIdx);
            }
        });
    }

    public boolean isMetadataEntry(String contentType) {
        MediaType type = MediaType.parse(contentType);
        while (type != null && !type.equals(MediaType.OCTET_STREAM)) {
            if (MediaTypes.METADATA_ENTRY.equals(type)) {
                return true;
            }
            type = this.getParentType(type);
        }
        return false;
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
                    webEngine.load(null);

                    if (content instanceof IItemBase) {
                        viewer.highlightTerms = terms;
                        String preview = generatePreview((IItemBase) content, htmlViewers.indexOf(viewer));
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

    private String generatePreview(IItemBase item, int tabIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n" //$NON-NLS-1$
                + "<html>\n" //$NON-NLS-1$
                + "<head>\n" //$NON-NLS-1$
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n" //$NON-NLS-1$
                + "<style>table {border-collapse: collapse; font-size:11pt; font-family: arial, verdana, sans-serif; width:100%; align:center; } table.t {margin-bottom:20px;} td { padding: 2px; } th {background-color:#D7D7D7; border: 1px solid black; padding: 3px; text-align: left; font-weight: normal;} td.s1 {font-size:10pt; background-color:#F2F2F2; width:170px; border: 1px solid black; text-align:left;} td.s2 {font-size:10pt; background-color:#F2F2F2; border: 1px solid black; word-break: break-all; word-wrap: break-word; text-align:left;}" //$NON-NLS-1$
                + "textarea {readonly: readonly; height: 60px; width: 100%; resize: none;}" //$NON-NLS-1$
                + "</style></head>\n" //$NON-NLS-1$
                + "<body>\n"); //$NON-NLS-1$

        if (tabIndex == 0)
            fillBasicProps(sb, item);
        if (tabIndex == 1)
            fillAdvancedProps(sb, item);
        if (tabIndex == 2)
            fillMetadata(sb, item.getMetadata());

        sb.append("</body>"); //$NON-NLS-1$
        sb.append("</html>"); //$NON-NLS-1$

        return sb.toString();
    }

    private void fillMetadata(StringBuilder sb, Metadata metadata) {

        String[] metas = metadata.names();
        if (metas.length == 0)
            return;
        sb.append("<table class=\"t\">"); //$NON-NLS-1$
        sb.append("<tr><th colspan=2>" + Messages.getString("MetadataViewer.Metadata") + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Arrays.sort(metas, collator);
        for (String meta : metas) {
            if (MetadataUtil.ignorePreviewMetas.contains(meta))
                continue;
            sb.append("<tr><td class=\"s1\">"); //$NON-NLS-1$
            sb.append(meta);
            sb.append("</td><td class=\"s2\">"); //$NON-NLS-1$
            if (!metadata.isMultiValued(meta))
                sb.append(SimpleHTMLEncoder.htmlEncode(metadata.get(meta)));
            else
                sb.append(SimpleHTMLEncoder.htmlEncode(Arrays.asList(metadata.getValues(meta)).toString()));
            sb.append("</td></tr>"); //$NON-NLS-1$
        }
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillBasicProps(StringBuilder sb, IItemBase item) {
        sb.append("<table class=\"t\">"); //$NON-NLS-1$
        sb.append("<tr><th colspan=2>" + Messages.getString("MetadataViewer.BasicProps") + "</th></tr>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        fillProp(sb, BasicProps.NAME, item.getName());
        fillProp(sb, BasicProps.LENGTH, item.getLength());
        fillProp(sb, BasicProps.TYPE, item.getTypeExt());
        fillProp(sb, BasicProps.DELETED, item.isDeleted());
        fillProp(sb, BasicProps.CATEGORY, item.getCategorySet());
        fillProp(sb, BasicProps.CREATED, item.getCreationDate());
        fillProp(sb, BasicProps.MODIFIED, item.getModDate());
        fillProp(sb, BasicProps.ACCESSED, item.getAccessDate());
        fillProp(sb, BasicProps.RECORDDATE, item.getRecordDate());
        fillProp(sb, BasicProps.HASH, item.getHash());
        fillProp(sb, BasicProps.PATH, item.getPath());
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillAdvancedProps(StringBuilder sb, IItemBase item) {
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
        fillProp(sb, BasicProps.DUPLICATE, item.isDuplicate());
        fillProp(sb, BasicProps.TIMEOUT, item.isTimedOut());
        String[] keys = item.getExtraAttributeMap().keySet().toArray(new String[0]);
        Arrays.sort(keys, collator);
        for (String key : keys) {
            fillProp(sb, key, item.getExtraAttributeMap().get(key));
        }
        fillProp(sb, ExtraProperties.TIKA_PARSER_USED, item.getMetadata().get(ExtraProperties.TIKA_PARSER_USED));
        sb.append("</table>"); //$NON-NLS-1$
    }

    private void fillProp(StringBuilder sb, String key, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            sb.append("<tr><td class=\"s1\">" + key + "</td>"); //$NON-NLS-1$ //$NON-NLS-2$
            sb.append("<td class=\"s2\">" + SimpleHTMLEncoder.htmlEncode(value.toString()) + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
        }
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
