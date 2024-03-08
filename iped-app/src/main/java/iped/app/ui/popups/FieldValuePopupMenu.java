package iped.app.ui.popups;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import iped.app.metadata.MetadataSearchable;
import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.TableHeaderFilterManager;
import iped.data.IItemId;
import iped.engine.task.index.IndexItem;
import iped.utils.DateUtil;

public class FieldValuePopupMenu extends JPopupMenu implements ActionListener{
    private static final String STARTS_WITH_STR = Messages.get("FieldValuePopupMenu.StartsWith");
    public static final String EQUALS_STR = Messages.get("FieldValuePopupMenu.Equals");
    public static final String NON_EMPTY_STR = Messages.get("FieldValuePopupMenu.NonEmpty");
    public static final String EMPTY_STR = Messages.get("FieldValuePopupMenu.Empty");
    public static final String NOT_CONTAINS_STR = Messages.get("FieldValuePopupMenu.NotContains");
    public static final String AFTER_STR = Messages.get("FieldValuePopupMenu.After");
    public static final String BEFORE_STR = Messages.get("FieldValuePopupMenu.Before");
    public static final String FILTER_GREATER_THAN_STR = Messages.get("FieldValuePopupMenu.GreaterThan");
    public static final String FILTER_LESS_THAN_STR = Messages.get("FieldValuePopupMenu.LessThan");
    public static final String CONTAINS_STR = Messages.get("FieldValuePopupMenu.Contains");
    public static final String CLEAR = Messages.get("FieldValuePopupMenu.Clear");
    public static final String FILTER = Messages.get("FieldValuePopupMenu.Filter");

    JMenuItem filterLessThan;
    JMenuItem filterGreaterThan;
    IItemId itemId;
    String field;
    String value;
    boolean isDate = false;
    private TableHeaderFilterManager fm;
    MetadataSearchable ms = null;

    private SimpleDateFormat df = new SimpleDateFormat(Messages.getString("ResultTableModel.DateFormat")); //$NON-NLS-1$
    private JMenuItem filterContains;
    private JMenuItem filterNotContains;
    private JMenuItem filterEmpty;
    private JMenuItem filterNonEmpty;
    private JButton btValue;
    private JMenuItem filterEquals;
    private JMenuItem filterStartsWith;
    
    HashMap<JMenuItem, JMenu> parentMenus = new HashMap<JMenuItem, JMenu>();

