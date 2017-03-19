/*Rain Reminders v1.0.0
 * By Michael Oster
 */

package com.rainreminder.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.csvreader.CsvReader;
import com.github.dvdme.ForecastIOLib.FIOHourly;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/*
 * Desired Functionality:
First reply to a new text message:
"Hello New User. Would you like to receive a rain report from me at your chosen time of day only when
there's a chance of rain within the next 48 hours in your zip code? If so, say "Reminder XX:XX", with any time of day on a 24 hour clock.
For example, to set your reminders to 3:15pm say "Reminder 15:15".
Based on your phone number I've set your location to ___, ___. To change it say "Zip" followed by a 5 digit US Zip Code.
For a full list of my commands, say "commands"

Default reply:
"No commands detected. Would you like to receive a rain report from me at your chosen time of day only when
there's a chance of rain within the next 48 hours in your zip code? If so, say "Reminder XX:XX", with any time of day on a 24 hour clock.
For example, to set your reminders to 3:15pm say "Reminder 15:15".
For a full list of my commands, say "commands"

"commands" reply:
If you say "Report Now" I will send you the current weather at your location.
If you message me the word "forecast" I will send you the forecast for the upcoming week.
Right now your location is set to ___, ___, but to change it just message me a zip code."
 */
public class SMSReplyServlet extends HttpServlet {

	// service() responds to both GET and POST requests.
	// You can also use doGet() or doPost()

