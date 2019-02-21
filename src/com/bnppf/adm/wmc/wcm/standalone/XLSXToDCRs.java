package com.bnppf.adm.wmc.wcm.standalone;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import com.interwoven.cssdk.common.CSClient;
import com.interwoven.cssdk.factory.CSFactory;
import com.interwoven.cssdk.filesys.CSDir;
import com.interwoven.cssdk.filesys.CSExtendedAttribute;
import com.interwoven.cssdk.filesys.CSFile;
import com.interwoven.cssdk.filesys.CSSimpleFile;
import com.interwoven.cssdk.filesys.CSVPath;
import com.interwoven.cssdk.filesys.CSWorkarea;

/**
 * XLSXToDCRs reads an Excel file and uses it to generate DCRs.
 * 
 * @author jpope
 * @version 2018-10-30 11:48
 *
 */
public class XLSXToDCRs {
	private static final String[] LANGUAGES_SHORT = new String[] { "nl", "fr", "en", "de" };
	private static final String[] LANGUAGES = new String[] { "dutch", "french", "english", "german" };
	private static final String DIR_NAME_HEADING = "modul";
	private static final String DIR_NAME_HEADING_SUB = "submodul";
	private static final String LINK_ARGB_INTERNAL = "FF00B0F0";
	private static final String LINK_ARGB_EXTERNAL = "FFFF0000";
	private static final String LINK_ARGB_ANCHOR_GENERIC = "FFF79646";
	private static final String LINK_ARGB_ANCHOR_SPECIFIC = "FF00B050";

	@SuppressWarnings({"deprecation"})
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the main method", startTime);

