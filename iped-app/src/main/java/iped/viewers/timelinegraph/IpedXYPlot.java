package iped.viewers.timelinegraph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.Date;
import java.util.HashSet;

import org.jfree.chart.LegendItem;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;

public class IpedXYPlot extends XYPlot{

	IpedChartPanel ipedChartPanel;
	
	private Stroke filterLimitStroke;
	private AffineTransform affineTransform;
	Color filterIntervalFillPaint;

	public IpedXYPlot(IpedChartPanel ipedChartPanel, TimeTableCumulativeXYDataset timeTableCumulativeXYDataset, DateAxis domainAxis,
			NumberAxis rangeAxis, XYItemRenderer renderer) {		
		super(timeTableCumulativeXYDataset, domainAxis, rangeAxis, renderer);
		this.ipedChartPanel = ipedChartPanel;
		
	    this.filterIntervalFillPaint = new Color(0, 0, 255, 43);
	    this.filterLimitStroke = new BasicStroke(3,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER, 1);
	    this.affineTransform = new AffineTransform();
	    this.affineTransform.rotate(Math.toRadians(90), 0, 0);
	}

	@Override
	public void drawOutline(Graphics2D g2, Rectangle2D area) {
		super.drawOutline(g2, area);
	}

	@Override
	public void drawBackground(Graphics2D g2, Rectangle2D area) {
		// TODO Auto-generated method stub
		super.drawBackground(g2, area);

		drawDefinedFiltersRectangles(g2, area, false);
	}

	@Override
	protected void drawNoDataMessage(Graphics2D g2, Rectangle2D area) {
		super.drawNoDataMessage(g2, area);
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
    private void drawDefinedFilterRectangles(Date[] dates, Graphics2D g2, Rectangle2D area, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
           g2.setXORMode(Color.GRAY);
       }
       g2.setPaint(ipedChartPanel.filterIntervalFillPaint);

       int xstart = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[0],  area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());
       int xend = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[1],  area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());
       
       double minX = area.getMinX();
       double maxX = area.getMaxX();
       double maxY = area.getMaxY();

       int x = (int) Math.max(minX, xstart);
       int y = (int) area.getMinY();
       int w = (int) Math.min(xend-x,
               maxX - x);
       double h = area.getHeight();

       Rectangle2D rectangle2d = new Rectangle2D.Double(x, y, w, h);
       
       g2.fill(rectangle2d);
       
       drawDefinedFiltersDates(dates, g2, area, xor);
       
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
    public void drawDefinedFiltersRectangles(Graphics2D g2, Rectangle2D area, boolean xor) {
    	for (Date[] dates : ipedChartPanel.definedFilters) {
    		drawDefinedFilterRectangles(dates, g2, area, xor);
   		}
    }
	

	private void drawDefinedFiltersDates(Date[] dates, Graphics2D g2, Rectangle2D area, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
           g2.setXORMode(Color.GRAY);
       }

        String strStartDate = iped.utils.DateUtil.dateToString(dates[0]); 
        String strEndDate = iped.utils.DateUtil.dateToString(dates[1]);
        
        int xstart = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[0],  area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());
        int xend = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[1],  area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());

        double minX = area.getMinX();
        double maxX = area.getMaxX();
        double maxY = area.getMaxY();

        int x = (int) Math.max(minX, xstart);
        int y = (int) area.getMinY();
        int w = (int) Math.min(xend-x,
                maxX - x);
        double h = area.getHeight();

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
    private void drawDefinedFiltersDates(Graphics2D g2, Rectangle2D area, boolean xor) {
    	for (Date[] dates : ipedChartPanel.definedFilters) {
    		drawDefinedFiltersDates(dates, g2, area, xor);
		}
    	
    }
}
