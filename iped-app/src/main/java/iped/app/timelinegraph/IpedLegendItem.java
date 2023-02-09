package iped.app.timelinegraph;

import java.awt.Font;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.util.Map;

import org.jfree.chart.LegendItem;
import org.jfree.chart.title.LegendTitle;

public class IpedLegendItem extends LegendItem {
    IpedCombinedDomainXYPlot plot;

    public IpedLegendItem(String seriesKey, Paint paint, IpedCombinedDomainXYPlot plot) {
        super(seriesKey, paint);
        this.plot = plot;
    }

    @Override
    public Font getLabelFont() {
        if (plot.getIpedChartsPanel().getChartPanel().getExcludedEvents().contains(this.getSeriesKey())) {
            Font f = LegendTitle.DEFAULT_ITEM_FONT;
            Map attributes = f.getAttributes();
            attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
            return f.deriveFont(attributes);
        }
        return LegendTitle.DEFAULT_ITEM_FONT;
    }

    @Override
    public Shape getShape() {
        return super.getShape();
    }

}
