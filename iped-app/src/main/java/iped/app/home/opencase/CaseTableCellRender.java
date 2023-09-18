package iped.app.home.opencase;/*
 * @created 29/12/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.CaseInfo;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
import iped.engine.data.ReportInfo;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class CaseTableCellRender extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        DefaultTableCellRenderer result = (DefaultTableCellRenderer) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        Path casePath = (Path) value;
        //Check if caseinfo file exists
        ReportInfo reportInfo = getCaseInfoFromPath(casePath);
        JPanel rowPanel = reportInfo != null ? getCaseInfoJPanel(reportInfo, casePath) : getPathJLabel(casePath);
        setRowBackgroundColor(row, isSelected, rowPanel, hasFocus);
        rowPanel.setBorder(BorderFactory.createEmptyBorder(3, 15, 3, 15));
        table.setRowHeight(row, (int) rowPanel.getPreferredSize().getHeight());
        return rowPanel;
    }

    private void setRowBackgroundColor(int row, boolean isSelected, JPanel casePanel, boolean hasFocus){
        if(isSelected)
            casePanel.setBackground(StyleManager.getColumnRowSelectedBackground());
        else
            casePanel.setBackground( StyleManager.getColumnRowUnSelectedBackground(row) );
        if(hasFocus)
            casePanel.setBorder( StyleManager.getColumnRowFocusBorder() );


    }

    private JPanel getPathJLabel(Path casePath){
        JPanel casePanel = new JPanel();
        casePanel.setLayout(new BoxLayout(casePanel, BoxLayout.LINE_AXIS));
        casePanel.add(getCasePathJlabel(casePath));
        return casePanel;
    }

    private JLabel getCasePathJlabel(Path casePath){
        return new JLabel(getLabelText("Home.OpenCase.CaseLocation", casePath.toString()));
    }

    private JPanel getCaseInfoJPanel(ReportInfo reportInfo, Path casePath){
        JPanel casePanel = new JPanel();
        casePanel.setLayout(new BoxLayout(casePanel, BoxLayout.PAGE_AXIS));
        casePanel.add(getCasePathJlabel(casePath));
        if( !StringUtils.isBlank(reportInfo.caseNumber) )
            casePanel.add(new JLabel( getLabelText("Home.NewCase.CaseNumber", reportInfo.caseNumber) ) );
        if( !StringUtils.isBlank(reportInfo.reportTitle) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.CaseName", reportInfo.reportTitle) ));
        if( ! (reportInfo.investigatedName == null || reportInfo.investigatedName.isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Investigated", reportInfo.investigatedName.stream().map(String::trim).collect(Collectors.joining(", "))) ));
        if( !StringUtils.isBlank(reportInfo.requestDate) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.RequestDate", reportInfo.requestDate) ));
        if( !StringUtils.isBlank(reportInfo.requester) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Requester", reportInfo.requester) ));
        if( !StringUtils.isBlank(reportInfo.organizationName) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.OrganizationName", reportInfo.organizationName) ));
        if( !(reportInfo.examiners == null || reportInfo.examiners.isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Examiners", reportInfo.examiners.stream().map(String::trim).collect(Collectors.joining(", "))  )) );
        if( !StringUtils.isBlank(reportInfo.contact) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Contact", reportInfo.contact) ));
        if( !StringUtils.isBlank(reportInfo.caseNotes) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Notes", reportInfo.caseNotes) ));
        /*if( !(reportInfo.evidences == null || reportInfo.evidences.isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Materials", reportInfo.evidences.stream().map(String::trim).collect(Collectors.joining(", "))) ));*/
        return casePanel;
    }

    private String getLabelText(String messageID, String text){
        return "<HTML><b>"+Messages.get(messageID)+": </b>" + text + "</html>";
    }

    private ReportInfo getCaseInfoFromPath(Path path){
        File caseInfoFile = Paths.get(path.toString(), "CaseInfo.json").toFile();
        ReportInfo reportInfo = null;
        if( caseInfoFile.exists() ){
            try {
                reportInfo =  ReportInfo.readReportInfoFile(caseInfoFile); //new CaseInfoManager().loadCaseInfo(caseInfoFile);
            } catch (ClassNotFoundException | IOException ex) {
                ex.printStackTrace();
            }
        }
        return reportInfo;
    }

}
