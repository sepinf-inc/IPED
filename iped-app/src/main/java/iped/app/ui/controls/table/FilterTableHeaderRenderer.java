package iped.app.ui.controls.table;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

import iped.app.ui.ResultTableModel;
import iped.app.ui.TableHeaderFilterManager;
import iped.utils.UiUtil;

public class FilterTableHeaderRenderer extends DefaultTableCellRenderer implements UIResource {
    private static final long serialVersionUID = 6359006660516330179L;

    private static final RenderingHints renderingHints;

    static {
        Map<Key, Object> hints = new HashMap<>();
        hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        renderingHints = new RenderingHints(hints);
    }

    private static final int arrowIconSize = 6;
    private static final int filterIconSize = 8;
    private static final int iconGap = 2;

    private final SpaceIcon spaceIcon = new SpaceIcon();
    private final JTableHeader header;
    private final Map<Integer, Integer> xFilter = new HashMap<Integer, Integer>();

    private int hoverColumn = -1;
    private boolean hasFilterIcon;
    private boolean isFiltered;
    private int sortArrow = -1;
    private int hoverFilter = -1;
    private int column;

    private static final Stroke stroke = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final float[] dist2 = { 0, 1 };
    private static final float[] dist3 = { 0, 0.5f, 1 };

    private final Color colorFilterIcon0;
    private final Color colorFilterIcon0Hover;
    private final Color colorFilterIcon1;
    private final Color colorFilterIcon2;
    private final Color colorFilterIcon2Hover;
    private final Color colorArrow;
    private final Color colorArrowFiltered;
    private final Color colorHeaderBorder0;
    private final Color colorHeaderBorder1;
    private final Color colorHeaderBorder2;
    private final Color colorHeader1;
    private final Color colorHeader1Filtered;
    private final Color colorHeader1Sorted;
    private final Color colorHeader1Hover;
    private final Color colorHeader1FilteredHover;
    private final Color colorHeader1SortedHover;
    private final Color colorHeader2;
    private final Color colorHeader2Filtered;
    private final Color colorHeader2Sorted;
    private final Color colorHeader2Hover;
    private final Color colorHeader2FilteredHover;
    private final Color colorHeader2SortedHover;

