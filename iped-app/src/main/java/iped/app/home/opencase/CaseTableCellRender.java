package iped.app.home.opencase;/*
                               * @created 29/12/2022
                               * @project IPED
                               * @author Thiago S. Figueiredo
                               */

import static java.util.Comparator.comparing;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.lang3.StringUtils;

import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.engine.data.ReportInfo;

public class CaseTableCellRender extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        DefaultTableCellRenderer result = (DefaultTableCellRenderer) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Path casePath = (Path) value;
        // Check if caseinfo file exists
        ReportInfo reportInfo = getCaseInfoFromPath(casePath);
        JPanel rowPanel = reportInfo != null ? getCaseInfoJPanel(reportInfo, casePath) : getPathJLabel(casePath);
        setRowBackgroundColor(row, isSelected, rowPanel, hasFocus);
        rowPanel.setBorder(BorderFactory.createEmptyBorder(3, 15, 3, 15));
        table.setRowHeight(row, (int) rowPanel.getPreferredSize().getHeight());
        return rowPanel;
    }

    private void setRowBackgroundColor(int row, boolean isSelected, JPanel casePanel, boolean hasFocus) {
        if (isSelected)
            casePanel.setBackground(StyleManager.getColumnRowSelectedBackground());
        else
            casePanel.setBackground(StyleManager.getColumnRowUnSelectedBackground(row));
        if (hasFocus)
            casePanel.setBorder(StyleManager.getColumnRowFocusBorder());

    }

    private JPanel getPathJLabel(Path casePath) {
        JPanel casePanel = new JPanel();
        casePanel.setLayout(new BoxLayout(casePanel, BoxLayout.LINE_AXIS));
        casePanel.add(getCasePathJlabel(casePath));
        return casePanel;
    }

    private JLabel getCasePathJlabel(Path casePath) {
        return new JLabel(getLabelText("Home.OpenCase.CaseLocation", casePath.toString()));
    }

    private JPanel getCaseInfoJPanel(ReportInfo reportInfo, Path casePath) {
        JPanel casePanel = new JPanel();
        casePanel.setLayout(new BoxLayout(casePanel, BoxLayout.PAGE_AXIS));
        casePanel.add(getCasePathJlabel(casePath));
        if (!StringUtils.isBlank(reportInfo.caseNumber))
            casePanel.add(new JLabel(getLabelText("ReportDialog.Investigation", reportInfo.caseNumber)));
        if (!StringUtils.isBlank(reportInfo.reportTitle))
            casePanel.add(new JLabel(getLabelText("ReportDialog.ReportTitle", reportInfo.reportTitle)));
        if (!(reportInfo.investigatedName == null || reportInfo.investigatedName.isEmpty()))
            casePanel.add(new JLabel(getLabelText("ReportDialog.InvestigatedNames", reportInfo.investigatedName.stream().map(String::trim).collect(Collectors.joining(", ")))));
        if (!StringUtils.isBlank(reportInfo.requestDate))
            casePanel.add(new JLabel(getLabelText("ReportDialog.RequestDate", reportInfo.requestDate)));
        if (!StringUtils.isBlank(reportInfo.requester))
            casePanel.add(new JLabel(getLabelText("ReportDialog.Requester", reportInfo.requester)));
        if (!StringUtils.isBlank(reportInfo.organizationName))
            casePanel.add(new JLabel(getLabelText("ReportDialog.organizationName", reportInfo.organizationName)));
        if (!(reportInfo.examiners == null || reportInfo.examiners.isEmpty()))
            casePanel.add(new JLabel(getLabelText("ReportDialog.Examiner", reportInfo.examiners.stream().map(String::trim).collect(Collectors.joining(", ")))));
        if (!StringUtils.isBlank(reportInfo.contact))
            casePanel.add(new JLabel(getLabelText("ReportDialog.contact", reportInfo.contact)));
        if (!StringUtils.isBlank(reportInfo.caseNotes))
            casePanel.add(new JLabel(getLabelText("ReportDialog.caseNotes", reportInfo.caseNotes)));
        if (!(reportInfo.evidences == null || reportInfo.evidences.isEmpty()))
            casePanel.add(new JLabel(getLabelText("ReportDialog.Evidences", reportInfo.evidences.stream().sorted(comparing(ReportInfo.EvidenceDesc::getId)).map(ReportInfo.EvidenceDesc::getDesc).collect(Collectors.joining(", ")))));
        return casePanel;
    }

    private String getLabelText(String messageID, String text) {
        return "<HTML><b>" + Messages.get(messageID) + ": </b>" + text + "</html>";
    }

    private ReportInfo getCaseInfoFromPath(Path path) {
        File caseInfoFile = Paths.get(path.toString(), "CaseInfo.json").toFile();
        ReportInfo reportInfo = null;
        if (caseInfoFile.exists()) {
            try {
                reportInfo = new ReportInfo();
                reportInfo.readJsonInfoFile(caseInfoFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return reportInfo;
    }

}
