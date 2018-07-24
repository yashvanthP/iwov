package com.bnppf.adm.wmc.wcm.externals;

import java.io.BufferedInputStream;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.interwoven.livesite.file.FileDal;
import com.interwoven.livesite.runtime.RequestContext;

public class XLSXToXMLDocument {
	private static final String[] LANGUAGES_SHORT = new String[] {"nl", "fr", "en", "de"};
	private static final String[] LANGUAGES = new String[] {"dutch", "french", "english", "german"};
	private static final String	DEFAULTATTRIBUTE = "faq.synonyms";
	private static final String	DOCUMENTFILESPATH = "rsc/contrib/image/Files";
	private static final String	DEFAULTLANG = "en";
	private static final String LANGPARAMNAME = "axes1";
	private static final String ATTRPARAMNAME = "xlsxattr";
	private static final String ACTIONPARAMNAME = "renderer";
	private static final String SEPARATOR = "/";
	
	/**
	 * This method builds the XML Document containing all content from the Excel, stores it in a JVM Servlet Context attribute, and returns it.
	 * If the JVM attribute already exists, it just returns the cached Object.
	 * @param context The LiveSite RequestContext object.
	 * @return The String array of entries.
	 */
	public Document execute(RequestContext context) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the execute method", startTime);
		
		HttpServletRequest req = context.getRequest();
		
		// The XML Document in which we will store the content from the Excel
		Document content = null;
		
		String lang = req.getParameter(LANGPARAMNAME);
		if ((null == lang) || (lang.isEmpty())) {
			lang = DEFAULTLANG;
		}
		debugMsg("Language: " + lang, startTime);
		
		String jvmAttribute = req.getParameter(ATTRPARAMNAME);
		if ((null == jvmAttribute) || (jvmAttribute.isEmpty())) {
			jvmAttribute = DEFAULTATTRIBUTE;
		}
		debugMsg("jvmAttribute: " + jvmAttribute, startTime);

		// Try to get the stored Document from the JVM Servlet context
		Object jvmObject = req.getSession().getServletContext().getAttribute(jvmAttribute);
		if (null != jvmObject) {
			try {
				content = (Document)jvmObject;
				debugMsg("Retreived the Object from the JVM attribute", startTime);
			} catch (Exception e) {
				debugMsg(e.getMessage(), startTime);
			}
		}
		
		String actionParam = req.getParameter(ACTIONPARAMNAME);
		if ((null == actionParam) || (actionParam.isEmpty())) {
			actionParam = "";
		}
		
		if ((null == content) || actionParam.equalsIgnoreCase("reload")) {
			String rootPath = context.getFileDal().getRoot();
			debugMsg("rootPath: " + rootPath, startTime);
			
			// jvmAttribute e.g. "faq.synonyms"
			String[] jvmAttrSplit = jvmAttribute.split("\\.");
			
			// documentsPath is the workarea-relative path to the Excel file we want, e.g. "rsrc/contrib/image/Files/faq/synonyms.xlsx"
			String documentPath = DOCUMENTFILESPATH + SEPARATOR + jvmAttrSplit[0] + SEPARATOR + jvmAttrSplit[1] + ".xlsx";
			debugMsg("documentsPath: " + documentPath, startTime);
	
			try {
				// parentDirFullPath is the full file system path to the parent directory
				String filePathToExcel = rootPath + SEPARATOR + documentPath;  // adding separator for higher environments required
				debugMsg("filePathToExcel: " + filePathToExcel, startTime);
				try {
					content = extractContentsOfExcel(context.getFileDal(), filePathToExcel);
					
					// Save our updated content Object into the JVM Servlet context for use next time we call this Class
					req.getSession().getServletContext().setAttribute(jvmAttribute, (Object)content);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				debugMsg(e.getMessage(), startTime);
			}
		}
		
		return content;
	}

	@SuppressWarnings("deprecation")
	private Document extractContentsOfExcel(FileDal fileDal, String filePathToExcel) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the extractContentsOfExcel method", startTime);

		Document contentDoc = DocumentHelper.createDocument();
		Element rootElement = contentDoc.addElement("rows");
    	
    	if ((!filePathToExcel.isEmpty()) && (fileDal.isFile(filePathToExcel)) ) {
			try {
				BufferedInputStream bis = new BufferedInputStream(fileDal.getStream(filePathToExcel));
				XSSFWorkbook wb = new XSSFWorkbook(bis);
				XSSFSheet sheet = wb.getSheetAt(0);
				String[] headings = null;
				String[] headingLangs = null;
	        	
		        Iterator<Row> rowIterator = sheet.rowIterator();
		        while (rowIterator.hasNext()) {
		        	XSSFRow row = (XSSFRow) rowIterator.next();
		        	Element currentRowEle = rootElement.addElement("row");
		        	currentRowEle.addAttribute("num", "" + row.getRowNum());
	        		
		        	// Extract cell contents for the current row
		            Iterator<Cell> cellIterator = row.cellIterator();
		            while (cellIterator.hasNext()) {
		            	XSSFCell cell = (XSSFCell) cellIterator.next();
		            	String cellValue = "";
		            	if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
		            		cellValue = cell.getNumericCellValue() + "";
		            	} else if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
		            		cellValue = cell.getStringCellValue();
		            	}
		            	
		            	if (!cellValue.isEmpty()) {
				        	if (sheet.getFirstRowNum() == row.getRowNum()) {
				        		if (null == headings) {
				        			headings = new String[row.getLastCellNum()];
				        		}
				        		if (null == headingLangs) {
				        			headingLangs = new String[row.getLastCellNum()];
				        		}
				        		String headingValue = cellValue.trim().toLowerCase().replaceAll(" ", "_");
				        		headings[cell.getColumnIndex()] = headingValue;

				        		String currentLangShort = DEFAULTLANG;
				        		for (int i = 0; i < LANGUAGES.length; i++) {
				        			String currentLang = LANGUAGES[i];
				        			if (headingValue.indexOf(currentLang) != -1) {
				        				currentLangShort = LANGUAGES_SHORT[i];
				        			}
				        		}
				        		headingLangs[cell.getColumnIndex()] = currentLangShort;
				        		
				        		debugMsg("Added heading: " + headings[cell.getColumnIndex()] + " at index: " + cell.getColumnIndex(), startTime);
				        	} else {
				        		String currentHeading = headings[cell.getColumnIndex()];
				        		String currentHeadingLang = headingLangs[cell.getColumnIndex()];
					        	Element currentCellEle = currentRowEle.addElement("cell");
					        	currentCellEle.addAttribute("name", currentHeading);
					        	currentCellEle.addAttribute("lang", currentHeadingLang);
					        	String[] cellValueSplit = cellValue.trim().split("\\n");
					        	for (String cellValueLine : cellValueSplit) {
					        		Element currentLineEle = currentCellEle.addElement("line");
					        		currentLineEle.setText(cellValueLine.toLowerCase().trim());
					        	}
				        	}
		            	}
		            }
		        }
		        wb.close();
		        bis.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    	
		debugMsg("Finished the extractContentsOfExcel method", startTime);
		return contentDoc;
	}
	
	/**
	 * Utility method for outputting to the debug logger.
	 * @param theStr String to output
	 * @param startTime long System.nanoTime()
	 */
	public static void debugMsg(String theStr, long startTime) {
		System.out.println("[" + (System.currentTimeMillis() - startTime) + " ms] " + theStr);
	}
}