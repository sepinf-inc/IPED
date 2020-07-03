package br.gov.pf.labld.cases;

import java.awt.Color;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.UIManager;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.task.regex.RegexValidator;

public class FormUtils {

    public static boolean validateTextFieldNotEmpty(JTextField field) {
        return validateTextFieldNotEmpty(field, field.getText().trim());
    }

    public static boolean validateTextFieldNotEmpty(JTextField field, String text) {
        boolean valid = !text.isEmpty();
        setJTextValidationStatus(field, valid);
        return valid;
    }

    public static boolean validateTextFieldRegex(JTextField field, String regexName) {
        RegexValidator validator = new RegexValidator();
        validator.init(new File(Configuration.getInstance().configPath));
        String text = field.getText().trim();
        boolean valid = validator.validate(regexName, text);
        setJTextValidationStatus(field, valid);
        return valid;
    }

    private static void setJTextValidationStatus(JTextField field, boolean valid) {
        if (valid) {
            field.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
        } else {
            field.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
        }
    }

}