    public FilterTableHeaderRenderer(JTableHeader header) {
        setHorizontalAlignment(JLabel.LEFT);
        setHorizontalTextPosition(JLabel.LEADING);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 3, 4, 4));
        this.header = header;

        int hoverShift = 20;
        colorFilterIcon0 = UIManager.getColor("Filter.Icon0");
        colorFilterIcon0Hover = UIManager.getColor("Filter.Icon0Hover");
        colorFilterIcon1 = UIManager.getColor("Filter.Icon1");
        colorFilterIcon2 = UIManager.getColor("Filter.Icon2");
        colorFilterIcon2Hover = UIManager.getColor("Filter.Icon2Hover");
        colorArrow = UIManager.getColor("Filter.Arrow");
        colorArrowFiltered = new Color(255, 255, 255, 220);
        colorHeaderBorder0 = UIManager.getColor("Filter.Border0");
        colorHeaderBorder1 = UIManager.getColor("Filter.Border1");
        colorHeaderBorder2 = UIManager.getColor("Filter.Border2");
        colorHeader1 = UIManager.getColor("Filter.Header1");
        colorHeader1Filtered = new Color(190, 0, 0);
        colorHeader1Sorted = UIManager.getColor("Filter.Header1Sorted");
        colorHeader1Hover = UiUtil.shift(colorHeader1, hoverShift);
        colorHeader1FilteredHover = UiUtil.shift(colorHeader1Filtered, hoverShift);
        colorHeader1SortedHover = UiUtil.shift(colorHeader1Sorted, hoverShift);
        colorHeader2 = UIManager.getColor("Filter.Header2");
        colorHeader2Filtered = new Color(240, 50, 50);
        colorHeader2Sorted = UIManager.getColor("Filter.Header2Sorted");
        colorHeader2Hover = UiUtil.shift(colorHeader2, hoverShift);
        colorHeader2FilteredHover = UiUtil.shift(colorHeader2Filtered, hoverShift);
        colorHeader2SortedHover = UiUtil.shift(colorHeader2Sorted, hoverShift);
    }

    public void clear() {
        hoverColumn = hoverFilter = -1;
        header.repaint();
    }

    public boolean update(MouseEvent e, int click) {
        synchronized (xFilter) {
            int prevHoverColumn = hoverColumn;
            int prevHoverFilter = hoverFilter;
            hoverColumn = hoverFilter = -1;
            if (header != null && header.getDraggedColumn() == null && header.contains(e.getPoint())) {
                int c = header.columnAtPoint(e.getPoint());
                if (c >= 0) {
                    if (click == 3) {
                        if (hasFilterIcon) {
                            MetadataValueSearchList.show(header, c);
                        }
                        return true;
                    }
                    if (hasFilterIcon) {
                        Integer x = xFilter.get(c);
                        if (x != null && e.getX() >= x - iconGap && e.getX() <= x + iconGap + filterIconSize) {
                            hoverFilter = c;
                        } else {
                            hoverColumn = c;
                        }
                    } else {
                        hoverColumn = c;
                    }
                }
            }
            if (hoverColumn != prevHoverColumn || hoverFilter != prevHoverFilter) {
                header.repaint();
            }
            if (click == 1 && hoverFilter != -1) {
                MetadataValueSearchList.show(header, hoverFilter);
                return true;
            }
        }
        return false;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.column = column;

        Color fgColor = header.getForeground();
        setForeground(fgColor);
        setBackground(null);
        setFont(header.getFont());

        if (table != null) {
            sortArrow = -1;
            if (table.getRowSorter() != null) {
                SortOrder sortOrder = getColumnSortOrder(table, column);
                if (sortOrder == SortOrder.ASCENDING) {
                    sortArrow = 1;
                } else if (sortOrder == SortOrder.DESCENDING) {
                    sortArrow = 2;
                }
            }
            int modelColumn = table.convertColumnIndexToModel(column);
            String field = ((ResultTableModel) table.getModel()).getColumnFieldName(modelColumn);
            isFiltered = TableHeaderFilterManager.get().isFieldFiltered(field);
            hasFilterIcon = modelColumn >= 4; // Columns 0 to 3 can not be filtered

            setText(value == null ? "" : value.toString());
            setIcon(sortArrow == -1 ? null : spaceIcon);
        }
        return this;
    }

    public static SortOrder getColumnSortOrder(JTable table, int column) {
        SortOrder rv = null;
        if (table == null || table.getRowSorter() == null) {
            return rv;
        }
        java.util.List<? extends RowSorter.SortKey> sortKeys = table.getRowSorter().getSortKeys();
        if (sortKeys.size() > 0 && sortKeys.get(0).getColumn() == table.convertColumnIndexToModel(column)) {
            rv = sortKeys.get(0).getSortOrder();
        }
        return rv;
    }

    @Override
    public void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Rectangle rc = new Rectangle(0, 0, w, h);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHints(renderingHints);

        Color c1 = null;
        Color c2 = null;
        Color fg = getForeground();
        if (column == hoverColumn) {
            if (isFiltered) {
                c1 = colorHeader1FilteredHover;
                c2 = colorHeader2FilteredHover;
                setForeground(colorArrowFiltered);
            } else if (sortArrow != -1) {
                c1 = colorHeader1SortedHover;
                c2 = colorHeader2SortedHover;
            } else {
                c1 = colorHeader1Hover;
                c2 = colorHeader2Hover;
            }
        } else {
            if (isFiltered) {
                c1 = colorHeader1Filtered;
                c2 = colorHeader2Filtered;
                setForeground(colorArrowFiltered);
            } else if (sortArrow != -1) {
                c1 = colorHeader1Sorted;
                c2 = colorHeader2Sorted;
            } else {
                c1 = colorHeader1;
                c2 = colorHeader2;
            }
        }
        Point2D start = new Point2D.Float(0, 0);
        Point2D end = new Point2D.Float(0, h);
        Color[] colors = { c2, c1, c2 };
        LinearGradientPaint p = new LinearGradientPaint(start, end, dist3, colors);
        g2.setPaint(p);
        g2.fill(rc);

        g2.setColor(colorHeaderBorder0);
        g2.drawLine(0, h - 1, w, h - 1);

        colors[1] = colorHeaderBorder1;
        colors[0] = colors[2] = colorHeaderBorder2;
        p = new LinearGradientPaint(start, end, dist3, colors);
        g2.setPaint(p);
        g2.fill(new Rectangle2D.Double(w - 1, 0, 1, h - 1));

        if (sortArrow != -1 || hasFilterIcon) {
            spaceIcon.height = spaceIcon.width = 0;
            if (sortArrow != -1) {
                spaceIcon.width += arrowIconSize;
                spaceIcon.height = Math.max(spaceIcon.height, arrowIconSize);
            }
            if (hasFilterIcon) {
                spaceIcon.width += filterIconSize;
                spaceIcon.height = Math.max(spaceIcon.height, filterIconSize);
            }
            if (sortArrow != -1 && hasFilterIcon) {
                spaceIcon.width += iconGap;
            }
            setIcon(spaceIcon);
            super.paintComponent(g);
            int x = computeIconsX(g);
            synchronized (xFilter) {
                xFilter.put(column, hasFilterIcon ? getX() + x : -1);
            }
            if (hasFilterIcon) {
                paintFilterIcon(g2, x, column == hoverFilter);
                x += filterIconSize + iconGap;
            }
            if (sortArrow != -1) {
                paintArrowIcon(g2, x, isFiltered);
            }
            setForeground(fg);
        } else {
            super.paintComponent(g);
        }
    }

    private void paintFilterIcon(Graphics2D g2, int x, boolean hover) {
        int y = (getHeight() - filterIconSize) / 2;
        GeneralPath gp = new GeneralPath();
        gp.moveTo(x, y);
        gp.lineTo(x + filterIconSize, y);
        gp.lineTo(x + filterIconSize, y + filterIconSize * 0.2);
        gp.lineTo(x + filterIconSize * 0.6, y + filterIconSize * 0.6);
        gp.lineTo(x + filterIconSize * 0.6, y + filterIconSize * 1.0);
        gp.lineTo(x + filterIconSize * 0.4, y + filterIconSize * 1.0);
        gp.lineTo(x + filterIconSize * 0.4, y + filterIconSize * 0.6);
        gp.lineTo(x, y + filterIconSize * 0.2);
        gp.closePath();

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(stroke);

        Point2D start = new Point2D.Float(x, y);
        Point2D end = new Point2D.Float(x + filterIconSize, y);
        Color[] colors = { colorFilterIcon1, hover ? colorFilterIcon2Hover : colorFilterIcon2 };
        LinearGradientPaint p = new LinearGradientPaint(start, end, dist2, colors);
        g2.setPaint(p);
        g2.fill(gp);

        g2.setColor(hover ? colorFilterIcon0Hover : colorFilterIcon0);
        g2.draw(gp);

        g2.setStroke(oldStroke);
    }

    private void paintArrowIcon(Graphics2D g2, int x, boolean isFiltered) {
        int y = (getHeight() - arrowIconSize) / 2;
        GeneralPath gp = new GeneralPath();
        if (sortArrow == 1) {
            gp.moveTo(x, y + arrowIconSize);
            gp.lineTo(x + arrowIconSize, y + arrowIconSize);
            gp.lineTo(x + arrowIconSize / 2.0, y);
            gp.closePath();
        } else if (sortArrow == 2) {
            gp.moveTo(x, y);
            gp.lineTo(x + arrowIconSize, y);
            gp.lineTo(x + arrowIconSize / 2.0, y + arrowIconSize);
            gp.closePath();
        }

        g2.setColor(isFiltered ? colorArrowFiltered : colorArrow);
        g2.fill(gp);
    }

    private int computeIconsX(Graphics g) {
        FontMetrics fontMetrics = g.getFontMetrics();
        Rectangle viewR = new Rectangle();
        Rectangle textR = new Rectangle();
        Rectangle iconR = new Rectangle();
        Insets i = getInsets();
        viewR.x = i.left;
        viewR.y = i.top;
        viewR.width = getWidth() - (i.left + i.right);
        viewR.height = getHeight() - (i.top + i.bottom);
        SwingUtilities.layoutCompoundLabel(this, fontMetrics, getText(), spaceIcon, getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), viewR, iconR, textR, getIconTextGap());
        int x = getWidth() - i.right;
        if (hasFilterIcon) {
            x -= filterIconSize;
        }
        if (sortArrow != -1) {
            x -= arrowIconSize;
        }
        if (hasFilterIcon && sortArrow != -1) {
            x -= iconGap;
        }
        return x;
    }

    private class SpaceIcon implements Icon {
        int width, height;

        public void paintIcon(Component c, Graphics g, int x, int y) {
        }

        public int getIconWidth() {
            return width;
        }

        public int getIconHeight() {
            return height;
        }
    }
}
