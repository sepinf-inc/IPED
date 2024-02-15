package iped.app.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.jfree.data.time.DateRange;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.app.timelinegraph.ChartTimePeriodConstraint;
import iped.app.timelinegraph.DateUtil;
import iped.app.timelinegraph.IpedChartsPanel;
import iped.app.ui.Messages;
import iped.jfextensions.model.Minute;

public class TimePeriodSelectionPopupMenu extends JPopupMenu implements ActionListener {
    static HashMap<String, SimpleDateFormat> sdfMap = null;
    static HashMap<Class<? extends TimePeriod>, JTimePeriodMenuItem> miMap = new HashMap<Class<? extends TimePeriod>, JTimePeriodMenuItem>();

    IpedChartsPanel ipedChartsPanel;

    ButtonGroup timePeriodGroup = new ButtonGroup();
    JTimePeriodMenuItem yearMenu;
    JTimePeriodMenuItem quarterMenu;
    JTimePeriodMenuItem monthMenu;
    JTimePeriodMenuItem weekMenu;
    JTimePeriodMenuItem dayMenu;
    JTimePeriodMenuItem hourMenu;
    JTimePeriodMenuItem minuteMenu;
    JTimePeriodMenuItem secondMenu;
    // JTimePeriodMenuItem millisecondMenu;

    class JTimezoneMenuItem extends JRadioButtonMenuItem {
        TimeZone tz;

        public JTimezoneMenuItem(String value, TimeZone tz) {
            super(value);
            this.tz = tz;
        }

        public TimeZone getTimezone() {
            return tz;
        }
    }

    class JTimePeriodMenuItem extends JRadioButtonMenuItem {
        Class<? extends TimePeriod> timePeriodClass;

        public JTimePeriodMenuItem(Class<? extends TimePeriod> timePeriodClass) {
            super(DateUtil.getTimePeriodName(timePeriodClass));
            this.timePeriodClass = timePeriodClass;
        }

        public Class<? extends TimePeriod> getTimePeriodClass() {
            return timePeriodClass;
        }
    }

    static {
        sdfMap = new HashMap<String, SimpleDateFormat>();
        sdfMap.put("Year", new SimpleDateFormat("YYYY"));
        sdfMap.put("Quarter", new SimpleDateFormat("YYYY"));
        sdfMap.put("Month", new SimpleDateFormat("MM-YYYY"));
        sdfMap.put("Week", new SimpleDateFormat("WW MM-YYYY"));
        sdfMap.put("Day", new SimpleDateFormat("dd-MM-YYYY"));
        sdfMap.put("Hour", new SimpleDateFormat("dd-MM-YYYY HH:'00'"));
        sdfMap.put("Minute", new SimpleDateFormat("dd-MM-YYYY HH:mm"));
        sdfMap.put("Second", new SimpleDateFormat("dd-MM-YYYY HH:mm:ss"));
        sdfMap.put("Millisecond", new SimpleDateFormat("dd-MM-YYYY HH:mm:ss"));
    }

