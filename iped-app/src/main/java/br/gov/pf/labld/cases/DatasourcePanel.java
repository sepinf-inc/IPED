package br.gov.pf.labld.cases;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JTextField;
import javax.swing.text.MaskFormatter;

import br.gov.pf.labld.cases.IpedCase.IpedDatasourceType;
import dpf.sp.gpinf.indexer.desktop.Messages;

class DatasourcePanel extends FormPanel {

  private static final long serialVersionUID = 9109944026028003727L;

  private NewCasePanel parentForm;

  private Map<String, JTextField> inputFields = new HashMap<>();
  private Map<String, JButton> chooseInputButtons = new HashMap<>();

  public DatasourcePanel(NewCasePanel parentForm, boolean removable) {
    super();
    this.parentForm = parentForm;
    this.createGUI(removable);
  }

  private void createGUI(boolean removable) {
    setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

    JTextField datasourceNameField = new JTextField(30);
    addItem("case.datasourcename", Messages.getString("Case.DataSourceName"), datasourceNameField);

    JComboBox<IpedDatasourceType> datasourceTypesCombo = new JComboBox<>(IpedDatasourceType.values());
    datasourceTypesCombo.setRenderer(new DatasourceTypeRenderer());
    datasourceTypesCombo.addActionListener(new TypeComboListener());
    addItem("case.datasourcetype", Messages.getString("Case.DataSourceType"), datasourceTypesCombo);

    String persodIdMask = Messages.getString("Case.PersonIdMask").trim();
    JTextField personIdField;
    if (persodIdMask.isEmpty()) {
      personIdField = new JTextField(30);
    } else {
      personIdField = new JFormattedTextField(createFormatter(persodIdMask));
    }
    addItem("case.personId", Messages.getString("Case.PersonId"), personIdField);

    String businessIdMask = Messages.getString("Case.BusinessIdMask").trim();
    JTextField businessIdField;
    if (businessIdMask.isEmpty()) {
      businessIdField = new JTextField(30);
    } else {
      businessIdField = new JFormattedTextField(createFormatter(businessIdMask));
    }
    addItem("case.businessId", Messages.getString("Case.BusinessId"), businessIdField);

    if (removable) {
      JButton removeDatasourceButton = new JButton(new RemoveDatasourceAction());
      removeDatasourceButton.setText(Messages.getString("Case.RemoveDatasource"));
      addButton(removeDatasourceButton);
    }

    JButton addFileButton = new JButton(new AddFileAction());
    addFileButton.setText(Messages.getString("Case.AddInput"));
    addButton(addFileButton);

    layoutForm();
    setGenericGUI();
  }

