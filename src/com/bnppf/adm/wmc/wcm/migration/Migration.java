package com.bnppf.adm.wmc.wcm.migration;
import com.interwoven.cssdk.common.CSClient;
import com.interwoven.cssdk.common.CSIterator;
import com.interwoven.cssdk.common.CSObjectNotFoundException;
import com.interwoven.cssdk.factory.CSJavaFactory;
import com.interwoven.cssdk.filesys.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.BaseElement;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.exit;

/**
 * Migration scans the local TeamSite or LiveSite and generates a migration report.
 *
 * @author jpope
 * @version 2018-11-27 16:46
 *
 */
public class Migration {
	private static final String[] SERVER_TYPES = {"teamsite", "livesite"};
	private static final String[] REPORT_TYPES = {"all", "pages", "dcrs", "components", "templates", "resources", "assets"};
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		debugMsg("Starting the main method", startTime);

		if (args.length < 2) {
			debugMsg("ERROR: Migration <server type (teamsite|livesite)> <output report path> [report type (all|pages|dcrs|...)]", startTime);
			exit(0);
		} else {
			String serverType = args[0];
			if (!Arrays.asList(SERVER_TYPES).contains(serverType)) {
				debugMsg("ERROR: '" + serverType + "' isn't a valid value. It must be empty (default: " + SERVER_TYPES[0] + ") or one of: " + Arrays.toString(SERVER_TYPES), startTime);
				exit(0);
			}

			String outputPath = args[1];
			File reportFile = new File(outputPath);
			if (!reportFile.canWrite()) {
				debugMsg("ERROR: Can't write to file at path '" + outputPath + "'.", startTime);
				exit(0);
			}

			String reportType = (args.length == 3) ? args[2] : REPORT_TYPES[0];
			if (!Arrays.asList(REPORT_TYPES).contains(reportType)){
				debugMsg("ERROR: '" + reportType + "' isn't a valid value. It must be empty (default: " + REPORT_TYPES[0] + ") or one of: " + Arrays.toString(REPORT_TYPES), startTime);
				exit(0);
			}

			if (serverType.equalsIgnoreCase("teamsite")) {
				String username = "root";//new String(System.console().readLine("Username: "));
				String password = "password";//new String(System.console().readPassword("Password: "));
				String pathToCSSDKCfg = "C:\\Users\\jpope\\IdeaProjects\\bnppf\\cssdk.cfg";//new String(System.console().readLine("Path to cssdk.cfg: "));

				// Get the CSClient which we'll use for reading/writing to the TeamSite content store
				CSClient client = getCSSDKClient(username, password, pathToCSSDKCfg);
				debugMsg("Server type is: TeamSite. Got CSSDK client. Generating results document now...", startTime);

				Document resultsDoc = generateTeamSiteResults(reportType, client, "/default/main/component-guide/", startTime);

				debugMsg("Completed results document generation. Saving to file now...", startTime);
				//debugMsg(resultsDoc.asXML(), startTime);
				try {
					FileOutputStream fos = new FileOutputStream(reportFile);
					OutputFormat format = OutputFormat.createPrettyPrint();
					XMLWriter writer = new XMLWriter(fos, format);
					writer.write(resultsDoc);
					writer.flush();
					debugMsg("Saved results document file to: " + reportFile.getCanonicalPath(), startTime);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				debugMsg("Server type is: LiveSite.", startTime);
				// TODO
			}
		}
		debugMsg("Finished.", startTime);
	}

	private static Document generateTeamSiteResults(String reportType, CSClient client, String branchVpath, long startTime) {
		Document resultsDoc = DocumentHelper.createDocument();
		Element rootEle = resultsDoc.addElement("results");
		rootEle.addAttribute("server", client.getRoot().getVPath().getServerName());

		// Pages
		if (reportType.equalsIgnoreCase(REPORT_TYPES[0]) || reportType.equalsIgnoreCase(REPORT_TYPES[1])) {
			Element pageResults = generateTeamSitePageResults(client, branchVpath, startTime);
			rootEle.add(pageResults);
		}

		// DCRs
		if (reportType.equalsIgnoreCase(REPORT_TYPES[0]) || reportType.equalsIgnoreCase(REPORT_TYPES[2])) {
			Element DCRResults = generateTeamSiteDCRResults(client, branchVpath, startTime);
			rootEle.add(DCRResults);
		}

		// Assets
		if (reportType.equalsIgnoreCase(REPORT_TYPES[0]) || reportType.equalsIgnoreCase(REPORT_TYPES[6])) {
			Element assetsResults = generateTeamSiteAssetsResults(client, branchVpath, startTime);
			rootEle.add(assetsResults);
		}

		return resultsDoc;
	}

