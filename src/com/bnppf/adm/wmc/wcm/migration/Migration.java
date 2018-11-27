package com.bnppf.adm.wmc.wcm.migration;
import com.interwoven.cssdk.common.CSClient;
import com.interwoven.cssdk.common.CSIterator;
import com.interwoven.cssdk.factory.CSJavaFactory;
import com.interwoven.cssdk.filesys.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.tree.BaseElement;

import java.io.File;
import java.util.*;

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
				String pathToCSSDKCfg = "C:\\Users\\jpope\\Documents\\opentext\\clients\\bnp_paribas\\projects\\2018-11-27_migration\\cssdk.cfg";//new String(System.console().readLine("Path to cssdk.cfg: "));

				// Get the CSClient which we'll use for reading/writing to the TeamSite content store
				CSClient client = getCSSDKClient(username, password, pathToCSSDKCfg);
				debugMsg("Server type is: TeamSite. Got CSSDK client.", startTime);

				Document resultsDoc = generateTeamSiteResults(reportType, client, "/default/main/bnppf/");
				debugMsg(resultsDoc.asXML(), startTime);
			} else {
				debugMsg("Server type is: LiveSite.", startTime);

			}
		}
	}

	private static Document generateTeamSiteResults(String reportType, CSClient client, String branchVpath) {
		Document resultsDoc = DocumentHelper.createDocument();
		Element rootEle = resultsDoc.addElement("results");
		rootEle.addAttribute("server", client.getRoot().getVPath().getServerName());

		if (reportType.equalsIgnoreCase(REPORT_TYPES[0])) {
			Element pageResults = generateTeamSitePageResults(client, branchVpath);
			rootEle.add(pageResults);
		}

		return resultsDoc;
	}

	private static Element generateTeamSitePageResults(CSClient client, String branchVpath) {
		Element resultEle = new BaseElement("pages");
		resultEle.addAttribute("branch", branchVpath);
		try {
			CSDir rootDir = client.getBranch(new CSVPath(branchVpath), true).getWorkareas()[0].getRootDir();
			CSIterator files = recursiveFileSearch(rootDir, ".*\\.page");
			while (files.hasNext()) {
				CSSimpleFile file = (CSSimpleFile) files.next();
				String vpath = file.getVPath().toString();
				resultEle.addElement("vpath").setText(vpath.substring(vpath.indexOf("/sites/") + 7));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultEle;
	}

	private static CSIterator recursiveFileSearch(CSDir dir, String searchRegex) {
		try {
			return dir.getFiles(CSFileKindMask.SIMPLEFILE, null, CSFileKindMask.SIMPLEFILE, searchRegex, 0, -1, true);
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