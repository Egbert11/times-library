// Copyright 2012 Square, Inc.
package com.squareup.timessquare;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.SUNDAY;
import static java.util.Calendar.YEAR;
/**
 * Android component to allow picking a date from a calendar view (a list of
 * months). Must be initialized after inflation with
 * {@link #init(java.util.Date, java.util.Date, java.util.Date)}. The currently
 * selected date can be retrieved with {@link #getSelectedDate()}.
 */
public class CalendarPickerView extends ListView {
	private final CalendarPickerView.MonthAdapter adapter;
	private final DateFormat monthNameFormat;
	private final DateFormat weekdayNameFormat;
	private final DateFormat fullDateFormat;
	final List<MonthDescriptor> months = new ArrayList<MonthDescriptor>();
	final List<List<List<MonthCellDescriptor>>> cells = new ArrayList<List<List<MonthCellDescriptor>>>();
	
	final Calendar today = Calendar.getInstance();
	//车位共享日期列表
	private List<Calendar> selectedCalList = null;
	
	private final Calendar minCal = Calendar.getInstance();
	private final Calendar maxCal = Calendar.getInstance();
	private final Calendar monthCounter = Calendar.getInstance();
	private final MonthView.Listener listener = new CellClickedListener();
	public CalendarPickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		adapter = new MonthAdapter();
		setDivider(null);
		setDividerHeight(0);
		setAdapter(adapter);
		
		//11.08
//		for(int i = 0; i < 7; i++) {
//			Calendar cal = Calendar.getInstance();
//			cal.set(2014, 11, 10 + i * 2);
//			setMidnight(cal);
//			selectedCalList.add(cal);
//		}
		
