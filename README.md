**Instructions for FAQ Excel to DCRs functionality**

1.	Copy [XLSXToDCRs.java](src/com/bnppf/adm/wmc/wcm/standalone/XLSXToDCRs.java) to <iw-home>/local/config/lib/content_center/customer_src/src/com/bnppf/adm/wmc/wcm/standalone/
2. Copy the sample Excel file [sample_faqs_ebw.xlsx](sample_faqs_ebw.xlsx) to <iw-home>/local/config/lib/content_center/customer_src/
3.	Download the Apache POI Excel library from http://archive.apache.org/dist/poi/release/bin/poi-bin-3.17-20170915.zip 
4.	Extract the ZIP, and copy the following files to <iw-home>/local/config/lib/content_center/customer_src/lib/ 
  * poi-3.17.jar
  * poi-ooxml-3.17.jar
  * poi-ooxml-schemas-3.17.jar
  * commons-collections4-4.1.jar (it’s in the lib directory in the ZIP)
  * xmlbeans-2.6.0.jar (it’s in the ooxml-lib directory in the ZIP)
5. Create the faq structure in your templatedata directory, if it doesn't already exist:
  * templatedata/faq/faq-QA/data/EBB/
  * templatedata/faq/faq-topic/data/EBB/
6.	Compile the Java Class. You can use the [compile.sh](compile.sh) or [compile.bat](compile.bat) file as an example. Run this from  <iw-home>/local/config/lib/content_center/customer_src/ directory.
7.	Run the Java Class. You can use the [run.sh](run.sh) or [run.bat](run.bat) file as an example. Run this from <iw-home>/local/config/lib/content_center/customer_src/ directory.

**Instructions for using the DCR Search functionality**

To allow the FAQ QA Search from any page (Pre-Controller JSON)
1. Deploy the FAQs (QA and topics) to LiveSite
2. Deploy the synonyms Excel [sample-faq_synonyms.xlsx](sample-faq_synonyms.xlsx) to LiveSite (e.g. rsc/contrib/image/Files/faq-synonyms.xlsx)
3. Download [DCRSearch.java](src/com/bnppf/adm/wmc/wcm/externals/DCRSearch.java) and [XLSXToXMLDocument.java](src/com/bnppf/adm/wmc/wcm/externals/XLSXToXMLDocument.java) to livesite_customer_src/src/com/bnppf/adm/wmc/wcm/externals/, compile (or build) and deploy to LiveSite
4. Create an empty “technical” page with the following Pre-Controller:
  * Param: attr=Pcbb.faq.faq-QA
  * Object: com.bnppf.adm.wmc.wcm.externals.DCRSearch
  * Method: searchJSON
5.	Call the page (using jQuery) with the search parameter and language, e.g. /faqsearch.page?search=account&axes1=en
6.	Parse the JSON results in the page to display them in the required format, adding syntax highlighting as required.
