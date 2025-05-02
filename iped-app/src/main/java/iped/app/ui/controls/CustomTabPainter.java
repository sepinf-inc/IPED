package iped.app.ui.controls;

import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.border.Border;

import bibliothek.extension.gui.dock.theme.eclipse.stack.EclipseTabPane;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.BorderedComponent;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.InvisibleTab;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.InvisibleTabPane;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.RectGradientPainter;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabComponent;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabPainter;
import bibliothek.extension.gui.dock.theme.eclipse.stack.tab.TabPanePainter;
import bibliothek.gui.DockController;
import bibliothek.gui.Dockable;

public class CustomTabPainter implements TabPainter {
    private static final Insets insets = new Insets(3, 2, 5, -2);

    public Border getFullBorder(BorderedComponent owner, DockController controller, Dockable dockable) {
        return RectGradientPainter.FACTORY.getFullBorder(owner, controller, dockable);
    }

    public TabComponent createTabComponent(EclipseTabPane pane, Dockable dockable) {
        return new RectGradientPainter(pane, dockable) {
            private static final long serialVersionUID = -9020339124009415001L;

            @Override
            public void setIcon(Icon icon) {
                super.setIcon(null);
            }

            @Override
            public void setLabelInsets(Insets labelInsets) {
                super.setLabelInsets(insets);
            }
        };
    }

    public InvisibleTab createInvisibleTab(InvisibleTabPane pane, Dockable dockable) {
        return RectGradientPainter.FACTORY.createInvisibleTab(pane, dockable);
    }

    public TabPanePainter createDecorationPainter(EclipseTabPane pane) {
        return RectGradientPainter.FACTORY.createDecorationPainter(pane);
    }
}