		final int bg = getResources().getColor(R.color.calendar_bg);
		setBackgroundColor(bg);
		setCacheColorHint(bg);
		monthNameFormat = new SimpleDateFormat(context.getString(R.string.month_name_format));
		weekdayNameFormat = new SimpleDateFormat(context.getString(R.string.day_name_format));
		fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
	}
	/**
	 * All date parameters must be non-null and their
	 * {@link java.util.Date#getTime()} must not return 0. Time of day will be
	 * ignored. For instance, if you pass in {@code minDate} as 11/16/2012
	 * 5:15pm and {@code maxDate} as 11/16/2013 4:30am, 11/16/2012 will be the
	 * first selectable date and 11/15/2013 will be the last selectable date (
	 * {@code maxDate} is exclusive).
	 * 
	 * @param selectedDate
	 *            Initially selected date. Must be between {@code minDate} and
	 *            {@code maxDate}.
	 * @param minDate
	 *            Earliest selectable date, inclusive. Must be earlier than
	 *            {@code maxDate}.
	 * @param maxDate
	 *            Latest selectable date, exclusive. Must be later than
	 *            {@code minDate}.
	 * @param selectedCalList
	 * 			  The share days of one parking
	 */
	public void init(Date selectedDate, Date minDate, Date maxDate, List<Calendar> selectedCalList) {
		if (selectedDate == null || minDate == null || maxDate == null) {
			throw new IllegalArgumentException("All dates must be non-null.  "
					+ dbg(selectedDate, minDate, maxDate));
		}
		if (selectedDate.getTime() == 0 || minDate.getTime() == 0 || maxDate.getTime() == 0) {
			throw new IllegalArgumentException("All dates must be non-zero.  "
					+ dbg(selectedDate, minDate, maxDate));
		}
		if (minDate.after(maxDate)) {
			throw new IllegalArgumentException("Min date must be before max date.  "
					+ dbg(selectedDate, minDate, maxDate));
		}
		if (selectedDate.before(minDate) || selectedDate.after(maxDate)) {
			throw new IllegalArgumentException(
					"selectedDate must be between minDate and maxDate.  "
							+ dbg(selectedDate, minDate, maxDate));
		}
		// Clear previous state.
		this.selectedCalList = selectedCalList;
		cells.clear();
		months.clear();
		minCal.setTime(minDate);
		maxCal.setTime(maxDate);

		setMidnight(minCal);
		setMidnight(maxCal);
		// maxDate is exclusive: bump back to the previous day so if maxDate is
		// the first of a month,
		// we don't accidentally include that month in the view.
		maxCal.add(MINUTE, -1);
		// Now iterate between minCal and maxCal and build up our list of months
		// to show.
		monthCounter.setTime(minCal.getTime());
		final int maxMonth = maxCal.get(MONTH);
		final int maxYear = maxCal.get(YEAR);

		while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the
													// month.
				|| monthCounter.get(YEAR) < maxYear) // Up to the year.
				&& monthCounter.get(YEAR) < maxYear + 1) { // But not > next yr.
			MonthDescriptor month = new MonthDescriptor(monthCounter.get(MONTH),
					monthCounter.get(YEAR), monthNameFormat.format(monthCounter.getTime()));
			cells.add(getMonthCells(month, monthCounter, selectedCalList));
			Logr.d("Adding month %s", month);
			months.add(month);
			monthCounter.add(MONTH, 1);
		}
		adapter.notifyDataSetChanged();
	}
	private void scrollToSelectedMonth(final int selectedIndex) {
		post(new Runnable() {
			public void run() {
				smoothScrollToPosition(selectedIndex);
			}
		});
	}
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (months.isEmpty()) {
			throw new IllegalStateException(
					"Must have at least one month to display.  Did you forget to call init()?");
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
	/** Returns a string summarizing what the client sent us for init() params. */
	private static String dbg(Date startDate, Date minDate, Date maxDate) {
		return "startDate: " + startDate + "\nminDate: " + minDate + "\nmaxDate: " + maxDate;
	}
	
	/** Clears out the hours/minutes/seconds/millis of a Calendar. */
	private static void setMidnight(Calendar cal) {
		cal.set(HOUR_OF_DAY, 0);
		cal.set(MINUTE, 0);
		cal.set(SECOND, 0);
		cal.set(MILLISECOND, 0);
	}
	
	/**
	 * 
	 * @author Huang Jiaming
	 *
	 */
	private class CellClickedListener implements MonthView.Listener {
		/**
		 * cell点击监听事件
		 */
		public void handleClick(MonthCellDescriptor cell) {
			if (!betweenDates(cell.getDate(), minCal, maxCal)) {
				String errMessage = getResources().getString(R.string.invalid_date,
						fullDateFormat.format(minCal.getTime()),
						fullDateFormat.format(maxCal.getTime()));
				Toast.makeText(getContext(), errMessage, Toast.LENGTH_SHORT).show();
			} else {
				Date date = new Date();
				date = cell.getDate();  
				Calendar cal = Calendar.getInstance();
				cal.setTime(date);
				setMidnight(cal);
				//原本被选中，点击后取消
				if(cell.isSelected()){
					cell.setSelected(false);
					selectedCalList.remove(cal);
					Toast.makeText(getContext(), date + "被撤回", Toast.LENGTH_SHORT).show();
					Toast.makeText(getContext(), "被选中的日期数为：" + selectedCalList.size(), Toast.LENGTH_SHORT).show();
				}
				//原本未选中，点击后选中
				else {
					cell.setSelected(true);
					selectedCalList.add(cal);
					Toast.makeText(getContext(), date + "被选中", Toast.LENGTH_SHORT).show();
					Toast.makeText(getContext(), "被选中的日期数为：" + selectedCalList.size(), Toast.LENGTH_SHORT).show();
				}
				
//				// De-select the currently-selected cell.
//				selectedCell.setSelected(false);
//				// Select the new cell.
//				selectedCell = cell;
//				selectedCell.setSelected(true);
//				// Track the currently selected date value.
//				selectedCal.setTime(cell.getDate());
				// Update the adapter.
				adapter.notifyDataSetChanged();
			}
		}
	}
	private class MonthAdapter extends BaseAdapter {
		private final LayoutInflater inflater;
		private MonthAdapter() {
			inflater = LayoutInflater.from(getContext());
		}
		public boolean isEnabled(int position) {
			// Disable selectability: each cell will handle that itself.
			return false;
		}
		public int getCount() {
			return months.size();
		}
		public Object getItem(int position) {
			return months.get(position);
		}
		public long getItemId(int position) {
			return position;
		}
		public View getView(int position, View convertView, ViewGroup parent) {
			MonthView monthView = (MonthView) convertView;
			if (monthView == null) {
				monthView = MonthView.create(parent, inflater, weekdayNameFormat, listener, today);
			}
			monthView.init(months.get(position), cells.get(position));
			return monthView;
		}
	}
	/**
	 * 
	 * @param month  某年某月
	 * @param startCal 开始计算的日期
	 * @param selectedDate 已经选择的日期
	 * @return
	 */
	List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month, Calendar startCal,
			List<Calendar> selectedCalList) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(startCal.getTime());
		List<List<MonthCellDescriptor>> cells = new ArrayList<List<MonthCellDescriptor>>();
		cal.set(DAY_OF_MONTH, 1);
		int firstDayOfWeek = cal.get(DAY_OF_WEEK);
		cal.add(DATE, SUNDAY - firstDayOfWeek);
		while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month.getYear()) //
				&& cal.get(YEAR) <= month.getYear()) {
			Logr.d("Building week row starting at %s", cal.getTime());
			List<MonthCellDescriptor> weekCells = new ArrayList<MonthCellDescriptor>();
			cells.add(weekCells);
			for (int c = 0; c < 7; c++) {
				Date date = cal.getTime();
				boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
				//boolean isSelected = isCurrentMonth && sameDate(cal, selectedDate);
				boolean isSelected = isCurrentMonth && isSelected(cal, selectedCalList);
				boolean isSelectable = isCurrentMonth && betweenDates(cal, minCal, maxCal);
				boolean isToday = sameDate(cal, today);
				int value = cal.get(DAY_OF_MONTH);
				MonthCellDescriptor cell = new MonthCellDescriptor(date, isCurrentMonth,
						isSelectable, isSelected, isToday, value);
//				if (isSelected) {
//					//selectedCell = cell;
//					selectedCellList.add(cell);
//				}
				weekCells.add(cell);
				cal.add(DATE, 1);
			}
		}
		return cells;
	}
	
	
	/**
	 * 
	 * @param cal 当前的日期
	 * @param selectedCalList 被选择的日期列表
	 * @return 如果当前日期在被选择的日期列表，则返回true， 否则， 返回false
	 */
	private static boolean isSelected(Calendar cal, List<Calendar> selectedCalList) {
		if(selectedCalList.isEmpty())
			return false;
		for(int i = 0; i < selectedCalList.size(); i++){
			Calendar selectedDate = selectedCalList.get(i);
			if(sameDate(cal, selectedDate))
				return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param cal 当前日期
	 * @param selectedDate 被选择的日期
	 * @return 判断cal和selectedDate是否为同一日期，如果是，返回true，否则，返回false
	 */
	private static boolean sameDate(Calendar cal, Calendar selectedDate) {
		return cal.get(MONTH) == selectedDate.get(MONTH) && cal.get(YEAR) == selectedDate.get(YEAR)
				&& cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
	}
	private static boolean betweenDates(Calendar cal, Calendar minCal, Calendar maxCal) {
		final Date date = cal.getTime();
		return betweenDates(date, minCal, maxCal);
	}
	static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
		final Date min = minCal.getTime();
		return (date.equals(min) || date.after(min)) // >= minCal
				&& date.before(maxCal.getTime()); // && < maxCal
	}
}