	public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {

		// Program Instantiation
		System.out.println("\nSMS Reply Servelet Triggered. Beginning of program output.\n");

		// Grab some parameters from the Parameter Map of the inbound SMS
		String fromNumber = request.getParameter("From");
		String fromZip = request.getParameter("FromZip");
		String messageBody = request.getParameter("Body");

		// trim +1 from number (assumes it came from US country code)
		fromNumber = removeFirstChar(fromNumber);
		fromNumber = removeFirstChar(fromNumber);

		String latitude = "";
		String longitude = "";
		String city = "";
		String state = "";
		String messageOut = "";
		boolean newUser = true;
		String lookUpNum = "";
		String lookUpZip = "";
		String newName = "";

		double[] probabilityArr = new double[50];
		double[] intensityArr = new double[50];
		String title = "Rain Report for ";
		String filenamePrefix = "src/main/webapp/reports/";
		String filename = "";
		String darksky_api_key = "";
		String twilio_account_sid = "";
		String twilio_auth_token = "";
		String twilio_phone_number = "";
		String host_domain = "";
		String database_url = "";
		String database_username = "";
		String database_password = "";

		// Read credentials from config.properties file
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("config.properties"));
			darksky_api_key = prop.getProperty("darksky_api_key");
			twilio_account_sid = prop.getProperty("twilio_account_sid");
			twilio_auth_token = prop.getProperty("twilio_auth_token");
			twilio_phone_number = prop.getProperty("twilio_phone_number");
			host_domain = prop.getProperty("host_domain");
			database_url = prop.getProperty("database_url");
			database_username = prop.getProperty("database_username");
			database_password = prop.getProperty("database_password");
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Start tryihng to read user database
		System.out.println("Establishing database connection.");
		try {
			// Get a connection to the database
			Connection myConn = DriverManager.getConnection(database_url, database_username, database_password);

			// Create a statement
			Statement myStmt = myConn.createStatement();

			// Execute SQL query
			ResultSet myRs = myStmt.executeQuery("select * from users_by_phone_number");
			System.out.println("Checking database for your ID (phone number)...");

			// Process the result set
			while (myRs.next()) {
				System.out.println("Checking database for your user id.");
				lookUpNum = myRs.getString("phone_number");
				System.out.println("lookUpNum=" + lookUpNum + " FromNumber=" + fromNumber);

				if (lookUpNum.equals(fromNumber)) {
					System.out.println("You already exist in our DB: " + myRs.getString("name"));
					newUser = false;
				}
			}
			if (newUser == true) {
				System.out.println("New user detected.");

				// Look up fromZips zip_code to fill lat, long, city, and state
				// variables
				try {
					CsvReader products = new CsvReader("zips.csv");
					products.readHeaders();
					try {
						while (products.readRecord()) {
							lookUpZip = products.get(products.getHeader(0));
							if (lookUpZip.equals(fromZip)) {
								System.out.println("Matching Zip Code found. LookUpZip is equal to: " + lookUpZip);

								latitude = products.get(products.getHeader(2));
								longitude = products.get(products.getHeader(3));
								city = products.get(products.getHeader(4));
								state = products.get(products.getHeader(5));
								break;
							}

						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e2) {
					e2.printStackTrace();
				}

				// Trim the quotes and spaces from the variables fed in from
				// spreadsheet
				latitude = removeLastChar(latitude);
				latitude = removeFirstChar(latitude);
				latitude = removeFirstChar(latitude);
				longitude = removeLastChar(longitude);
				longitude = removeFirstChar(longitude);
				longitude = removeFirstChar(longitude);
				city = removeLastChar(city);
				city = removeFirstChar(city);
				city = removeFirstChar(city);
				state = removeLastChar(state);
				state = removeFirstChar(state);
				state = removeFirstChar(state);

				System.out.println("Latitude:" + latitude);
				System.out.println("Longitude:" + longitude);
				System.out.println("City:" + city);
				System.out.println("State:" + state);

				// Add new user to the database
				myStmt.executeUpdate(
						"INSERT INTO `myusers`.`users_by_phone_number` (`phone_number`, `name`, `zip_code`, `latitude`, `longitude`, "
								+ "`city`, `state`, `email`, `report`, `report_time`) VALUES ('" + fromNumber
								+ "', 'NewUser', '" + fromZip + "', '" + latitude + "', '" + longitude + "', '" + city
								+ "', '" + state + "', 'none', '0', '12:00');");
				System.out.println("NEW USER ADDED TO DATABASE");

				// Append new user message to the messageOut buffer
				messageOut += "Hello New User. Due to your phone number your zip code has been set to " + fromZip + " ("
						+ city + ", " + state
						+ "). For a list of commands, send me a message that contains the words \"commands\".";

			}

			// Look for report enable command
			Pattern p2 = Pattern.compile(".*report (\\d{2}:\\d{2}).*", Pattern.CASE_INSENSITIVE);
			Matcher m2 = p2.matcher(messageBody);
			// Here report code is detected, and the users record is updated in
			// database
			if (m2.matches()) {
				System.out.println("Report enable code detected.");
				// Here is the process of updating the database
				myStmt.executeUpdate("UPDATE `myusers`.`users_by_phone_number` SET `report`='true', `report_time`='"
						+ m2.group(1) + "' WHERE `phone_number`='" + fromNumber + "';");
				System.out.println("User Table sucessfully updated with new report time. ");
				messageOut += "Your report time has been changed to " + m2.group(1) + ". ";

			} else
				System.out.println("Report enable code not detected.");

			// Look for report disable command
			p2 = Pattern.compile(".*report off.*", Pattern.CASE_INSENSITIVE);
			m2 = p2.matcher(messageBody);
			// Here report disable code is detected, and the users record is
			// updated in database
			if (m2.matches()) {
				System.out.println("Report disable code detected.");
				// Here is the process of updating the database
				myStmt.executeUpdate(
						"UPDATE `myusers`.`users_by_phone_number` SET `report`='false' WHERE `phone_number`='"
								+ fromNumber + "';");
				System.out.println("User Table sucessfully updated with report disabled. ");
				messageOut += "Your daily report has been disabled. ";

			} else
				System.out.println("Report disable code not detected.");

			// Look for name command
			p2 = Pattern.compile(".*name ([a-zA-Z]+).*", Pattern.CASE_INSENSITIVE);
			m2 = p2.matcher(messageBody);
			// Here a name code is detected, and the users name is updated in
			// database
			if (m2.matches()) {
				System.out.println("Name change request detected.");
				newName = m2.group(1);
				// Here is the process of updating the database
				if (newName.length() <= 45) {
					myStmt.executeUpdate("UPDATE `myusers`.`users_by_phone_number` SET `name`='" + m2.group(1)
							+ "' WHERE `phone_number`='" + fromNumber + "';");
					System.out.println("User Table sucessfully updated with new user name.\n");
					messageOut += "Your name has been changed to " + m2.group(1) + ". ";
				} else {
					System.out.println("User requested a name longer than the maximum of 45 characters.");
				}

			} else
				System.out.println("Name change request not detected.");

			// Look for zip command
			p2 = Pattern.compile(".*zip (\\d{5}).*", Pattern.CASE_INSENSITIVE);
			m2 = p2.matcher(messageBody);
			// Here a zip command is detected, and the users location is updated
			// in database
			if (m2.matches()) {
				System.out.println("Location change request detected.");

				// Here we read the lat, long, city, and state from CSV file via
				// new zip
				// Look up fromZips zip_code to fill lat, long, city, and state
				// variables
				try {
					CsvReader products = new CsvReader("zips.csv");
					products.readHeaders();
					try {
						while (products.readRecord()) {
							lookUpZip = products.get(products.getHeader(0));
							if (lookUpZip.equals(m2.group(1))) {
								System.out.println("Matching Zip Code found. LookUpZip is equal to: " + lookUpZip);

								latitude = products.get(products.getHeader(2));
								longitude = products.get(products.getHeader(3));
								city = products.get(products.getHeader(4));
								state = products.get(products.getHeader(5));
								break;
							}

						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e2) {
					e2.printStackTrace();
				}

				// Trim the quotes and spaces from the variables fed in from
				// spreadsheet
				latitude = removeLastChar(latitude);
				latitude = removeFirstChar(latitude);
				latitude = removeFirstChar(latitude);
				longitude = removeLastChar(longitude);
				longitude = removeFirstChar(longitude);
				longitude = removeFirstChar(longitude);
				city = removeLastChar(city);
				city = removeFirstChar(city);
				city = removeFirstChar(city);
				state = removeLastChar(state);
				state = removeFirstChar(state);
				state = removeFirstChar(state);

				// Here is the process of updating the database
				myStmt.executeUpdate("UPDATE `myusers`.`users_by_phone_number` SET `zip_code`='" + m2.group(1)
						+ "', `latitude`='" + latitude + "', `longitude`='" + longitude + "', `city`='" + city
						+ "', `state`='" + state + "' WHERE `phone_number`='" + fromNumber + "';");
				System.out.println("User Table sucessfully updated with new user name.\n");
				messageOut += "Your zip code has been changed to " + m2.group(1) + ". ";

			} else
				System.out.println("Location change request not detected.");

			// Look for report now command
			p2 = Pattern.compile(".*report now.*", Pattern.CASE_INSENSITIVE);
			m2 = p2.matcher(messageBody);
			// Here report now code is detected, and the users record is updated
			// in database
			if (m2.matches()) {
				System.out.println("Report now code detected.");

				////////////// Read User Data//////////////////
				try {
					// Execute SQL query
					myRs = myStmt.executeQuery("select * from users_by_phone_number");
					System.out.println("Checking database for your lat and long...");

					// Process the result set
					while (myRs.next()) {
						System.out.println("Checking database for your user id.");
						lookUpNum = myRs.getString("phone_number");
						System.out.println("lookUpNum=" + lookUpNum + " FromNumber=" + fromNumber);
						if (lookUpNum.equals(fromNumber)) {
							// Capture lat and long from DB entry
							lookUpZip = myRs.getString("zip_code");
							latitude = myRs.getString("latitude");
							longitude = myRs.getString("longitude");
						}
					}
				} catch (Exception e) {
				}

				title += lookUpZip;
				filename += lookUpZip;

				// Here the report is generated and sent

				// Instantiate the class with the API key
				ForecastIO fio = new ForecastIO(latitude, longitude, darksky_api_key);
				System.out.println("Timezone is set to:" + fio.getTimezone());
				fio.setUnits(ForecastIO.UNITS_SI); // sets the units as SI -
													// optional
				fio.setExcludeURL("minutely"); // excluded the minutely reports
												// from the reply
				// fio.getForecast(latitude, longitude); //sets the latitude and
				// longitude - not optional

				// Here is my sample hourly output
				FIOHourly hourly = new FIOHourly(fio);
				// System.out.println("Timezone is set to:" +
				// hourly.getTimezone());
				// In case there is no hourly data available
				if (hourly.hours() < 0)
					System.out.println("No hourly data.");
				else
					System.out.println("\nHourly:\n");
				// Print hourly data
				for (int i = 0; i < hourly.hours(); i++) {
					// At the hour i, grab the probability and intensity of
					// precipitation and put into arrays
					probabilityArr[i + 1] = 100 * Double.parseDouble(hourly.getHour(i).getByKey("precipProbability"));
					intensityArr[i + 1] = 100 * Double.parseDouble(hourly.getHour(i).getByKey("precipIntensity"));
					if (i == 0) {

						System.out.println("Time: " + hourly.getHour(i).getByKey("time"));
						DateTimeZone.setDefault(DateTimeZone.forID("UTC"));
						String dateTime = hourly.getHour(i).getByKey("time");
						// Format for input
						DateTimeFormatter dtf = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss");
						// Parsing the date
						DateTime jodatime = dtf.parseDateTime(dateTime);
						// Change to PST
						jodatime = jodatime.withZone(DateTimeZone.forID(fio.getTimezone()));
						// Format for output
						DateTimeFormatter dtfOut = DateTimeFormat.forPattern("MM/dd/yy");
						if (jodatime.getHourOfDay() == 0) {
							title += " on " + dtfOut.print(jodatime) + " at 12am";
						}
						if (jodatime.getHourOfDay() > 0 && jodatime.getHourOfDay() < 12) {
							title += " on " + dtfOut.print(jodatime) + " at " + jodatime.getHourOfDay() + "am";
						}
						if (jodatime.getHourOfDay() == 12) {
							title += " on " + dtfOut.print(jodatime) + " at 12pm";
						}
						if (jodatime.getHourOfDay() > 12 && jodatime.getHourOfDay() < 24) {
							title += " on " + dtfOut.print(jodatime) + " at " + (jodatime.getHourOfDay() - 12) + "pm";
						}
						System.out.println(dtfOut.print(jodatime));
						dtfOut = DateTimeFormat.forPattern("yy_MM_dd_HH");
						// Printing the date
						System.out.println(dtfOut.print(jodatime));
						filename += "_" + dtfOut.print(jodatime) + ".jpeg";
					}
				}

				System.out.println("Timezone: " + fio.getTimezone());

				if (!(new File(filenamePrefix + filename).isFile())) {
					// Here the chart is built and saved to file
					final XYSeries probability = new XYSeries("Probability");
					for (int z = 1; z < 49; z++)
						probability.add(z, probabilityArr[z]);
					final XYSeries intensity = new XYSeries("Intensity");
					for (int z = 1; z < 49; z++)
						intensity.add(z, intensityArr[z]);
					final XYSeriesCollection dataset = new XYSeriesCollection();
					dataset.addSeries(probability);
					dataset.addSeries(intensity);

					JFreeChart xylineChart = ChartFactory.createXYLineChart(title, "Hours From Now",
							"Percentage Chance of Rain", dataset, PlotOrientation.VERTICAL, true, false, false);

					XYPlot xyPlot = xylineChart.getXYPlot();
					ValueAxis domainAxis = xyPlot.getDomainAxis();
					ValueAxis rangeAxis = xyPlot.getRangeAxis();

					domainAxis.setRange(1, 48);
					rangeAxis.setRange(0, 100);

					int width = 640; // Width of the image
					int height = 480; // Height of the image
					File XYChart = new File(filenamePrefix + filename);
					try {
						ChartUtilities.saveChartAsJPEG(XYChart, xylineChart, width, height);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("This report already exists, so I didn't recreate it. ");
				}

				// Here is where the twilio connection is established, and
				// the SMS is sent
				Twilio.init(twilio_account_sid, twilio_auth_token);
				Message message = Message.creator(new PhoneNumber(fromNumber), new PhoneNumber(twilio_phone_number),
						"Here's your forecast").setMediaUrl(host_domain + "reports/" + filename).create(); //
				System.out.println(message.getSid());
				System.out.println("Report sent. filename=" + filename);
				System.out.println("and toNum is:" + fromNumber);
				System.out.println(message.getSid());
				messageOut += "Report sent. ";

			} else
				System.out.println("Report now command not detected.");

		} catch (SQLException e) {
			e.printStackTrace();
		}
		System.out.println("Here's your message: " + messageOut);

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