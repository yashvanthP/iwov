package com.bnppf.adm.wmc.wcm.externals;

import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log; // this is only for lab
import org.apache.commons.logging.LogFactory; // this is only for lab
//[BNPPF]import org.slf4j.Logger;
//[BNPPF]import org.slf4j.LoggerFactory;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.json.JSONException;
import org.json.XML;

import com.interwoven.livesite.common.web.ForwardAction;
import com.interwoven.livesite.dom4j.Dom4jUtils;
import com.interwoven.livesite.external.impl.LivesiteSiteMap;
import com.interwoven.livesite.file.FileDal;
import com.interwoven.livesite.runtime.LiveSiteDal;
import com.interwoven.livesite.runtime.RequestContext;

/**
 * DCRSearch is a Class for indexing and searching through a list of TeamSite DCRs. The indexed DCRs are stored in JVM Servlet
 * Context attributes.
 * 
 * @author jpope
 * @version 1.4
 *
 */
public class DCRSearch {
	private static final Log LOGGER = LogFactory.getLog(DCRSearch.class);
	//[BNPPF]private static final Logger LOGGER = LoggerFactory.getLogger(DCRSearch.class);
	private static final String	ROOTATTRIBUTENAME = "dcrsdoc";
	private static final String	DEFAULTATTRIBUTE = ROOTATTRIBUTENAME + ".EBB.faq.faq-QA";
	private static final List<String> LANGUAGES = Arrays.asList("de", "en", "fr", "nl");
	private static final String	DEFAULTLANG = "en";
	private static final String DEFAULTCHANNEL = "PC";
	private static final String DEFAULTBRAND = "fb";
	private static final String DEFAULTAUDIENCE = "rpb";
	private static final String SEPARATOR = "/";
	private static final int MAXRESULTS = 10;
	private static final int UPDATEMINUTES = 5;
	private static final String ACTIONPARAMNAME = "renderer";
	private static final String LANGPARAMNAME = "axes1";
	private static final String CHANNELPARAMNAME = "axes2";
	private static final String BRANDPARAMNAME = "axes3";
	private static final String AUDIENCEPARAMNAME = "axes4";
	private static final String ATTRPARAMNAME = "attr";
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final String JSONCONTENTELEMENT = "JSONContent";
	private static final String TEXTCONTENTELEMENT = "TextContent";
	private static final String CONTENTELEMENTATTR = "contentElementName";
	private static final String NODEID = "nodeId";
	private static final String SYNONYMSATTRIBUTE = "faq.synonyms";
	
	// These next vars define the weights for search results
	private static final int HIT_POINTS_COMPLETE_TERM_QUESTION = 200;
	private static final int HIT_POINTS_COMPLETE_TERM_ANSWER = 5;
	private static final int HIT_POINTS_IN_QUESTION = 40;
	private static final int HIT_POINTS_IN_SYNONYM = 1;
	private static final int HIT_POINTS_IN_ANSWER = 2;
	
	// Stop words
	public static final String[] STOP_WORDS_ENGLISH = new String[] {"and", "if", "or", "with", "else", "when", "why", "what", "who", "where", "how", "can", "you", "see", "get"};
	public static final String[] STOP_WORDS_FRENCH = new String[] {"et", "si", "ou", "avec", "sinon", "quand", "pourquoi", "quoi", "qui", "où", "comment", "peux", "vous", "voir", "avoir"};
	public static final String[] STOP_WORDS_DUTCH = new String[] {"en", "als", "of", "met", "anders", "wanneer", "waarom", "wat", "wie", "waar", "hoe", "kan", "u", "zien", "krijgen"};
	public static final String[] STOP_WORDS_GERMAN = new String[] {"und", "wenn", "oder", "mit", "sonst", "wann", "warum", "was", "wer", "wo", "wie", "kann", "du", "sehen", "bekommen"};
	public static final Map<String, String[]> STOP_WORDS = createMap();

	public static Map<String, String[]> createMap() {
		Map<String, String[]> result = new HashMap<String, String[]>();
		result.put("en", STOP_WORDS_ENGLISH);
		result.put("fr", STOP_WORDS_FRENCH);
		result.put("nl", STOP_WORDS_DUTCH);
		result.put("de", STOP_WORDS_GERMAN);
		return Collections.unmodifiableMap(result);
	}

