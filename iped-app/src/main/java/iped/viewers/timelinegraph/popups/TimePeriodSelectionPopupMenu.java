package iped.viewers.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Month;
import org.jfree.data.time.Quarter;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimePeriod;
import org.jfree.data.time.Week;
import org.jfree.data.time.Year;

import iped.app.ui.Messages;
import iped.viewers.timelinegraph.IpedChartsPanel;

public class TimePeriodSelectionPopupMenu extends JPopupMenu implements ActionListener {

	static HashMap<String, SimpleDateFormat> sdfMap = null;

	IpedChartsPanel ipedChartsPanel;

	JTimePeriodMenuItem yearMenu;
	JTimePeriodMenuItem quarterMenu;
	JTimePeriodMenuItem monthMenu;
	JTimePeriodMenuItem weekMenu;
	JTimePeriodMenuItem dayMenu;
	JTimePeriodMenuItem hourMenu;
	JTimePeriodMenuItem minuteMenu;
	JTimePeriodMenuItem secondMenu;
	JTimePeriodMenuItem millisecondMenu;

	class JTimePeriodMenuItem extends JMenuItem{
		Class<? extends TimePeriod> timePeriodClass;

		public JTimePeriodMenuItem(String value, Class<? extends TimePeriod> timePeriodClass) {
			super(value);
			this.timePeriodClass = timePeriodClass;
		}

		public Class<? extends TimePeriod> getTimePeriodClass() {
			return timePeriodClass;
		}
	}

	static {
		sdfMap = new HashMap<String, SimpleDateFormat> ();
		sdfMap.put("Year",new SimpleDateFormat("YYYY"));
		sdfMap.put("Quarter",new SimpleDateFormat("YYYY"));
		sdfMap.put("Month",new SimpleDateFormat("MM-YYYY"));
		sdfMap.put("Week",new SimpleDateFormat("WW MM-YYYY"));
		sdfMap.put("Day",new SimpleDateFormat("dd-MM-YYYY"));
		sdfMap.put("Hour",new SimpleDateFormat("dd-MM-YYYY HH:'00'"));
		sdfMap.put("Minute",new SimpleDateFormat("dd-MM-YYYY HH:mm"));
		sdfMap.put("Second",new SimpleDateFormat("dd-MM-YYYY HH:mm:ss"));
		sdfMap.put("Millisecond",new SimpleDateFormat("dd-MM-YYYY HH:mm:ss"));
	}

	public TimePeriodSelectionPopupMenu(IpedChartsPanel ipedChartsPanel) {
		this.ipedChartsPanel = ipedChartsPanel;

		yearMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Year"), Year.class);
		yearMenu.setActionCommand("Year");
		yearMenu.addActionListener(this);
		add(yearMenu);

		quarterMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Quarter"), Quarter.class);
		quarterMenu.setActionCommand("Quarter");
		quarterMenu.addActionListener(this);
		add(quarterMenu);

		monthMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Month"), Month.class);
		monthMenu.setActionCommand("Month");
		monthMenu.addActionListener(this);
		add(monthMenu);

		weekMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Week"), Week.class);
		weekMenu.setActionCommand("Week");
		weekMenu.addActionListener(this);
		add(weekMenu);

		dayMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Day"), Day.class);
		dayMenu.setActionCommand("Day");
		dayMenu.addActionListener(this);
		add(dayMenu);

		hourMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Hour"), Hour.class);
		hourMenu.setActionCommand("Hour");
		hourMenu.addActionListener(this);
		add(hourMenu);

		minuteMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Minute"), Minute.class);
		minuteMenu.setActionCommand("Minute");
		minuteMenu.addActionListener(this);
		add(minuteMenu);

		secondMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Second"), Second.class);
		secondMenu.setActionCommand("Second");
		secondMenu.addActionListener(this);
		add(secondMenu);

		millisecondMenu = new JTimePeriodMenuItem(Messages.getString("TimeLineGraph.Millisecond"), Millisecond.class);
		millisecondMenu.setActionCommand("Millisecond");
		millisecondMenu.addActionListener(this);
		add(millisecondMenu);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		ipedChartsPanel.setTimePeriodClass(((JTimePeriodMenuItem) e.getSource()).getTimePeriodClass());
		ipedChartsPanel.setTimePeriodString(e.getActionCommand());
		ipedChartsPanel.refreshChart();
	}
}