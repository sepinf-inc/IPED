package iped.app.ui.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CCheckBox;

public abstract class CCustomCheckBox extends CCheckBox {
    public CCustomCheckBox(String text, Icon icon) {
        super(text, icon);
        Icon disabled = new TranslucentIcon(icon, 0.1f);
        setDisabledHoverIcon(disabled);
        setDisabledIcon(disabled);
        setDisabledPressedIcon(disabled);

        setSelectedIcon(new SelectedHighlightIcon(icon, 80));
        setSelectedHoverIcon(new SelectedHighlightIcon(icon, 100));
        setSelectedPressedIcon(new SelectedHighlightIcon(icon, 120));

        setDisabledSelectedHoverIcon(disabled);
        setDisabledSelectedPressedIcon(disabled);
    }

    private class SelectedHighlightIcon implements Icon {
        private final Icon delegate;
        private final Color bgColor;

        public SelectedHighlightIcon(Icon delegate, int whiteAlpha) {
            this.delegate = delegate;
            this.bgColor = new Color(255, 255, 255, whiteAlpha);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(x - 2, y - 2, getIconWidth() + 4, getIconHeight() + 4, 6, 6);
            } finally {
                g2.dispose();
            }
            delegate.paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }
    }
}