	/**
	 * This method builds the XML Document containing all relevant DCRs, stores it in a JVM Servlet Content attribute, and returns it.
	 * If the JVM attribute already exists, it just returns the cached XML Document.
	 * @param context The LiveSite RequestContext object.
	 * @return The XML Document of DCRs, e.g. (dcrs)(dcr path="...")(Contents/)(/dcr)(dcr path="...")(Contents/)(/dcr)(/dcrs).
	 */
	public Document execute(RequestContext context) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the execute method", startTime);
		
		HttpServletRequest req = context.getRequest();
		
		// The XML document in which we will store the DCRs
		Document doc = null;
		
		String lang = req.getParameter(LANGPARAMNAME);
		if ((null == lang) || (lang.isEmpty())) {
			lang = DEFAULTLANG;
		}
		debugMsg("Language: " + lang, startTime);
		
		String jvmAttribute = generateJVMAttributeName(context, lang);
		debugMsg("jvmAttribute: " + jvmAttribute, startTime);

		// Try to get the stored XML Document from the JVM Servlet context
		Object jvmDoc = req.getSession().getServletContext().getAttribute(jvmAttribute);
		if (null != jvmDoc) {
			try {
				doc = (Document)jvmDoc;
				if (checkUpdateDCRDoc(doc) || (!StringUtils.isEmpty(req.getParameter(ACTIONPARAMNAME)) && req.getParameter(ACTIONPARAMNAME).equalsIgnoreCase("reload"))) {
					debugMsg("Updating DCR Document, it's been over " + UPDATEMINUTES + " minutes since last update...", startTime);
					doc = null;
				}
				debugMsg("Retreived the XML Document from the JVM attribute", startTime);
			} catch (Exception e) {
				debugMsg(e.getMessage(), startTime);
			}
		}

		if ((null == doc) || (!doc.hasContent())) {
			doc = Dom4jUtils.newDocument();
			Element rootElement = doc.addElement("dcrs");

			// rootPath is the root path of the current LiveSite context, e.g. on TS: //tsserver/default/main/component-guide/WORKAREA/shared/
			// e.g. on LSDS: /usr/Interwoven/LiveSiteDisplayServices/runtime/web/
			String rootPath = context.getFileDal().getRoot();
			debugMsg("rootPath: " + rootPath, startTime);
			
			// jvmAttribute e.g. "dcrdoc.bnppf.faq.faq-QA.en"
			String[] jvmAttrSplit = jvmAttribute.split("\\.");
			
			// DCRRootPath is the workarea-relative path in which the DCRs we want are stored, e.g. "templatedata/faq/faq-QA/data/bnppf"
			String dcrsPath = "templatedata" + SEPARATOR + jvmAttrSplit[2] + SEPARATOR + jvmAttrSplit[3] + SEPARATOR + "data" + SEPARATOR + jvmAttrSplit[1];
			debugMsg("dcrsPath: " + dcrsPath, startTime);

			try {
				// parentDirFullPath is the full file system path to the parent directory
				String parentDirFullPath = rootPath + SEPARATOR + dcrsPath;  // adding separator for higher environments required
				debugMsg("parentDirFullPath: " + parentDirFullPath, startTime);
				try {
					addDCRs(context.getFileDal(), context.getLiveSiteDal(), rootElement, parentDirFullPath, lang);
					
					// Save our updated XML Document into the JVM servlet context for use next time we call this Class
					rootElement.addAttribute("timestamp", dateFormat.format(new Date()));
					req.getSession().getServletContext().setAttribute(jvmAttribute, (Object)doc);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				debugMsg(e.getMessage(), startTime);
			}
		}

		debugMsg("Finished the execute method", startTime);
		
		return doc;
	}
	