  private MaskFormatter createFormatter(String s) {
    MaskFormatter formatter = null;
    try {
      formatter = new MaskFormatter(s);
      formatter.setValueContainsLiteralCharacters(false);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
    return formatter;
  }

  public IpedDatasourceType getDatasourceType() {
    IpedDatasourceType type = (IpedDatasourceType) getItemValue("case.datasourcetype");
    return type;
  }

  public void setDatasourceType(IpedDatasourceType type) {
    switch (type) {
    case GENERIC:
      setGenericGUI();
      break;
    case BUSINESS:
      setBusinessGUI();
      break;
    case PERSON:
      setPersonGUI();
      break;
    }
    JComboBox<?> datasourceTypesCombo = getJComboBox("case.datasourcetype");
    datasourceTypesCombo.setSelectedItem(type);
  }

  public String getDatasourceName() {
    return this.geJTextField("case.datasourcename").getText().trim();
  }

  public void setDatasourceName(String dsName) {
    this.geJTextField("case.datasourcename").setText(dsName);
  }

  public Map<String, String> getExtras() {
    Map<String, String> extras = new HashMap<>();

    IpedDatasourceType type = (IpedDatasourceType) getItemValue("case.datasourcetype");
    String value = null;
    String property = null;
    if (type == IpedDatasourceType.PERSON) {
      value = getItemText("case.personId");
      property = Messages.get("Case.PersonIdProperty");
    } else if (type == IpedDatasourceType.BUSINESS) {
      value = getItemText("case.businessId");
      property = Messages.get("Case.BusinessIdProperty");
    }
    extras.put("value", value);
    extras.put("property", property);
    extras.put("type", type.name());

    return extras;
  }

  public void setExtras(Map<String, String> extras) {
    String id = extras.get("value");
    JTextField textField = null;
    if (id != null) {
      IpedDatasourceType type = getDatasourceType();
      if (type == IpedDatasourceType.PERSON) {
        textField = geJTextField("case.personId");
      } else if (type == IpedDatasourceType.BUSINESS) {
        textField = geJTextField("case.businessId");
      }
      if (textField != null) {
        textField.setText(id);
      }
    }
  }

  public void setInputEnabled(boolean enabled) {
    for (Entry<String, JTextField> entry : inputFields.entrySet()) {
      String key = entry.getKey();
      JTextField input = entry.getValue();
      JButton button = chooseInputButtons.get(key);
      input.setEnabled(enabled);
      button.setEnabled(enabled);
    }
    getJComboBox("case.datasourcetype").setEnabled(enabled);
    geJTextField("case.personId").setEnabled(enabled);
    geJTextField("case.businessId").setEnabled(enabled);
  }

  public List<String> getInputs() {
    List<String> inputs = new ArrayList<>(inputFields.size());
    for (JTextField inputField : inputFields.values()) {
      inputs.add(inputField.getText().trim());
    }
    return inputs;
  }

  public void addInput(boolean removable, String input) {
    addDatasourceItemGUI(removable, false, input);
  }

  public boolean validateForm() {
    boolean valid = true;

    valid = valid & FormUtils.validateTextFieldNotEmpty(this.geJTextField("case.datasourcename"));
    valid = valid & validateDatasourceInputs();
    valid = valid & validateExtraFields();

    return valid;
  }

  private boolean validateExtraFields() {
    boolean valid = true;

    JTextField textField = null;
    IpedDatasourceType type = (IpedDatasourceType) getItemValue("case.datasourcetype");

    String regexName = null;

    if (type == IpedDatasourceType.PERSON) {
      textField = this.geJTextField("case.personId");
      regexName = Messages.get("Case.PersonIdRegex");
    } else if (type == IpedDatasourceType.BUSINESS) {
      textField = this.geJTextField("case.businessId");
      regexName = Messages.get("Case.BusinessIdRegex");
    }

    if (textField != null) {
      String text = textField.getText();
      text = text.replaceAll("[-\\.\\/]", "").trim();
      boolean notEmpty = FormUtils.validateTextFieldNotEmpty(textField, text);
      valid = valid & notEmpty;

      if (notEmpty) {
        valid = valid & FormUtils.validateTextFieldRegex(textField, regexName);
      }
    }

    return valid;
  }

  private boolean validateDatasourceInputs() {
    boolean valid = true;
    for (JTextField inputField : inputFields.values()) {
      valid = valid & FormUtils.validateTextFieldNotEmpty(inputField);
    }
    return valid;
  }

  protected void addDatasourceItemGUI(boolean removable, boolean addLabel, String input) {
    JTextField datasourceInputField = new JTextField(30);
    datasourceInputField.setDisabledTextColor(Color.GRAY);
    datasourceInputField.setText(input);

    JButton chooseButton = new JButton();
    chooseButton.setMargin(new Insets(0, 0, 0, 0));

    ChooseInputAction chooseInputAction = new ChooseInputAction(this, datasourceInputField, chooseButton);
    datasourceInputField.addFocusListener(chooseInputAction);
    chooseButton.setAction(chooseInputAction);
    chooseButton.setText(Messages.getString("Case.CaseChoose"));

    String name = "case.input" + inputFields.size() + System.currentTimeMillis();
    inputFields.put(name, datasourceInputField);
    chooseInputButtons.put(name, chooseButton);

    List<Component> comps = new ArrayList<>(Arrays.asList(datasourceInputField, chooseButton));

    if (removable) {
      JButton removeBtn = new JButton(new RemoveFileAction(name));
      removeBtn.setMargin(new Insets(0, 0, 0, 0));
      removeBtn.setText(Messages.getString("Case.RemoveFile"));

      comps.add(removeBtn);
    }

    String label = Messages.getString("Case.DatasourceInput");
    if (addLabel) {
      addComplexItem(name, label, comps);
    } else {
      addComplexItem(name, (Component) null, comps);
    }
    layoutForm();
  }

  private void removeDatasourceItemGUI(String name) {
    this.geJTextField("case.datasourcename").requestFocusInWindow();
    inputFields.remove(name);
    chooseInputButtons.remove(name);
    removeItem(name);
    revalidate();
    repaint();
  }

  private void removeDatasource() {
    parentForm.removeDatasource(this);
  }

  private final class RemoveFileAction extends AbstractAction {

    private static final long serialVersionUID = 6216832289837530830L;

    private String name;

    public RemoveFileAction(String name) {
      super();
      this.name = name;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      removeDatasourceItemGUI(name);
    }

  }

  private class TypeComboListener implements ActionListener {

    @SuppressWarnings("unchecked")
    @Override
    public void actionPerformed(ActionEvent e) {
      JComboBox<String> cb = (JComboBox<String>) e.getSource();
      IpedDatasourceType selected = (IpedDatasourceType) cb.getSelectedItem();
      setDatasourceType(selected);
    }

  }

  private final class RemoveDatasourceAction extends AbstractAction {
    private static final long serialVersionUID = 5779260040163094566L;

    @Override
    public void actionPerformed(ActionEvent e) {
      removeDatasource();
    }
  }

  private final class AddFileAction extends AbstractAction {
    private static final long serialVersionUID = 5779260040163094566L;

    @Override
    public void actionPerformed(ActionEvent e) {
      addDatasourceItemGUI(true, false, "");
    }
  }

  private void setGenericGUI() {
    setItemVisible("case.personId", false);
    setItemVisible("case.businessId", false);
  }

  private void setBusinessGUI() {
    setItemVisible("case.personId", false);
    setItemVisible("case.businessId", true);
  }

  private void setPersonGUI() {
    setItemVisible("case.personId", true);
    setItemVisible("case.businessId", false);
  }

}