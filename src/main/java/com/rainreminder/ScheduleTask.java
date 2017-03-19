package com.rainreminder;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class ScheduleTask implements Runnable {
	ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

	long currentTimeMillis = System.currentTimeMillis();
	String delaybuf = "";
	int timeofday = 0;
	int delay = 0;
	long offsetInMin = (((System.currentTimeMillis() - 25200000) % 86400000L) / 60000);
	String database_url = "";
	String database_username = "";
	String database_password = "";

	public ScheduleTask() {
	}

	@Override
	public void run() {
		// Read credentials from config.properties file
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("config.properties"));
			database_url = prop.getProperty("database_url");
			database_username = prop.getProperty("database_username");
			database_password = prop.getProperty("database_password");
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("ScheduleTask executing");
		try {
			// Get a connection to the database
			Connection myConn = DriverManager.getConnection(database_url, database_username, database_password);

			// Create a statement
			Statement myStmt = myConn.createStatement();

			// Execute SQL query
			ResultSet myRs = myStmt.executeQuery("select * from users_by_phone_number");

			// Process the result set
			while (myRs.next()) {
				boolean report = Boolean.parseBoolean(myRs.getString("report"));
				if (report) {
					System.out.println("Report boolean deemed true. Report variable value is: " + report);
					// Retrieve report_time - a string with 24 hour format
					// (XX:XX) &
					// generate int timeofday - a value >= 0 (midnight aka
					// 00:00) <=
					// 1439 (23:59)
					delaybuf = myRs.getString("report_time");
					timeofday = (Integer.parseInt(delaybuf.substring(0, 2)) * 60);
					timeofday += Integer.parseInt(delaybuf.substring(3, 5));

					delay = timeofday - (int) offsetInMin;
					if (delay >= 0 && delay < 60) {
						System.out.println(
								"Reminder scheduled in " + delay + " minute(s) for user: " + myRs.getString("name"));

						ForecastTask forecastTask = new ForecastTask(myRs.getString("name"), myRs.getInt("zip_code"),
								myRs.getString("latitude"), myRs.getString("longitude"), myRs.getString("city"),
								myRs.getString("state"), myRs.getString("phone_number"));
						executor.schedule(forecastTask, delay, TimeUnit.MINUTES);
					} else {
						System.out.println(
								"User \"" + myRs.getString("name") + "\" has no reports scheduled for the next hour.");
					}
				} else {
					System.out.println(
							"This user in the table doesn't have reminders set to on. Report variable value is:"
									+ report);
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		try {
			executor.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("thread is shutting down");
		executor.shutdown();
	}

	public String removeFirstChar(String s) {
		if (s == null || s.length() == 0) {
			return s;
		}
		return s.substring(1, s.length());
	}

	public String removeLastChar(String s) {
		if (s == null || s.length() == 0) {
			return s;
		}
		return s.substring(0, s.length() - 1);
	}
}