	/**
	 * Searches through all DCRs for a search term, and returns the results in JSON format, ordered by most relevant first.
	 * Uses the XML Document containing all DCRs from the JVM Servlet Context attribute.
	 * @param context The LiveSite RequestContext object.
	 * @return A null ForwardAction, as we want the rendering chain to stop at this step.
	 */
	@SuppressWarnings("unchecked")
	public ForwardAction searchJSON(RequestContext context) {
		long startTime = System.currentTimeMillis();
		debugMsg("Calling the searchJSON method", startTime);
		
		HttpServletRequest req = context.getRequest();
		
		String lang = req.getParameter(LANGPARAMNAME);
		String channel = req.getParameter(CHANNELPARAMNAME);
		String brand = req.getParameter(BRANDPARAMNAME);
		String audience = req.getParameter(AUDIENCEPARAMNAME);
		if ((null == lang) || (lang.isEmpty())) {
			lang = DEFAULTLANG;
		}
		if ((null == channel) || (channel.isEmpty())) {
			channel = DEFAULTCHANNEL;
		}
		if ((null == brand) || (brand.isEmpty())) {
			brand = DEFAULTBRAND;
		}
		if ((null == audience) || (audience.isEmpty())) {
			audience = DEFAULTAUDIENCE;
		}
		
		String jvmAttribute = generateJVMAttributeName(context, lang);
		debugMsg("Got the jvmAttribute (\"" + jvmAttribute + "\")", startTime);
		
		Document doc = null;
		boolean madeChangeToDoc = false;
		try {
			// Set default action as "search", for future use other actions may be available
			String action = req.getParameter(ACTIONPARAMNAME);
			if (StringUtils.isEmpty(action)) {
				action = "search";
			}
			
			doc = (Document)req.getSession().getServletContext().getAttribute(jvmAttribute);
			if ((null == doc) || checkUpdateDCRDoc(doc) || action.equalsIgnoreCase("reload")) {
				debugMsg("Running execute method to populate DCR Document...", startTime);
				doc = execute(context);
			}
			debugMsg("Got the XML doc", startTime);
			
			// Get the synonyms doc
			Document synonymsDoc = (Document)req.getSession().getServletContext().getAttribute(SYNONYMSATTRIBUTE);
			if (null == synonymsDoc) {
				XLSXToXMLDocument synonymsDocClass = new XLSXToXMLDocument();
				synonymsDoc = synonymsDocClass.execute(context);
			}
			
			String[] CURRENT_STOP_WORDS = STOP_WORDS.get(lang);
			
			String search = req.getParameter("search");
			String fullSearchQuery = search;
			String fullSearchQueryLower = search.toLowerCase();
			debugMsg("search: " + search, startTime);
			search = search.replace("?", "");
			for (String stopWord : CURRENT_STOP_WORDS) {
				search = search.replaceAll("(?i)\\b" + stopWord + "\\b", "").trim();
			}
			StringBuilder cleanedSearch = new StringBuilder();
			String[] splitSearchTerms = search.trim().split("\\s+");
			for (String searchTerm : splitSearchTerms) {
				if (searchTerm.length() > 2) {
					// Remove plural character(s) from end of search term
					if (lang.equalsIgnoreCase("en") || lang.equalsIgnoreCase("fr")) {
						if (searchTerm.endsWith("s")) {
							searchTerm = searchTerm.substring(0, searchTerm.length() - 1);
						}
					} else if (lang.equalsIgnoreCase("de") || lang.equalsIgnoreCase("nl")) {
						if (searchTerm.endsWith("en")) {
							searchTerm = searchTerm.substring(0, searchTerm.length() - 2);
						}
					}
					cleanedSearch.append(searchTerm.toLowerCase() + " ");
				}
			}
			search = cleanedSearch.toString().trim();
			debugMsg("search after cleanup and stop words removal: " + search, startTime);

			try {
				// Add synonyms now
				debugMsg("Looking for synonyms...", startTime);
				if (null != synonymsDoc) {
					// Get all relevant cells from the Synonyms doc
					String lastPhrase = "";
					List<Element> synonymsMatchingCells = synonymsDoc.getRootElement().selectNodes("//cell[starts-with(@name, 'synonyms') and @lang='" + lang + "']");
					if (null != synonymsMatchingCells) {
						for (Element synonymsMatchingCell : synonymsMatchingCells) {
							List<Element> currentLines = synonymsMatchingCell.selectNodes("line");
							if (null != currentLines) {
								for (Element currentLine : currentLines) {
									String currentSynonym = currentLine.getText();
									if (fullSearchQueryLower.indexOf(currentSynonym) != -1) {
										Element currentPhraseEle = (Element) synonymsMatchingCell.getParent().selectSingleNode("cell[starts-with(@name, 'phrase') and @lang = '" + lang + "']");
										String currentPhrase = currentPhraseEle.elementText("line");
										if (!lastPhrase.equalsIgnoreCase(currentPhrase)) {
											debugMsg("Found matching synonym phrase:" + currentPhrase, startTime);
											search += " " + currentPhrase;
										}
										lastPhrase = currentPhrase;
									}
								}
							}
						}
						
					}
				}
				debugMsg("search with synonyms: " + search, startTime);
			} catch (Exception e) {
				debugMsg("Error: " + e.getMessage(), startTime);
			}

			StringBuilder json = new StringBuilder();
			json.append("{\r\n");

			debugMsg("Starting DCR search", startTime);
			if (action.equalsIgnoreCase("search") && (!StringUtils.isEmpty(search))) {
				Element rootEle = doc.getRootElement();
				debugMsg("Got XML doc root element", startTime);
				
				// Get the sitemap (for replacing .page links with NodeIds)
				Document sitemapDoc = null;
				Element sitemapRootEle = null;
				try {
					LivesiteSiteMap sitemap = new LivesiteSiteMap();
					sitemapDoc = sitemap.getSiteMap(context);
					sitemapRootEle = sitemapDoc.getRootElement();
				} catch (Exception e) {
					debugMsg("Error loading the sitemap: " + e.getMessage(), startTime);
				}
				
				// This is our regex pattern for finding HTML elements in the text
				Pattern htmlElementsPattern = Pattern.compile("<[^>]*>");
				debugMsg("Compiled HTML elements regex pattern", startTime);
				
				// Get all DCRs for the current language
				List<Element> currentDCRs = (List<Element>)rootEle.elements("dcr");
				TreeMap<Integer, List<Integer>> resultsMap = new TreeMap<Integer, List<Integer>>();
				debugMsg("Got list of " + currentDCRs.size() + " DCR elements", startTime);
				
				int dcrCounter = 0;
				for (Element currentDCR : currentDCRs) {
					// This will count the total number of search query hit points for the current DCR
					int hitPoints = 0;
					
					// Here we want to get only the text from every relevant Element, ignoring attributes text, ideally from cache
					String currentDcrContentsAsStr = currentDCR.elementText(TEXTCONTENTELEMENT);
					if (StringUtils.isEmpty(currentDcrContentsAsStr)) {
						currentDcrContentsAsStr = getOnlyElementsText((Element)currentDCR.element(currentDCR.attributeValue(CONTENTELEMENTATTR)));
						
						// The DCR as a string without any HTML elements
						currentDcrContentsAsStr = htmlElementsPattern.matcher(currentDcrContentsAsStr).replaceAll("");
						
						// Cache the text only content for future use
						currentDCR.addElement(TEXTCONTENTELEMENT).setText(currentDcrContentsAsStr);
						madeChangeToDoc = true;
					}

					Node questionNode = null;
					Node synonymsNode = null;
					if (null != currentDCR.element("Content")) {
						questionNode = currentDCR.element("Content").element("question");
						synonymsNode = currentDCR.element("Content").element("synonyms");
					}

					// Count the number of hits for the complete search term
					// This pattern matches words that start with the searched term (e.g. search "count" will also match "counting")
					Pattern patternComplete = Pattern.compile("\\b" + fullSearchQuery, Pattern.CASE_INSENSITIVE);
					Matcher matcherCompleteQuestion = patternComplete.matcher(questionNode.getText());
					while (matcherCompleteQuestion.find()) {
						hitPoints += HIT_POINTS_COMPLETE_TERM_QUESTION;
					}
					Matcher matcherCompleteAnswer = patternComplete.matcher(currentDcrContentsAsStr);
					while (matcherCompleteAnswer.find()) {
						hitPoints += HIT_POINTS_COMPLETE_TERM_ANSWER;
					}
					
					// Count the number of hits for each of the search terms
					String[] splitTerms = search.trim().split("\\s+");
					if (splitTerms.length > 0) {
						for (String searchTerm : splitTerms) {
							if (searchTerm.length() > 2) {
								// This pattern matches words that start with the searched term (e.g. search "count" will also match "counting")
								Pattern pattern = Pattern.compile("\\b" + searchTerm, Pattern.CASE_INSENSITIVE);
								
								Matcher matcher = pattern.matcher(currentDcrContentsAsStr);
								while (matcher.find()) {
									hitPoints += HIT_POINTS_IN_ANSWER;
								}

								// Count the number of hits for the complete search term in the question (10 points per hit)
								if (null != questionNode) {
									Matcher matcherQuestion = pattern.matcher(questionNode.getText());
									while (matcherQuestion.find()) {
										hitPoints += HIT_POINTS_IN_QUESTION;
									}
								}

								// Count the number of hits for the complete search term in the synonyms (10 points per hit)
								if (null != synonymsNode) {
									Matcher matcherSynonyms = pattern.matcher(synonymsNode.getText());
									while (matcherSynonyms.find()) {
										hitPoints += HIT_POINTS_IN_SYNONYM;
									}
								}
							}
						}
					}
					
					if (hitPoints > 0) {
						if (null == resultsMap.get(hitPoints)) {
							List<Integer> resultDCRsArray = new ArrayList<Integer>();
							resultDCRsArray.add(new Integer(dcrCounter));
							resultsMap.put(hitPoints, resultDCRsArray);
						} else {
							List<Integer> resultDCRsArray = resultsMap.get(hitPoints);
							resultDCRsArray.add(new Integer(dcrCounter));
							resultsMap.put(hitPoints, resultDCRsArray);
						}
					}
					
					dcrCounter++;
				}
				debugMsg("Finished DCR search", startTime);
				
				List<Integer> sortedResultsArray = new LinkedList<Integer>();
				for (List<Integer> DCRsArray : resultsMap.descendingMap().values()) {
					for (Integer currentDCRIndex : DCRsArray) {
						sortedResultsArray.add(currentDCRIndex);
					}
				}
				debugMsg("Generated the sorted array", startTime);
				
				// This is will be the number of results to send, either the maximum of MAXRESULTS, or less if there are fewer DCRs total
				int resultsToSend = sortedResultsArray.size();
				if (resultsToSend > MAXRESULTS) {
					resultsToSend = MAXRESULTS;
				}
				
				json.append("\"dcrs\": [\r\n");
				for (int i=0; i < resultsToSend; i++) {
					Element currentDCR = (Element)currentDCRs.get(sortedResultsArray.get(i));

					String currentDCRJSONContent = currentDCR.elementText(JSONCONTENTELEMENT);
					if (null == currentDCRJSONContent) {
						// We don't have the JSON-ready output for this DCR in the cache, so let's cache it for next time
						currentDCRJSONContent = generateJSONContent(currentDCR.element(currentDCR.attributeValue(CONTENTELEMENTATTR)));
						currentDCR.addElement(JSONCONTENTELEMENT).setText(currentDCRJSONContent);
						madeChangeToDoc = true;
					}
					
					// Replace .page URLs with their associated NodeId from the site map
					if (null != sitemapRootEle) {
			            Pattern patternPages = Pattern.compile("/sites/[^/]+/([^\\\"]+)\\.page", Pattern.CASE_INSENSITIVE);
						Matcher matcherPages = patternPages.matcher(currentDCRJSONContent);
						while (matcherPages.find()) {
							String foundURL = matcherPages.group(0);
							String foundPage = matcherPages.group(1);
							Node foundNode = sitemapRootEle.selectSingleNode("//node[link/value = '" + foundPage + "']");
							if (null != foundNode) {
								Node foundNodeDescription = foundNode.selectSingleNode("description");
								if (null != foundNodeDescription) {
									String foundNodeDescStr = foundNodeDescription.asXML();
									String nodeIdStr = "&lt;" + NODEID + "&gt;";
									String nodeId = foundNodeDescStr.substring(foundNodeDescStr.indexOf(nodeIdStr) + nodeIdStr.length(), foundNodeDescStr.indexOf("&lt;/" + NODEID + "&gt;"));
									String fullNodeId = "/" + lang + "/" + channel + "/" + brand + "/" + audience + "/" + nodeId;
									currentDCRJSONContent = currentDCRJSONContent.replaceAll(foundURL, fullNodeId);
									matcherPages = patternPages.matcher(currentDCRJSONContent);
								}
							} else {
								debugMsg("Could not find a node matching page " + foundPage, startTime);
							}
						}
					}
					
					json.append(currentDCRJSONContent);
					
					if (i < (resultsToSend - 1)) {
						json.append(",\r\n");
					} else {
						json.append("\r\n");
					}
				}
				json.append("]\r\n");
			} else if (action.equalsIgnoreCase("reload")) {
				json.append("\"reload\":\"OK\"");
			} else {
				debugMsg("Unknown action: " + action + " and/or search was empty.", startTime);
			}
			json.append("}");
			
			debugMsg("Finished creating JSON", startTime);
			
			HttpServletResponse res = context.getResponse();
			res.setContentType("application/json");
			PrintWriter writer = res.getWriter();
			writer.print(json.toString());
			writer.flush();
			writer.close();
			
			// If changes were made to the doc during this method, then update the object in JVM cache
			if (madeChangeToDoc) {
				req.getSession().getServletContext().setAttribute(jvmAttribute, (Object)doc);
				debugMsg("Updated Document in JVM cache", startTime);
			}
			
		} catch (Exception e) {
			debugMsg("Error: " + e.getMessage(), startTime);
		}
		
		debugMsg("Finished the executeAjax method", startTime);
		return null;
	}
	

