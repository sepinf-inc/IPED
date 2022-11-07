package iped.app.home.newcase.tabs.process;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;

import org.reflections.Reflections;

import iped.engine.task.AbstractTask;

public class AbstractTaskClassPopupMenu extends JPopupMenu implements ActionListener{

    private static final long serialVersionUID = 734426681117556452L;
    List<AbstractTask> taskArrayList;
    private JTable jtable;
    
    class JAbstractTaskClassMenuItem extends JMenuItem{
        private static final long serialVersionUID = -2921811070681015925L;
        Class<? extends AbstractTask> taskClass;

        public JAbstractTaskClassMenuItem(Class<? extends AbstractTask> aClass) {
            super(aClass.getName());
            this.taskClass = aClass;
        }
        
        public Class<? extends AbstractTask> getAbstractTaskClass(){
            return taskClass;
        }
    }

    public AbstractTaskClassPopupMenu(JTable jtable) {
        this.jtable = jtable;        
        this.taskArrayList = ((TasksTableModel)jtable.getModel()).getTaskList();
        List<Class<? extends AbstractTask>> installedClasses = new ArrayList<Class<? extends AbstractTask>>();
        for (Iterator<AbstractTask> iterator = taskArrayList.iterator(); iterator.hasNext();) {
            AbstractTask abstractTask = iterator.next();
            installedClasses.add(abstractTask.getClass());            
        }
        
        Reflections reflections = new Reflections("iped.engine.task");
        Set<Class<? extends AbstractTask>> classes = reflections.getSubTypesOf(iped.engine.task.AbstractTask.class);
        for(Class<? extends AbstractTask> aClass : classes) {
            if(!Modifier.isAbstract(aClass.getModifiers())) {
                if(!installedClasses.contains(aClass)) {
                    JMenuItem item = new JAbstractTaskClassMenuItem(aClass);
                    item.addActionListener(this);
                    add(item);
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource() instanceof JAbstractTaskClassMenuItem) {
            JAbstractTaskClassMenuItem item = (JAbstractTaskClassMenuItem) e.getSource();
            try {
                taskArrayList.add(item.getAbstractTaskClass().getDeclaredConstructor().newInstance());
                this.remove(item);
                ((TasksTableModel)jtable.getModel()).fireTableRowsInserted(taskArrayList.size()-1, taskArrayList.size()-1);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void show(Component invoker, int x, int y) {
        y = y - this.getPreferredSize().height;
        super.show(invoker, x, y);
    }

}