    public FieldValuePopupMenu(IItemId itemId, String field, String value) {
        super();

        fm = TableHeaderFilterManager.get();

        this.itemId = itemId;
        this.field=field;
        this.value = value;

        if(value!=null && value!="" && value.length()>0) {
            JPanel valuePanel = new JPanel();
            valuePanel.setBorder(BorderFactory.createEmptyBorder());
            JLabel lbValue = new JLabel(value);
            btValue = new JButton();
            btValue.setBorder(BorderFactory.createEmptyBorder());
            URL imageUrl = App.class.getResource("copy.png");
            ImageIcon imageIcon = new ImageIcon(imageUrl);
            Image image = imageIcon.getImage(); // transform it 
            Image newimg = image.getScaledInstance(16, 16,  java.awt.Image.SCALE_SMOOTH); // scale it the smooth way  
            imageIcon = new ImageIcon(newimg);  // transform it back
            btValue.setIcon(imageIcon);
            btValue.addActionListener(this);
            btValue.setMaximumSize(new Dimension(16,16));
            valuePanel.add(lbValue);
            valuePanel.add(btValue);
            this.add(valuePanel);
        }
        
        try {
            ms = new MetadataSearchable(field);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        this.add(new JSeparator());

        if(value.length()>0) {
            
            isDate = isDate(value);

            // TODO: fix for non numeric and non Date fields
            if (IndexItem.isNumeric(field) || isDate) {
                filterEquals = createValuedMenuItem(FieldValuePopupMenu.EQUALS_STR);
                filterEquals.addActionListener(this);
                this.add(filterEquals);
            }

            if(IndexItem.isNumeric(field)) {
                filterLessThan=createValuedMenuItem(FieldValuePopupMenu.FILTER_LESS_THAN_STR);
                this.add(filterLessThan);

                filterGreaterThan=createValuedMenuItem(FieldValuePopupMenu.FILTER_GREATER_THAN_STR);
                filterGreaterThan.addActionListener(this);
                this.add(filterGreaterThan);
            } else if (isDate) {
                filterLessThan=createValuedMenuItem(FieldValuePopupMenu.BEFORE_STR);
                filterLessThan.addActionListener(this);
                this.add(filterLessThan);

                filterGreaterThan=createValuedMenuItem(FieldValuePopupMenu.AFTER_STR);
                filterGreaterThan.addActionListener(this);
                this.add(filterGreaterThan);
            }else {
                // TODO: fix for non numeric and non Date fields
                // filterStartsWith=createValuedMenuItem(FieldValuePopupMenu.STARTS_WITH_STR);
                // filterStartsWith.addActionListener(this);
                // this.add(filterStartsWith);

                filterContains=createValuedMenuItem(FieldValuePopupMenu.CONTAINS_STR);
                filterContains.addActionListener(this);
                this.add(filterContains);

                filterNotContains=createValuedMenuItem(FieldValuePopupMenu.NOT_CONTAINS_STR);
                filterNotContains.addActionListener(this);
                this.add(filterNotContains);
            }
        }
        filterEmpty=new JMenuItem(FieldValuePopupMenu.EMPTY_STR);
        filterEmpty.addActionListener(this);
        this.add(filterEmpty);

        filterNonEmpty=new JMenuItem(FieldValuePopupMenu.NON_EMPTY_STR);
        filterNonEmpty.addActionListener(this);
        this.add(filterNonEmpty);
    }
    
    private JMenuItem createValuedMenuItem(String text) {
        if(ms.isSingleValuedField()) {
            JMenuItem result = new JMenuItem(text);
            result.addActionListener(this);
            return result;
        }else {
            String[] itemValues = value.split("\\s\\|\\s");
            
            if(itemValues.length>1) {
                JMenu result = new JMenu(text);

                for (int i = 0; i < itemValues.length; i++) {
                    String itemValue = itemValues[i];
                    JMenuItem item = new JMenuItem(itemValue);
                    item.addActionListener(this);
                    result.add(item);
                    parentMenus.put(item, result);
                }

                return result;
            }else {
                JMenuItem result = new JMenuItem(text);
                result.addActionListener(this);
                return result;
            }

        }
    }

    private boolean isDate(String value) {
        try {
            df.parse(value);
            return true;
        }catch (Exception e) {
        }
        return false;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        String strValue = value;
        
        if(source instanceof JMenuItem) {
            JMenu parentMenu = parentMenus.get((JMenuItem)source);
            if(parentMenu != null) {
                strValue = ((JMenuItem)source).getText(); 
                source = parentMenu;
            }
        }
        
        if(source==filterEquals) {
            fm.addEqualsFilter(field, strValue);
        }
        if(source==filterStartsWith) {
            fm.addStartsWithFilter(field, strValue);
        }
        if(source==filterContains) {
            fm.addFilter(field, field+":\""+strValue+"\"");
        }
        if(source==filterNotContains) {
            fm.addFilter(field, "-"+field+":\""+strValue+"\"");
        }
        if(source==filterEmpty) {
            fm.addEmptyFilter(field);
        }
        if(source==filterNonEmpty) {
            fm.addNonEmptyFilter(field);
        }
        if(isDate) {
            try {
                Date d = df.parse(strValue);
                value = DateUtil.dateToString(d);
            } catch (ParseException e1) {
                e1.printStackTrace();
            }
        }
        if(source==filterLessThan) {
            fm.addFilter(field, field+":[* TO "+strValue+"]");
        }
        if(source==filterGreaterThan) {
            fm.addFilter(field, field+":["+strValue+" TO *]");
        }
        
        if(source==btValue) {
            StringSelection stringSelection = new StringSelection(value);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            this.setVisible(false);
            return;
        }
        
        App.get().getAppListener().updateFileListing();
        App.get().setDockablesColors();
    }

}
