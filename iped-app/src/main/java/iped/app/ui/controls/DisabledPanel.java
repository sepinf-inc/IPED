package iped.app.ui.controls;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/*
 *  The DisablePanel will simulate the usage of a "Glass Pane" except that the
 *  glass pane effect is only for the container and not the entire frame.
 *  By default the glass pane is invisible. When the DisablePanel is disabled
 *  then the glass pane is made visible. In addition:
 *
 *  a) All MouseEvents will now be intercepted by the glass pane.
 *  b) The panel is removed from the normal focus traveral policy to prevent
 *     any component on the panel from receiving focus.
 *  c) Key Bindings for any component on the panel will be intercepted.
 *
 *  Therefore, the panel and components will behave as though they are disabled
 *  even though the state of each component has not been changed.
 *
 *  The background color of the glass pane should use a color with an
 *  alpha value to create the disabled look.
 *
 *  The other approach to disabling components on a Container is to recurse
 *  through all the components and set each individual component disabled.
 *  This class supports two static methods to support this functionality:
 *
 *  a) disable( Container ) - to disable all Components on the Container
 *  b) enable ( Container ) - to enable all Components disabled by the
 *     disable() method. That is any component that was disabled prior to using
 *     the disable() method method will remain disabled.
 */
public class DisabledPanel extends JPanel {
    private static DisabledEventQueue queue = new DisabledEventQueue();

    private static Map<Container, List<JComponent>> containers = new HashMap<Container, List<JComponent>>();

    private JComponent glassPane;

    /**
     * Create a DisablePanel for the specified Container. The disabled color will be
     * the following color from the UIManager with an alpha value:
     * UIManager.getColor("inactiveCaptionBorder");
     *
     * @param container
     *            a Container to be added to this DisabledPanel
     */
    public DisabledPanel(Component component) {
        this(component, null);
    }

    /**
     * Create a DisablePanel for the specified Container using the specified
     * disabled color.
     *
     * @param disabledColor
     *            the background color of the GlassPane
     * @param component
     *            a Container to be added to this DisabledPanel
     */
    public DisabledPanel(Component component, Color disabledColor) {
        setLayout(new OverlapLayout());
        add(component);

        glassPane = new GlassPane();
        add(glassPane);

        if (disabledColor != null)
            glassPane.setBackground(disabledColor);

        setFocusTraversalPolicy(new DefaultFocusTraversalPolicy());
    }

    /**
     * The background color of the glass pane.
     *
     * @return the background color of the glass pane
     */
    public Color getDisabledColor() {
        return glassPane.getBackground();
    }

    /**
     * Set the background color of the glass pane. This color should contain an
     * alpha value to give the glass pane a transparent effect.
     *
     * @param disabledColor
     *            the background color of the glass pane
     */
    public void setDisabledColor(Color disabledColor) {
        glassPane.setBackground(disabledColor);
    }

    /**
     * The glass pane of this DisablePanel. It can be customized by adding
     * components to it.
     *
     * @return the glass pane
     */
    public JComponent getGlassPane() {
        return glassPane;
    }

    /**
     * Use a custom glass pane. You are responsible for adding the appropriate mouse
     * listeners to intercept mouse events.
     *
     * @param glassPane
     *            a JComponent to be used as a glass pane
     */
    public void setGlassPane(JComponent glassPane) {
        this.glassPane = glassPane;
    }

    /**
     * Change the enabled state of the panel.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (enabled) {
            glassPane.setVisible(false);
            setFocusCycleRoot(false);
            queue.removePanel(this);
        } else {
            glassPane.setVisible(true);
            setFocusCycleRoot(true); // remove from focus cycle
            queue.addPanel(this);
        }
    }

    /**
     * Because we use layered panels this should be disabled.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /**
     * Convenience static method to disable all components of a given Container,
     * including nested Containers.
     *
     * @param container
     *            the Container containing Components to be disabled
     */
    public static void disable(Container container) {
        List<JComponent> components = SwingUtils.getDescendantsOfType(JComponent.class, container, true);
        List<JComponent> enabledComponents = new ArrayList<JComponent>();
        containers.put(container, enabledComponents);

        for (JComponent component : components) {
            if (component.isEnabled()) {
                enabledComponents.add(component);
                component.setEnabled(false);
            }
        }
    }

    /**
     * Convenience static method to enable Components disabled by using the
     * disable() method. Only Components disable by the disable() method will be
     * enabled.
     *
     * @param container
     *            a Container that has been previously disabled.
     */
    public static void enable(Container container) {
        List<JComponent> enabledComponents = containers.get(container);

        if (enabledComponents != null) {
            for (JComponent component : enabledComponents) {
                component.setEnabled(true);
            }

            containers.remove(container);
        }
    }

    /**
     * A simple "glass pane" that has two functions:
     *
     * a) to paint over top of the Container added to the DisablePanel b) to
     * intercept mouse events when visible
     */
    class GlassPane extends JComponent {
        public GlassPane() {
            setOpaque(false);
            setVisible(false);
            Color base = UIManager.getColor("inactiveCaptionBorder");
            base = (base == null) ? Color.LIGHT_GRAY : base;
            Color background = new Color(base.getRed(), base.getGreen(), base.getBlue(), 128);
            setBackground(background);

            // Disable Mouse events for the panel

            addMouseListener(new MouseAdapter() {
            });
            addMouseMotionListener(new MouseMotionAdapter() {
            });
        }

