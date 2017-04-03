package com.google.maps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.google.maps.model.DirectionsResult;

public class CSVGeocoder {
	
	private static final Properties properties = getProperties();
	private static final GeoApiContext context = getContext();
	private static final BigDecimal METERS_IN_MILE = new BigDecimal(1609.34);
	
	public enum COLS {
		CUS_NO, 
		SHP_NO, 
		CUS_NM,
		ADDR1,
		ADDR2,
		ADDR,
		CITY_STATE,
		POSTAL_CODE,
		ZIP_ADDRESS,
		ZIP_MILEAGE
	}

	public static void main(String[] args) {
		
		Map<String, Map<String, String>> recordMap = new LinkedHashMap<>();
		Map<String, List<Map<String, String>>> zipMap = new LinkedHashMap<>();
		
		try {
			String fromAddress = getFromAddress();
			CSVParser parser = CSVParser.parse(new File("C:\\etc\\ship_to_addresses.csv"), Charset.forName("ISO-8859-1"), CSVFormat.EXCEL.withHeader());
			List<CSVRecord> records = parser.getRecords();
			
			for (CSVRecord r : records) {
				Map<String, String> rMap = r.toMap();
				String mileage = rMap.get(COLS.ZIP_MILEAGE.name());
				String pCode = rMap.get(COLS.POSTAL_CODE.name());
				if (null != pCode && ! "".equals(pCode.trim())) {
					String cusNo = rMap.get(COLS.CUS_NO.name());
					String shpNo = rMap.get(COLS.SHP_NO.name());
					recordMap.put(cusNo + "-" + shpNo, rMap);
					
					if (null == mileage || "".equals(mileage.trim())) {
						List<Map<String, String>> zipList = zipMap.get(pCode);
						if (null == zipList) {
							zipList = new ArrayList<>();
							zipMap.put(pCode, zipList);
						}
						
						zipList.add(rMap);
					}
				}
			}
			
			// int count = 0;
			for (Entry<String, List<Map<String, String>>> entry : zipMap.entrySet()) {
				String pCode = entry.getKey();
				List<Map<String, String>> recordList = entry.getValue();
				
				if (null != recordList) {
					System.out.println(pCode);
					
					try {
						DirectionsResult result = DirectionsApi.getDirections(context, fromAddress, pCode).await();
						
				        BigDecimal distance = getDistance(result);
				        String endAddress = getEndAddress(result);
				        
				        for (Map<String, String> record : recordList) {
				        	if (null != distance) {
				        		record.put(COLS.ZIP_MILEAGE.name(), distance.toString());
				        	}
				        	record.put(COLS.ZIP_ADDRESS.name(), endAddress);
				        }
						
					} catch (Exception e) {
						System.err.println(e.getMessage());
					}
					
				}

			}
			
			parser.close();
			

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				Appendable out = new FileWriter(new File("C:\\etc\\ship_to_addresses_out.csv"));
				CSVPrinter printer = new CSVPrinter(out, CSVFormat.EXCEL.withHeader(COLS.class));
				
		        for (Map<String, String> record : recordMap.values()) {
		        	for (COLS c : COLS.values()) {
		        		printer.print(record.get(c.name()));
		        	}
		        	printer.println();
		        	printer.flush();
		        }
			
				printer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	        

	}

	private static String getFromAddress() {
		return properties.getProperty("from.address");
	}

	private static String getEndAddress(DirectionsResult result) {
		if (null != result && result.routes.length > 0 && null != result.routes[0] && result.routes[0].legs.length > 0 && null != result.routes[0].legs[0]) {
			return result.routes[0].legs[0].endAddress;
		} else {
			return null;
		}
	}

	private static BigDecimal getDistance(DirectionsResult result) {
		if (null != result && result.routes.length > 0 && null != result.routes[0] && result.routes[0].legs.length > 0 && null != result.routes[0].legs[0]) {
			long inMeters = result.routes[0].legs[0].distance.inMeters;
			return new BigDecimal(inMeters).divide(METERS_IN_MILE, 0, BigDecimal.ROUND_HALF_UP);
		} else {
			return null;
		}
	}

	public static GeoApiContext getContext() {
		GeoApiContext context = getApiContext();

		if (null == context) {
			throw new IllegalArgumentException("No credentials found! Set the API_KEY or CLIENT_ID and "
					+ "CLIENT_SECRET environment variables to run tests requiring authentication.");
		}

		return context.setQueryRateLimit(3)
	            .setConnectTimeout(2, TimeUnit.SECONDS)
	            .setReadTimeout(2, TimeUnit.SECONDS)
	            .setWriteTimeout(2, TimeUnit.SECONDS);
	}

	private static GeoApiContext getApiContext() {
		String apiKey = properties.getProperty("api.key");
		if (apiKey == null || apiKey.trim().equalsIgnoreCase("")) {
			throw new IllegalArgumentException("No credentials found! Set the API_KEY or CLIENT_ID and "
					+ "CLIENT_SECRET environment variables to run tests requiring authentication.");
		}

		return new GeoApiContext().setApiKey(apiKey);
	}

	private static Properties getProperties() {
		Properties props = new Properties();
		try {
			InputStream resource = new FileInputStream("C:\\etc\\maps.properties");
			props.load(resource);
		} catch (IOException e) {
			throw new IllegalArgumentException("could not find properties ");
		}
		return props;
	}
}