	private static Element generateTeamSitePageResults(CSClient client, String branchVpath, long startTime) {
		Element resultEle = new BaseElement("pages");
		resultEle.addAttribute("branch", branchVpath);
		try {
			CSDir rootDir = client.getBranch(new CSVPath(branchVpath), true).getWorkareas()[0].getRootDir();
			debugMsg("- Got branch root directory: " + rootDir.getVPath().getPathNoServer().toString() + ". Looking for Pages now...", startTime);
			CSIterator files = recursiveFileSearch(rootDir, ".*\\.page");
			if (files.getTotalSize() > 0) {
				debugMsg("- Found " + files.getTotalSize() + " Pages. Getting associations for these Pages now...", startTime);
				while (files.hasNext()) {
					CSSimpleFile file = (CSSimpleFile) files.next();

					Element page = resultEle.addElement("page");
					String vpath = file.getVPath().getPathNoServer().toString();
					page.addAttribute("vpath", vpath);

					Element associations = getChildAssociations(client, file, startTime);
					if (associations.hasContent()) {
						page.add(associations);
					}
				}
				debugMsg("- Finished getting associations for the Pages.", startTime);
			} else {
				debugMsg("- Didn't find any Pages.", startTime);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultEle;
	}

	private static Element generateTeamSiteDCRResults(CSClient client, String branchVpath, long startTime) {
		Element resultEle = new BaseElement("dcrs");
		resultEle.addAttribute("branch", branchVpath);
		try {
			CSDir rootDir = client.getBranch(new CSVPath(branchVpath), true).getWorkareas()[0].getRootDir();
			debugMsg("- Got branch root directory: " + rootDir.getVPath().getPathNoServer().toString() + ". Looking for DCRs now...", startTime);
			CSIterator files = recursiveFileSearch((CSDir)client.getFile(new CSVPath(rootDir.getVPath().toString() + "/templatedata/")), ".*\\.xml");
			if (files.getTotalSize() > 0) {
				debugMsg("- Found " + files.getTotalSize() + " DCRs. Getting associations for these DCRs now...", startTime);
				while (files.hasNext()) {
					CSSimpleFile file = (CSSimpleFile) files.next();

					//if (file.getVPath().toString().contains("templatedata")) {
						Element dcr = resultEle.addElement("dcr");
						String vpath = file.getVPath().getPathNoServer().toString();
						dcr.addAttribute("vpath", vpath);

						Element associations = getChildAssociations(client, file, startTime);
						if (associations.hasContent()) {
							dcr.add(associations);
						}
					//}
				}
				debugMsg("- Finished getting associations for the DCRs.", startTime);
			} else {
				debugMsg("- Didn't find any DCRs.", startTime);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultEle;
	}

	private static Element generateTeamSiteAssetsResults(CSClient client, String branchVpath, long startTime) {
		Element resultEle = new BaseElement("assets");
		resultEle.addAttribute("branch", branchVpath);
		try {
			CSDir rootDir = client.getBranch(new CSVPath(branchVpath), true).getWorkareas()[0].getRootDir();
			debugMsg("- Got branch root directory: " + rootDir.getVPath().getPathNoServer().toString() + ". Looking for assets now...", startTime);
			CSIterator files = recursiveFileSearch(rootDir, ".*\\.jpg");
			if (files.getTotalSize() > 0) {
				debugMsg("- Found " + files.getTotalSize() + " assets. Getting associations now...", startTime);
				while (files.hasNext()) {
					CSSimpleFile file = (CSSimpleFile) files.next();

					Element dcr = resultEle.addElement("asset");
					String vpath = file.getVPath().getPathNoServer().toString();
					dcr.addAttribute("vpath", vpath);

					Element associations = getChildAssociations(client, file, startTime);
					if (associations.hasContent()) {
						dcr.add(associations);
					}
				}
				debugMsg("- Finished getting associations for the assets.", startTime);
			} else {
				debugMsg("- Didn't find any assets.", startTime);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultEle;
	}

	private static Element getParentAssociations(CSClient client, CSSimpleFile file, long startTime) {
		Element associations = DocumentHelper.createElement("associations");

		try {
			String localWAVpath = file.getArea().getVPath().getPathNoServer().toString();

			CSAssociation[] parentAssociations = file.getParentAssociations(null);
			for (CSAssociation currentAssociation : parentAssociations) {
				Element associationElement = associations.addElement("association");
				associationElement.addAttribute("direction", "PARENT");
				associationElement.addAttribute("type", currentAssociation.getType());
				try {
					CSAssociatable currentParent = currentAssociation.getParent();
					associationElement.addAttribute("vpath", currentParent.getUAI());
				} catch (CSObjectNotFoundException missingE) {
					CSVpathAssociatable missingAsset = (CSVpathAssociatable) currentAssociation.getParent(false);
					associationElement.addAttribute("missing", "true");
					associationElement.addAttribute("vpath", missingAsset.getVPath().toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return associations;
	}

	private static Element getChildAssociations(CSClient client, CSSimpleFile file, long startTime) {
		Element associations = DocumentHelper.createElement("associations");

		try {
			String localWAVpath = file.getArea().getVPath().getPathNoServer().toString();

			CSAssociation[] childAssociations = file.getChildAssociations(null, false, false, true);
			for (CSAssociation currentAssociation : childAssociations) {
				Element associationElement = associations.addElement("association");
				associationElement.addAttribute("direction", "CHILD");
				associationElement.addAttribute("type", currentAssociation.getType());
				try {
					CSAssociatable currentChild = currentAssociation.getChild();
					associationElement.addAttribute("vpath", currentChild.getUAI());
				} catch (CSObjectNotFoundException missingE) {
					CSVpathAssociatable missingAsset = (CSVpathAssociatable) currentAssociation.getChild(false);
					associationElement.addAttribute("missing", "true");
					associationElement.addAttribute("vpath", missingAsset.getVPath().toString());
				}
			}

			// Look in Pages
			if (file.getVPath().toString().endsWith(".page")) {
				BufferedInputStream fileIS = file.getBufferedInputStream(true);
				StringBuilder sbFileContents = new StringBuilder();
				int content;
				while ((content = fileIS.read()) != -1) {
					sbFileContents.append((char) content);
				}
				fileIS.close();

				Pattern patternXSL = Pattern.compile("(&lt;xsl:include href=\")(.*\\.xsl)(\".*/&gt;)", Pattern.CASE_INSENSITIVE);
				Matcher matcherXSL = patternXSL.matcher(sbFileContents.toString());
				while (matcherXSL.find()) {
					String foundXSLResult = matcherXSL.group(2);
					int localVpathStartIndex = foundXSLResult.indexOf("/custom/") + 8;
					if (localVpathStartIndex > 7) {
						String vpathXSLResult = localWAVpath + "/" + foundXSLResult.substring(localVpathStartIndex);
						CSVPath vpathXSL = new CSVPath(vpathXSLResult);
						CSFile fileXSL = client.getFile(vpathXSL);
						Element associationElement = associations.addElement("association");
						associationElement.addAttribute("direction", "PARENT");
						if ((null == fileXSL) || !fileXSL.isReadable()) {
							associationElement.addAttribute("missing", "true");
						}
						associationElement.addAttribute("vpath", vpathXSLResult);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return associations;
	}

	private static CSIterator recursiveFileSearch(CSDir dir, String searchRegex) {
		try {
			return dir.getFiles(CSFileKindMask.ALLFILES, null, CSFileKindMask.ALLFILES, searchRegex, 0, -1, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static CSClient getCSSDKClient(String username, String password, String pathToCSSDKCfg) {
		Properties properties = new Properties();
		properties.setProperty("com.interwoven.cssdk.factory.CSFactory", "com.interwoven.cssdk.factory.CSJavaFactory");
		properties.setProperty("cssdk.cfg.path", pathToCSSDKCfg);

		CSJavaFactory factory = (CSJavaFactory)CSJavaFactory.getFactory(properties);
		CSClient theClient = null;
		try {
			theClient = factory.getClient(username, "", password, Locale.getDefault(), "Migration", "192.168.180.129");
		} catch (Exception e) {
			System.out.println("Exception occurred while retrieving CSClient object: " + e.getMessage());
			e.printStackTrace();
		}

		return theClient;
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