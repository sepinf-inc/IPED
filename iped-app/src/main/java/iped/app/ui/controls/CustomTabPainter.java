package iped.app.ui.controls;

import java.awt.Insets;

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
    public Border getFullBorder(BorderedComponent owner, DockController controller, Dockable dockable) {
        return RectGradientPainter.FACTORY.getFullBorder(owner, controller, dockable);
    }

    public TabComponent createTabComponent(EclipseTabPane pane, Dockable dockable) {
        return new RectGradientPainter(pane, dockable) {
            private static final long serialVersionUID = -9020339124009415001L;

            public void setLabelInsets(Insets labelInsets) {
                labelInsets = new Insets(labelInsets.top - 1, labelInsets.left - 3, labelInsets.bottom - 1, labelInsets.right - 3);
                super.setLabelInsets(labelInsets);
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
