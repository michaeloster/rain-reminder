package com.rainreminder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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

import com.github.dvdme.ForecastIOLib.FIODaily;
import com.github.dvdme.ForecastIOLib.FIOHourly;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

class ForecastTask implements Runnable {
	private String name;
	private int zip_code;
	private String latitude;
	private String longitude;
	private String city;
	private String toNum;
	String darksky_api_key = "";
	String twilio_account_sid = "";
	String twilio_auth_token = "";
	String host_domain = "";

	public ForecastTask(String name, int zip_code, String latitude, String longitude, String city, String state,
			String toNum) {
		this.name = name;
		this.zip_code = zip_code;
		this.latitude = latitude;
		this.longitude = longitude;
		this.city = city;
		this.toNum = toNum;
	}

	public String getName() {
		return name;
	}

	@Override
	public void run() {
		System.out.println("ForecastTask executing");
		try {
			double[] probabilityArr = new double[50];
			double[] intensityArr = new double[50];
			String title = "Rain Report for " + this.zip_code;
			String filenamePrefix = "src/main/webapp/reports/";
			String filename = "" + this.zip_code;
			boolean itRains = false;

			// Read credentials from config.properties file
			Properties prop = new Properties();
			try {
				prop.load(new FileInputStream("config.properties"));
				darksky_api_key = prop.getProperty("darksky_api_key");
				twilio_account_sid = prop.getProperty("twilio_account_sid");
				twilio_auth_token = prop.getProperty("twilio_auth_token");
				host_domain = prop.getProperty("host_domain");
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Here is where the body of the forecast message is built
			String body = "Hello " + this.name + ", here's what the weather in " + this.city
					+ " looks like tomorrow:\n";

			// Instantiate the class with the API key
			ForecastIO fio = new ForecastIO(latitude, longitude, darksky_api_key);

			System.out.println("Timezone is set to:" + fio.getTimezone());
			fio.setUnits(ForecastIO.UNITS_SI); // sets the units as SI
			fio.setExcludeURL("minutely"); // excluded the minutely reports from
											// the reply

			FIODaily daily = new FIODaily(fio);
			// In case there is no daily data available
			if (daily.days() < 0) {
				System.out.println("No daily data.");
			} else {
				System.out.println("\nDaily:\n");
				for (int i = 0; i < daily.days(); i++) {
					String[] h = daily.getDay(i).getFieldsArray();
					for (int j = 0; j < h.length; j++) {
						if (h[j].equals("time")) {
							Double rainchance = 100 * Double.parseDouble(daily.getDay(i).getByKey("precipProbability"));
							String time = daily.getDay(i).getByKey(h[j]);
							for (int k = 0; k < 9; k++) {
								time = removeLastChar(time);
							}
							body += time + " - Chance of Rain: " + rainchance + "% - "
									+ daily.getDay(i).getByKey("summary") + "\n";
						}
					}
				}
			}

			// Here is my sample hourly output
			FIOHourly hourly = new FIOHourly(fio);
			// System.out.println("Timezone is set to:" + hourly.getTimezone());
			// In case there is no hourly data available
			if (hourly.hours() < 0)
				System.out.println("No hourly data.");
			else
				System.out.println("\nHourly:\n");
			// Print hourly data
			for (int i = 0; i < hourly.hours(); i++) {
				// At hour i, put probability and intensity of precipitation
				// into arrays
				probabilityArr[i + 1] = 100 * Double.parseDouble(hourly.getHour(i).getByKey("precipProbability"));
				intensityArr[i + 1] = 100 * Double.parseDouble(hourly.getHour(i).getByKey("precipIntensity"));
				System.out.println("Hour #" + (i + 1) + " rain probability: " + probabilityArr[i + 1]
						+ "% with intensity of " + intensityArr[i + 1] + "%");
				if (probabilityArr[i + 1] > 0) {
					itRains = true;

				}
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

			if (itRains) {
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

					int width = 640; /* Width of the image */
					int height = 480; /* Height of the image */
					File XYChart = new File(filenamePrefix + filename);
					try {
						ChartUtilities.saveChartAsJPEG(XYChart, xylineChart, width, height);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("This report already exists, so I didn't recreate it. ");
				}

				// Here is where the twilio connection is established, and the
				// SMS
				// is sent

				Twilio.init(twilio_account_sid, twilio_auth_token);

				Message message = Message
						.creator(new PhoneNumber(toNum), new PhoneNumber("+14158952840"), "Here's your forecast")
						.setMediaUrl(host_domain + "reports/" + filename).create();

				System.out.println(message.getSid());
				System.out.println("Here's toNum and filename:" + toNum + " " + filename);
				System.out.println("Here's boolean itRains:" + itRains);
				System.out.println(body);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
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