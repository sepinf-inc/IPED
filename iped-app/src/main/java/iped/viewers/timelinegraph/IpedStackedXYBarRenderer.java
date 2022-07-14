package iped.viewers.timelinegraph;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataset;

public class IpedStackedXYBarRenderer extends StackedXYBarRenderer{
	HashMap<Dataset, HashSet<Integer>> invisibleSeries = new HashMap<Dataset, HashSet<Integer>>(); 
	HashMap<Dataset, HashSet<Integer>> invisibleSeriesInLegend = new HashMap<Dataset, HashSet<Integer>>();
	
	IpedChartsPanel ipedChartsPanel;

	public IpedStackedXYBarRenderer(IpedChartsPanel ipedChartsPanel) {
		super();
		this.ipedChartsPanel = ipedChartsPanel;
	}

	public Paint getItemPaint(int row, int column) {
		IpedCombinedDomainXYPlot rootPlot = (IpedCombinedDomainXYPlot) getPlot().getRootPlot();
		
        return rootPlot.getLegends().getSeriesPaint((String) getPlot().getDataset().getSeriesKey(row));
	}
/*
	public void drawItem(Graphics2D g2, XYItemRendererState state, Rectangle2D dataArea, PlotRenderingInfo info,
			XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset, int series, int item,
			CrosshairState crosshairState, int pass) {
		plot.getRenderer().setPlot(plot);
		super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series, item, crosshairState,
				pass);
	}
*/
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

	@Override
	public double getMargin() {
		double length = ipedChartsPanel.getDomainAxis().lengthToJava2D(ipedChartsPanel.getTimePeriodLength(), ipedChartsPanel.getChartPanel().getScreenDataArea(), ipedChartsPanel.combinedPlot.getDomainAxisEdge());
		return super.getMargin()/length;
	}

	@Override
	public int getPassCount() {
		return 1;
	}

	@Override
	public XYItemRendererState initialise(Graphics2D g2, Rectangle2D dataArea, XYPlot plot, XYDataset data,
			PlotRenderingInfo info) {
		XYItemRendererState state = super.initialise(g2, dataArea, plot, data, info);
		//state.setProcessVisibleItemsOnly(false);
		return state;
	}

    /**
     * Draws the visual representation of a single data item.
     *
     * @param g2  the graphics device.
     * @param state  the renderer state.
     * @param dataArea  the area within which the plot is being drawn.
     * @param info  collects information about the drawing.
     * @param plot  the plot (can be used to obtain standard color information
     *              etc).
     * @param domainAxis  the domain axis.
     * @param rangeAxis  the range axis.
     * @param dataset  the dataset.
     * @param series  the series index (zero-based).
     * @param item  the item index (zero-based).
     * @param crosshairState  crosshair information for the plot
     *                        ({@code null} permitted).
     * @param pass  the pass index.
     */
    @Override
    public void drawItem(Graphics2D g2, XYItemRendererState state,
            Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
            ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset,
            int series, int item, CrosshairState crosshairState, int pass) {

		plot.getRenderer().setPlot(plot);
		
        if (!getItemVisible(series, item)) {
            return;
        }

        IntervalXYDataset intervalDataset = (IntervalXYDataset) dataset;
        
        RectangleEdge edgeD = plot.getDomainAxisEdge();
        double startX = intervalDataset.getStartXValue(series, item);
        if (Double.isNaN(startX)) {
            return;
        }
        
        double endX = intervalDataset.getEndXValue(series, item);
        if (Double.isNaN(endX)) {
            return;
        }
        double translatedEndX = domainAxis.valueToJava2D(endX, dataArea, edgeD);
        if(translatedEndX<0) {
        	return;//out of chart visible area
        }
        double translatedStartX = domainAxis.valueToJava2D(startX, dataArea,
                edgeD);

        double dataAreaWidth = dataArea.getWidth();

        double translatedWidth = Math.max(1, Math.abs(translatedEndX
                - translatedStartX));

        if(translatedStartX>dataAreaWidth+dataAreaWidth) {
        	return;//out of chart visible area
        }

        double value = intervalDataset.getYValue(series, item);
        if (Double.isNaN(value)) {
            return;
        }

        double positiveBase = 0.0;
        double negativeBase = 0.0;

        for (int i = 0; i < series; i++) {
            double v = dataset.getYValue(i, item);
            if (!Double.isNaN(v) && isSeriesVisible(i)) {
                if (v > 0) {
                    positiveBase = positiveBase + v;
                }
                else {
                    negativeBase = negativeBase + v;
                }
            }
        }

        double translatedBase;
        double translatedValue;
        RectangleEdge edgeR = plot.getRangeAxisEdge();
        if (value > 0.0) {
            translatedBase = rangeAxis.valueToJava2D(positiveBase, dataArea,
                    edgeR);
            translatedValue = rangeAxis.valueToJava2D(positiveBase + value,
                    dataArea, edgeR);
        }
        else {
            translatedBase = rangeAxis.valueToJava2D(negativeBase, dataArea,
                    edgeR);
            translatedValue = rangeAxis.valueToJava2D(negativeBase + value,
                    dataArea, edgeR);
        }

        double translatedHeight = Math.abs(translatedValue - translatedBase);
        if (getMargin() > 0.0) {
            double cut = translatedWidth * getMargin();
            translatedWidth = translatedWidth - cut;
            translatedStartX = translatedStartX + cut / 2;
        }
        
        double cut = translatedWidth * 0.01;
        translatedWidth = translatedWidth - cut;
        translatedStartX = translatedStartX + cut / 2;
        
        Rectangle2D bar = null;
        PlotOrientation orientation = plot.getOrientation();
        if (orientation == PlotOrientation.HORIZONTAL) {
            bar = new Rectangle2D.Double(Math.min(translatedBase,
                    translatedValue), Math.min(translatedEndX,
                    translatedStartX), translatedHeight, translatedWidth);
        }
        else if (orientation == PlotOrientation.VERTICAL) {
            bar = new Rectangle2D.Double(Math.min(translatedStartX,
                    translatedEndX), Math.min(translatedBase, translatedValue),
                    translatedWidth, translatedHeight);
        } else {
            throw new IllegalStateException();
        }
        boolean positive = (value > 0.0);
        boolean inverted = rangeAxis.isInverted();
        RectangleEdge barBase;
        if (orientation == PlotOrientation.HORIZONTAL) {
            if (positive && inverted || !positive && !inverted) {
                barBase = RectangleEdge.RIGHT;
            }
            else {
                barBase = RectangleEdge.LEFT;
            }
        }
        else {
            if (positive && !inverted || !positive && inverted) {
                barBase = RectangleEdge.BOTTOM;
            }
            else {
                barBase = RectangleEdge.TOP;
            }
        }

        if (pass == 0) {
            if (getShadowsVisible()) {
                getBarPainter().paintBarShadow(g2, this, series, item, bar,
                        barBase, false);
            }
            getBarPainter().paintBar(g2, this, series, item, bar, barBase);

            // add an entity for the item...
            if (info != null) {
                EntityCollection entities = info.getOwner()
                        .getEntityCollection();
                if (entities != null) {
                    addEntity(entities, bar, dataset, series, item,
                            bar.getCenterX(), bar.getCenterY());
                }
            }
        }
    }

	@Override
	public boolean getShadowsVisible() {
		// TODO Auto-generated method stub
		return false;
	}
}
