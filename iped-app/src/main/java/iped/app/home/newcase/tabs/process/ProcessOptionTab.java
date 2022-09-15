package iped.app.home.newcase.tabs.process;

/*
 * @created 13/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class ProcessOptionTab extends DefaultPanel {

    private JTable jtableTasks;
    private TasksTableModel tasksTableModel;

    public ProcessOptionTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    @Override
    protected void createAndShowGUI() {
        this.setLayout( new BorderLayout() );
        setBorder(new EmptyBorder(10,10,10,10));
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTitlePanel(){
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel("Opções de processamento");
        labelTitle.setFont(new Font("Arial Bold", Font.PLAIN, 28));
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    private JPanel createFormPanel(){
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout( panelForm, BoxLayout.PAGE_AXIS ));
        panelForm.setBackground(Color.white);
        setupProfilesPanel(panelForm);
        panelForm.add( Box.createRigidArea( new Dimension(10, 10) ) );
        setupTasksTables(panelForm);
        return panelForm;
    }

    private void setupProfilesPanel(JPanel panel){
        JPanel buttonPanel = new JPanel();
        //buttonPanel.setLayout(new BoxLayout( buttonPanel, BoxLayout.LINE_AXIS ));
        buttonPanel.setBackground(Color.white);
        buttonPanel.add( new JLabel("Perfil de execução:") );
        buttonPanel.add( new JComboBox<>() );
        buttonPanel.add( new JButton("Criar novo perfil") );
        panel.add(buttonPanel);
    }

    private void setupTasksTables(JPanel panel){
        ArrayList<Task> taskArrayList = new ArrayList<>();
        Task task = new Task();
        task.setName("Calcular Hash");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Calcular Hash do PhotoDNA");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Pesquisa de Hash no banco de dados do IPED");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Pesquisa de PhotoDNA no banco de dados do IPED");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Detecção de Nudez");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Detecção de nudez usando o algoritmo de aprendizagem profundo do Yahoo OpenNSFW");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Detecção e decodificação de QRCode");
        taskArrayList.add(task);
        task = new Task();
        task.setName("Ignorar do processamento e do caso arquivos duplicados com o mesmo hash");
        taskArrayList.add(task);

        tasksTableModel = new TasksTableModel(taskArrayList);
        jtableTasks = new JTable();
        setupTableLayout();
        panel.add( new JScrollPane(jtableTasks));
    }

    private void setupTableLayout(){
        jtableTasks.setFillsViewportHeight(true);
        jtableTasks.setRowHeight(30);
        jtableTasks.setModel(tasksTableModel);
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellRenderer( new TableTaskOptionsCellRenderer() );
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setCellEditor( new TableTaskOptionsCellEditor(new JCheckBox()) );

        jtableTasks.getColumn( jtableTasks.getColumnName(0)).setMaxWidth(30);
        jtableTasks.getColumn( jtableTasks.getColumnName(2)).setMaxWidth(60);
    }

    private Component createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton("Voltar");
        buttoCancel.addActionListener( e -> NewCaseContainerPanel.getInstance().goToPreviousTab());
        JButton buttonNext = new JButton("Próximo");
        buttonNext.addActionListener( e -> NewCaseContainerPanel.getInstance().goToNextTab());
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

}


