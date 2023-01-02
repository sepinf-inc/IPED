package iped.app.home.opencase;/*
 * @created 29/12/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.CaseInfo;
import iped.app.home.newcase.tabs.caseinfo.CaseInfoManager;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;
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
        CaseInfo caseInfo = getCaseInfoFromPath(casePath);
        JPanel rowPanel = caseInfo != null ? getCaseInfoJPanel(caseInfo, casePath) : getPathJLabel(casePath);
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

    private JPanel getCaseInfoJPanel(CaseInfo caseInfo, Path casePath){
        JPanel casePanel = new JPanel();
        casePanel.setLayout(new BoxLayout(casePanel, BoxLayout.PAGE_AXIS));
        casePanel.add(getCasePathJlabel(casePath));
        if( !StringUtils.isBlank(caseInfo.getCaseNumber()) )
            casePanel.add(new JLabel( getLabelText("Home.NewCase.CaseNumber", caseInfo.getCaseNumber()) ) );
        if( !StringUtils.isBlank(caseInfo.getCaseName()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.CaseName", caseInfo.getCaseName()) ));
        if( ! (caseInfo.getInvestigatedNames() == null || caseInfo.getInvestigatedNames().isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Investigated", caseInfo.getInvestigatedNames().stream().map(String::trim).collect(Collectors.joining(", "))) ));
        if( !StringUtils.isBlank(caseInfo.getRequestDate()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.RequestDate", caseInfo.getRequestDate()) ));
        if( !StringUtils.isBlank(caseInfo.getRequester()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Requester", caseInfo.getRequester()) ));
        if( !StringUtils.isBlank(caseInfo.getOrganizationName()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.OrganizationName", caseInfo.getOrganizationName()) ));
        if( !(caseInfo.getExaminers() == null || caseInfo.getExaminers().isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Examiners", caseInfo.getExaminers().stream().map(String::trim).collect(Collectors.joining(", "))  )) );
        if( !StringUtils.isBlank(caseInfo.getContact()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Contact", caseInfo.getContact()) ));
        if( !StringUtils.isBlank(caseInfo.getCaseNotes()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Notes", caseInfo.getCaseNotes()) ));
        if( !(caseInfo.getMaterials() == null || caseInfo.getMaterials().isEmpty()) )
            casePanel.add(new JLabel(getLabelText("Home.NewCase.Materials", caseInfo.getMaterials().stream().map(String::trim).collect(Collectors.joining(", "))) ));
        return casePanel;
    }

    private String getLabelText(String messageID, String text){
        return "<HTML><b>"+Messages.get(messageID)+": </b>" + text + "</html>";
    }

    private CaseInfo getCaseInfoFromPath(Path path){
        File caseInfoFile = Paths.get(path.toString(), "CaseInfo.json").toFile();
        CaseInfo caseInfo = null;
        if( caseInfoFile.exists() ){
            try {
                caseInfo = new CaseInfoManager().loadCaseInfo(caseInfoFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return caseInfo;
    }

}
