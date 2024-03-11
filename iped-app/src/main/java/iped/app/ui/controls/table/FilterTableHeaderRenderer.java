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
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
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
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

import iped.app.ui.ResultTableModel;
import iped.app.ui.TableHeaderFilterManager;

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
    private final MouseListener[] mouseListeners;

    private int hoverColumn = -1;
    private boolean hasFilterIcon;
    private boolean isFiltered;
    private int sortArrow = -1;
    private int hoverFilter = -1;
    private int column;

    public FilterTableHeaderRenderer(JTable table) {
        setHorizontalAlignment(JLabel.LEFT);
        setHorizontalTextPosition(JLabel.LEADING);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 3, 4, 4));
        header = table.getTableHeader();
        header.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                update(e, 0);
            }
        });
        mouseListeners = header.getMouseListeners();
        for (MouseListener ml : mouseListeners) {
            header.removeMouseListener(ml);
        }
        header.addMouseListener(new MouseListener() {
            public void mouseExited(MouseEvent e) {
                hoverColumn = hoverFilter = -1;
                header.repaint();
                for (MouseListener ml : mouseListeners) {
                    ml.mouseExited(e);
                }
            }

            public void mouseEntered(MouseEvent e) {
                update(e, 0);
                for (MouseListener ml : mouseListeners) {
                    ml.mouseEntered(e);
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (update(e, 1)) {
                        e.consume();
                        return;
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    if (update(e, 3)) {
                        e.consume();
                        return;
                    }
                }
                for (MouseListener ml : mouseListeners) {
                    ml.mouseClicked(e);
                }
            }

            public void mousePressed(MouseEvent e) {
                for (MouseListener ml : mouseListeners) {
                    ml.mousePressed(e);
                }
            }

            public void mouseReleased(MouseEvent e) {
                for (MouseListener ml : mouseListeners) {
                    ml.mouseReleased(e);
                }
            }
        });
    }

    private boolean update(MouseEvent e, int click) {
        synchronized (xFilter) {
            int prevHoverColumn = hoverColumn;
            int prevHoverFilter = hoverFilter;
            hoverColumn = hoverFilter = -1;
            if (header != null && header.getDraggedColumn() == null && header.contains(e.getPoint())) {
                int c = header.columnAtPoint(e.getPoint());
                if (c >= 0) {
                    if (click == 3) {
                        MetadataValueSearchList.show(header, c);
                        return true;
                    }
                    Integer x = xFilter.get(c);
                    if (x != null && e.getX() >= x - iconGap && e.getX() <= x + iconGap + filterIconSize) {
                        hoverFilter = c;
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

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        this.column = column;

        Color fgColor = header.getForeground();
        setForeground(fgColor);
        setBackground(null);
        setFont(header.getFont());

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
        hasFilterIcon = modelColumn >= 4;

        setText(value == null ? "" : value.toString());
        setIcon(sortArrow == -1 ? null : spaceIcon);
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
        // TODO: Create color constants and handle dark mode
        Color fg = getForeground();
        if (column == hoverColumn) {
            if (isFiltered) {
                c1 = new Color(220, 50, 50);
                c2 = new Color(250, 90, 90);
                setForeground(Color.white);
            } else if (sortArrow != -1) {
                c1 = new Color(200, 218, 234);
                c2 = new Color(240, 248, 255);
            } else {
                c1 = new Color(235, 238, 244);
                c2 = new Color(255, 255, 255);
            }
        } else {
            if (isFiltered) {
                c1 = new Color(190, 0, 0);
                c2 = new Color(240, 50, 50);
                setForeground(Color.white);
            } else if (sortArrow != -1) {
                c1 = new Color(180, 198, 214);
                c2 = new Color(220, 228, 236);
            } else {
                c1 = new Color(215, 218, 224);
                c2 = new Color(249, 249, 251);
            }
        }
        Point2D start = new Point2D.Float(0, 0);
        Point2D end = new Point2D.Float(0, h);
        float[] dist = { 0.0f, 0.5f, 1.0f };
        Color[] colors = { c2, c1, c2 };
        LinearGradientPaint p = new LinearGradientPaint(start, end, dist, colors);
        g2.setPaint(p);
        g2.fill(rc);

        Color c0 = new Color(141, 145, 153);
        g2.setColor(c0);
        g2.drawLine(0, h - 1, w, h - 1);

        c1 = new Color(171, 175, 181);
        c2 = new Color(214, 216, 220);
        colors = new Color[] { c2, c1, c2 };
        p = new LinearGradientPaint(start, end, dist, colors);
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
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // TODO: Create color constants and handle dark mode
        Point2D start = new Point2D.Float(x, y);
        Point2D end = new Point2D.Float(x + filterIconSize, y);
        float[] dist = { 0.0f, 1.0f };
        Color c1 = new Color(242, 246, 252);
        Color c2 = null;
        if (hover) {
            c2 = new Color(70, 116, 162);
        } else {
            c2 = new Color(172, 176, 182);
        }
        Color[] colors = { c1, c2 };
        LinearGradientPaint p = new LinearGradientPaint(start, end, dist, colors);
        g2.setPaint(p);
        g2.fill(gp);

        Color c0 = null;
        if (hover) {
            c0 = new Color(40, 86, 132, 200);
        } else {
            c0 = new Color(152, 156, 162, 200);
        }
        g2.setColor(c0);
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
        // TODO: Create color constants and handle dark mode
        g2.setColor(isFiltered ? new Color(255, 255, 255, 200) : new Color(70, 116, 162, 200));
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
        SwingUtilities.layoutCompoundLabel(this, fontMetrics, getText(), spaceIcon, getVerticalAlignment(),
                getHorizontalAlignment(), getVerticalTextPosition(), getHorizontalTextPosition(), viewR, iconR, textR,
                getIconTextGap());
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
