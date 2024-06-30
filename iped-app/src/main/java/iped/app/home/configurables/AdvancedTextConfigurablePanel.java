package iped.app.home.configurables;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;

import javax.swing.DefaultSingleSelectionModel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.ui.Messages;
import iped.configuration.Configurable;

public abstract class AdvancedTextConfigurablePanel extends TextConfigurablePanel {
    protected JTabbedPane tabbedPane;
    protected JPanel basicPanel;
    private VetoableSingleSelectionModel tabModel;

    protected AdvancedTextConfigurablePanel(Configurable<?> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        tabbedPane = new JTabbedPane(JTabbedPane.BOTTOM);
        tabModel = new VetoableSingleSelectionModel(this);
        tabbedPane.setModel(tabModel);
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override
            protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
                return 25;
            }

        });
    }

    /**
     * selection model that implement tab vetoable change listeners that avoid tab
     * change if configuration state is not valid. From
     * https://stackoverflow.com/questions/12389801/forbid-tab-change-in-a-jtabbedpane
     */
    public static class VetoableSingleSelectionModel extends DefaultSingleSelectionModel {
        private VetoableChangeSupport vetoableChangeSupport;
        AdvancedTextConfigurablePanel componentSource;

        public VetoableSingleSelectionModel(AdvancedTextConfigurablePanel componentSource) {
            this.componentSource = componentSource;
        }

        @Override
        public void setSelectedIndex(int index) {
            if (getSelectedIndex() == index)
                return;
            try {
                fireVetoableChange(getSelectedIndex(), index);
            } catch (PropertyVetoException e) {
                JOptionPane.showMessageDialog(componentSource, e.getMessage());

                return;
            }
            super.setSelectedIndex(index);
        }

        private void fireVetoableChange(int oldSelectionIndex, int newSelectionIndex) throws PropertyVetoException {
            if (!isVetoable())
                return;
            vetoableChangeSupport.fireVetoableChange("selectedIndex", oldSelectionIndex, newSelectionIndex);

        }

        private boolean isVetoable() {
            if (vetoableChangeSupport == null)
                return false;
            return vetoableChangeSupport.hasListeners(null);
        }

        public void addVetoableChangeListener(VetoableChangeListener l) {
            if (vetoableChangeSupport == null) {
                vetoableChangeSupport = new VetoableChangeSupport(this);
            }
            vetoableChangeSupport.addVetoableChangeListener(l);
        }

        public void removeVetoableChangeListener(VetoableChangeListener l) {
            if (vetoableChangeSupport == null)
                return;
            vetoableChangeSupport.removeVetoableChangeListener(l);
        }
    }

    public void createConfigurableGUI() {
        super.createConfigurableGUI();

        this.remove(txtAreaScroll);

        tabbedPane.addTab(getBasicPaneTitle(), UIManager.getIcon("FileView.fileIcon"), getBasicPane(), "");
        tabbedPane.addTab(Messages.get("Home.configurables.AdvancedPanelLabel"), UIManager.getIcon("FileView.fileIcon"), txtAreaScroll, "");
        this.add(tabbedPane);

        tabbedPane.getModel().addChangeListener(null);

        AdvancedTextConfigurablePanel self = this;

        tabModel.addVetoableChangeListener(new VetoableChangeListener() {

            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                if (self.hasChanged()) {
                    try {
                        self.applyChanges();
                        changed = false;
                        fireChangeListener(new ChangeEvent(this));
                    } catch (ConfigurableValidationException cve) {
                        JOptionPane.showMessageDialog(self, cve.getMessage() + "\n" + cve.getCause(), "", JOptionPane.ERROR_MESSAGE);
                        PropertyVetoException pve = new PropertyVetoException("Change not valid", evt);
                        throw pve;
                    }
                }
            }
        });

    }

    protected String getBasicPaneTitle() {
        return Messages.get("Home.configurables.BasicPanelLabel");
    }

    protected abstract Component getBasicPane();
}
