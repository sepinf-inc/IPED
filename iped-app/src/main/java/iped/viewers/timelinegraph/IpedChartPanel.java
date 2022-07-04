package iped.viewers.timelinegraph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.entity.AxisEntity;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.JFreeChartEntity;
import org.jfree.chart.entity.LegendItemEntity;
import org.jfree.chart.entity.PlotEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

import iped.app.ui.App;
import iped.viewers.timelinegraph.popups.DataItemPopupMenu;
import iped.viewers.timelinegraph.popups.PlotPopupMenu;
import iped.viewers.timelinegraph.popups.SeriesAxisPopupMenu;
import iped.viewers.timelinegraph.popups.LegendItemPopupMenu;
import iped.viewers.timelinegraph.popups.TimePeriodSelectionPopupMenu;
import iped.viewers.timelinegraph.popups.TimelineFilterSelectionPopupMenu;
import iped.viewers.timelinegraph.swingworkers.SelectWorker;

public class IpedChartPanel extends ChartPanel implements KeyListener{
	Rectangle2D filterIntervalRectangle;
	Point2D filterIntervalPoint;
	Color filterIntervalFillPaint;
	
	int lastMouseMoveX=-1;

    IpedChartsPanel ipedChartsPanel = null;
	
    private int filterMask = InputEvent.SHIFT_DOWN_MASK;
	
	boolean useBuffer;
	private double filterTriggerDistance;

	ArrayList<Date[]> definedFilters = new ArrayList<Date[]>();
	ArrayList<String> excludedEvents = new ArrayList<String>();

	Date startFilterDate = null;
	Date endFilterDate = null;
	private Stroke filterLimitStroke;
	private AffineTransform affineTransform;
	Date[] mouseOverDates = null;

    TimelineFilterSelectionPopupMenu timelineSelectionPopupMenu = null;
    PlotPopupMenu domainPopupMenu = null;
    LegendItemPopupMenu legendItemPopupMenu = null;
    DataItemPopupMenu itemPopupMenu = null;
    TimePeriodSelectionPopupMenu timePeriodSelectionPopupMenu = null;
    SeriesAxisPopupMenu seriesAxisPopupMenu = null;
	boolean splitByBookmark=true;
	boolean splitByCategory=false;
	
