package com.bnppf.adm.wmc.wcm.standalone;
import com.interwoven.cssdk.common.CSClient;
import com.interwoven.cssdk.factory.CSFactory;
import com.interwoven.cssdk.filesys.CSDir;
import com.interwoven.cssdk.filesys.CSFile;
import com.interwoven.cssdk.filesys.CSVPath;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.*;

/**
 * ExcelToDCRs reads an Excel file, and a template DCR file, and uses them to generate DCRs.
 *
 * @author jpope
 * @version 2018-11-20 11:22
 *
 */
public class ExcelToDCRs {
	private static final String[] LANGUAGES_SHORT = new String[] { "nl", "fr", "en", "de" };

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the main method", startTime);

		if (args.length < 6) {
			System.out.println("ERROR: ExcelToDCRs <username> <path to XLSX file> <path to DCR template file> <vpath to DCR root directory> <path to cssdk.cfg> <optional password>");
		} else {
			String username = args[0];
			String excelFilePath = args[1];
			String templateDCRPath = args[2];
			String indexFileColumn = args[3];
			String rootPathDCRs = args[4].endsWith("/") ? args[4] : args[4] + "/";
			String pathToCSSDKCfg = args[5];
			String password = (args.length == 7) ? args[6] : new String(System.console().readPassword("Password: "));

			debugMsg("Username: " + username, startTime);
			debugMsg("XSLX file: " + excelFilePath, startTime);
			debugMsg("Template DCR file: " + templateDCRPath, startTime);
			debugMsg("Index file column name: " + indexFileColumn, startTime);
			debugMsg("DCRs root path: " + rootPathDCRs, startTime);
			debugMsg("CSSDK config: " + pathToCSSDKCfg, startTime);

			// Get the CSClient which we'll use for reading/writing to the TeamSite content store
			CSClient client = getCSSDKClient(username, password, pathToCSSDKCfg);
			try {
				CSFile fileAtVpath = client.getFile(new CSVPath(rootPathDCRs));
				if ((null != fileAtVpath) && (fileAtVpath.isWritable())) {
					debugMsg("DCRs root path is valid and writeable.", startTime);
				} else {
					debugMsg("ERROR: DCRs root path is invalid and/or not writeable. Does that directory exist?", startTime);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				Document templateDCR = getTemplateDCR(templateDCRPath);
				debugMsg("Template DCR: \n" + templateDCR.asXML(), startTime);
				List<Node> replacementNodes = templateDCR.selectNodes("//*[text()[contains(.,'{{')]]");
				for (Node currentNode : replacementNodes) {
					String currentNodeText = currentNode.getText().trim();
					currentNodeText = currentNodeText.replaceFirst("\\{\\{", "").replaceFirst("}}", "").trim();
					debugMsg("Node: " + currentNode.getName() + ": " + currentNodeText, startTime);
					currentNode.setText(currentNodeText);
				}

				List<HashMap<String, String>> excelContents = getExcelContents(excelFilePath);
				for (HashMap<String, String> excelRow : excelContents) {
					String currentIndexName = "";
					HashMap<String, Document> dcrDocuments = new HashMap<String, Document>();
					for (String currentLangShort : LANGUAGES_SHORT) {
						dcrDocuments.put(currentLangShort, (Document)templateDCR.clone());
					}
					for (Map.Entry<String, String> excelRowContents : excelRow.entrySet()) {
						String columnName = excelRowContents.getKey();
						String columnValue = excelRowContents.getValue();
						if (columnName.equalsIgnoreCase(indexFileColumn)) {
							currentIndexName = columnValue.toLowerCase().trim().replaceAll(" ", "_");
						}
						
						int langSeperatorIndex = columnName.indexOf("_") + 1;
						if (langSeperatorIndex > 0) {
							String currentColumnLang = columnName.substring(langSeperatorIndex);
							Document currentDCR = dcrDocuments.get(currentColumnLang);
							if (null != currentDCR) {
								for (Node currentReplacementNode : replacementNodes) {
									if (columnName.contains(currentReplacementNode.getStringValue())) {
										Node foundNode = currentDCR.selectSingleNode("//" + currentReplacementNode.getName());
										if (null != foundNode) {
											foundNode.setText(columnValue);
										}
									}
								}
							}
						}
					}
					for (String currentLangShort : LANGUAGES_SHORT) {
						Document dcrDoc = (Document)dcrDocuments.get(currentLangShort);
						String fullPathToDCR = rootPathDCRs + currentLangShort + "/" + currentIndexName + ".xml";
						boolean writeOK = XLSXToDCRs.writeXML(fullPathToDCR, dcrDoc, client, currentLangShort, "site-management/page");
						debugMsg("Generated DCR at " + fullPathToDCR + " OK: " + writeOK, startTime);
						if (currentLangShort.equalsIgnoreCase("en")) {
							fullPathToDCR = rootPathDCRs + currentIndexName + ".xml";
							writeOK = XLSXToDCRs.writeXML(fullPathToDCR, dcrDoc, client, currentLangShort, "site-management/page");
							debugMsg("Generated DCR at " + fullPathToDCR + " OK: " + writeOK, startTime);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		debugMsg("Finished.", startTime);
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

	@SuppressWarnings("deprecation")
	private static List<HashMap<String, String>> getExcelContents(String excelFilePath) {
		List<HashMap<String, String>> excelContents = new LinkedList<HashMap<String, String>>();
		try {
			String headings[] = null;
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(excelFilePath));
			XSSFWorkbook wb = new XSSFWorkbook(bis);
			XSSFSheet sheet = wb.getSheetAt(0);

			Iterator<Row> rowIterator = sheet.rowIterator();
			while (rowIterator.hasNext()) {
				XSSFRow row = (XSSFRow) rowIterator.next();
				HashMap<String, String> currentRowContents = new HashMap<String, String>();

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

					// If this is the first row, then extract the headings
					if (sheet.getFirstRowNum() == row.getRowNum()) {
						cellValue = cellValue.toLowerCase().trim().replaceAll(" ", "_");
						if (null == headings) {
							headings = new String[row.getLastCellNum()];
						}
						headings[cell.getColumnIndex()] = cellValue;
					} else {
						if (!cellValue.isEmpty()) {
							atLeastOneNotEmpty = true;
						}
						currentRowContents.put(headings[cell.getColumnIndex()], cellValue);
					}
				}

				if (atLeastOneNotEmpty) {
					excelContents.add(currentRowContents);
				}
			}
			bis.close();
			wb.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return excelContents;
	}

	private static Document getTemplateDCR(String filePath) {
		Document templateDCR = null;
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath));
			SAXReader reader = new SAXReader();
			templateDCR = reader.read(bis);
			bis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return templateDCR;
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