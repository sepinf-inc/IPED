package iped.app.timelinegraph.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.text.MaskFormatter;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocaleConfig;
import iped.jfextensions.model.Minute;

public class IntervalDefinitionDialog {
    JDialog dialog = new JDialog(App.get());
    JPanel panel = new JPanel(new BorderLayout());
    JButton exit = new JButton();
    JPanel footer1;
    GridBagConstraints c = new GridBagConstraints();
    SimpleDateFormat sdfHour;
    SimpleDateFormat sdfDate;

    static final String DATES_CLIENT_PROPERTY = "dates";
    static final String DATEINDEX_CLIENT_PROPERTY = "dateIndex";

    MaskFormatter maskData;

    AbstractAction delete = new AbstractAction("x") {
        @Override
        public void actionPerformed(ActionEvent e) {
            Date[] interval = buttonToIntervalIndex.get(e.getSource());
            int index = definedFilters.indexOf(interval);
            definedFilters.remove(index);
            JComponent[] comps = components.get(index);
            components.remove(index);
            for (int i = 0; i < comps.length; i++) {
                footer1.remove(comps[i]);
            }
            dialog.setVisible(true);
        }
    };

    AbstractAction exitAction;

    void saveDateFieldDate(JTextFieldDateEditor textField) throws ParseException {
        Date d = textField.getDate();
        if (d == null) {
            throw new ParseException("", 1);
        }
        Date[] old = (Date[]) textField.getClientProperty(DATES_CLIENT_PROPERTY);
        int i = (Integer) textField.getClientProperty(DATEINDEX_CLIENT_PROPERTY);
        cal.setTime(d);
        Calendar oldCal = ((Calendar) cal.clone());
        oldCal.setTime(old[i]);
        cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), oldCal.get(Calendar.HOUR), oldCal.get(Calendar.MINUTE), oldCal.get(Calendar.SECOND));
        old[i] = cal.getTime();
    }

    void saveTimeFieldDate(JTextField textField) throws ParseException {
        String text = textField.getText();
        Date d = sdfHour.parse(text);
        Date[] old = (Date[]) textField.getClientProperty(DATES_CLIENT_PROPERTY);
        int i = (Integer) textField.getClientProperty(DATEINDEX_CLIENT_PROPERTY);
        cal.setTime(d);
        Calendar oldCal = ((Calendar) cal.clone());
        oldCal.setTime(old[i]);
        cal.set(oldCal.get(Calendar.YEAR), oldCal.get(Calendar.MONTH), oldCal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
        old[i] = cal.getTime();
    }

    FocusAdapter dateFieldFocusAdapter = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent event) {
            JTextFieldDateEditor textField = ((JTextFieldDateEditor) event.getSource());
            try {
                saveDateFieldDate(textField);
            } catch (ParseException e1) {
                alertTextField(textField);
                textField.requestFocus();
            }
        }
    };

    FocusAdapter timeFieldExitFocusAdapter = new FocusAdapter() {
        public void focusLost(FocusEvent event) {
            JTextField textField = ((JTextField) event.getSource());
            try {
                saveTimeFieldDate(textField);
            } catch (ParseException e1) {
                alertTextField(textField);
                textField.requestFocus();
            }
        }
    };

    ActionListener timeFieldExitActionListener = new ActionListener() {
        public void actionPerformed(ActionEvent event) {
            JTextField textField = ((JTextField) event.getSource());
            try {
                saveTimeFieldDate(textField);
            } catch (ParseException e1) {
                alertTextField(textField);
            }
        }
    };

    KeyListener timeFieldKeyListener = new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                JTextField timeField = (JTextField) e.getSource();
                Date[] d = (Date[]) timeField.getClientProperty(DATES_CLIENT_PROPERTY);
                int i = (Integer) timeField.getClientProperty(DATEINDEX_CLIENT_PROPERTY);
                if (timeField instanceof JTextFieldDateEditor) {
                    timeField.setText(sdfDate.format(d[i]));
                } else {
                    timeField.setText(sdfHour.format(d[i]));
                }
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
        }
    };

    public void alertTextField(JComponent textField) {
        Color c = textField.getBackground();
        textField.setBackground(Color.RED);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                textField.setBackground(c);
            }
        });
        t.start();
    }

    AbstractAction addAction = new AbstractAction("Add") {
        @Override
        public void actionPerformed(ActionEvent e) {
            definedFilters = ipedChartPanel.getDefinedFilters();

            Date[] d = new Date[2];
            d[0] = ipedChartPanel.getIpedChartsPanel().getDomainAxis().getDateOnConfiguredTimePeriod(ipedChartPanel.getIpedChartsPanel().getTimePeriodClass(), cal.getTime()).getStart();
            d[1] = new Date(d[0].getTime() + (long) ipedChartPanel.getIpedChartsPanel().getTimePeriodLength());
            createDateLine(d, definedFilters.size());
            definedFilters.add(d);

            dialog.setVisible(true);
        }
    };

    JButton add = new JButton(addAction);

    HashMap<JButton, Date[]> buttonToIntervalIndex = new HashMap<JButton, Date[]>();

    IpedChartPanel ipedChartPanel;
    private ArrayList<Date[]> definedFilters;
    ArrayList<JComponent[]> components = new ArrayList<JComponent[]>();
    private Calendar cal;
    private Locale locale;

    static HashMap<Class<? extends TimePeriod>, String> dateFormaters = new HashMap<Class<? extends TimePeriod>, String>();
    static {
        dateFormaters.put(Year.class, "yyyy");
        dateFormaters.put(Quarter.class, "MM/yyyy");
        dateFormaters.put(Month.class, "MM/yyyy");
        dateFormaters.put(Week.class, "dd/MM/yyyy");
        dateFormaters.put(Day.class, "dd/MM/yyyy");
        dateFormaters.put(Hour.class, "dd/MM/yyyy HH");
        dateFormaters.put(Minute.class, "dd/MM/yyyy HH:mm");
        dateFormaters.put(Second.class, "dd/MM/yyyy HH:mm:ss");
        dateFormaters.put(Millisecond.class, "dd/MM/yyyy HH:mm:ss.SSS");
    }

    public void createDateLine(Date[] dates, int pos) {
        JComponent[] comps = new JComponent[6];
        components.add(comps);

        int count = 0;
        JLabel label = new JLabel(Integer.toString(pos) + ": ");
        c.gridx = count;
        c.gridy = pos;
        comps[count] = label;
        footer1.add(label, c); // $NON-NLS-1$
        count++;

        c.fill = GridBagConstraints.HORIZONTAL;
        String dateFormat = "dd/MM/yyyy";

        c.gridx = count;
        JTextFieldDateEditor textEditor = new JTextFieldDateEditor();
        textEditor.setLocale(locale);
        textEditor.putClientProperty(DATES_CLIENT_PROPERTY, dates);
        textEditor.putClientProperty(DATEINDEX_CLIENT_PROPERTY, 0);
        JDateChooser dateField = new JDateChooser(textEditor);
        textEditor.addKeyListener(timeFieldKeyListener);
        dateField.setDateFormatString(dateFormat);
        textEditor.setDate(dates[0]);
        textEditor.addFocusListener(dateFieldFocusAdapter);
        comps[count] = dateField;
        footer1.add(dateField, c);
        footer1.add(Box.createHorizontalStrut(130), c);
        count++;

        c.gridx = count;
        JFormattedTextField timeField = new JFormattedTextField();
        maskData.install(timeField);
        timeField.addFocusListener(timeFieldExitFocusAdapter);
        timeField.addActionListener(timeFieldExitActionListener);
        timeField.setText(sdfHour.format(dates[0]));
        timeField.putClientProperty(DATES_CLIENT_PROPERTY, dates);
        timeField.putClientProperty(DATEINDEX_CLIENT_PROPERTY, 0);
        timeField.addKeyListener(timeFieldKeyListener);
        comps[count] = timeField;
        footer1.add(timeField, c);
        count++;

        c.gridx = count;
        textEditor = new JTextFieldDateEditor();
        textEditor.setLocale(locale);
        textEditor.putClientProperty(DATES_CLIENT_PROPERTY, dates);
        textEditor.putClientProperty(DATEINDEX_CLIENT_PROPERTY, 1);
        dateField = new JDateChooser(textEditor);
        dateField.setDateFormatString(dateFormat);
        textEditor.addKeyListener(timeFieldKeyListener);
        textEditor.setDate(dates[1]);
        textEditor.addFocusListener(dateFieldFocusAdapter);
        comps[count] = dateField;
        footer1.add(dateField, c);
        footer1.add(Box.createHorizontalStrut(130), c);
        count++;

        c.gridx = count;
        timeField = new JFormattedTextField();
        try {
            ((MaskFormatter) maskData.clone()).install(timeField);
        } catch (CloneNotSupportedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        timeField.setText(sdfHour.format(dates[1]));
        timeField.putClientProperty(DATES_CLIENT_PROPERTY, dates);
        timeField.putClientProperty(DATEINDEX_CLIENT_PROPERTY, 1);
        timeField.addFocusListener(timeFieldExitFocusAdapter);
        timeField.addActionListener(timeFieldExitActionListener);
        timeField.addKeyListener(timeFieldKeyListener);
        comps[count] = timeField;
        footer1.add(timeField, c);
        count++;

        c.gridx = count;
        JButton b = new JButton(delete);
        buttonToIntervalIndex.put(b, dates);
        comps[count] = b;
        footer1.add(b, c);
    }

    public IntervalDefinitionDialog(IpedChartPanel ipedChartPanel) {
        this.ipedChartPanel = ipedChartPanel;

        LocaleConfig localeConfig = ConfigurationManager.get().findObject(LocaleConfig.class);
        locale = localeConfig.getLocale();

        dialog.setTitle(Messages.getString("IntervalDefinitionDialog.Title")); //$NON-NLS-1$
        dialog.setBounds(0, 0, 500, 500);
        dialog.setLocationRelativeTo(null);

        cal = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());

        sdfHour = new SimpleDateFormat(Messages.getString("IntervalDefinitionDialog.hourFormat", "HH:mm:ss"));
        sdfDate = new SimpleDateFormat(Messages.getString("IntervalDefinitionDialog.dateFormat", "dd/MM/yyyy"));
        sdfHour.setLenient(false);

        exitAction = new AbstractAction(Messages.getString("IntervalDefinitionDialog.Exit")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
                ipedChartPanel.setRefreshBuffer(true);
                ipedChartPanel.repaint();
            }

        };

        try {
            maskData = new MaskFormatter("##:##:##");
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        definedFilters = ipedChartPanel.getDefinedFilters();

        add.setText("Adicionar");
        exit.setText("Sair");
        exit.setAction(exitAction);

        Box footer = Box.createVerticalBox();
        footer1 = new JPanel(new GridBagLayout());
        footer1.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        int count = 0;
        for (Iterator iterator = definedFilters.iterator(); iterator.hasNext();) {
            Date[] dates = (Date[]) iterator.next();
            createDateLine(dates, count);
            count++;
        }
        JPanel panelAdd = new JPanel();
        panelAdd.setLayout(new BorderLayout());
        JPanel panelSubAdd = new JPanel();
        panelSubAdd.setLayout(new BoxLayout(panelSubAdd, BoxLayout.X_AXIS));
        // c.gridy=count;
        // c.gridx=2;
        add.setMaximumSize(new Dimension(100, 20));
        panelSubAdd.add(add, BorderLayout.CENTER);
        panelAdd.add(panelSubAdd, BorderLayout.NORTH);

        for (Component comp : footer.getComponents())
            ((JComponent) comp).setAlignmentX(0);

        panel.add(footer, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(footer1);
        footer.add(sp, BorderLayout.NORTH);
        footer.add(panelAdd, BorderLayout.NORTH);

        JPanel footer2 = new JPanel(new FlowLayout());
        footer2.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer2.add(exit);
        panel.add(footer2, BorderLayout.SOUTH);

        dialog.setMinimumSize(new Dimension(500, 300));
        dialog.getContentPane().add(panel);
    }

    public void setVisible() {
        dialog.setModal(true);
        dialog.setVisible(true);
    }
}