    public TimePeriodSelectionPopupMenu(IpedChartsPanel ipedChartsPanel) {
        this.ipedChartsPanel = ipedChartsPanel;
        JMenu periodGranularityMenu = new JMenu(Messages.getString("TimeLineGraph.PeriodGranularity"));
        JMenu timezoneMenu = new JMenu(Messages.getString("TimeLineGraph.Timezone"));
        add(periodGranularityMenu);
        add(timezoneMenu);
        timezoneMenu.setAutoscrolls(true);

        yearMenu = new JTimePeriodMenuItem(Year.class);
        yearMenu.setActionCommand("Year");
        yearMenu.addActionListener(this);
        periodGranularityMenu.add(yearMenu);
        timePeriodGroup.add(yearMenu);

        quarterMenu = new JTimePeriodMenuItem(Quarter.class);
        quarterMenu.setActionCommand("Quarter");
        quarterMenu.addActionListener(this);
        periodGranularityMenu.add(quarterMenu);
        timePeriodGroup.add(quarterMenu);

        monthMenu = new JTimePeriodMenuItem(Month.class);
        monthMenu.setActionCommand("Month");
        monthMenu.addActionListener(this);
        periodGranularityMenu.add(monthMenu);
        timePeriodGroup.add(monthMenu);

        weekMenu = new JTimePeriodMenuItem(Week.class);
        weekMenu.setActionCommand("Week");
        weekMenu.addActionListener(this);
        periodGranularityMenu.add(weekMenu);
        timePeriodGroup.add(weekMenu);

        dayMenu = new JTimePeriodMenuItem(Day.class);
        dayMenu.setActionCommand("Day");
        dayMenu.addActionListener(this);
        periodGranularityMenu.add(dayMenu);
        timePeriodGroup.add(dayMenu);

        hourMenu = new JTimePeriodMenuItem(Hour.class);
        hourMenu.setActionCommand("Hour");
        hourMenu.addActionListener(this);
        periodGranularityMenu.add(hourMenu);
        timePeriodGroup.add(hourMenu);

        minuteMenu = new JTimePeriodMenuItem(Minute.class);
        minuteMenu.setActionCommand("Minute");
        minuteMenu.addActionListener(this);
        periodGranularityMenu.add(minuteMenu);
        timePeriodGroup.add(minuteMenu);

        secondMenu = new JTimePeriodMenuItem(Second.class);
        secondMenu.setActionCommand("Second");
        secondMenu.addActionListener(this);
        periodGranularityMenu.add(secondMenu);
        timePeriodGroup.add(secondMenu);

        /*
         * millisecondMenu = new JTimePeriodMenuItem(Millisecond.class);
         * millisecondMenu.setActionCommand("Millisecond");
         * millisecondMenu.addActionListener(this);
         * periodGranularityMenu.add(millisecondMenu);
         * timePeriodGroup.add(millisecondMenu);
         */

        miMap.put(Year.class, yearMenu);
        miMap.put(Quarter.class, quarterMenu);
        miMap.put(Month.class, monthMenu);
        miMap.put(Week.class, weekMenu);
        miMap.put(Day.class, dayMenu);
        miMap.put(Hour.class, hourMenu);
        miMap.put(Minute.class, minuteMenu);
        miMap.put(Second.class, secondMenu);
        // miMap.put(Millisecond.class, millisecondMenu);

        timePeriodGroup.setSelected(dayMenu.getModel(), true);

        HashMap<String, JMenu> zoneSubMenus = new HashMap<String, JMenu>();
        for (String timeZoneStr : ZoneId.getAvailableZoneIds()) {
            String zoneinfo[] = timeZoneStr.split("/");
            JMenu zoneSubMenu = zoneSubMenus.get(zoneinfo[0]);
            if (zoneSubMenu == null) {
                zoneSubMenu = new JMenu(zoneinfo[0]);
                zoneSubMenus.put(zoneinfo[0], zoneSubMenu);
                timezoneMenu.add(zoneSubMenu);
            }

            String timezoneofssetformat = DateUtil.getTimezoneOffsetInformation(TimeZone.getTimeZone(timeZoneStr));

            JTimezoneMenuItem tzmi = new JTimezoneMenuItem(timeZoneStr + "(" + timezoneofssetformat + ")", TimeZone.getTimeZone(timeZoneStr));
            tzmi.setActionCommand(timeZoneStr);
            tzmi.addActionListener(this);
            zoneSubMenu.add(tzmi);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JTimePeriodMenuItem) {
            Class<? extends TimePeriod> tpclass = ((JTimePeriodMenuItem) e.getSource()).getTimePeriodClass();
            String tpClassName = DateUtil.getTimePeriodName(tpclass);
            Range dateRange = ipedChartsPanel.getDomainAxis().getRange();
            Date startDate = new Date((long) dateRange.getLowerBound());
            Date endDate = new Date((long) dateRange.getUpperBound());
            ChartTimePeriodConstraint c = ipedChartsPanel.getChartPanel().getTimePeriodConstraints(tpclass);
            long rangeSize = endDate.getTime() - startDate.getTime();
            int input = 0;

            double java2dlower = ipedChartsPanel.getDomainAxis().valueToJava2D(dateRange.getLowerBound(), ipedChartsPanel.getChartPanel().getScreenDataArea(), RectangleEdge.BOTTOM);
            double java2dupper = ipedChartsPanel.getDomainAxis().valueToJava2D(dateRange.getUpperBound(), ipedChartsPanel.getChartPanel().getScreenDataArea(), RectangleEdge.BOTTOM);
            double newbarsize = ((java2dupper - java2dlower) / (dateRange.getUpperBound() - dateRange.getLowerBound())) * ChartTimePeriodConstraint.getTimePeriodUnit(tpclass);// size in pixels

            if (c != null && rangeSize > c.getMaxZoomoutRangeSize()) {
                Date centerDate = new Date(startDate.getTime() + rangeSize / 2);
                String msg = String.format(Messages.get("TimeLineGraph.visibleRangeToGreat"), tpClassName, iped.utils.DateUtil.dateToString(centerDate));
                input = JOptionPane.showConfirmDialog(null, msg, "", JOptionPane.OK_CANCEL_OPTION);
                if (input == 0) {
                    Date lstartDate = new Date((long) Math.floor(centerDate.getTime() - c.getMaxZoomoutRangeSize() / 2));
                    Date lendDate = new Date((long) Math.ceil(centerDate.getTime() + c.getMaxZoomoutRangeSize() / 2));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ipedChartsPanel.getDomainAxis().forceRange(new DateRange(lstartDate, lendDate), false, false);
                        }
                    }).start();
                }
            } else if (c != null && rangeSize < c.getMinZoominRangeSize()) {
                Date centerDate = new Date(startDate.getTime() + rangeSize / 2);
                Date lstartDate = new Date((long) Math.floor(centerDate.getTime() - c.getMinZoominRangeSize() / 2));
                Date lendDate = new Date((long) Math.ceil(centerDate.getTime() + c.getMinZoominRangeSize() / 2));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ipedChartsPanel.getDomainAxis().forceRange(new DateRange(lstartDate, lendDate), false, false);
                    }
                }).start();
            } else if (newbarsize > (java2dupper - java2dlower) / 3) { // if the bar size of the new timeperiod granularity is greater than 1/3 of the
                                                                       // total screen area
                // zooms out to show at least 3 bars
                Date centerDate = new Date(startDate.getTime() + rangeSize / 2);
                startDate = new Date((long) Math.floor(centerDate.getTime() - ChartTimePeriodConstraint.getTimePeriodUnit(tpclass)));
                endDate = new Date((long) Math.ceil(centerDate.getTime() + ChartTimePeriodConstraint.getTimePeriodUnit(tpclass)));

                ipedChartsPanel.getDomainAxis().forceRange(new DateRange(startDate, endDate), false, false);
            }
            if (input == 0) {
                ipedChartsPanel.setTimePeriodClass(tpclass);
                ipedChartsPanel.setTimePeriodString(e.getActionCommand());
                ipedChartsPanel.refreshChart();
            }
        } else {
            ipedChartsPanel.setTimeZone(((JTimezoneMenuItem) e.getSource()).getTimezone());
        }
    }

    @Override
    public void setVisible(boolean b) {
        timePeriodGroup.setSelected(miMap.get(ipedChartsPanel.getTimePeriodClass()).getModel(), true);
        super.setVisible(b);
    }

}