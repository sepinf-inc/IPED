package iped.viewers.search;


import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;

public class CenterTopLayoutManager implements LayoutManager2 {
    public static final String TOP = "TOP";
    public static final String CENTER = "CENTER";
    private static final int xBorder = 16;
    private static final int yBorder = 4;
    protected Component center, top;

    @Override
    public void addLayoutComponent(String name, Component comp) {
        if (name.equalsIgnoreCase(CENTER)) {
            center = comp;
        } else if (name.equalsIgnoreCase(TOP)) {
            top = comp;
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        if (comp.equals(center)) {
            center = null;
        } else if (comp.equals(top)) {
            top = null;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return center.getPreferredSize();
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return center.getMinimumSize();
    }

    @Override
    public void layoutContainer(Container parent) {
        if (center != null) {
            center.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        }
        if (top != null) {
            Dimension d = top.getPreferredSize();
            int x = parent.getWidth() - xBorder - d.width;
            top.setBounds(x, yBorder, d.width, d.height);
        }
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        addLayoutComponent(constraints == null ? "" : constraints.toString(), comp);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return center.getMaximumSize();
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
    }
}
