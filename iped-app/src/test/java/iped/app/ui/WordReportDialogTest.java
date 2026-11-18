package iped.app.ui;

import static org.junit.Assert.*;

import java.awt.CardLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import iped.engine.data.ItemId;
import sun.misc.Unsafe;

public class WordReportDialogTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    // Like AppPreviewCloseTest, avoid a JFrame and investigation database in headless tests.
    private WordReportDialog partialDialog() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (WordReportDialog) ((Unsafe) field.get(null)).allocateInstance(WordReportDialog.class);
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object invoke(WordReportDialog dialog, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = WordReportDialog.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(dialog, args);
    }

    private Object item(int id, int mediaType) throws Exception {
        Class<?> type = Class.forName(WordReportDialog.class.getName() + "$ReportItem");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object result = constructor.newInstance(new ItemId(0, id), null);
        set(result, "mediaType", mediaType);
        return result;
    }

    @Test
    public void savedItemsLeaveTheCurrentBatchAndUncheckedPendingItemsRemain() throws Exception {
        WordReportDialog dialog = partialDialog();
        Object first = item(1, 1), pending = item(2, 1), last = item(3, 1);
        List<Object> items = new ArrayList<>(Arrays.asList(first, pending, last));
        DefaultTableModel model = new DefaultTableModel(new Object[] { "Include", "Name" }, 0);
        model.addRow(new Object[] { true, "first" });
        model.addRow(new Object[] { false, "pending" });
        model.addRow(new Object[] { true, "last" });
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(1);
        set(dialog, "items", items);
        set(dialog, "tableModel", model);
        invoke(dialog, "removeSavedItems", new Class<?>[] { List.class, int.class }, Arrays.asList(first, last), 5);
        assertEquals(Collections.singletonList(pending), items);
        assertEquals(1, table.getRowCount());
        assertEquals("pending", model.getValueAt(0, 1));
        assertEquals(false, model.getValueAt(0, 0));
        assertEquals(false, get(dialog, "updatingEvidence"));
    }

    @Test
    public void anUnsavedPairCanFollowItsFirstImageAfterThatImageIsSaved() throws Exception {
        WordReportDialog dialog = partialDialog();
        Object first = item(1, 1), next = item(2, 1);
        set(next, "beside", first);
        List<Object> items = new ArrayList<>(Arrays.asList(first, next));
        DefaultTableModel model = new DefaultTableModel(new Object[] { "Include" }, 0);
        model.addRow(new Object[] { true });
        model.addRow(new Object[] { false });
        Path template = WordReportFiles.defaultTemplate(temporary.newFolder().toPath());
        WordReportDocument report = new WordReportDocument(template);
        WordReportDocument.Evidence image = new WordReportDocument.Evidence();
        image.title = "synthetic.png";
        image.image = new java.awt.image.BufferedImage(20, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int after = report.insert(0, Collections.singletonList(image), true, false);
        Path saved = temporary.getRoot().toPath().resolve("saved.docx");
        report.write(saved);
        set(dialog, "loaded", new WordReportDocument(saved));
        set(dialog, "items", items);
        set(dialog, "tableModel", model);
        invoke(dialog, "removeSavedItems", new Class<?>[] { List.class, int.class }, Collections.singletonList(first), after);
        assertEquals(Collections.singletonList(next), items);
        assertNull(get(next, "beside"));
        assertEquals(after, get(next, "besideLocation"));
    }

    @Test
    public void defaultPairsAreNotOfferedAThirdImageButSeparateImagesCanBePaired() throws Exception {
        WordReportDialog dialog = partialDialog();
        JComboBox<String> layout = new JComboBox<>(new String[] { "one", "two" });
        layout.setSelectedIndex(1);
        set(dialog, "layout", layout);
        Object first = item(1, 1), second = item(2, 1), third = item(3, 1);
        assertEquals(true, invoke(dialog, "hasPendingImageColumn", new Class<?>[] { List.class }, Collections.singletonList(first)));
        assertEquals(false, invoke(dialog, "hasPendingImageColumn", new Class<?>[] { List.class }, Arrays.asList(first, second)));
        assertEquals(true, invoke(dialog, "hasPendingImageColumn", new Class<?>[] { List.class }, Arrays.asList(first, second, third)));
        set(second, "separateRow", true);
        assertEquals(true, invoke(dialog, "hasPendingImageColumn", new Class<?>[] { List.class }, Arrays.asList(first, second)));
        Object audio = item(4, 3);
        assertEquals(false, invoke(dialog, "hasPendingImageColumn", new Class<?>[] { List.class }, Arrays.asList(audio, first)));
    }

    @Test
    public void evidencePreviewReplacesReportPagesWhenThePreparedResultIsInvalidated() throws Exception {
        WordReportDialog dialog = partialDialog();
        CardLayout layout = new CardLayout();
        JPanel cards = new JPanel(layout), evidence = new JPanel(), report = new JPanel();
        cards.add(evidence, "evidence");
        cards.add(report, "report");
        set(dialog, "previewLayout", layout);
        set(dialog, "previewCards", cards);
        set(dialog, "previewPanel", new JPanel());
        set(dialog, "status", new JLabel());
        set(dialog, "prepared", temporary.newFile("prepared.docx").toPath());
        assertTrue(evidence.isVisible());
        assertFalse(report.isVisible());
        invoke(dialog, "showReportPreview", new Class<?>[0]);
        assertTrue(report.isVisible());
        invoke(dialog, "invalidatePrepared", new Class<?>[0]);
        assertTrue(evidence.isVisible());
        assertFalse(report.isVisible());
        assertNull(get(dialog, "prepared"));
    }
}
