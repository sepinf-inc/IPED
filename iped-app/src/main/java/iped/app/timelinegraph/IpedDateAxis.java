package iped.app.timelinegraph;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.JOptionPane;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTick;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.Tick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.Args;
import org.jfree.data.Range;
import org.jfree.data.time.DateRange;
import org.jfree.data.time.Day;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;
import org.jfree.data.xy.XYDataset;

import iped.app.timelinegraph.datasets.AsynchronousDataset;
import iped.app.ui.Messages;
import iped.jfextensions.model.Minute;

public class IpedDateAxis extends DateAxis implements MouseResponsiveChartEntity {
    volatile SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    HashMap<DateTickUnitType, DateFormat> dateFormaters = new HashMap<DateTickUnitType, DateFormat>();
    IpedChartsPanel ipedChartsPanel;

    private boolean needTimePeriodClassUpdate;

    private Paint mouseOverPaint;

    public IpedDateAxis(String string, IpedChartsPanel ipedChartsPanel) {
        super(string, TimeZone.getDefault(), Locale.getDefault());

        setAutoRange(false);
        configure();

        this.ipedChartsPanel = ipedChartsPanel;

        dateFormaters.put(DateTickUnitType.YEAR, new SimpleDateFormat("yyyy"));
        dateFormaters.put(DateTickUnitType.MONTH, new SimpleDateFormat("MMM-yyyy"));
        dateFormaters.put(DateTickUnitType.DAY, new SimpleDateFormat("d-MMM'\n'yyyy"));
        dateFormaters.put(DateTickUnitType.HOUR, new SimpleDateFormat("HH:'00'\nd-MMM-yyyy"));
        dateFormaters.put(DateTickUnitType.MINUTE, new SimpleDateFormat("HH:mm\nd-MMM-yyyy"));
        dateFormaters.put(DateTickUnitType.SECOND, new SimpleDateFormat(" HH:mm:ss\nd-MMM-yyyy"));
        dateFormaters.put(DateTickUnitType.MILLISECOND, new SimpleDateFormat("HH:mm:ss.SSS\nd-MMM-yyyy"));
    }

