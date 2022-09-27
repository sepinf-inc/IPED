package iped.viewers.timelinegraph;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTick;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.Tick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.Args;
import org.jfree.data.Range;
import org.jfree.data.time.DateRange;
import org.jfree.data.time.Month;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Year;

public class IpedDateAxis extends DateAxis {
    volatile SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	HashMap<DateTickUnitType, DateFormat> dateFormaters = new HashMap<DateTickUnitType, DateFormat>();
	IpedChartsPanel ipedChartsPanel;

    public IpedDateAxis(String string, IpedChartsPanel ipedChartsPanel) {
		super(string, TimeZone.getDefault(), Locale.getDefault());

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
     * @param g2  the graphics device.
     * @param dataArea  the area in which the data is to be drawn.
     * @param edge  the location of the axis.
     *
     * @return A list of ticks.
     */
    protected List refreshTicksHorizontal(Graphics2D g2,
                Rectangle2D dataArea, RectangleEdge edge) {
        List result = new java.util.ArrayList();

        Font tickLabelFont = getTickLabelFont();
        g2.setFont(tickLabelFont);

        if (isAutoTickUnitSelection()) {
            selectAutoTickUnit(g2, dataArea, edge);
        }

        DateTickUnit unit = getTickUnit();
        Date tickDate = calculateLowestVisibleTickValue(unit);
        Date upperDate = getMaximumDate();
        
        int lastUpperTickUnitCalendarValue=-1;

        boolean hasRolled = false;
        while (tickDate.before(upperDate)) {
            // could add a flag to make the following correction optional...
            if (!hasRolled) {
                tickDate = correctTickDateForPosition(tickDate, unit,
                     this.getTickMarkPosition());
            }

            long lowestTickTime = tickDate.getTime();
            long distance = unit.addToDate(tickDate, this.getTimeZone()).getTime()
                    - lowestTickTime;
            int minorTickSpaces = getMinorTickCount();
            if (minorTickSpaces <= 0) {
                minorTickSpaces = unit.getMinorTickCount();
            }
            for (int minorTick = 1; minorTick < minorTickSpaces; minorTick++) {
                long minorTickTime = lowestTickTime - distance
                        * minorTick / minorTickSpaces;
                if (minorTickTime > 0 && getRange().contains(minorTickTime)
                        && (!isHiddenValue(minorTickTime))) {
                    result.add(new DateTick(TickType.MINOR, new Date(minorTickTime), "", TextAnchor.TOP_CENTER, TextAnchor.CENTER, 0.0));
                }
            }

            if (!isHiddenValue(tickDate.getTime())) {
                // work out the value, label and position
                String tickLabel;
                DateFormat formatter = getDateFormatOverride();
                if (formatter != null) {
                    tickLabel = formatter.format(tickDate);
                }
                else {
    		        Calendar cal = Calendar.getInstance(getTimeZone());
    		        cal.setTime(tickDate);
    		        int upperTickUnitCalendarValue = cal.get(DateUtil.getUpperCalendarField(this.getTickUnit().getCalendarField()));
                	if(lastUpperTickUnitCalendarValue!=upperTickUnitCalendarValue) {
                        tickLabel = getLongDateFormaterTickUnit(this.getTickUnit()).format(tickDate);
                        lastUpperTickUnitCalendarValue=upperTickUnitCalendarValue;
                	}else {
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
                    }
                    else {
                        angle = -Math.PI / 2.0;
                    }
                }
                else {
                    if (edge == RectangleEdge.TOP) {
                        anchor = TextAnchor.BOTTOM_CENTER;
                        rotationAnchor = TextAnchor.BOTTOM_CENTER;
                    }
                    else {
                        anchor = TextAnchor.TOP_CENTER;
                        rotationAnchor = TextAnchor.TOP_CENTER;
                    }
                }

                Tick tick = new DateTick(tickDate, tickLabel, anchor,
                        rotationAnchor, angle);
                result.add(tick);
                hasRolled = false;

                long currentTickTime = tickDate.getTime();
                tickDate = unit.addToDate(tickDate, this.getTimeZone());
                long nextTickTime = tickDate.getTime();
                for (int minorTick = 1; minorTick < minorTickSpaces;
                        minorTick++) {
                    long minorTickTime = currentTickTime
                            + (nextTickTime - currentTickTime)
                            * minorTick / minorTickSpaces;
                    if (getRange().contains(minorTickTime)
                            && (!isHiddenValue(minorTickTime))) {
                        result.add(new DateTick(TickType.MINOR,
                                new Date(minorTickTime), "",
                                TextAnchor.TOP_CENTER, TextAnchor.CENTER,
                                0.0));
                    }
                }

            }
            else {
                tickDate = unit.rollDate(tickDate, this.getTimeZone());
                hasRolled = true;
            }

        }
        return result;

    }

    /**
     * Corrects the given tick date for the position setting.
     *
     * @param time  the tick date/time.
     * @param unit  the tick unit.
     * @param position  the tick position.
     *
     * @return The adjusted time.
     */
    private Date correctTickDateForPosition(Date time, DateTickUnit unit,
            DateTickMarkPosition position) {
        Date result = time;
        if (unit.getUnitType().equals(DateTickUnitType.MONTH)) {
            result = calculateDateForPosition(new Month(time, this.getTimeZone(),
                    this.getLocale()), position);
        } else if (unit.getUnitType().equals(DateTickUnitType.YEAR)) {
            result = calculateDateForPosition(new Year(time, this.getTimeZone(),
                    this.getLocale()), position);
        }
        return result;
    }

    /**
     * Returns a {@link java.util.Date} corresponding to the specified position
     * within a {@link RegularTimePeriod}.
     *
     * @param period  the period.
     * @param position  the position ({@code null} not permitted).
     *
     * @return A date.
     */
    private Date calculateDateForPosition(RegularTimePeriod period,
            DateTickMarkPosition position) {
        Args.nullNotPermitted(period, "period");
        Date result = null;
        if (position == DateTickMarkPosition.START) {
            result = new Date(period.getFirstMillisecond());
        }
        else if (position == DateTickMarkPosition.MIDDLE) {
            result = new Date(period.getMiddleMillisecond());
        }
        else if (position == DateTickMarkPosition.END) {
            result = new Date(period.getLastMillisecond());
        }
        return result;

    }

    protected double findMaximumTickLabelHeight(List ticks, Graphics2D g2,
            Rectangle2D drawArea, boolean vertical) {
        return super.findMaximumTickLabelHeight(ticks, g2, drawArea, vertical)*2;//doubled as tick labels upper text are painted bellow main tick label
    }

    public TimePeriod getDateOnConfiguredTimePeriod(Class<? extends TimePeriod> timePeriodClass, Date date) {
		Class[] cArg = new Class[2];
        cArg[0] = Date.class;
        cArg[1] = Calendar.class;
        Calendar cal = Calendar.getInstance(getTimeZone());
		try {
			TimePeriod t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(date, cal);
			return t;
		}catch(InvocationTargetException e) {
			try {
				TimePeriod t = null;
		        cal.set(1900, 0, 1, 0, 0, 0);
				if(date.before(cal.getTime())){
					t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(cal.getTime(), cal);
				}
		        cal.set(9999, 12, 31, 23, 59, 59);
				if(date.after(cal.getTime())){
					t = timePeriodClass.getDeclaredConstructor(cArg).newInstance(cal.getTime(), cal);
				}
				return t;
			}catch(InvocationTargetException | InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e2) {
				return null;
			}
		}catch( InstantiationException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e){
			System.out.println("ISO8601DateParse:"+date);
			e.printStackTrace();
			return null;
		}
	}

    public String ISO8601DateFormat(Date date) {
		synchronized(ISO8601DATEFORMAT) {
			return ISO8601DATEFORMAT.format(date);
		}
	}


    public Date ISO8601DateParse(String strDate) {
		try {
			synchronized(ISO8601DATEFORMAT) {
				return ISO8601DATEFORMAT.parse(strDate);
			}
		} catch (ParseException|NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void setTimeZone(TimeZone zone) {
		synchronized(ISO8601DATEFORMAT) {
			ISO8601DATEFORMAT.setTimeZone(zone);
		}
		
		for(DateFormat f: dateFormaters.values()) {
			f.setCalendar(Calendar.getInstance(zone));
		}
		
		super.setTimeZone(zone);
	}
	
	
	public DateFormat getLongDateFormaterTickUnit(DateTickUnit dateTickUnit) {
		return dateFormaters.get(dateTickUnit.getUnitType());
	}

	public String dateToString(Date date) {
		synchronized(ISO8601DATEFORMAT) {
			return ISO8601DATEFORMAT.format(date);
		}
	}

    /**
     * Sets the range for the axis, if requested, sends an
     * {@link AxisChangeEvent} to all registered listeners.  As a side-effect,
     * the auto-range flag is set to {@code false} (optional).
     *
     * @param range  the range ({@code null} not permitted).
     * @param turnOffAutoRange  a flag that controls whether or not the auto
     *                          range is turned off.
     * @param notify  a flag that controls whether or not listeners are
     *                notified.
     */
    @Override
    public void setRange(Range range, boolean turnOffAutoRange,
                         boolean notify) {
        Args.nullNotPermitted(range, "range");
        // usually the range will be a DateRange, but if it isn't do a
        // conversion...
        if (!(range instanceof DateRange)) {
            range = new DateRange(range);
        }
        
        if(getTimeZone()!=null) {
            Calendar cal = Calendar.getInstance(getTimeZone());
            cal.set(1900, 0, 1,0,0,0);
            if(((DateRange)range).getLowerMillis()<cal.getTimeInMillis()) {
                range = new DateRange(cal.getTime(), ((DateRange)range).getUpperDate());
            }
            cal.set(9999, 11, 31,23,59,59);
            if(((DateRange)range).getUpperMillis()>cal.getTimeInMillis()) {
                range = new DateRange(((DateRange)range).getLowerDate(),cal.getTime());
            }
            
            super.setRange(range, turnOffAutoRange, notify);
        }else {
            super.setRange(range, turnOffAutoRange, notify);
        }
    }
    
    public DateRange putMargin(DateRange range) {
    	long margin = (range.getUpperMillis()-range.getLowerMillis())*2/100;
    	
    	return new DateRange(range.getLowerMillis()-margin,range.getUpperMillis()+margin);
    }


    /**
     * Adjusts visible range to guarantee that the current minimum and maximum
     * visible x values are visible with the minimum and maximum dates informed 
     *
     * @param min  the minimum date of the new range to guarantee visibility
     * @param max  the maximum date of the new range to guarantee visibility
     */
	public void guaranteeShowRange(Date min, Date max) {
		DateRange range = (DateRange) getRange();
		DateRange newRange = null;
		System.out.println(range.getLowerMillis());
		if(range.getLowerMillis()>min.getTime()) {
			if(range.getUpperMillis()<max.getTime()) {
				newRange = new DateRange(min.getTime(),max.getTime());
			}else {
				newRange = new DateRange(min.getTime(),range.getUpperMillis());
			}
		}else {
			if(range.getUpperMillis()<max.getTime()) {				
				newRange = new DateRange(range.getLowerMillis(),max.getTime());
			}else {
				return;
			}
		}

		newRange = putMargin(newRange);

		setRange(newRange);
	}

	public IpedChartsPanel getIpedChartsPanel() {
		return ipedChartsPanel;
	}

}