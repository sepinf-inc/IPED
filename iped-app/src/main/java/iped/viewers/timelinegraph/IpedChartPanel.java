package iped.viewers.timelinegraph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
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

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotRenderingInfo;

public class IpedChartPanel extends ChartPanel implements KeyListener{
	Rectangle2D filterIntervalRectangle;
	Point2D filterIntervalPoint;
	Color filterIntervalFillPaint;

    IpedChartsPanel ipedChartsPanel = null;
	
    private int filterMask = InputEvent.SHIFT_DOWN_MASK;
	
	boolean useBuffer;
	private double filterTriggerDistance;

	ArrayList<Date[]> definedFilters = new ArrayList<Date[]>();

	Date startFilterDate = null;
	Date endFilterDate = null;
	private Stroke filterLimitStroke;
	private AffineTransform affineTransform;
	Date[] mouseOverDates = null;

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
	    
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		int mods = e.getModifiersEx();
		if ((mods & this.filterMask) == this.filterMask){
            setCursor(Cursor.getPredefinedCursor(
                    Cursor.E_RESIZE_CURSOR));
		}else {
            setCursor(Cursor.getDefaultCursor());
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
        			startFilterDate = DateUtil.removeFromDatePart(startFilterDate, Calendar.DAY_OF_MONTH);
        			
        			Date[] dates = findDefinedFilterDates(startFilterDate);
        			if(dates!=null) {
        				startFilterDate = dates[0];
        				removeFilter(dates);

        				//dates[1]=new Date(dates[1].getTime()+1);
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
            endFilterDate = DateUtil.lastdateFromDatePart(endFilterDate, Calendar.DAY_OF_MONTH);
            
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
           			ipedChartsPanel.selectItemsOnInterval(filterDates[0],filterDates[1],false);

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

            String strStartDate = iped.utils.DateUtil.dateToString(startFilterDate);
            String strEndDate = iped.utils.DateUtil.dateToString(endFilterDate); 
            g2.drawString(strStartDate, (int) this.filterIntervalRectangle.getMinX()+2, (int) this.filterIntervalRectangle.getMinY()+g2.getFontMetrics().getHeight()+2);
            g2.drawString(strEndDate, (int) this.filterIntervalRectangle.getMaxX()+2, (int) this.filterIntervalRectangle.getMinY()+g2.getFontMetrics().getHeight()+2);
            
            if (xor) {
                // Reset to the default 'overwrite' mode
                g2.setPaintMode();
            }
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
		ipedChartsPanel.unselectItemsOnInterval(removedDates[0],removedDates[1],false);
		for (Date[] dates : definedFilters) {
			ipedChartsPanel.selectItemsOnInterval(dates[0],dates[1],false);
		}
	}
	
	public void removeAllFilters() {
		for (Date[] removedDates : definedFilters) {
			ipedChartsPanel.unselectItemsOnInterval(removedDates[0],removedDates[1],false);
		}
		definedFilters.clear();
	}

	public IpedChartsPanel getIpedChartsPanel() {
		return ipedChartsPanel;
	}

}