	public ForwardAction executeAjax(RequestContext context) {
		return searchJSON(context);
	}
	
	/**
	 * Utility method to determine whether we need to update the DCR Document. Default behaviour is to update after UPDATEMINUTES minutes.
	 * @param theDoc The XML Document containing the DCRs. Must have a timestamp attribute on its root Element.
	 * @return true if it's time to update, false if not.
	 */
	private boolean checkUpdateDCRDoc(Document theDoc) {
		Element rootEle = theDoc.getRootElement();
		String timestamp = rootEle.attributeValue("timestamp");
		if (!StringUtils.isEmpty(timestamp)) {
			try {
				Date lastModified = dateFormat.parse(timestamp);
				Date now = new Date();
				if (((now.getTime() - lastModified.getTime()) / 1000 / 60) > UPDATEMINUTES) {
					return true;
				}
			} catch (ParseException e) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(e.getMessage());
				}
			}
		}
		return false;
	}

	/**
	 * Recursively read all DCRs in the current directory and all lang child directories. Each time a valid DCR is found, its contents are
	 * added to the parent XML Document, e.g. <dcrs path="..."><dcr path="..."><Contents/></dcr><dcr path=".."><Contents/></dcr></dcrs>.
	 * Ignores directories inside language directories.
	 * @param context The RequestContext
	 * @param rootElement The root XML Element in which we will store the DCRs
	 * @param dirPath The directory which we will scan for DCRs, must be TeamSite standard, e.g. .../templatedata/.../.../data/.../[.../]en/
	 * @param lang The language of DCRs to search for (e.g. "en"), one of LANGUAGES array
	 */
	@SuppressWarnings("unchecked")
	private void addDCRs(FileDal fileDal, LiveSiteDal liveSiteDal, Element rootElement, String dirPath, String lang) {
		if ((null != dirPath) && (dirPath.indexOf("templatedata") > -1)) {
			if (LOGGER.isDebugEnabled()) { LOGGER.debug("Working in path: " + dirPath); }
			dirPath = StringUtils.removeEnd(dirPath, SEPARATOR);
			
			// Get the current directory from the path (e.g. for /templatedata/faq/faq-QA/data/bnppf/Accounts/fr, it's "fr"
			String currentPathDir = dirPath.substring(dirPath.lastIndexOf(SEPARATOR) + 1);
			String langDir = "/" + lang + "/";
			
			// Check whether this is a language directory (i.e. containing DCRs we want).
			if (currentPathDir.equalsIgnoreCase(lang)) {
				// Get all files in current directory
				String[] DCRPaths = fileDal.getChildFiles(dirPath);
				if (null != DCRPaths) {
					if (LOGGER.isDebugEnabled()) { LOGGER.debug("Found " + DCRPaths.length + " DCRs."); }
					for (String currentDCRPath : DCRPaths) {
						// Get the relative path to the current form (DCR)
						currentDCRPath = dirPath.substring(dirPath.indexOf("templatedata")) + SEPARATOR + currentDCRPath;
						if (LOGGER.isDebugEnabled()) { LOGGER.debug("Trying to get DCR at path: " + currentDCRPath); }
						
						// Try and read the current file from disk, and store it as an XML Document
						Document currentDCRDoc = liveSiteDal.readXmlFile(currentDCRPath);
						if ((null != currentDCRDoc) && (null != currentDCRDoc.getRootElement())) {
							// Add a new DCR element to the root element, with the path as an attribute
							Element currentDCREle = rootElement.addElement("dcr");
							currentDCREle.addAttribute("path", currentDCRPath);
							currentDCREle.addAttribute(CONTENTELEMENTATTR, currentDCRDoc.getRootElement().getName());
							currentDCREle.add(currentDCRDoc.getRootElement().detach());
							
							// See if current DCR has any references to other DCRs
							List<Element> elementsWithDCRs = (List<Element>)currentDCREle.selectNodes("//*[contains(text(), 'templatedata')]");
							if (null != elementsWithDCRs) {
								for (Element currentEleWithDCR : elementsWithDCRs) {
									String linkedDCRPath = currentEleWithDCR.getText();
									int lastSlash = linkedDCRPath.lastIndexOf("/");
									if (linkedDCRPath.indexOf(langDir) == -1) {
										linkedDCRPath = linkedDCRPath.substring(0, lastSlash + 1) + lang + "/" + linkedDCRPath.substring(lastSlash + 1);
									}
									if (LOGGER.isDebugEnabled()) { LOGGER.debug("Found element with DCR path: " + currentEleWithDCR.getName() + ", path: " + linkedDCRPath); }
									Document linkedDCRDoc = liveSiteDal.readXmlFile(linkedDCRPath);
									if ((null != linkedDCRDoc) && (null != linkedDCRDoc.getRootElement())) {
										currentEleWithDCR.setText("");
										currentEleWithDCR.add(linkedDCRDoc.getRootElement().detach());
									}
								}
							}
						} else {
							if (LOGGER.isDebugEnabled()) { LOGGER.debug("Could not get DCR at path: " + currentDCRPath); }
						}
					}
				} else {
					if (LOGGER.isDebugEnabled()) { LOGGER.debug("context.getFileDal().getChildFiles(\"" + dirPath + "\") returned null."); }
				}
			} else {
				// Try to recurse through all directories in the current directory
				String[] directories = fileDal.getChildDirectories(dirPath);
				if (null != directories) {
					if (LOGGER.isDebugEnabled()) { LOGGER.debug("Found " + directories.length + " directories."); }
					for (String currentDirectoryPath : directories) {
						// Only recurse inside the current directory if it's the lang directory, or not another lang directory
						if (currentDirectoryPath.equalsIgnoreCase(lang) || (!LANGUAGES.contains(currentDirectoryPath))) {
							addDCRs(fileDal, liveSiteDal, rootElement, dirPath + "/" + currentDirectoryPath, lang);
						}
					}
				} else {
					if (LOGGER.isDebugEnabled()) { LOGGER.debug("context.getFileDal().getChildDirectories(\"" + dirPath + "\") returned null."); }
				}
			}
		}
	}
	
	/**
	 * Generates the attribute name for the JVM Servlet Context attribute for this site, DCT, and lang. Tries to get the base
	 * attribute name from the request or context (format is "<sitename>.<DCT category>.<DCT name>" e.g. "bnppf.faq.faq-QA").
	 * Otherwise it tries to get it from an example DCR path in the Component Datum called "sampleDCR".
	 * @param context The LiveSite RequestContext object.
	 * @param lang The language for this request, e.g. "en".
	 * @return The full name of the JVM Servlet Context attribute, e.g. dcrdoc.bnppf.faq.faq-QA.en
	 */
	@SuppressWarnings("deprecation")
	private String generateJVMAttributeName(RequestContext context, String lang) {
		String jvmAttribute = DEFAULTATTRIBUTE + "." + lang;
		
		// attr parameter must be of the format <sitename>.<DCT category>.<DCT name>, e.g. "bnppf.faq.faq-QA"
		String attr = context.getRequest().getParameter(ATTRPARAMNAME);
		String attrFromContext = context.getParameterString(ATTRPARAMNAME);
		
		if (StringUtils.isNotEmpty(attr)) {
			if (LOGGER.isDebugEnabled()) { LOGGER.debug("Got attr from request: " + attr); }
			jvmAttribute = ROOTATTRIBUTENAME + "." + attr + "." + lang;
		} else if (StringUtils.isNotEmpty(attrFromContext)) {
			if (LOGGER.isDebugEnabled()) { LOGGER.debug("Got attr from context: " + attrFromContext); }
			jvmAttribute = ROOTATTRIBUTENAME + "." + attrFromContext + "." + lang;
		} else {
			// Try and get the sample DCR path from the context parameters, use case: Component in a Page
			String exampleDCRPath = context.getParameterString("sampleDCR");
			if (StringUtils.isNotEmpty(exampleDCRPath)) {
				if (LOGGER.isDebugEnabled()) { LOGGER.debug("exampleDCRPath: " + exampleDCRPath); }
				// Assumes sample path like: "templatedata/faq/faq-QA/data/bnppf/Accounts/QA-accounts-001.xml"
				try {
					String[] pathSplit = exampleDCRPath.split(SEPARATOR);
					String DCTCategory = pathSplit[1];
					String DCTName = pathSplit[2];
					String siteName = pathSplit[4];
					jvmAttribute = ROOTATTRIBUTENAME + "." + siteName + "." + DCTCategory + "." + DCTName + "." + lang;
				} catch (Exception e) {
					if (LOGGER.isDebugEnabled()) { LOGGER.debug(e.getMessage()); }
					jvmAttribute = DEFAULTATTRIBUTE + "." + lang;
				}
			}
		}
		return jvmAttribute;
	}
	
	/**
	 * Recursively extracts the text content from all elements that contain text, and appends it to strBuilder.
	 * @param theEle The XML Element in which we'll recursively search.
	 * @param strBuilder The StringBuilder in which we'll append the text content found.
	 */
	@SuppressWarnings("unchecked")
	private void getTextElements(Element theEle, StringBuilder strBuilder) {
		List<Element> eleList = theEle.elements();
		if (null != eleList) {
			for (Element currentEle : eleList) {
				if (currentEle.hasContent()) {
					if ((null != currentEle.getText()) && (!currentEle.getText().isEmpty())) {
						String theText = currentEle.getText();
						theText = StringEscapeUtils.unescapeHtml(theText);
						strBuilder.append(theText);
					}
					getTextElements(currentEle, strBuilder);
				}
			}
		}
	}
	
	/**
	 * Extracts the text only content from theEle and all child Elements recursively.
	 * @param theEle The XML Element in which we'll recursively search.
	 * @return The String containing the text content.
	 */
	public String getOnlyElementsText(Element theEle) {
		StringBuilder strBuilder = new StringBuilder();
		getTextElements(theEle, strBuilder);
		return strBuilder.toString();
	}
	
	/**
	 * Goes through all elements in a DCR and converts the XML structure to JSON
	 * @param theEle The XML Element we want to get the content from
	 * @return A String with the XML converted to JSON
	 */
	public String generateJSONContent(Element theEle) {
		String eleAsJSON = new String("");
		try {
			eleAsJSON = XML.toJSONObject(theEle.asXML()).toString();
			eleAsJSON = eleAsJSON.replaceAll("\\\\/", "/");
			eleAsJSON = eleAsJSON.replaceAll("\\\\r", "");
			eleAsJSON = eleAsJSON.replaceAll("\\\\n", "");
			eleAsJSON = eleAsJSON.replaceAll("\\\\u200b", "");
			
			// Convert XOpus to HTML
			String[][] patterns = new String [][] {
				{"<Region>", "<div class='region'>"},
				{"<Section>", "<div class='section level1'>"},
				{"<Paragraph>", "<p>"},
				{"</Region>", "</div>"},
				{"</Section>", "</div>"},
				{"</Paragraph>", "</p>"},
				{"<Bold[^>]*>", "<span class='highlight_text'>"},
				{"</Bold>", "</span>"},
				{"<UnorderedList>", "<ul>"},
				{"</UnorderedList>", "</ul>"},
				{"<Item>", "<li>"},
				{"</Item>", "</li>"},
				{"<OrderedList>", "<ol>"},
				{"</OrderedList>", "</ol>"},
				{"<InternalLink reference", "<a href"},
				{"<ExternalLink reference", "<a href"},
				{" target=\\\\\\\"[^\"]+\\\\\\\"", ""},
				{"</InternalLink>", "</a>"},
				{"</ExternalLink>", "</a>"},
				{"<Span", "<span"},
				{"xml:id", "id"},
				{"<AnchorLink reference", "<a href"},
				{"</AnchorLink>", "</a>"},
				{"</Span>", "</span>"}};
			
			for (String[] pattern : patterns) {
				Pattern r = Pattern.compile(pattern[0]);
				Matcher matcher = r.matcher(eleAsJSON);
				eleAsJSON = matcher.replaceAll(pattern[1]);
			}
			
		} catch (JSONException e) {
			if (LOGGER.isDebugEnabled()) { LOGGER.debug("Could not convert XML Element to a JSON String: " + e.getMessage() + ". Cause: " + e.getCause()); }
		}
		return eleAsJSON;
	}
	
	/**
	 * Utility method for outputting to the debug logger.
	 * @param theStr String to output
	 * @param startTime long System.nanoTime()
	 */
	public void debugMsg(String theStr, long startTime) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("[" + (System.currentTimeMillis() - startTime) + " milliseconds] " + theStr);
		}
	}
}