    /**
     * Recalculates the ticks for the date axis.
     *
     * @param g2
     *            the graphics device.
     * @param dataArea
     *            the area in which the data is to be drawn.
     * @param edge
     *            the location of the axis.
     *
     * @return A list of ticks.
     */
    protected List refreshTicksHorizontal(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {
        List result = new java.util.ArrayList();

        Font tickLabelFont = getTickLabelFont();
        g2.setFont(tickLabelFont);

        if (isAutoTickUnitSelection()) {
            selectAutoTickUnit(g2, dataArea, edge);
        }

        DateTickUnit unit = getTickUnit();
        Date tickDate = calculateLowestVisibleTickValue(unit);
        Date upperDate = getMaximumDate();

        int lastUpperTickUnitCalendarValue = -1;

        boolean hasRolled = false;
        while (tickDate.before(upperDate)) {
            // could add a flag to make the following correction optional...
            if (!hasRolled) {
                tickDate = correctTickDateForPosition(tickDate, unit, this.getTickMarkPosition());
            }

            long lowestTickTime = tickDate.getTime();
            long distance = unit.addToDate(tickDate, this.getTimeZone()).getTime() - lowestTickTime;
            int minorTickSpaces = getMinorTickCount();
            if (minorTickSpaces <= 0) {
                minorTickSpaces = unit.getMinorTickCount();
            }
            for (int minorTick = 1; minorTick < minorTickSpaces; minorTick++) {
                long minorTickTime = lowestTickTime - distance * minorTick / minorTickSpaces;
                if (minorTickTime > 0 && getRange().contains(minorTickTime) && (!isHiddenValue(minorTickTime))) {
                    result.add(new DateTick(TickType.MINOR, new Date(minorTickTime), "", TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
                }
            }

            if (!isHiddenValue(tickDate.getTime())) {
                // work out the value, label and position
                String tickLabel;
                DateFormat formatter = getDateFormatOverride();
                if (formatter != null) {
                    tickLabel = formatter.format(tickDate);
                } else {
                    Calendar cal = Calendar.getInstance(getTimeZone());
                    cal.setTime(tickDate);
                    int upperTickUnitCalendarValue = cal.get(DateUtil.getUpperCalendarField(this.getTickUnit().getCalendarField()));
                    if (lastUpperTickUnitCalendarValue != upperTickUnitCalendarValue) {
                        tickLabel = getLongDateFormaterTickUnit(this.getTickUnit()).format(tickDate);
                        lastUpperTickUnitCalendarValue = upperTickUnitCalendarValue;
                    } else {
                        tickLabel = this.getTickUnit().dateToString(tickDate);
                    }
                }
                TextAnchor anchor, rotationAnchor;
                double angle = 0.0;
                if (isVerticalTickLabels()) {
                    anchor = TextAnchor.CENTER_RIGHT;
                    rotationAnchor = TextAnchor.CENTER_RIGHT;
                    if (edge == RectangleEdge.TOP) {
                        angle = Math.PI / 2.0;
                    } else {
                        angle = -Math.PI / 2.0;
                    }
                } else {
                    if (edge == RectangleEdge.TOP) {
                        anchor = TextAnchor.BOTTOM_CENTER;
                        rotationAnchor = TextAnchor.BOTTOM_CENTER;
                    } else {
                        anchor = TextAnchor.TOP_CENTER;
                        rotationAnchor = TextAnchor.TOP_CENTER;
                    }
                }

                Tick tick = new DateTick(tickDate, tickLabel, anchor, rotationAnchor, angle);
                result.add(tick);
                hasRolled = false;

                long currentTickTime = tickDate.getTime();
                tickDate = unit.addToDate(tickDate, this.getTimeZone());
                long nextTickTime = tickDate.getTime();
                for (int minorTick = 1; minorTick < minorTickSpaces; minorTick++) {
                    long minorTickTime = currentTickTime + (nextTickTime - currentTickTime) * minorTick / minorTickSpaces;
                    if (getRange().contains(minorTickTime) && (!isHiddenValue(minorTickTime))) {
                        result.add(new DateTick(TickType.MINOR, new Date(minorTickTime), "", TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
                    }
                }

            } else {
                tickDate = unit.rollDate(tickDate, this.getTimeZone());
                hasRolled = true;
            }

        }
        return result;

    }

    /**
     * Corrects the given tick date for the position setting.
     *
     * @param time
     *            the tick date/time.
     * @param unit
     *            the tick unit.
     * @param position
     *            the tick position.
     *
     * @return The adjusted time.
     */
    private Date correctTickDateForPosition(Date time, DateTickUnit unit, DateTickMarkPosition position) {
        Date result = time;
        if (unit.getUnitType().equals(DateTickUnitType.MONTH)) {
            result = calculateDateForPosition(new Month(time, this.getTimeZone(), this.getLocale()), position);
        } else if (unit.getUnitType().equals(DateTickUnitType.YEAR)) {
            result = calculateDateForPosition(new Year(time, this.getTimeZone(), this.getLocale()), position);
        }
        return result;
    }

    /**
     * Returns a {@link java.util.Date} corresponding to the specified position
     * within a {@link RegularTimePeriod}.
     *
     * @param period
     *            the period.
     * @param position
     *            the position ({@code null} not permitted).
     *
     * @return A date.
     */
    private Date calculateDateForPosition(RegularTimePeriod period, DateTickMarkPosition position) {
        Args.nullNotPermitted(period, "period");
        Date result = null;
        if (position == DateTickMarkPosition.START) {
            result = new Date(period.getFirstMillisecond());
        } else if (position == DateTickMarkPosition.MIDDLE) {
            result = new Date(period.getMiddleMillisecond());
        } else if (position == DateTickMarkPosition.END) {
            result = new Date(period.getLastMillisecond());
        }
        return result;

    }

    protected double findMaximumTickLabelHeight(List ticks, Graphics2D g2, Rectangle2D drawArea, boolean vertical) {
        return super.findMaximumTickLabelHeight(ticks, g2, drawArea, vertical) * 2;// doubled as tick labels upper text are painted bellow main tick label
    }

    private Calendar calendarInstance;

    public TimePeriod getDateOnConfiguredTimePeriod(Class<? extends TimePeriod> timePeriodClass, Date date) {
        if (calendarInstance == null) {
            calendarInstance = Calendar.getInstance(getTimeZone());
        }
        Calendar cal = (Calendar) calendarInstance.clone();
        try {
            TimePeriod t = _getDateOnConfiguredTimePeriod(timePeriodClass, date, cal);
            return t;

        } catch (Exception e) {
            try {
                TimePeriod t = null;
                cal.set(1900, 0, 1, 0, 0, 0);
                if (date.before(cal.getTime())) {
                    t = _getDateOnConfiguredTimePeriod(timePeriodClass, cal.getTime(), cal);
                } else {
                    cal.set(9999, 12, 31, 23, 59, 59);
                    if (date.after(cal.getTime())) {
                        t = _getDateOnConfiguredTimePeriod(timePeriodClass, cal.getTime(), cal);
                    }
                }
                return t;
            } catch (Exception e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    private TimePeriod _getDateOnConfiguredTimePeriod(Class<? extends TimePeriod> timePeriodClass, Date date, Calendar cal) {
        if (timePeriodClass == Day.class) {
            return new Day(date, cal);
        } else if (timePeriodClass == Hour.class) {
            return new Hour(date, cal);
        } else if (timePeriodClass == Year.class) {
            return new Year(date, cal);
        } else if (timePeriodClass == Quarter.class) {
            return new Quarter(date, cal);
        } else if (timePeriodClass == Month.class) {
            return new Month(date, cal);
        } else if (timePeriodClass == Week.class) {
            return new Week(date, cal);
        } else if (timePeriodClass == Minute.class) {
            return new Minute(date, cal);
        } else if (timePeriodClass == iped.jfextensions.model.Minute.class) {
            return new iped.jfextensions.model.Minute(date, cal);
        } else if (timePeriodClass == Second.class) {
            return new Second(date, cal);
        } else if (timePeriodClass == Millisecond.class) {
            return new Millisecond(date, cal);
        } else if (timePeriodClass == FixedMillisecond.class) {
            return new FixedMillisecond(date);
        }
        throw new RuntimeException(timePeriodClass.getName() + " not handled!");
    }

    public String ISO8601DateFormat(Date date) {
        synchronized (ISO8601DATEFORMAT) {
            return ISO8601DATEFORMAT.format(date);
        }
    }

    public String ISO8601DateFormatUTC(Date date) {
        StringBuffer result = new StringBuffer();
        Date d = new Date(date.getTime()-DateUtil.computerTimezoneOffset);
        result.append(String.format("%04d", d.getYear()+1900));
        result.append("-");
        result.append(String.format("%02d", d.getMonth()+1));
        result.append("-");
        result.append(String.format("%02d", d.getDate()));
        result.append("T");
        result.append(String.format("%02d", d.getHours()));
        result.append(":");
        result.append(String.format("%02d", d.getMinutes()));
        result.append(":");
        result.append(String.format("%02d", d.getSeconds()));
        result.append("Z");
        return result.toString();
    }

    public Date ISO8601DateParse(String strDate) {
        try {
            synchronized (ISO8601DATEFORMAT) {
                return ISO8601DATEFORMAT.parse(strDate);
            }
        } catch (ParseException | NumberFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void setTimeZone(TimeZone zone) {
        synchronized (ISO8601DATEFORMAT) {
            ISO8601DATEFORMAT.setTimeZone(zone);
        }

        for (DateFormat f : dateFormaters.values()) {
            f.setCalendar(Calendar.getInstance(zone));
        }

        super.setTimeZone(zone);
    }

    public DateFormat getLongDateFormaterTickUnit(DateTickUnit dateTickUnit) {
        return dateFormaters.get(dateTickUnit.getUnitType());
    }

    public String dateToString(Date date) {
        synchronized (ISO8601DATEFORMAT) {
            return ISO8601DATEFORMAT.format(date);
        }
    }

    public void forceRange(Range range, boolean turnOffAutoRange, boolean notify) {
        Args.nullNotPermitted(range, "range");
        // usually the range will be a DateRange, but if it isn't do a
        // conversion...
        if (!(range instanceof DateRange)) {
            range = new DateRange(range);
        }

        this.notifyListeners(null);

        if (getTimeZone() != null) {
            Calendar cal = Calendar.getInstance(getTimeZone());
            cal.set(1900, 0, 1, 0, 0, 0);
            if (((DateRange) range).getLowerMillis() < cal.getTimeInMillis()) {
                range = new DateRange(cal.getTime(), ((DateRange) range).getUpperDate());
            }
            cal.set(9999, 11, 31, 23, 59, 59);
            if (((DateRange) range).getUpperMillis() > cal.getTimeInMillis()) {
                range = new DateRange(((DateRange) range).getLowerDate(), cal.getTime());
            }

            super.setRange(range, turnOffAutoRange, notify);
        } else {
            super.setRange(range, turnOffAutoRange, notify);
        }
        List<XYPlot> plots = new ArrayList<XYPlot>();
        XYPlot cplot = ((XYPlot) getPlot());
        if (cplot != null) {
            cplot = (XYPlot) cplot.getRootPlot();
            if (cplot instanceof IpedCombinedDomainXYPlot) {
                plots.addAll(((IpedCombinedDomainXYPlot) cplot).getSubplots());
            } else if (cplot instanceof IpedXYPlot) {
                plots.add(cplot);
            }
            for (Iterator<XYPlot> iterator = plots.iterator(); iterator.hasNext();) {
                XYPlot plot = iterator.next();
                XYDataset ds = plot.getDataset();
                if (ds instanceof AsynchronousDataset && notify) {
                    ((AsynchronousDataset) ds).notifyVisibleRange(range.getLowerBound(), range.getUpperBound());
                }
            }
        }
    }

    /**
     * Sets the range for the axis, if requested, sends an {@link AxisChangeEvent}
     * to all registered listeners. As a side-effect, the auto-range flag is set to
     * {@code false} (optional).
     *
     * @param range
     *            the range ({@code null} not permitted).
     * @param turnOffAutoRange
     *            a flag that controls whether or not the auto range is turned off.
     * @param notify
     *            a flag that controls whether or not listeners are notified.
     */
    @Override
    public void setRange(Range range, boolean turnOffAutoRange, boolean notify) {
        if (canZoom(range)) {
            forceRange(range, turnOffAutoRange, notify);
            if (needTimePeriodClassUpdate) {
                ipedChartsPanel.cancel();
                ipedChartsPanel.refreshChart();
                needTimePeriodClassUpdate = false;
            }
        }
    }

    public DateRange putMargin(DateRange range) {
        long margin = (range.getUpperMillis() - range.getLowerMillis()) * 2 / 100;

        return new DateRange(range.getLowerMillis() - margin, range.getUpperMillis() + margin);
    }

    public Class<? extends TimePeriod> bestGranularityForRange(Date min, Date max) {
        Class<? extends TimePeriod> result = ipedChartsPanel.getTimePeriodClass();
        ChartTimePeriodConstraint c = ipedChartsPanel.getChartPanel().getTimePeriodConstraints(result);
        if (c == null)
            return result;
        long rangeSize = max.getTime() - min.getTime();
        while (c != null && c.getMaxZoomoutRangeSize() < rangeSize) {
            result = upsize(result);
            c = ipedChartsPanel.getChartPanel().getTimePeriodConstraints(result);
        }
        return result;
    }

    /**
     * Adjusts visible range to guarantee that the current minimum and maximum
     * visible x values are visible with the minimum and maximum dates informed
     *
     * @param min
     *            the minimum date of the new range to guarantee visibility
     * @param max
     *            the maximum date of the new range to guarantee visibility
     */
    public void garanteeShowRange(Date min, Date max) {
        DateRange range = (DateRange) getRange();
        DateRange newRange = null;
        if (range.getLowerMillis() > min.getTime()) {
            if (range.getUpperMillis() < max.getTime()) {
                newRange = new DateRange(min.getTime(), max.getTime());
            } else {
                newRange = new DateRange(min.getTime(), range.getUpperMillis());
            }
        } else {
            if (range.getUpperMillis() < max.getTime()) {
                newRange = new DateRange(range.getLowerMillis(), max.getTime());
            } else {
                return;
            }
        }

        newRange = putMargin(newRange);

        Class<? extends TimePeriod> tpclass = bestGranularityForRange(newRange.getLowerDate(), newRange.getUpperDate());
        if (!tpclass.equals(ipedChartsPanel.getTimePeriodClass())) {
            ipedChartsPanel.setTimePeriodClass(tpclass);
            ipedChartsPanel.setTimePeriodString(tpclass.getSimpleName());
            ipedChartsPanel.refreshChart();
        }
        needTimePeriodClassUpdate = true;
        setRange(newRange);
    }

    public IpedChartsPanel getIpedChartsPanel() {
        return ipedChartsPanel;
    }

    /**
     * Determines if the axis can be zoomed to the range according to the contraint
     * rules.
     *
     * @param range
     *            the range to zoom in or out.
     */
    public boolean canZoom(Range range) {
        boolean result = true;

        if (ipedChartsPanel != null) {
            ChartTimePeriodConstraint c = ipedChartsPanel.getChartPanel().getTimePeriodConstraints();

            Class<? extends TimePeriod> tpclass = ipedChartsPanel.getTimePeriodClass();
            double curRangeSize = this.getRange().getUpperBound() - this.getRange().getLowerBound();
            double rangeSize = range.getUpperBound() - range.getLowerBound();
            // uses the constraint rules applied
            double java2dlower = valueToJava2D(range.getLowerBound(), ipedChartsPanel.getChartPanel().getScreenDataArea(), RectangleEdge.BOTTOM);
            double java2dupper = valueToJava2D(range.getUpperBound(), ipedChartsPanel.getChartPanel().getScreenDataArea(), RectangleEdge.BOTTOM);
            double barsize = ((java2dupper - java2dlower) / (range.getUpperBound() - range.getLowerBound())) * ChartTimePeriodConstraint.getTimePeriodUnit(ipedChartsPanel.getTimePeriodClass());// size in pixels

            if (c != null) {
                if (curRangeSize < rangeSize && (rangeSize > c.maxZoomoutRangeSize || barsize <= c.minBarSizeInPixels)) {
                    result = false;
                }
                if (curRangeSize > rangeSize && (rangeSize < c.minZoominRangeSize || barsize >= ((java2dupper - java2dlower) / 3))) {
                    result = false;
                }
            } else {
                if (curRangeSize > rangeSize && barsize >= ((java2dupper - java2dlower) / 3)) {
                    result = false;
                }
            }

            if (!result) {
                Method method;
                try {
                    if (curRangeSize > rangeSize && tpclass != Second.class) {// zoomIn

                        Class<? extends TimePeriod> downtpclass = downsize(tpclass);

                        String tpClassName = DateUtil.getTimePeriodName(tpclass);
                        String msg = String.format(Messages.get("TimeLineGraph.visibleZoominForGranularity"), tpClassName, DateUtil.getTimePeriodName(downtpclass));
                        int input = JOptionPane.showConfirmDialog(null, msg, "", JOptionPane.OK_CANCEL_OPTION);
                        if (input == 0) {
                            result = true;
                            ipedChartsPanel.setTimePeriodClass(downtpclass);
                            ipedChartsPanel.setTimePeriodString(DateUtil.getTimePeriodName(downtpclass));
                            needTimePeriodClassUpdate = true;
                        }
                    }
                    if (curRangeSize < rangeSize) {// zoomIn
                        Class<? extends TimePeriod> uptpclass = upsize(tpclass);

                        String tpClassName = DateUtil.getTimePeriodName(tpclass);
                        String msg = String.format(Messages.get("TimeLineGraph.visibleZoomoutForGranularity"), tpClassName, DateUtil.getTimePeriodName(uptpclass));
                        int input = JOptionPane.showConfirmDialog(null, msg, "", JOptionPane.OK_CANCEL_OPTION);
                        if (input == 0) {
                            result = true;
                            ipedChartsPanel.setTimePeriodClass(uptpclass);
                            ipedChartsPanel.setTimePeriodString(DateUtil.getTimePeriodName(uptpclass));
                            needTimePeriodClassUpdate = true;
                        }
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }

        return result;
    }

    /**
     * Returns a subclass of {@link RegularTimePeriod} that is smaller than the
     * specified class.
     *
     * @param c
     *            a subclass of {@link RegularTimePeriod}.
     *
     * @return A class.
     */
    public static Class downsize(Class c) {
        if (c.equals(Year.class)) {
            return Quarter.class;
        } else if (c.equals(Quarter.class)) {
            return Month.class;
        } else if (c.equals(Month.class)) {
            return Day.class;
        } else if (c.equals(Day.class)) {
            return Hour.class;
        } else if (c.equals(Hour.class)) {
            return Minute.class;
        } else if (c.equals(Minute.class)) {
            return Second.class;
        } else if (c.equals(Second.class)) {
            return Millisecond.class;
        } else {
            return Millisecond.class;
        }
    }

    /**
     * Returns a subclass of {@link RegularTimePeriod} that is smaller than the
     * specified class.
     *
     * @param c
     *            a subclass of {@link RegularTimePeriod}.
     *
     * @return A class.
     */
    public static Class upsize(Class c) {
        if (c.equals(Quarter.class)) {
            return Year.class;
        } else if (c.equals(Month.class)) {
            return Quarter.class;
        } else if (c.equals(Day.class)) {
            return Month.class;
        } else if (c.equals(Hour.class)) {
            return Day.class;
        } else if (c.equals(Minute.class)) {
            return Hour.class;
        } else if (c.equals(Second.class)) {
            return Minute.class;
        } else if (c.equals(Millisecond.class)) {
            return Second.class;
        } else {
            return Year.class;
        }
    }

    @Override
    public AxisState draw(Graphics2D g2, double cursor, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, PlotRenderingInfo plotState) {
        if (mouseOverPaint != null) {
            g2.setPaint(mouseOverPaint);
            g2.fillRect((int) dataArea.getMinX(), (int) dataArea.getMaxY(), (int) plotArea.getMaxX() - (int) dataArea.getMinX(), (int) plotArea.getMaxY() - (int) dataArea.getMaxY());
        }
        AxisState result = super.draw(g2, cursor, plotArea, dataArea, edge, plotState);
        return result;
    }

    public Paint getMouseOverPaint() {
        return mouseOverPaint;
    }

    public void setMouseOverPaint(Paint mouseOverPaint) {
        this.mouseOverPaint = mouseOverPaint;
    }

    @Override
    public void configure() {
        super.configure();
    }

    @Override
    public void autoAdjustRange() {
        // TODO Auto-generated method stub
        super.autoAdjustRange();
    }

}