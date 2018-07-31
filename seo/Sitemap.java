import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Sitemap {
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		if (args.length != 4) {
			System.out.println("Command is: Sitemap sitemap_file default_site_file xslt_file output_file");
		} else {
			try {
				Document sitemapDoc = loadXMLFromFilePath(args[0]);
				Document siteDoc = loadXMLFromFilePath(args[1]);
				System.out.println("Processing...");
				String domainUrl = siteDoc.getElementsByTagName("domainUrl").item(0).getTextContent();
				System.out.println("- default.site domainUrl: " + domainUrl);
				String brand = siteDoc.getElementsByTagName("axis3").item(0).getTextContent();
				System.out.println("- default.site brand: " + brand);
				String device = siteDoc.getElementsByTagName("axis2").item(0).getTextContent();
				System.out.println("- default.site device: " + device);
				String xslFilePath = args[2];
				String outputFilePath = args[3];
				transform(sitemapDoc, xslFilePath, outputFilePath, domainUrl);
			} catch (Exception e) {
				System.out.println("Error processing: " + e.getMessage());
			}
		}
		System.out.println("Finished. Took " + (System.currentTimeMillis() - startTime) + " milliseconds.");
	}
	
	public static Document loadXMLFromFilePath(String filePath) {
		Document theDocument = null;
		try {
			File xmlFile = new File(filePath);
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
	
			docFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			docFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
	
			theDocument = docBuilder.parse(xmlFile);
			System.out.println("Loaded XML Document from file " + filePath);
			
			// Convert HTML descriptions to XML Nodes
			NodeList descriptionNodes = theDocument.getElementsByTagName("description");
			if (descriptionNodes.getLength() == 0) {
				descriptionNodes = theDocument.getElementsByTagName("Description");
			}
			System.out.println("- Descriptions node with HTML to potentially convert to XML: " + descriptionNodes.getLength());
			int convertedCounter = 0;
			for (int i = 0; i < descriptionNodes.getLength(); i++) {
				Node descriptionNode = descriptionNodes.item(i);
				if (!descriptionNode.getTextContent().isEmpty()) {
					convertNodeHTMLToXML(docBuilder, descriptionNode);
					convertedCounter++;
				}
			}
			System.out.println("- Descriptions node with HTML actually converted to XML: " + convertedCounter);
		} catch (FileNotFoundException e) {
			System.out.println("Couldn't load the file: " + filePath);
		} catch (Exception e) {
			System.out.println("Error loading XML from file: " + e.getMessage());
		}
		return theDocument;
	}
	
	public static void convertNodeHTMLToXML(DocumentBuilder docBuilder, Node theNode) {
		String nodeAsXMLString = StringEscapeUtils.unescapeXml(theNode.getTextContent());
		nodeAsXMLString = nodeAsXMLString.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("='", "=\"").replaceAll("'>", "\">");
		try {
			appendXmlFragment(docBuilder, theNode, nodeAsXMLString);
		} catch (Exception e) {
			System.out.println("Error converting Node HTML to XML: " + e.getMessage());
		}
	}

	public static void appendXmlFragment(DocumentBuilder docBuilder, Node parent, String fragment)
			throws IOException, SAXException {
		Document doc = parent.getOwnerDocument();
		Node fragmentNode = docBuilder
				.parse(new InputSource(new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + fragment)))
				.getDocumentElement();
		fragmentNode = doc.importNode(fragmentNode, true);
		parent.setTextContent("");
		parent.appendChild(fragmentNode);
	}

	public static void transform(Document sitemapDoc, String inputXSL, String outputXML, String domainURL)
			throws TransformerConfigurationException, TransformerException {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			StreamSource xslStream = new StreamSource(inputXSL);
			Transformer transformer = factory.newTransformer(xslStream);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.setParameter("domain", domainURL);
			String lastmod = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			transformer.setParameter("lastmod", lastmod);

			StringWriter outWriter = new StringWriter();
			StreamResult result = new StreamResult(outWriter);

			transformer.transform(new DOMSource(sitemapDoc), result);
			StringBuffer sb = outWriter.getBuffer();
			String finalstring = sb.toString();

			File file = new File(outputXML);
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(finalstring);
			bw.close();

			System.out.println("The generated XML file is: " + outputXML);
		} catch (TransformerConfigurationException e) {
			System.err.println("TransformerConfigurationException");
			System.err.println(e);
		} catch (TransformerException e) {
			System.err.println("TransformerException");
			System.err.println(e);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}