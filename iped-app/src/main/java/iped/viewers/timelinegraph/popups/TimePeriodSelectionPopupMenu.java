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

import iped.viewers.timelinegraph.IpedChartPanel;
import iped.viewers.timelinegraph.IpedChartsPanel;

public class TimePeriodSelectionPopupMenu extends JPopupMenu implements ActionListener {
	IpedChartPanel ipedChartPanel = null;

	static HashMap<String, SimpleDateFormat> sdfMap = null;

	IpedChartsPanel ipedChartsPanel;

	public TimePeriodSelectionPopupMenu(IpedChartsPanel ipedChartsPanel) {
		this.ipedChartsPanel = ipedChartsPanel;
	}

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

	public TimePeriodSelectionPopupMenu(IpedChartPanel ipedChartPanel) {
		this.ipedChartPanel = ipedChartPanel;

		yearMenu = new JTimePeriodMenuItem("Year", Year.class);
		yearMenu.addActionListener(this);
		add(yearMenu);

		quarterMenu = new JTimePeriodMenuItem("Quarter", Quarter.class);
		quarterMenu.addActionListener(this);
		add(quarterMenu);

		monthMenu = new JTimePeriodMenuItem("Month", Month.class);
		monthMenu.addActionListener(this);
		add(monthMenu);

		weekMenu = new JTimePeriodMenuItem("Week", Week.class);
		weekMenu.addActionListener(this);
		add(weekMenu);

		dayMenu = new JTimePeriodMenuItem("Day", Day.class);
		dayMenu.addActionListener(this);
		add(dayMenu);

		hourMenu = new JTimePeriodMenuItem("Hour", Hour.class);
		hourMenu.addActionListener(this);
		add(hourMenu);

		minuteMenu = new JTimePeriodMenuItem("Minute", Minute.class);
		minuteMenu.addActionListener(this);
		add(minuteMenu);

		secondMenu = new JTimePeriodMenuItem("Second", Second.class);
		secondMenu.addActionListener(this);
		add(secondMenu);

		millisecondMenu = new JTimePeriodMenuItem("Millisecond", Millisecond.class);
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