
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.XMLConstants;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.io.File;
import org.w3c.dom.Node;
import java.io.FileWriter;
import java.io.*;


public class sitemap {
public static void main(String[] args)
{
    /*if (args.length != 3)s
    {
        System.err.println("give command as follows : ");
        System.err.println("XSLTTest data.xml converted.xsl converted.html");
        return;
    }*/
	
	//System.out.println(args[0]);
	String dataXML = "C:/UserTemp/seo/default.sitemap"; 
		String siteXML = "C:/UserTemp/seo/default.site";
	  	  String inputXSL = "C:/UserTemp/seo/ebw-seo-sitemap.xslt";
	  	  String outputXML = "C:/UserTemp/seo/bnppf-sitemap.xml";
	String domainURL = "";
	
	dataXML.replace('\\', '/');
	siteXML.replace('\\', '/');
	inputXSL.replace('\\', '/');
	outputXML.replace('\\', '/');
	
	//if(dataXML.matches("Y:/default/main/ssc-irb/lot-1.2/WORKAREA/devwa(.*)") && siteXML.matches("Y:/default/main/ssc-irb/lot-1.2/WORKAREA/devwa(.*)") && inputXSL.matches("Y:/default/main/ssc-irb/lot-1.2/WORKAREA/devwa(.*)") && outputXML.matches("Y:/default/main/ssc-irb/lot-1.2/WORKAREA/devwa(.*)"))
//{
		
	
	
  try
  {
	
	File xmlFile = new File(siteXML);
	System.out.println("xmlFile "+xmlFile);
	DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
	
	documentFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
	documentFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
	
	Document doc = documentBuilder.parse(xmlFile);
	System.out.println("doc "+doc);
	
	doc.getDocumentElement().normalize();

	NodeList nodeList = doc.getElementsByTagName("Description");
	System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

	for (int temp = 0; temp < nodeList.getLength(); temp++)
	{
		Node node = nodeList.item(temp);
		 System.out.println("\nElement type :" + node.getNodeName());

		if (node.getNodeType() == Node.ELEMENT_NODE)
		{

			Element ele = (Element) node;
			String replacestr = ele.getTextContent();
			//TextNodeParser textparse = new TextNodeParser();
			NodeList  newNodeList  = parseText(replacestr);
			for (int temp1 = 0; temp1 < newNodeList.getLength(); temp1++)
			{
				 Node node1 = newNodeList.item(temp1);
				 System.out.println("\nElement type :" + node1.getNodeName());
				if (node1.getNodeType() == Node.ELEMENT_NODE)
				{
					Element ele1 = (Element) node;
					if(node1.getNodeName() == "domainUrl")
					{
						domainURL = node1.getTextContent();
					}
				}
			}
		   
		}
	}
	 System.out.println("\ndomainURL :" + domainURL);
 } 
 catch (Exception e) {
   System.out.println(e);
  }

	
  sitemap st = new sitemap();
    try
    {
        st.transform(dataXML, siteXML, inputXSL, outputXML, domainURL);
    }
    catch (TransformerConfigurationException e)
    {
        System.err.println("TransformerConfigurationException");
        System.err.println(e);
    }
    catch (TransformerException e)
    {
        System.err.println("TransformerException");
        System.err.println(e);
    }
  //  }
	}

    public void transform(String dataXML, String siteXML, String inputXSL, String outputXML, String domainURL)
    throws TransformerConfigurationException,
    TransformerException
    {
		String finalstring=null;
		try
		{
			TransformerFactory factory = TransformerFactory.newInstance();
			StreamSource xslStream = new StreamSource(inputXSL);
			Transformer transformer = factory.newTransformer(xslStream);
			 System.out.println("transformer" + transformer);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			transformer.setParameter("siteid", siteXML);
			
			
			StreamSource in = new StreamSource(dataXML);
			StringWriter outWriter = new StringWriter();
			StreamResult result = new StreamResult( outWriter );
			 System.out.println("result" + result);
			
			transformer.transform( in, result );  
			StringBuffer sb = outWriter.getBuffer(); 
			finalstring = sb.toString();
			finalstring = finalstring.replaceAll("--URL--",domainURL);
			System.out.println("finalstring" + finalstring);
			

			File file = new File(outputXML);

			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(finalstring);
			bw.close();

			
			System.out.println("The generated HTML file is:" + outputXML);
		}
		catch (TransformerConfigurationException e)
		{
			System.err.println("TransformerConfigurationException");
			System.err.println(e);
		}
		catch (TransformerException e)
		{
			System.err.println("TransformerException");
			System.err.println(e);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
    }
	
	  static Document dom;
	
	public static NodeList parseText(String text) {
	
	text = text.replaceAll("&lt;", "<");
	text = text.replaceAll("&gt;", ">");
	
	
	
    

    try {
	
	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    StringReader reader = new StringReader(text);
    InputSource source = new InputSource(reader);
    
    dbf.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
	
      // Using factory get an instance of document builder
      DocumentBuilder db = dbf.newDocumentBuilder();

      // parse using builder to get DOM representation of the XML file
      dom = db.parse(source);
    } catch (ParserConfigurationException pce) {
	  System.out.println("A ParserConfigurationException occurred while trying to create the DocumentBuilder. AT: ");
	  System.out.println(text + "\n");
	  pce.printStackTrace();
    } catch (SAXException se) {
      //LOG.error("A SAXException occurred while trying to parse the text", se);
	  System.out.println("A SAXException occurred while trying to parse the text AT:");
	  System.out.println(text + "\n");
	  se.printStackTrace();
    } catch (IOException ioe) {
      //LOG.error("A IOException occurred while trying to parse the text", ioe);
	  System.out.println("A IOException occurred while trying to parse the text AT:");
	  System.out.println(text + "\n");
	  ioe.printStackTrace();
    }catch (Exception e) {
      //LOG.error("A IOException occurred while trying to parse the text", ioe);
	  System.out.println("A General exception occurred while trying to parse the text AT:");
	  System.out.println(text + "\n");
	  e.printStackTrace();
    }
    // get the root elememt
    Element rootElement = dom.getDocumentElement();

    // get the list of child nodes
    NodeList parsedNodes = rootElement.getChildNodes();

    return parsedNodes;
  }
  
	public static String formatString(String value){
		try{
			//value = value.toLowerCase();
			value = URLEncoder.encode(java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
			.replaceAll(" ", "-").replaceAll("[^a-zA-Z 0-9 .-]+", ""), "UTF-8");
		}catch (final UnsupportedEncodingException e){
			System.out.println("Error in formatting string:" + value);
		}
      return value;
  }

	
	
	
	
	
}