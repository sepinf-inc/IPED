package iped.app.ui.controls;

import java.awt.Color;
import java.awt.Graphics;

import bibliothek.extension.gui.dock.theme.eclipse.RoundRectButton;
import bibliothek.gui.dock.control.focus.FocusAwareComponent;
import bibliothek.gui.dock.themes.basic.action.BasicButtonModel;
import bibliothek.gui.dock.themes.basic.action.BasicResourceInitializer;
import bibliothek.gui.dock.themes.basic.action.BasicTrigger;
import bibliothek.gui.dock.util.AbstractPaintableComponent;
import bibliothek.gui.dock.util.BackgroundComponent;
import bibliothek.gui.dock.util.BackgroundPaint;
import bibliothek.gui.dock.util.Transparency;
import bibliothek.util.Colors;

public class CustomButton extends RoundRectButton implements FocusAwareComponent {
    private static final long serialVersionUID = -6072450328234243332L;

    public CustomButton(BasicTrigger trigger, BasicResourceInitializer initializer) {
        super(trigger, initializer);
    }

    @Override
    protected void paintComponent(Graphics g) {
        BasicButtonModel model = getModel();
        BackgroundPaint paint = model.getBackground();
        BackgroundComponent component = model.getBackgroundComponent();
        if (paint == null) {
            doPaintBackground(g);
            doPaintForeground(g);
        } else {
            AbstractPaintableComponent paintable = new AbstractPaintableComponent(component, this, paint) {
                protected void foreground(Graphics g) {
                    doPaintForeground(g);
                }

                protected void background(Graphics g) {
                    doPaintBackground(g);
                }

                protected void border(Graphics g) {
                }

                protected void children(Graphics g) {
                }

                protected void overlay(Graphics g) {
                }

                public Transparency getTransparency() {
                    return Transparency.DEFAULT;
                }
            };
            paintable.paint(g);
        }
    }

    private void doPaintBackground(Graphics g) {
        BasicButtonModel model = getModel();
        Color background = getBackground();

        Color border = null;
        if (model.isMousePressed()) {
            border = Colors.diffMirror(background, 0.3);
            background = Colors.undiffMirror(background, 0.6);
        } else if (model.isMouseInside()) {
            border = Colors.diffMirror(background, 0.3);
            background = Colors.undiffMirror(background, 0.3);
        } else if (model.isSelected()) {
            border = Colors.diffMirror(background, 0.2);
            background = Colors.undiffMirror(background, 0.9);
        }

        int w = getWidth() - 1;
        int h = getHeight() - 1;

        if (border != null) {
            g.setColor(background);
            g.fillRoundRect(0, 0, w, h, 4, 4);

            g.setColor(border);
            g.drawRoundRect(0, 0, w, h, 4, 4);
        }
    }

    private void doPaintForeground(Graphics g) {
        BasicButtonModel model = getModel();
        Color background = getBackground();
        if (model.isMousePressed()) {
            background = Colors.undiffMirror(background, 0.6);
        } else if (model.isSelected() || model.isMouseInside()) {
            background = Colors.undiffMirror(background, 0.3);
        }
        paintChildren(g);
    }
}
