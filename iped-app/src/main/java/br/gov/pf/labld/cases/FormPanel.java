package br.gov.pf.labld.cases;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class FormPanel extends JPanel {

  private static final long serialVersionUID = -5981709940984206915L;

  private Map<String, Component> labels = new LinkedHashMap<>();
  private Map<String, Component> fields = new LinkedHashMap<>();

  private JPanel fieldsPanel = new JPanel();
  private JPanel sectionsPanel = new JPanel();
  private JPanel buttonsPanel = new JPanel();

  private GroupLayout groupLayout;

  public FormPanel() {
    super();
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    groupLayout = new GroupLayout(fieldsPanel);
    groupLayout.setAutoCreateGaps(true);
    groupLayout.setAutoCreateContainerGaps(true);

    fieldsPanel.setLayout(groupLayout);

    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
    sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));

    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    add(fieldsPanel);
    add(sectionsPanel);
    add(buttonsPanel);
  }

  public void addComplexItem(String name, Component label, List<Component> comps) {
    addComplexItem(name, label, comps.toArray(new Component[comps.size()]));
  }

  public void addComplexItem(String name, String label, List<Component> comps) {
    addComplexItem(name, new JLabel(label), comps);
  }

  public void addComplexItem(String name, String label, Component... comps) {
    addComplexItem(name, new JLabel(label), comps);
  }

  public void addComplexItem(String name, Component label, Component... comps) {
    if (comps.length == 0) {
      throw new IllegalArgumentException("1 or more components needed.");
    } else if (comps.length == 1) {
      addItem(name, label, comps[0]);
    } else {
      JPanel field = new JPanel();

      GroupLayout layout = new GroupLayout(field);
      layout.setAutoCreateGaps(false);
      layout.setAutoCreateContainerGaps(false);
      field.setLayout(layout);

      Group horizontalGroup = layout.createSequentialGroup();

      Group verticalGroup = layout.createSequentialGroup();
      ParallelGroup vParallelGroup = layout.createParallelGroup(Alignment.BASELINE);
      verticalGroup.addGroup(vParallelGroup);
      for (int i = 0; i < comps.length; i++) {
        Component comp = comps[i];
        horizontalGroup.addComponent(comp);
        vParallelGroup.addComponent(comp);
      }

      layout.setHorizontalGroup(horizontalGroup);
      layout.setVerticalGroup(verticalGroup);

      addItem(name, label, field);
    }
  }

  public void setItemVisible(String name, boolean visible) {
    Component field = fields.get(name);
    Component label = labels.get(name);

    if (field != null) {
      field.setVisible(visible);
    }
    if (label != null) {
      label.setVisible(visible);
    }
  }

  public void addItem(String name, Component label, Component field) {
    labels.put(name, label);
    fields.put(name, field);
  }

  public void addItem(String name, String label, Component field) {
    addItem(name, new JLabel(label), field);
  }

  public void addBooleanItem(String name, JLabel label) {

    JRadioButton yesBtn = new JRadioButton(Messages.getString("Case.Yes"));
    yesBtn.setName(name + "-yes");

    JRadioButton noBtn = new JRadioButton(Messages.getString("Case.No"));
    noBtn.setName(name + "-no");

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(yesBtn);
    buttonGroup.add(noBtn);

    addComplexItem(name, label, yesBtn, noBtn);
  }

  public boolean getBooleanItemValue(String name) {
    JRadioButton yesBtn = (JRadioButton) getComponent(name, name + "-yes");
    return yesBtn.isSelected();
  }

  public void setBooleanItemValue(String name, boolean value) {
    JRadioButton yesBtn = (JRadioButton) getComponent(name, name + "-yes");
    JRadioButton noBtn = (JRadioButton) getComponent(name, name + "-no");
    yesBtn.setSelected(value);
    noBtn.setSelected(!value);
  }

  protected Component getComponent(String itemName, String componentName) {
    Container item = (Container) getItem(itemName);
    for (int index = 0; index < item.getComponentCount(); index++) {
      Component comp = item.getComponent(index);
      if (comp.getName().equals(componentName)) {
        return comp;
      }
    }
    return null;
  }

  public JTextField geJTextField(String name) {
    Container item = (Container) getItem(name);
    return (JTextField) item;
  }

  @SuppressWarnings("rawtypes")
  public JComboBox<?> getJComboBox(String name) {
    Container item = (Container) getItem(name);
    return (JComboBox) item;
  }

  @SuppressWarnings("rawtypes")
  public String getItemText(String name) {
    Container item = (Container) getItem(name);
    if (item != null) {
      if (item instanceof JTextField) {
        return ((JTextField) item).getText();
      } else if (item instanceof JComboBox) {
        return (String) ((JComboBox) item).getSelectedItem();
      }
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  public Object getItemValue(String name) {
    Container item = (Container) getItem(name);
    if (item != null) {
      if (item instanceof JTextField) {
        return ((JTextField) item).getText();
      } else if (item instanceof JComboBox) {
        return ((JComboBox) item).getSelectedItem();
      }
    }
    return null;
  }

  public void addButton(Component button) {
    buttonsPanel.add(button);
  }

  public void addSection(FormPanel section) {
    sectionsPanel.add(section);
  }

  public void removeSection(FormPanel section) {
    sectionsPanel.remove(section);
  }

  public Component getItem(String name) {
    return fields.get(name);
  }

  public void removeItem(String name) {

    Component field = fields.get(name);
    Component label = labels.get(name);

    if (field != null) {
      fieldsPanel.remove(field);
    }
    if (label != null) {
      fieldsPanel.remove(label);
    }

    fields.remove(name);
    labels.remove(name);
  }

  public void layoutForm() {
    Group horizontalGroup = groupLayout.createSequentialGroup();
    Group verticalGroup = groupLayout.createSequentialGroup();

    Group hLabelGroup = groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING);
    Group hFieldGroup = groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING);

    horizontalGroup.addGroup(hLabelGroup).addGroup(hFieldGroup);

    groupLayout.setHorizontalGroup(horizontalGroup);
    groupLayout.setVerticalGroup(verticalGroup);

    for (Entry<String, Component> entry : labels.entrySet()) {
      String name = entry.getKey();
      Component label = entry.getValue();
      Component field = fields.get(name);

      if (label != null) {
        hLabelGroup.addComponent(label);
      }
      hFieldGroup.addComponent(field);

      ParallelGroup vParallelGroup = groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE);
      verticalGroup.addGroup(vParallelGroup.addComponent(field));
      if (label != null) {
        vParallelGroup.addComponent(label);
      }
    }
  }

}
