package iped.viewers.timelinegraph;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.XYDataset;

public class IpedStackedXYBarRenderer extends StackedXYBarRenderer{
	HashMap<Dataset, HashSet<Integer>> invisibleSeries = new HashMap<Dataset, HashSet<Integer>>(); 
	HashMap<Dataset, HashSet<Integer>> invisibleSeriesInLegend = new HashMap<Dataset, HashSet<Integer>>();


	public Paint getItemPaint(int row, int column) {
		IpedCombinedDomainXYPlot rootPlot = (IpedCombinedDomainXYPlot) getPlot().getRootPlot();
		
        return rootPlot.getLegends().getSeriesPaint((String) getPlot().getDataset().getSeriesKey(row));
	}

	public void drawItem(Graphics2D g2, XYItemRendererState state, Rectangle2D dataArea, PlotRenderingInfo info,
			XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset, int series, int item,
			CrosshairState crosshairState, int pass) {
		plot.getRenderer().setPlot(plot);
		super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series, item, crosshairState,
				pass);
	}

    /**
     * Returns a boolean that indicates whether or not the specified series
     * should be drawn.  In fact this method should be named 
     * lookupSeriesVisible() to be consistent with the other series
     * attributes and avoid confusion with the getSeriesVisible() method.
     *
     * @param series  the series index.
     *
     * @return A boolean.
     */
    public boolean isSeriesVisible(int series) {
    	HashSet<Integer> invisibles = invisibleSeries.get(getPlot().getDataset());
    	if(invisibles==null) {
    		return true;
    	}else {
        	if(invisibles.contains(series)) {
        		return false;
        	}else{
        		return true;
        	}
    	}
    }

    public Boolean getSeriesVisible(int series) {
    	HashSet<Integer> invisibles = invisibleSeries.get(getPlot().getDataset());
    	if(invisibles==null) {
    		return true;
    	}else {
        	if(invisibles.contains(series)) {
        		return false;
        	}else{
        		return true;
        	}
    	}
    }

	public void setSeriesVisible(int series, Boolean visible) {
		XYPlot xyPlot = getPlot();
    	HashSet<Integer> invisibles = invisibleSeries.get(xyPlot.getDataset());
    	if(invisibles==null) {
    		invisibles = new HashSet<Integer>();
    		invisibleSeries.put(xyPlot.getDataset(), invisibles);
    	}
    	if(visible) {
    		invisibles.remove(series);
    	}else {
    		invisibles.add(series);
    	}
	}

	public void setSeriesVisible(int series, Boolean visible, boolean notify) {
		setSeriesVisible(series, visible);
		if(notify) {
			fireChangeEvent();
		}
	}
}
