package com.bnppf.adm.wmc.wcm.standalone;
import com.interwoven.cssdk.common.CSClient;
import com.interwoven.cssdk.factory.CSFactory;
import com.interwoven.cssdk.filesys.CSDir;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.*;

/**
 * ExcelToDCRs reads an Excel file and uses it to generate DCRs.
 *
 * @author jpope
 * @version 2018-11-20 11:22
 *
 */
public class ExcelToDCRs {
	private static final String[] LANGUAGES_SHORT = new String[] { "nl", "fr", "en", "de" };
	private static final String[] LANGUAGES = new String[] { "dutch", "french", "english", "german" };
	private static CSClient client = null;
	private static String excelFilePath;
	private static String rootPathDCRs;
	private static CSDir rootDirDCRs = null;

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the main method", startTime);

		if (args.length < 4) {
			System.out.println("ERROR: ExcelToDCRs <username> <path to XLSX file> <vpath to DCR root directory> <path to cssdk.cfg> <optional password>");
		} else {
			System.out.println(args.length);
			String username = args[0];
			excelFilePath = args[1];
			rootPathDCRs = args[2].endsWith("/") ? args[2] : args[2] + "/";
			String pathToCSSDKCfg = args[3];
			String password = (args.length == 5) ? args[4] : new String(System.console().readPassword("Password: "));

			debugMsg("Username: " + username, startTime);
			debugMsg("XSLX file: " + excelFilePath, startTime);
			debugMsg("DCRs root path: " + rootPathDCRs, startTime);
			debugMsg("CSSDK config: " + pathToCSSDKCfg, startTime);

			// Get the CSClient which we'll use for reading/writing to the TeamSite content store
			/*client = getCSSDKClient(username, password, pathToCSSDKCfg);
			try {
				CSFile fileAtVpath = client.getFile(new CSVPath(rootPathDCRs));
				if ((null != fileAtVpath) && (fileAtVpath.isWritable())) {
					rootDirDCRs = (CSDir)fileAtVpath;
					debugMsg("DCRs root path is valid and writeable.", startTime);
				} else {
					debugMsg("ERROR: DCRs root path is invalid and/or not writeable. Does that directory exist?", startTime);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}*/

			try {
				List<List<String>> excelContents = getExcelContents(excelFilePath);
				for (List<String> excelRow : excelContents) {
					for (String excelColumn : excelRow) {
						System.out.print("\"" + excelColumn + "\",");
					}
					System.out.println("");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static CSClient getCSSDKClient(String username, String password, String pathToCSSDKCfg) {
		Properties properties = new Properties();
		properties.setProperty("com.interwoven.cssdk.factory.CSFactory", "com.interwoven.cssdk.factory.CSJavaFactory");
		properties.setProperty("cssdk.cfg.path", pathToCSSDKCfg);

		CSFactory factory = CSFactory.getFactory(properties);
		CSClient theClient = null;
		try {
			theClient = factory.getClient(username, "", password, Locale.getDefault(), "ExcelToDCRs", null);
			System.out.println("Got TeamSite client connection.");
		} catch (Exception e) {
			System.out.println("Exception occurred while retrieving CSClient object.");
			e.printStackTrace();
		}

		return theClient;
	}

	private static List<List<String>> getExcelContents(String excelFilePath) {
		List<List<String>> excelContents = new LinkedList<List<String>>();
		try {
			StringBuilder sbErrors = new StringBuilder();
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(excelFilePath));
			XSSFWorkbook wb = new XSSFWorkbook(bis);
			XSSFSheet sheet = wb.getSheetAt(0);

			Iterator<Row> rowIterator = sheet.rowIterator();
			while (rowIterator.hasNext()) {
				XSSFRow row = (XSSFRow) rowIterator.next();
				List<String> currentRowContents = new LinkedList<String>();

				// Extract cell contents for the current row
				Iterator<Cell> cellIterator = row.cellIterator();
				boolean atLeastOneNotEmpty = false;
				while (cellIterator.hasNext()) {
					XSSFCell cell = (XSSFCell) cellIterator.next();
					String cellValue = "";
					if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
						cellValue = (cell.getNumericCellValue() + "").trim();
					} else if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
						cellValue = cell.getStringCellValue().trim();
					}

					if (!cellValue.isEmpty()) {
						atLeastOneNotEmpty = true;
						if (excelContents.isEmpty()) {
							// If this is the first row, convert headings
							cellValue = cellValue.toLowerCase().trim().replaceAll(" ", "_");
						}
					}

					currentRowContents.add(cellValue);
				}

				if (atLeastOneNotEmpty) {
					excelContents.add(currentRowContents);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return excelContents;
	}

	/**
	 * Utility method for outputting to the debug logger.
	 *
	 * @param theStr
	 *          String to output
	 * @param startTime
	 *          long System.currentTimeMillis()
	 */
	private static void debugMsg(String theStr, long startTime) {
		System.out.println("[" + (System.currentTimeMillis() - startTime) + " ms] " + theStr);
	}
}