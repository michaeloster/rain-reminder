package com.rainreminder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RainReportsScheduler {
	public static void main(String[] args) {

		long currentTimeMillis = System.currentTimeMillis();
		int delay = 3600;

		// Convert time from UTC to PST
		currentTimeMillis = currentTimeMillis - 25200000;

		// Mod the current time of day (in milliseconds) by the number of
		// milliseconds in an hour, to get current milliseconds past top of the
		// hour, then divide by 1000 for seconds
		long offsetInSec = ((currentTimeMillis % 3600000L) / 1000);
		System.out.println("The current offset from the top of the hour (in seconds) is: " + offsetInSec);
		delay = delay - (int) offsetInSec;
		System.out.println("The ScheduleTask will run hourly starting in this many seconds: " + delay);

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
		ScheduleTask scheduleTask = new ScheduleTask();
		executor.scheduleAtFixedRate(scheduleTask, delay, 3600, TimeUnit.SECONDS);
	}
}