	public IpedChartPanel(JFreeChart chart, IpedChartsPanel ipedChartsPanel) {
		super(chart, false);
		useBuffer = false;

		this.ipedChartsPanel = ipedChartsPanel;
		this.setFocusable(true);
		this.addKeyListener(this);
	    this.filterIntervalFillPaint = new Color(0, 0, 255, 43);
	    this.filterLimitStroke = new BasicStroke(3,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER, 1);
	    this.affineTransform = new AffineTransform();
	    this.affineTransform.rotate(Math.toRadians(90), 0, 0);
	    
		timelineSelectionPopupMenu = new TimelineFilterSelectionPopupMenu(this);
		domainPopupMenu = new PlotPopupMenu(this, ipedChartsPanel.getResultsProvider());
		legendItemPopupMenu = new LegendItemPopupMenu(this);
        itemPopupMenu = new DataItemPopupMenu(this);
    	timePeriodSelectionPopupMenu = new TimePeriodSelectionPopupMenu(ipedChartsPanel);
    	seriesAxisPopupMenu = new SeriesAxisPopupMenu(this);
        
        IpedChartPanel self = this;
	
        this.addChartMouseListener(new ChartMouseListener() {
			@Override
			public void chartMouseMoved(ChartMouseEvent event) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void chartMouseClicked(ChartMouseEvent event) {
				ChartEntity ce = event.getEntity();
				int x = event.getTrigger().getX();
				if(ce instanceof XYItemEntity) {
					XYItemEntity ie = ((XYItemEntity) ce);
					itemPopupMenu.setChartEntity(ie);
					itemPopupMenu.show(event.getTrigger().getComponent(), x, event.getTrigger().getY());
					
					ArrayList<XYItemEntity> entityList = new ArrayList<XYItemEntity>();
					EntityCollection entities = self.getChartRenderingInfo().getEntityCollection();		            
		            if (entities != null) {
		                int entityCount = entities.getEntityCount();
		                for (int i = entityCount - 1; i >= 0; i--) {
		                    ChartEntity entity = (ChartEntity) entities.getEntity(i);
		                    if(entity instanceof XYItemEntity) {
			                    if (entity.getArea().getBounds().getMaxX()>x && entity.getArea().getBounds().getMinX()<x) {
			                    	entityList.add((XYItemEntity)entity);
			                    }
		                    }
		                }
		            }
					itemPopupMenu.setChartEntityList(entityList);
					
				}

				if(ce instanceof PlotEntity) {
					PlotEntity ie = ((PlotEntity) ce);
					XYPlot plot = (XYPlot) ie.getPlot();

					Date[] filteredDate = self.getDefinedFilter(event.getTrigger().getX());
					if(filteredDate!=null) {
						timelineSelectionPopupMenu.setDates(filteredDate);
						timelineSelectionPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
					}else {
						domainPopupMenu.setDate(new Date((long) ipedChartsPanel.getDomainAxis().java2DToValue(event.getTrigger().getX(), self.getScreenDataArea(), plot.getDomainAxisEdge())));
						domainPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
					}
				}
				
				if(ce instanceof AxisEntity) {
					if(((AxisEntity) ce).getAxis() instanceof DateAxis) {
						timePeriodSelectionPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
					}else {
						seriesAxisPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
					}
				}
				if(ce instanceof JFreeChartEntity) {
					if(event.getTrigger().getX()<self.getScreenDataArea().getMinX()) {
						seriesAxisPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
					}
				}
				
				if(ce instanceof LegendItemEntity) {
					LegendItemEntity le = (LegendItemEntity) ce;
					
					legendItemPopupMenu.setLegendItemEntity(le);
					legendItemPopupMenu.show(event.getTrigger().getComponent(), event.getTrigger().getX(), event.getTrigger().getY());
				}
			}
		});
        
        this.addMouseWheelListener(new IpedTimelineMouseWheelHandler(this));
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		int mods = e.getModifiersEx();
		int x=e.getX();
		if ((mods & this.filterMask) == this.filterMask){
            setCursor(Cursor.getPredefinedCursor(
                    Cursor.E_RESIZE_CURSOR));
		}else {
            setCursor(Cursor.getDefaultCursor());
            Graphics2D g2 = (Graphics2D) getGraphics();
            if(lastMouseMoveX>=0) {
                drawDateCursor(g2, lastMouseMoveX, true);
            }
            drawDateCursor(g2, x, true);
		}
		super.mouseMoved(e);
	}