		if (args.length < 5) {
			System.out.println("ERROR: XLSXToDCRs <username> <path to XLSX file> <vpath to QA DCR directory> <vpath to Topic DCR directory> <path to cssdk.cfg>");
		} else {
			Console console = System.console();
			String username = args[0];
			String xslx = args[1];
			String vpathQA = args[2];
			String vpathTopic = args[3];
			String cssdk = args[4];
			String password;
			if (!vpathQA.endsWith("/")) {
				vpathQA = vpathQA + "/";
			}
			if (!vpathTopic.endsWith("/")) {
				vpathTopic = vpathTopic + "/";
			}

			System.out.println("Username: " + username);
			System.out.println("XSLX file: " + xslx);
			System.out.println("Vpath QA: " + vpathQA);
			System.out.println("Vpath Topic: " + vpathTopic);
			System.out.println("CSSDK config: " + cssdk);

			if (args.length == 6) {
				password = args[5];
			} else {
				password = new String(console.readPassword("Password: "));
			}

			Properties properties = new Properties();
			properties.setProperty("com.interwoven.cssdk.factory.CSFactory", "com.interwoven.cssdk.factory.CSJavaFactory");
			properties.setProperty("cssdk.cfg.path", cssdk);

			CSFactory factory = CSFactory.getFactory(properties);
			CSClient client = null;
			try {
				client = factory.getClient(username, "", password, Locale.getDefault(), "XLSXToDCRs", null);
				System.out.println("Got TeamSite client connection.");
			} catch (Exception e) {
				System.out.println("Exception occured while retrieving CSClient object.");
				e.printStackTrace();
			}

			if (null != client) {
				// topicContents has modul as key, then HashMap <String, Integer> with sub-modul
				// as key and num of QA DCRs as value
				HashMap<String, HashMap<String, Integer>> topicContents = new HashMap<String, HashMap<String, Integer>>();
				// localisedModules has modul/sub-modul as key, then HashMap <String, String>
				// with short lang as key and localised modul as value
				HashMap<String, HashMap<String, String>> localisedModules = new HashMap<String, HashMap<String, String>>();

				CSDir rootDirQA = null;
				try {
					CSFile fileAtVpath = client.getFile(new CSVPath(vpathQA));
					if ((null != fileAtVpath) && (fileAtVpath.isWritable())) {
						rootDirQA = (CSDir) fileAtVpath;
						System.out.println("vpath is valid and writeable.");
					} else {
						System.out.println("ERROR: vpath is invalid and/or not writeable. Does that directory exist?");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (null != rootDirQA) {
					// This String array contains the headings extracted from the Excel sheet to use
					// as the element names
					// Each String is converted to lower case and spaces are replaced with
					// underscore characters
					String[] headings = null;
					int DCRCounter = 0;

					try {
						StringBuilder sbErrors = new StringBuilder();
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(xslx));
						XSSFWorkbook wb = new XSSFWorkbook(bis);
						XSSFSheet sheet = wb.getSheetAt(0);

						Iterator<Row> rowIterator = sheet.rowIterator();
						while (rowIterator.hasNext()) {
							XSSFRow row = (XSSFRow) rowIterator.next();

							HashMap<String, String> currentRowContent = new HashMap<String, String>();
							ArrayList<String> currentRowLinks = new ArrayList<String>();
							ArrayList<String> currentRowExternalLinks = new ArrayList<String>();
							ArrayList<String> currentRowFormIDs = new ArrayList<String>();

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
										headings[cell.getColumnIndex()] = cellValue.trim().toLowerCase().replaceAll(" ", "_");
										debugMsg("Added heading: " + headings[cell.getColumnIndex()] + " at index: " + cell.getColumnIndex(), startTime);
									} else {
										if (headings[cell.getColumnIndex()].equalsIgnoreCase("reference_to_link_attached")) {
											// Deal with the links column value
											cellValue = cellValue.replaceAll("\r", "").replaceAll(" ", "");
											String[] linksArray = cellValue.split("\n");
											for (String link : linksArray) {
												if (!link.isEmpty()) {
													currentRowLinks.add(link.trim());
												}
											}
										} else if (headings[cell.getColumnIndex()].equalsIgnoreCase("reference_to_form")) {
											// Deal with the form IDs column value
											cellValue = cellValue.replaceAll("\r", "").replaceAll(" ", "");
											String[] linksArray = cellValue.split("\n");
											for (String link : linksArray) {
												if (!link.isEmpty()) {
													currentRowFormIDs.add(link.trim());
												}
											}
										} else if (headings[cell.getColumnIndex()].equalsIgnoreCase("external_link")) {
											// Deal with the external links column value
											cellValue = cellValue.replaceAll("\r", "").replaceAll(" ", "");
											String[] linksArray = cellValue.split("\n");
											for (String link : linksArray) {
												if (!link.isEmpty()) {
													currentRowExternalLinks.add(link.trim());
												}
											}
										} else if (headings[cell.getColumnIndex()].contains("answer")) {
											// Special case to extract complex data from the cell
											cellValue = convertRTSCellToXML(cell.getRichStringCellValue());
											cellValue = "<Region><Section>" + cellValue + "</Section></Region>";
										}
										currentRowContent.put(headings[cell.getColumnIndex()], cellValue.trim());
									}
								}
							}

							// Generate DCRs if row has content
							if (!currentRowContent.isEmpty()) {
								String currentRowModule = currentRowContent.get(DIR_NAME_HEADING + "_english").trim().toLowerCase().replaceAll(" ", "_").replaceAll("\\?", "");
								String currentRowSubModule = currentRowContent.get(DIR_NAME_HEADING_SUB + "_english").trim().toLowerCase().replaceAll(" ", "_").replaceAll("\\?", "");

								// Add localised module
								String moduleKey = currentRowModule + "/" + currentRowSubModule;
								HashMap<String, String> localisedModule = localisedModules.get(moduleKey);
								if (null == localisedModule) {
									localisedModule = new HashMap<String, String>();
									boolean foundAtLeastOne = false;
									for (int l = 0; l < LANGUAGES.length; l++) {
										String currentLocalisedModuleValue = currentRowContent.get((DIR_NAME_HEADING + "_" + LANGUAGES[l]).trim().toLowerCase().replaceAll(" ", "_")).replaceAll("\\?", "");
										String currentLocalisedSubModuleValue = currentRowContent.get((DIR_NAME_HEADING_SUB + "_" + LANGUAGES[l]).trim().toLowerCase().replaceAll(" ", "_")).replaceAll("\\?", "");
										if ((null != currentLocalisedModuleValue) && (null != currentLocalisedSubModuleValue)) {
											if (!currentLocalisedModuleValue.isEmpty() && !currentLocalisedSubModuleValue.isEmpty()) {
												localisedModule.put(LANGUAGES_SHORT[l], currentLocalisedSubModuleValue);
												foundAtLeastOne = true;
											}
										}
									}
									if (foundAtLeastOne) {
										localisedModules.put(moduleKey, localisedModule);
									}
								}

								debugMsg("Row: " + (row.getRowNum() + 1) + ". Module: " + moduleKey, startTime);
								String currentPriority = currentRowContent.get("priority_top_faq");
								int currentPriorityNum = new Float(currentPriority).intValue();
								String currentDCRName = currentRowSubModule + "-" + String.format("%03d", currentPriorityNum) + ".xml";

								// Add content for Topics
								HashMap<String, Integer> currentTopic = topicContents.get(currentRowModule);
								if (null == currentTopic) {
									topicContents.put(currentRowModule, new HashMap<String, Integer>());
									currentTopic = topicContents.get(currentRowModule);
									System.out.println("Added topic: " + currentRowModule);
								}
								Integer currentSubModuleDCRCount = currentTopic.get(currentRowSubModule);
								if (null == currentSubModuleDCRCount) {
									currentTopic.put(currentRowSubModule, currentPriorityNum);
								} else if (currentSubModuleDCRCount < currentPriorityNum) {
									currentTopic.put(currentRowSubModule, currentPriorityNum);
								}
								if ((null != currentRowContent.get("generic")) && currentRowContent.get("generic").equalsIgnoreCase("yes")) {
									if (null != currentRowContent.get("priority_generic")) {
										Integer currentPriorityGeneric = new Float(currentRowContent.get("priority_generic")).intValue();
										currentTopic.put("generic", currentPriorityGeneric);
										System.out.println("Added generic topic: (" + currentPriorityGeneric + ") " + currentRowModule + "/" + currentDCRName);
									}
								}

								for (int l = 0; l < LANGUAGES.length; l++) {
									String currentDCRPathLocal = currentRowModule + "/" + LANGUAGES_SHORT[l] + "/" + currentDCRName;
									String currentDCRPath = vpathQA + currentDCRPathLocal;
									System.out.print("- QA DCR: " + currentDCRPathLocal);

									// Handle links
									String answerContent = currentRowContent.get("answer_" + LANGUAGES[l]);
									if (null == answerContent) {
										answerContent = "";
									}
									if (!currentRowLinks.isEmpty()) {
										for (String currentRowLink : currentRowLinks) {
											if (!answerContent.isEmpty()) {
												answerContent = answerContent.replaceFirst("LINKTODO", currentRowLink);
											}
										}
									}
									if (!currentRowExternalLinks.isEmpty()) {
										for (String currentRowExternalLink : currentRowExternalLinks) {
											if (!answerContent.isEmpty()) {
												answerContent = answerContent.replaceFirst("EXTERNALTODO", currentRowExternalLink);
											}
										}
									}

									// Handle Form IDs
									if (!currentRowFormIDs.isEmpty()) {
										for (String currentRowFormID : currentRowFormIDs) {
											if (!answerContent.isEmpty()) {
												answerContent = answerContent.replaceFirst("FORMIDTODO", currentRowFormID);
											}
										}
									}

									// The XML document in which we will store the DCR
									Document doc = DocumentHelper.createDocument();
									Element rootElement = doc.addElement("Content");
									String question = currentRowContent.get("question_" + LANGUAGES[l]);
									String friendlyURL = Normalizer.normalize(question, Normalizer.Form.NFD).toLowerCase();
									friendlyURL = friendlyURL.replaceAll("[^\\p{ASCII}]", "");
									friendlyURL = friendlyURL.replaceAll("[^a-zA-Z0-9\\s+]", "").trim().replaceAll(" ", "-");
									rootElement.setAttributeValue("analyticsParam", question.replaceAll("\\\"", "").replaceAll("\\?", "").trim());
									rootElement.addElement("question").setText(question);
									rootElement.addElement("answer").setText(answerContent);
									rootElement.addElement("friendly-url").setText(friendlyURL);

									String debugOutput = "- Row: " + (row.getRowNum() + 1) + ". Module: " + currentRowModule + "/" + currentRowSubModule + "/" + currentPriorityNum;

									// Handle XML validation and output any errors to text file
									String XMLToValidate = StringEscapeUtils.unescapeXml(rootElement.asXML());
									try {
										SAXReader reader = new SAXReader();
										reader.setValidation(false);
										Document xmlDocToValidate = reader.read(new StringReader(XMLToValidate));
										if (null == xmlDocToValidate) {
											sbErrors.append(debugOutput).append(" - ERROR (invalid XML)\n");
											System.out.println(" - ERROR (Invalid XML)");
											break;
										}
										if (XMLToValidate.contains("TODO")) {
											System.out.println(" --- ERROR: Found empty link(s).");
											sbErrors.append(debugOutput).append(" - ERROR (found empty link(s))\n");
										}
									} catch (Exception e) {
										sbErrors.append(debugOutput).append(" - ERROR: ");
										if (e.getMessage().contains("Link")) {
											sbErrors.append("problem with a link. Maybe a space at the end or start of a line?");
										} else if (e.getMessage().contains("Paragraph")) {
											sbErrors.append("problem with a paragraph. Maybe a new line inside a list?");
										} else if (e.getMessage().contains("Item") || e.getMessage().contains("List")) {
											sbErrors.append("problem with a list item.");
										} else if (e.getMessage().contains("&")) {
											sbErrors.append("problem with the & character, please use 'and' instead.");
										} else {
											sbErrors.append(e.getMessage());
										}
										sbErrors.append("\n");
										sbErrors.append("DEBUG: ").append(XMLToValidate).append("\n\n");
										System.out.println(" - ERROR (" + e.getMessage() + ")");
										System.out.println(XMLToValidate);
										break;
									}

									if (writeXML(currentDCRPath, doc, client, LANGUAGES_SHORT[l], "faq/faq-QA")) {
										System.out.println(" - OK");
										DCRCounter++;
									}

									// Interlingua
									if (LANGUAGES_SHORT[l].equalsIgnoreCase("en")) {
										currentDCRPathLocal = currentRowModule + "/" + currentDCRName;
										currentDCRPath = vpathQA + currentDCRPathLocal;
										System.out.print("- QA DCR: " + currentDCRPathLocal);
										// debugMsg("Document: " + doc.asXML(), startTime);
										if (writeXML(currentDCRPath, doc, client, "ia", "faq/faq-QA")) {
											System.out.println(" - OK");
											DCRCounter++;
										}
									}
									doc = null;
								}
							}

							currentRowContent = null;
						}
						wb.close();
						bis.close();

						if (sbErrors.length() > 0) {
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
							String nowAsISO = df.format(new Date());
							File file = new File("XLSXToDCRs-errors_" + nowAsISO + ".txt");
							BufferedWriter writer = null;
							try {
								writer = new BufferedWriter(new FileWriter(file));
								writer.write(sbErrors.toString());
							} finally {
								if (writer != null)
									writer.close();
							}
							System.out.println("\n*** Generated " + DCRCounter + " FAQ QAs with errors. ***");
						} else {
							System.out.println("\n*** Generated " + DCRCounter + " FAQ QAs without any errors. ***");
						}

						System.out.println("\n... waiting three seconds...\n");
						System.out.flush();
						Thread.sleep(3000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				CSDir rootDirTopic = null;
				try {
					CSFile fileAtVpath = client.getFile(new CSVPath(vpathTopic));
					if ((null != fileAtVpath) && (fileAtVpath.isWritable())) {
						rootDirTopic = (CSDir) fileAtVpath;
						System.out.println("vpath Topic is valid and writeable.");
					} else {
						System.out.println("ERROR: vpath Topic is invalid and/or not writeable. Does that directory exist?");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				// Generate Topics DCRs
				if ((null != topicContents) && (null != rootDirTopic)) {
					int topicsCounter = 0;

					for (Map.Entry<String, HashMap<String, Integer>> topicContent : topicContents.entrySet()) {
						String topicName = topicContent.getKey();
						//String topicNameCamelCase = WordUtils.capitalizeFully(topicName.replaceAll("_", " "));
						HashMap<String, Integer> topicSubContents = topicContent.getValue();
						for (Map.Entry<String, Integer> topicSubContent : topicSubContents.entrySet()) {
							String subTopicName = topicSubContent.getKey();
							String subTopicNameCamelCase = WordUtils.capitalizeFully(subTopicName.replaceAll("_", " "));
							Integer subTopicDCRCount = topicSubContent.getValue();
							String currentDCRName = topicName + "-" + subTopicName + ".xml";
							String heading = subTopicNameCamelCase;

							for (int l = 0; l < LANGUAGES.length; l++) {
								String currentDCRPathLocal = topicName + "/" + LANGUAGES_SHORT[l] + "/" + currentDCRName;
								String currentDCRPath = vpathTopic + currentDCRPathLocal;
								System.out.print("- Topic DCR: " + currentDCRPathLocal);

								String moduleKey = topicName + "/" + subTopicName;
								HashMap<String, String> localisedModule = localisedModules.get(moduleKey);
								if (null != localisedModule) {
									String localisedHeading = localisedModule.get(LANGUAGES_SHORT[l]);
									if ((null != localisedHeading) && !localisedHeading.isEmpty()) {
										heading = localisedHeading;
									}
								}

								Document doc = generateTopicDocument(vpathQA, heading, subTopicDCRCount, topicName, subTopicName);

								if (writeXML(currentDCRPath, doc, client, LANGUAGES_SHORT[l], "faq/faq-topic")) {
									System.out.println(" - OK");
									topicsCounter++;
								}

								// Interlingua
								if (LANGUAGES_SHORT[l].equalsIgnoreCase("en")) {
									currentDCRPathLocal = topicName + "/" + currentDCRName;
									currentDCRPath = vpathTopic + currentDCRPathLocal;
									System.out.print("- Topic DCR: " + currentDCRPathLocal);
									// debugMsg("Document: " + doc.asXML(), startTime);
									if (writeXML(currentDCRPath, doc, client, "ia", "faq/faq-topic")) {
										System.out.println(" - OK");
										topicsCounter++;
									}
								}
							}
						}
					}
					System.out.println("*** Generated " + topicsCounter + " Topics DCRs. ***");
				}

				client.endSession();
			}
		}
		debugMsg("Finished the main method", startTime);
	}

	private static Document generateTopicDocument(String vpath, String heading, int DCRCount, String topicName, String subTopicName) {
		Document doc = DocumentHelper.createDocument();
		Element rootElement = doc.addElement("Content");
		rootElement.addElement("crm_ref");
		rootElement.addElement("categorizationlist");
		rootElement.addElement("usertype").setText("0");
		rootElement.addElement("personalize").setText("Y");

		Element variationElement = rootElement.addElement("variation");
		variationElement.addAttribute("axes2", "all");
		variationElement.addAttribute("axes3", "all");
		variationElement.addAttribute("axes4", "all");

		Element topicElement = variationElement.addElement("topic");
		topicElement.addAttribute("analyticsParam", "");
		topicElement.addAttribute("heading", heading);
		topicElement.addAttribute("screenID", "");

		if (null != subTopicName) {
			for (int i = 1; i <= DCRCount; i++) {
				String subVpathTopic = vpath.substring(vpath.indexOf("/templatedata/")) + topicName + "/" + subTopicName + "-" + String.format("%03d", i) + ".xml";
				Element QAElement = topicElement.addElement("QA");
				QAElement.setText(subVpathTopic);
			}
		}

		return doc;
	}

	private static String convertRTSCellToXML(XSSFRichTextString rtsCellValue) {
		StringBuilder sb = new StringBuilder();
		String rtsAsString = rtsCellValue.toString();
		String previousChar = "";
		boolean inBold = false;
		boolean inInternalLink = false;
		boolean inExternalLink = false;
		boolean inAnchorLink = false;
		boolean inList = false;
		boolean inParagraph = false;
		for (int i = 0; i < rtsCellValue.length(); i++) {
			String currentChar = rtsAsString.charAt(i) + "";
			String nextChar = "";
			try {
				nextChar = rtsAsString.charAt(i + 1) + "";
			} catch (Exception e) {
				// Do nothing, ignore error}
			}
			String restOfStr = rtsAsString.substring(i + 1);
			String restOfStrCleaned = restOfStr.replaceAll("\r", "").replaceAll("\n", "").replaceAll(" ", "");
			String previousStr = rtsAsString.substring(0, i);
			String previousStrCleaned = previousStr.replaceAll("\r", "").replaceAll("\n", "").replaceAll(" ", "");

			XSSFFont currentFont = rtsCellValue.getFontAtIndex(i);
			XSSFFont previousFont = null;
			if (i > 0) {
				previousFont = rtsCellValue.getFontAtIndex(i - 1);
			}

			String currentARGBHexColour = "";
			if ((null != currentFont) && (currentFont.getColor() != XSSFFont.DEFAULT_FONT_COLOR)) {
				if (null != currentFont.getXSSFColor()) {
					currentARGBHexColour = currentFont.getXSSFColor().getARGBHex();
				}
			}
			String previousARGBHexColour = "";
			if ((null != previousFont) && (previousFont.getColor() != XSSFFont.DEFAULT_FONT_COLOR)) {
				if (null != previousFont.getXSSFColor()) {
					previousARGBHexColour = previousFont.getXSSFColor().getARGBHex();
				}
			}

			// Start of a new cell or a new line
			if ((i == 0) || previousChar.equalsIgnoreCase("\n")) {
				if (currentChar.equalsIgnoreCase("1") && restOfStr.startsWith(". ")) {
					sb.append("<OrderedList><Item>");
					inList = true;
				} else if (currentChar.matches("[2-9]") && restOfStr.startsWith(". ")) {
					sb.append("<Item>");
				} else if (!currentChar.equalsIgnoreCase("\n")) {
					sb.append("<Paragraph>");
					inParagraph = true;
				}
			}

			if (!inBold) {
				if (((null != currentFont) && currentFont.getBold()) && ((null == previousFont) || !previousFont.getBold())) {
					if (!lookAheadForBoldChange(rtsCellValue, i + 1, 3) && !inInternalLink && !inExternalLink && !inAnchorLink) {
						sb.append("<Bold style=\"highlight_text\">");
						inBold = true;
					}
				}
			}

			// Colour has changed, start of link
			if (!currentARGBHexColour.equalsIgnoreCase(previousARGBHexColour)) {
				// Look ahead to see if this is relevant (at least 3 characters)
				if (!lookAheadForColourChange(rtsCellValue, currentARGBHexColour, i + 1, 3)) {
					if (currentARGBHexColour.equalsIgnoreCase(LINK_ARGB_INTERNAL)) {
						sb.append("<InternalLink reference=\"LINKTODO\" target=\"current\">");
						inInternalLink = true;
					} else if (currentARGBHexColour.equalsIgnoreCase(LINK_ARGB_EXTERNAL)) {
						sb.append("<ExternalLink url=\"EXTERNALTODO\" target=\"new\">");
						inExternalLink = true;
					} else if (currentARGBHexColour.equalsIgnoreCase(LINK_ARGB_ANCHOR_GENERIC)) {
						sb.append("<Span xml:id=\"genericformsentrypoint\"><AnchorLink reference=\"javascript:void(0)\">");
						inAnchorLink = true;
					} else if (currentARGBHexColour.equalsIgnoreCase(LINK_ARGB_ANCHOR_SPECIFIC)) {
						sb.append("<Span xml:id=\"FORMIDTODO\"><AnchorLink reference=\"javascript:void(0)\">");
						inAnchorLink = true;
					}
				}
			}

			// Colour has changed, end of link
			if (!currentARGBHexColour.equalsIgnoreCase(previousARGBHexColour)) {
				if (inInternalLink && previousARGBHexColour.equalsIgnoreCase(LINK_ARGB_INTERNAL)) {
					sb.append("</InternalLink>");
					inInternalLink = false;
				} else if (inExternalLink && previousARGBHexColour.equalsIgnoreCase(LINK_ARGB_EXTERNAL)) {
					sb.append("</ExternalLink>");
					inExternalLink = false;
				} else if (inAnchorLink && previousARGBHexColour.equalsIgnoreCase(LINK_ARGB_ANCHOR_GENERIC)) {
					sb.append("</AnchorLink></Span>");
					inAnchorLink = false;
				} else if (inAnchorLink && previousARGBHexColour.equalsIgnoreCase(LINK_ARGB_ANCHOR_SPECIFIC)) {
					sb.append("</AnchorLink></Span>");
					inAnchorLink = false;
				}
			}

			if (((null != previousFont) && previousFont.getBold()) && ((null == currentFont) || !currentFont.getBold())) {
				if (inBold) {
					if (!inInternalLink && !inExternalLink && !inAnchorLink) {
						sb.append("</Bold>");
						inBold = false;
					}
				}
			} else if (inBold && ((null == currentFont) || !currentFont.getBold())) {
				// This is the case where the bold ended during a link
				if (!inInternalLink && !inExternalLink && !inAnchorLink) {
					sb.append("</Bold>");
					inBold = false;
				}
			}

			if (inList) {
				if (currentChar.matches("[1-9]") && restOfStr.startsWith(". ")) {
					// Do nothing, it's a list item char
				} else if (previousChar.matches("[1-9]") && currentChar.equalsIgnoreCase(".") && nextChar.matches("[0-9]")) {
					sb.append(currentChar);
				} else if (previousChar.matches("[1-9]") && currentChar.equalsIgnoreCase(".")) {
					// Do nothing, it's a list item char
				} else if (previousStrCleaned.substring(previousStrCleaned.length() - 2).matches("[1-9]\\.") && currentChar.equalsIgnoreCase(" ")) {
					// Do nothing, it's a list item char
				} else if (currentChar.matches("[\r\n]")) {
					// Do nothing, it's a new line char
				} else {
					sb.append(currentChar);
				}
			} else {
				if (currentChar.matches("[\r\n]")) {
					// Do nothing, it's a new line char
				} else {
					sb.append(currentChar);
				}
			}

			// End of line case
			if (restOfStr.startsWith("\n")) {
				if (inInternalLink) {
					sb.append("</InternalLink>");
					inInternalLink = false;
				} else if (inExternalLink) {
					sb.append("</ExternalLink>");
					inExternalLink = false;
				} else if (inAnchorLink) {
					sb.append("</AnchorLink></Span>");
					inAnchorLink = false;
				}
				if (inBold) {
					sb.append("</Bold>");
					inBold = false;
				}
				if (inParagraph) {
					sb.append("</Paragraph>");
					inParagraph = false;
				} else if (inList) {
					if ((restOfStrCleaned.length() > 2) && restOfStrCleaned.substring(0, 2).matches("[2-9]\\.")) {
						sb.append("</Item>");
					} else {
						sb.append("</Item></OrderedList>");
						inList = false;
					}
				}
			}

			// Last character case
			if (i == (rtsCellValue.length() - 1)) {
				if (inAnchorLink) {
					sb.append("</AnchorLink></Span>");
				} else if (inInternalLink) {
					sb.append("</InternalLink>");
				} else if (inExternalLink) {
					sb.append("</ExternalLink>");
				}
				if (inBold) {
					sb.append("</Bold>");
				}
				if (inList) {
					sb.append("</Item></OrderedList>");
				} else if (inParagraph) {
					sb.append("</Paragraph>");
				}
			}
			previousChar = currentChar;
		}

		return sb.toString();
	}

	private static boolean lookAheadForColourChange(XSSFRichTextString rtsCellValue, String currentARGBHexColour, int startPosition, int charCount) {
		boolean hasChanged = false;
		for (int i = startPosition; i < (startPosition + charCount); i++) {
			if (i < rtsCellValue.length()) {
				XSSFFont currentFont = rtsCellValue.getFontAtIndex(i);

				String theARGBHexColour = "";
				if ((null != currentFont) && (currentFont.getColor() != XSSFFont.DEFAULT_FONT_COLOR)) {
					if (null != currentFont.getXSSFColor()) {
						theARGBHexColour = currentFont.getXSSFColor().getARGBHex();
					}
				}

				if (!currentARGBHexColour.equalsIgnoreCase(theARGBHexColour)) {
					hasChanged = true;
					break;
				}
			} else {
				break;
			}
		}

		return hasChanged;
	}

	private static boolean lookAheadForBoldChange(XSSFRichTextString rtsCellValue, int startPosition, int charCount) {
		boolean hasChanged = false;
		for (int i = startPosition; i < (startPosition + charCount); i++) {
			if (i < rtsCellValue.length()) {
				XSSFFont currentFont = rtsCellValue.getFontAtIndex(i);

				if ((null == currentFont) || !currentFont.getBold()) {
					hasChanged = true;
					break;
				}
			} else {
				break;
			}
		}

		return hasChanged;
	}

	protected static boolean writeXML(String path, Document doc, CSClient client, String lang, String type) {
		try {
			CSVPath vpath = new CSVPath(path);
			CSSimpleFile theFile = (CSSimpleFile) client.getFile(vpath);
			if (null == theFile) {
				// File doesn't yet exist, let's create it and set the TeamSite Extended
				// Attributes
				CSWorkarea wa = client.getWorkarea(vpath.getArea(), true);

				// Create parent directories if needed
				CSFile theDir = client.getFile(vpath.getParentPath());
				if (null == theDir) {
					CSFile theParentDir = client.getFile(vpath.getParentPath().getParentPath());
					if (null == theParentDir) {
						theParentDir = wa.createDirectory(vpath.getParentPath().getParentPath().getAreaRelativePath());
					}
					theDir = wa.createDirectory(vpath.getParentPath().getAreaRelativePath());
				}

				theFile = wa.createSimpleFile(vpath.getAreaRelativePath());
				CSExtendedAttribute[] eas = new CSExtendedAttribute[] { new CSExtendedAttribute("G11N/Locale", lang), new CSExtendedAttribute("G11N/Localizable", "true"),
						new CSExtendedAttribute("TeamSite/Assocation/Version", "1"), new CSExtendedAttribute("TeamSite/Templating/DCR/Type", type), new CSExtendedAttribute("iw_form_valid", "true") };
				theFile.setExtendedAttributes(eas);
			}

			// Now output the XML content to the File
			OutputFormat format = OutputFormat.createCompactFormat();
			OutputStream os = theFile.getOutputStream(true);
			XMLWriter writer = new XMLWriter(os, format);
			writer.write(doc);
			os.flush();
			writer.flush();
			os.close();
			return true;
		} catch (Exception e) {
			System.out.println(e.toString());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Utility method for outputting to the debug logger.
	 * 
	 * @param theStr
	 *          String to output
	 * @param startTime
	 *          long System.nanoTime()
	 */
	private static void debugMsg(String theStr, long startTime) {
		System.out.println("[" + (System.currentTimeMillis() - startTime) + " ms] " + theStr);
	}
}