        /*
         * The component is transparent but we want to paint the background to give it
         * the disabled look.
         */
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getSize().width, getSize().height);
        }
    }

    /**
     * A custom EventQueue to intercept Key Bindings that are used by any component
     * on a DisabledPanel. When a DisabledPanel is disabled it is registered with
     * the DisabledEventQueue. This class will check if any components on the
     * DisablePanel use KeyBindings. If not then nothing changes. However, if some
     * do, then the DisableEventQueue installs itself as the current EquentQueue.
     * The dispatchEvent() method is overriden to check each KeyEvent. If the
     * KeyEvent is for a Component on a DisablePanel then the event is ignored,
     * otherwise it is dispatched for normal processing.
     */
    static class DisabledEventQueue extends EventQueue implements WindowListener {
        private Map<DisabledPanel, Set<KeyStroke>> panels = new HashMap<DisabledPanel, Set<KeyStroke>>();

        /**
         * Check if any component on the DisabledPanel is using Key Bindings. If so,
         * then track the bindings and use a custom EventQueue to intercept the
         * KeyStroke before it is passed to the component.
         */
        public void addPanel(DisabledPanel panel) {
            // Get all the KeyStrokes used by all the components on the panel

            Set<KeyStroke> keyStrokes = getKeyStrokes(panel);

            if (keyStrokes.size() == 0)
                return;

            panels.put(panel, keyStrokes);

            // More than one panel can be disabled but we only need to install
            // the custom EventQueue when the first panel is disabled.

            EventQueue current = Toolkit.getDefaultToolkit().getSystemEventQueue();

            if (current != this)
                current.push(queue);

            // We need to track when a Window is closed so we can remove
            // the references for all the DisabledPanels on that window.

            Window window = SwingUtilities.windowForComponent(panel);
            window.removeWindowListener(this);
            window.addWindowListener(this);
        }

        /**
         * Check each component to see if its using Key Bindings
         */
        private Set<KeyStroke> getKeyStrokes(DisabledPanel panel) {
            Set<KeyStroke> keyStrokes = new HashSet<KeyStroke>();

            // Only JComponents use Key Bindings

            Container container = ((Container) panel.getComponent(1));
            List<JComponent> components = SwingUtils.getDescendantsOfType(JComponent.class, container);

            // We only care about the WHEN_IN_FOCUSED_WINDOW bindings

            for (JComponent component : components) {
                InputMap im = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

                if (im != null && im.allKeys() != null) {
                    for (KeyStroke keyStroke : im.allKeys())
                        keyStrokes.add(keyStroke);
                }
            }

            return keyStrokes;
        }

        /**
         * The panel is no longer disabled so we no longer need to intercept its
         * KeyStrokes. Restore the default EventQueue when all panels using Key Bindings
         * have been enabled.
         */
        public void removePanel(DisabledPanel panel) {
            if (panels.remove(panel) != null && panels.size() == 0)
                pop();
        }

        /**
         * Intercept KeyEvents bound to a component on a DisabledPanel.
         */
        @Override
        protected void dispatchEvent(AWTEvent event) {
            // Potentially intercept KeyEvents

            if (event.getID() == KeyEvent.KEY_TYPED || event.getID() == KeyEvent.KEY_PRESSED
                    || event.getID() == KeyEvent.KEY_RELEASED) {
                KeyEvent keyEvent = (KeyEvent) event;
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(keyEvent);

                // When using Key Bindings, the source of the KeyEvent will be
                // the Window. Check each panel belonging to the source Window.

                for (DisabledPanel panel : panels.keySet()) {
                    Window panelWindow = SwingUtilities.windowForComponent(panel);

                    // A binding was found so just return without dispatching it.

                    if (panelWindow == keyEvent.getComponent() && searchForKeyBinding(panel, keyStroke))
                        return;
                }
            }

            // Dispatch normally

            super.dispatchEvent(event);
        }

        /**
         * Check if the KeyStroke is for a Component on the DisablePanel
         */
        private boolean searchForKeyBinding(DisabledPanel panel, KeyStroke keyStroke) {
            Set<KeyStroke> keyStrokes = panels.get(panel);

            return keyStrokes.contains(keyStroke);
        }

        // Implement WindowListener interface

        /**
         * When a Window containing a DisablePanel that has been disabled is closed,
         * remove the DisablePanel from the DisabledEventQueue. This may result in the
         * DisabledEventQueue deregistering itself as the current EventQueue.
         */
        public void windowClosed(WindowEvent e) {
            List<DisabledPanel> panelsToRemove = new ArrayList<DisabledPanel>();
            Window window = e.getWindow();

            // Create a List of DisabledPanels to remove

            for (DisabledPanel panel : panels.keySet()) {
                Window panelWindow = SwingUtilities.windowForComponent(panel);

                if (panelWindow == window) {
                    panelsToRemove.add(panel);
                }
            }

            // Remove panels here to prevent ConcurrentModificationException

            for (DisabledPanel panel : panelsToRemove) {
                removePanel(panel);
            }

            window.removeWindowListener(this);
        }

        public void windowActivated(WindowEvent e) {
        }

        public void windowClosing(WindowEvent e) {
        }

        public void windowDeactivated(WindowEvent e) {
        }

        public void windowDeiconified(WindowEvent e) {
        }

        public void windowIconified(WindowEvent e) {
        }

        public void windowOpened(WindowEvent e) {
        }
    }
}