	public Date[] findDefinedFilterDates(Date date) {
		for (Date[] dates : definedFilters) {
			if(dates[0].getTime()-1<=date.getTime() && dates[1].getTime()+1>=date.getTime()) {
				return dates;
			}
		}
		return null;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		int mods = e.getModifiersEx();
    	if(((mods & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK)) {
    		if ((mods & this.filterMask) == this.filterMask){
                setCursor(Cursor.getPredefinedCursor(
                        Cursor.E_RESIZE_CURSOR));
            	
        		if (this.filterIntervalRectangle == null) {
        			startFilterDate = new Date((long) ipedChartsPanel.domainAxis.java2DToValue(e.getX(), this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge()));
        			startFilterDate = removeFromDatePart(startFilterDate, Calendar.DAY_OF_MONTH);
        			
        			Date[] dates = findDefinedFilterDates(startFilterDate);
        			if(dates!=null) {
        				startFilterDate = dates[0];
        				removeFilter(dates);

        				//already exists an interval continuos with this so edits him instead of creating a new one
        	            Graphics2D g2 = (Graphics2D) getGraphics();

        				ipedChartsPanel.paintImmediately(this.getScreenDataArea().getBounds());
        			}

        			int x = (int) ipedChartsPanel.domainAxis.dateToJava2D(startFilterDate,  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());

        			Rectangle2D screenDataArea = getScreenDataArea(x, e.getY());

                    if (screenDataArea != null) {
                        this.filterIntervalPoint = getPointInRectangle(x, e.getY(),
                                screenDataArea);
                    }
                    else {
                        this.filterIntervalPoint = null;
                    }
                }
        		return;
        	}
        }

	}

	@Override
	public void mouseDragged(MouseEvent e) {
		int mods = e.getModifiersEx();
        if ((mods & this.filterMask) == this.filterMask) {
            // if no initial point was set, ignore dragging...
            if (this.filterIntervalPoint == null) {
                return;
            }

            Graphics2D g2 = (Graphics2D) getGraphics();

            // erase the previous zoom rectangle (if any).  We only need to do
            // this is we are using XOR mode, which we do when we're not using
            // the buffer (if there is a buffer, then at the end of this method we
            // just trigger a repaint)
            if (!this.useBuffer) {
                drawFilterIntervalRectangle(g2, true);
            }

            Rectangle2D scaledDataArea = getScreenDataArea(
                    (int) this.filterIntervalPoint.getX(), (int) this.filterIntervalPoint.getY());

            endFilterDate = new Date((long) ipedChartsPanel.domainAxis.java2DToValue(e.getX(), this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge()));
            endFilterDate = lastdateFromDatePart(endFilterDate, Calendar.DAY_OF_MONTH);
            
			Date[] dates = findDefinedFilterDates(endFilterDate);
			if(dates!=null) {
				removeFilter(dates);
				ipedChartsPanel.paintImmediately(this.getScreenDataArea().getBounds());
			}
            
			int x = (int) ipedChartsPanel.domainAxis.dateToJava2D(endFilterDate,  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());

            double xmax = Math.min(x, scaledDataArea.getMaxX());

            this.filterIntervalRectangle = new Rectangle2D.Double(
            		this.filterIntervalPoint.getX(), scaledDataArea.getMinY(),
                    xmax - this.filterIntervalPoint.getX(), scaledDataArea.getHeight());

            // Draw the new zoom rectangle...
            if (this.useBuffer) {
                repaint();
            }
            else {
                // with no buffer, we use XOR to draw the rectangle "over" the
                // chart...
            	drawFilterIntervalRectangle(g2, true);
            }
            
            g2.dispose();
    		
    		return;
        }

		super.mouseDragged(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		int mods = e.getModifiersEx();

        if ((mods & this.filterMask) != this.filterMask) {
    		// TODO Auto-generated method stub
    		super.mouseReleased(e);
        }

        if (this.filterIntervalRectangle != null) {
            boolean filterTrigger1 = Math.abs(e.getX()
                - this.filterIntervalPoint.getX()) >= this.filterTriggerDistance;

            if (filterTrigger1) {
                if ((e.getX() < this.filterIntervalPoint.getX())) {
                    restoreAutoBounds();
                }
                else {
                    Date[] filterDates = new Date[2];
                    filterDates[0]=startFilterDate;
                    filterDates[1]=endFilterDate;
                    definedFilters.add(filterDates);

					timelineSelectionPopupMenu.setDates(filterDates);
					timelineSelectionPopupMenu.show(e.getComponent(), e.getX(), e.getY());

                    mouseOverDates=filterDates;
                }
                this.filterIntervalPoint = null;
                this.filterIntervalRectangle = null;
            }
            else {
                // erase the zoom rectangle
                Graphics2D g2 = (Graphics2D) getGraphics();
                if (this.useBuffer) {
                    repaint();
                }
                else {
                	drawFilterIntervalRectangle(g2, true);
                }
                g2.dispose();
                this.filterIntervalPoint = null;
                this.filterIntervalRectangle = null;
            }

            setCursor(Cursor.getDefaultCursor());
        }
        else if (e.isPopupTrigger()) {
            if (this.getPopupMenu() != null) {
                displayPopupMenu(e.getX(), e.getY());
            }
        }
	}
	
	public void addFilter(Date startDate, Date endDate) {
        Date[] filterDates = new Date[2];
        filterDates[0]=startDate;
        filterDates[1]=endDate;
        definedFilters.add(filterDates);
	}

    /**
     * Draws zoom rectangle (if present).
     * The drawing is performed in XOR mode, therefore
     * when this method is called twice in a row,
     * the second call will completely restore the state
     * of the canvas.
     *
     * @param g2 the graphics device.
     * @param xor  use XOR for drawing?
     */
    private void drawFilterIntervalRectangle(Graphics2D g2, boolean xor) {
        if (this.filterIntervalRectangle != null) {
            if (xor) {
                 // Set XOR mode to draw the zoom rectangle
                g2.setXORMode(Color.GRAY);
            }
            g2.setPaint(this.filterIntervalFillPaint);
            g2.fill(this.filterIntervalRectangle);
            g2.setPaint(Color.BLACK);
            g2.setStroke(this.filterLimitStroke);
            g2.setFont(g2.getFont().deriveFont(affineTransform));//rotate text 90
            g2.drawLine((int) this.filterIntervalRectangle.getMaxX(),(int) this.filterIntervalRectangle.getMinY(),(int) this.filterIntervalRectangle.getMaxX(),(int) this.filterIntervalRectangle.getMaxY());

            String strStartDate = ipedChartsPanel.getDomainAxis().ISO8601DateFormat(startFilterDate);
            String strEndDate = ipedChartsPanel.getDomainAxis().ISO8601DateFormat(endFilterDate); 
            g2.drawString(strStartDate, (int) this.filterIntervalRectangle.getMinX()+2, (int) this.filterIntervalRectangle.getMinY()+g2.getFontMetrics().getHeight()+2);
            g2.drawString(strEndDate, (int) this.filterIntervalRectangle.getMaxX()+2, (int) this.filterIntervalRectangle.getMinY()+g2.getFontMetrics().getHeight()+2);
            
            if (xor) {
                // Reset to the default 'overwrite' mode
                g2.setPaintMode();
            }
        }
    }

    public Date removeFromDatePart(Date date, int fromDatePart) {
        Calendar cal = Calendar.getInstance(ipedChartsPanel.getTimeZone());
        cal.setTime(date);
        
        if(fromDatePart==Calendar.DAY_OF_MONTH) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return cal.getTime();
    }

    public Date lastdateFromDatePart(Date date, int fromDatePart) {
        Calendar cal = Calendar.getInstance(ipedChartsPanel.getTimeZone());
        cal.setTime(date);
        
        if(fromDatePart==Calendar.DAY_OF_MONTH) {
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
        }
        return cal.getTime();
    }
    

    /**
     * Draws zoom rectangle (if present).
     * The drawing is performed in XOR mode, therefore
     * when this method is called twice in a row,
     * the second call will completely restore the state
     * of the canvas.
     *
     * @param g2 the graphics device.
     * @param xor  use XOR for drawing?
     */
    private void drawDateCursor(Graphics2D g2, int x, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
           g2.setXORMode(Color.GRAY);
       }
       g2.setPaint(Color.BLACK);
       g2.setStroke(this.filterLimitStroke);
       g2.setFont(g2.getFont().deriveFont(affineTransform));//rotate text 90
       
       Rectangle2D screenDataArea = getScreenDataArea(
               x,
               2);
       
       double maxX = screenDataArea.getMaxX();
       double minX = screenDataArea.getMinX();
       if((x>minX)&&(x<maxX)) {
           double minY = screenDataArea.getMinY();
           double maxY = screenDataArea.getMaxY();

           double h = screenDataArea.getHeight();
           g2.drawLine(x,(int)minY,x,(int)maxY);

           Date correspondingDate = new Date((long) ipedChartsPanel.domainAxis.java2DToValue(x, this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge()));
           correspondingDate = removeFromDatePart(correspondingDate, Calendar.DAY_OF_MONTH);

           String strDate = ipedChartsPanel.getDomainAxis().ISO8601DateFormat(correspondingDate); 
           g2.drawString(strDate, x+2, (int) minY+g2.getFontMetrics().getHeight()+2);
       }

       lastMouseMoveX=x;

       if (xor) {
           // Reset to the default 'overwrite' mode
           g2.setPaintMode();
       }
    }
    
    
	private void drawDefinedFiltersDates(Date[] dates, Graphics2D g2, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
           g2.setXORMode(Color.GRAY);
       }

        String strStartDate = iped.utils.DateUtil.dateToString(dates[0]); 
        String strEndDate = iped.utils.DateUtil.dateToString(dates[1]);
        
        int xstart = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[0],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());
        int xend = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[1],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());

        Rectangle2D screenDataArea = getScreenDataArea(
                xend,
                2);

        double minX = screenDataArea.getMinX();
        double maxX = screenDataArea.getMaxX();
        double maxY = screenDataArea.getMaxY();

        int x = (int) Math.max(minX, xstart);
        int y = (int) screenDataArea.getMinY();
        int w = (int) Math.min(xend-x,
                maxX - x);
        double h = screenDataArea.getHeight();

        Rectangle2D rectangle2d = new Rectangle2D.Double(x, y, w, h);
        
        g2.setPaint(Color.BLACK);
        g2.setStroke(this.filterLimitStroke);
        g2.setFont(g2.getFont().deriveFont(affineTransform));//rotate text 90
        g2.drawString(strStartDate, (int) rectangle2d.getMinX()+2, (int) rectangle2d.getMinY()+g2.getFontMetrics().getHeight()+2);
        g2.drawString(strEndDate, (int) rectangle2d.getMaxX()+2, (int) rectangle2d.getMinY()+g2.getFontMetrics().getHeight()+2);
       
       if (xor) {
           // Reset to the default 'overwrite' mode
           g2.setPaintMode();
       }
	}

	/**
     * Draws defined filters dates text (if present).
     * The drawing is performed in XOR mode, therefore
     * when this method is called twice in a row,
     * the second call will completely restore the state
     * of the canvas.
     *
     * @param g2 the graphics device.
     * @param xor  use XOR for drawing?
     */
    private void drawDefinedFiltersDates(Graphics2D g2, boolean xor) {
    	for (Date[] dates : this.definedFilters) {
    		drawDefinedFiltersDates(dates, g2, xor);
		}
    	
    }    

    /**
     * Returns the data area (the area inside the axes) for the plot or subplot,
     * with the current scaling applied.
     *
     * @param x  the x-coordinate (for subplot selection).
     * @param y  the y-coordinate (for subplot selection).
     *
     * @return The scaled data area.
     */
    public Rectangle2D getScreenDataArea(int x, int y) {
        PlotRenderingInfo plotInfo = this.getChartRenderingInfo().getPlotInfo();
        Rectangle2D result;
        if (plotInfo.getSubplotCount() == 0) {
            result = getScreenDataArea();
        }
        else {
            // get the origin of the zoom selection in the Java2D space used for
            // drawing the chart (that is, before any scaling to fit the panel)
            Point2D selectOrigin = translateScreenToJava2D(new Point(x, y));
            int subplotIndex = plotInfo.getSubplotIndex(selectOrigin);
            if (subplotIndex == -1) {
                return scale(plotInfo.getSubplotInfo(0).getDataArea());
            }
            result = scale(plotInfo.getSubplotInfo(subplotIndex).getDataArea());
        }
        return result;
    }
    
    /**
     * Draws defined filters rectangles (if present).
     * The drawing is performed in XOR mode, therefore
     * when this method is called twice in a row,
     * the second call will completely restore the state
     * of the canvas.
     *
     * @param g2 the graphics device.
     * @param xor  use XOR for drawing?
     */
    private void drawDefinedFilterRectangles2(Date[] dates, Graphics2D g2, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
           g2.setXORMode(Color.GRAY);
       }
       g2.setPaint(this.filterIntervalFillPaint);

       int xstart = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[0],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());
       int xend = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[1],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());

       Rectangle2D screenDataArea = getScreenDataArea(
               xend,
               2);

       double minX = screenDataArea.getMinX();
       double maxX = screenDataArea.getMaxX();
       double maxY = screenDataArea.getMaxY();

       int x = (int) Math.max(minX, xstart);
       int y = (int) screenDataArea.getMinY();
       int w = (int) Math.min(xend-x,
               maxX - x);
       double h = screenDataArea.getHeight();

       Rectangle2D rectangle2d = new Rectangle2D.Double(x, y, w, h);
       
       g2.fill(rectangle2d);
       
       drawDefinedFiltersDates(dates, g2, xor);

       
       if (xor) {
           // Reset to the default 'overwrite' mode
           g2.setPaintMode();
       }
    }
    
    /**
     * Draws defined filters rectangles (if present).
     * The drawing is performed in XOR mode, therefore
     * when this method is called twice in a row,
     * the second call will completely restore the state
     * of the canvas.
     *
     * @param g2 the graphics device.
     * @param xor  use XOR for drawing?
     */
    public void drawDefinedFiltersRectangles(Graphics2D g2, boolean xor) {
    	for (Date[] dates : this.definedFilters) {
    		drawDefinedFilterRectangles2(dates, g2, xor);
   		}
    }
    
    public Date[] getDefinedFilter(int x) {
    	for (Date[] dates : definedFilters) {
            int xstart = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[0],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());
            int xend = (int) ipedChartsPanel.domainAxis.dateToJava2D(dates[1],  this.getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());
            if((xstart<=x)&& (x<=xend)) {
            	return dates;
            }
		}
    	return null;
    }

    /**
     * Returns a point based on (x, y) but constrained to be within the bounds
     * of the given rectangle.  This method could be moved to JCommon.
     *
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param area  the rectangle ({@code null} not permitted).
     *
     * @return A point within the rectangle.
     */
    private Point2D getPointInRectangle(int x, int y, Rectangle2D area) {
        double xx = Math.max(area.getMinX(), Math.min(x, area.getMaxX()));
        double yy = Math.max(area.getMinY(), Math.min(y, area.getMaxY()));
        return new Point2D.Double(xx, yy);
    }

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            setCursor(Cursor.getPredefinedCursor(
                    Cursor.E_RESIZE_CURSOR));
        }
	}

	@Override
	public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
        	setCursor(Cursor.getDefaultCursor());
        }
	}
	
	public void removeFilter(Date[] removedDates) {
		definedFilters.remove(removedDates);
		SelectWorker sw = new SelectWorker(ipedChartsPanel.getDomainAxis(),ipedChartsPanel.resultsProvider, removedDates[0], removedDates[1], false, false);
		for (Date[] dates : definedFilters) {
			ipedChartsPanel.selectItemsOnInterval(dates[0],dates[1],false);
		}
	}

	public void removeAllFilters() {
		for (Date[] removedDates : definedFilters) {
			SelectWorker sw = new SelectWorker(ipedChartsPanel.getDomainAxis(),ipedChartsPanel.resultsProvider, removedDates[0], removedDates[1], false, false);
		}
		definedFilters.clear();
		this.repaint();
	}

	public IpedChartsPanel getIpedChartsPanel() {
		return ipedChartsPanel;
	}

	@Override
	public void mouseExited(MouseEvent e) {
        Graphics2D g2 = (Graphics2D) getGraphics();
        drawDateCursor(g2, lastMouseMoveX, true);
		lastMouseMoveX=-1;
		super.mouseExited(e);
	}

	@Override
	public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) getGraphics();
        drawDateCursor(g2, lastMouseMoveX, true);
		lastMouseMoveX=-1;
		super.paint(g);
	}

	@Override
	public Graphics getGraphics() {
		Graphics g = super.getGraphics();
		
		if(g instanceof IpedGraphicsWrapper) {
			return g;
		}else {
			return new IpedGraphicsWrapper((Graphics2D) g);
		}
	}

	public boolean getSplitByBookmark() {
		return splitByBookmark;
	}

	public void setSplitByBookmark(boolean breakByBookmark) {
		if(breakByBookmark) {
			splitByCategory=false;
		}
		this.splitByBookmark = breakByBookmark;
	}

	public boolean getSplitByCategory() {
		return splitByCategory;
	}

	public void setSplitByCategory(boolean breakByCategory) {
		if(breakByCategory) {
			splitByBookmark=false;
		}
		this.splitByCategory = breakByCategory;
	}

	public ArrayList<Date[]> getDefinedFilters() {
		return definedFilters;
	}

	public void setDefinedFilters(ArrayList<Date[]> definedFilters) {
		this.definedFilters = definedFilters;
	}

	public void filterSelection() {
		this.getIpedChartsPanel().setInternalUpdate(true);
		App app = (App) this.getIpedChartsPanel().getResultsProvider();			
		app.getAppListener().updateFileListing();
		app.setDockablesColors();
	}

	public ArrayList<String> getExcludedEvents() {
		return excludedEvents;
	}

	public void setExcludedEvents(ArrayList<String> excludedEvents) {
		this.excludedEvents = excludedEvents;
	}

	public boolean hasNoFilter() {
		return (definedFilters.size()==0)&&(excludedEvents.size()==0);
	}

}
