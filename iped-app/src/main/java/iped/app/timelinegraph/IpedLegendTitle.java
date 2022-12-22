package iped.app.timelinegraph;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListModel;

import org.jfree.chart.LegendItemSource;
import org.jfree.chart.block.Block;
import org.jfree.chart.block.BlockContainer;
import org.jfree.chart.block.BlockResult;
import org.jfree.chart.block.EntityBlockParams;
import org.jfree.chart.block.RectangleConstraint;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.entity.TitleEntity;
import org.jfree.chart.title.LegendItemBlockContainer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.Size2D;

public class IpedLegendTitle extends LegendTitle {
    private IpedChartPanel ipedChartPanel;
    DefaultListModel<LegendItemBlockContainer> legendListModel;
    private JList legendList;

    public IpedLegendTitle(LegendItemSource source) {
        super(source);
    }

    public void setIpedChartPanel(IpedChartPanel ipedchartPanel) {
        this.ipedChartPanel = ipedchartPanel;
        legendListModel = ipedchartPanel.getIpedChartsPanel().getLegendListModel();
        legendList = ipedchartPanel.getIpedChartsPanel().getLegendList();
    }

    @Override
    public Object draw(Graphics2D g2, Rectangle2D area, Object params) {
        Rectangle b = (Rectangle) area.getBounds().clone();

        Graphics2D gPane = g2;

        Rectangle2D target = b;
        Rectangle2D hotspot = (Rectangle2D) target.clone();
        StandardEntityCollection sec = null;
        if (params instanceof EntityBlockParams && ((EntityBlockParams) params).getGenerateEntities()) {
            sec = new StandardEntityCollection();
            sec.add(new TitleEntity(hotspot, this));
        }
        // target = trimMargin(target);
        // if (this.getBackgroundPaint() != null) {
        // gPane.setPaint(this.getBackgroundPaint());
        // gPane.fill(target);
        // }
        // BlockFrame border = getFrame();
        // border.draw(gPane, target);
        // border.getInsets().trim(target);
        BlockContainer container = this.getWrapper();
        if (container == null) {
            container = this.getItemContainer();
        }
        // target = trimPadding(target);
        Object val = container.draw(gPane, target, params);
        Object[] o = legendList.getSelectedValues();
        legendListModel.clear();
        polupatesLegendListModel(legendListModel, container);
        list: for (int i = 0; i < legendList.getModel().getSize(); i++) {
            LegendItemBlockContainer l = (LegendItemBlockContainer) legendList.getModel().getElementAt(i);
            for (int j = 0; j < o.length; j++) {
                if (l.getSeriesKey().equals(((LegendItemBlockContainer) o[j]).getSeriesKey())) {
                    legendList.addSelectionInterval(i, i);
                    continue list;
                }
            }
        }

        if (val instanceof BlockResult) {
            EntityCollection ec = ((BlockResult) val).getEntityCollection();
            if (ec != null && sec != null) {
                sec.addAll(ec);
                ((BlockResult) val).setEntityCollection(sec);
            }
        }

        return val;
    }

    void polupatesLegendListModel(DefaultListModel<LegendItemBlockContainer> llm, BlockContainer container) {
        Iterator<Block> iterator = container.getBlocks().iterator();
        while (iterator.hasNext()) {
            Block b = iterator.next();
            if (b instanceof BlockContainer) {
                polupatesLegendListModel(llm, (BlockContainer) b);
            }
            if (b instanceof LegendItemBlockContainer) {
                llm.addElement((LegendItemBlockContainer) b);
            }
        }
    }

    @Override
    public void draw(Graphics2D g2, Rectangle2D area) {
        super.draw(g2, area);
    }

    @Override
    public Size2D arrange(Graphics2D g2, RectangleConstraint constraint) {
        Size2D result = new Size2D();
        fetchLegendItems();
        if (this.getItemContainer().isEmpty()) {
            return result;
        }
        BlockContainer container = this.getWrapper();
        if (container == null) {
            container = this.getItemContainer();
        }
        RectangleConstraint c = toContentConstraint(constraint);
        Size2D size = container.arrange(g2, c);
        result.height = calculateTotalHeight(size.height);
        result.height = 0;
        result.width = calculateTotalWidth(size.width);
        return result;
    }

}
