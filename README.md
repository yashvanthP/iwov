**Instructions for FAQ Excel to DCRs functionality**

1.	Copy [XLSXToDCRs.java](src/com/bnppf/adm/wmc/wcm/standalone/XLSXToDCRs.java) to <iw-home>/local/config/lib/content_center/customer_src/src/com/bnppf/adm/wmc/wcm/standalone/
2.	Download the Apache POI Excel library from http://archive.apache.org/dist/poi/release/bin/poi-bin-3.17-20170915.zip 
3.	Extract the ZIP, and copy the following files to <iw-home>/local/config/lib/content_center/customer_src/lib/ 
  * poi-3.17.jar
  * poi-ooxml-3.17.jar
  * poi-ooxml-schemas-3.17.jar
  * commons-collections4-4.1.jar (it’s in the lib directory in the ZIP)
  * xmlbeans-2.6.0.jar (it’s in the ooxml-lib directory in the ZIP)
4.	Compile the Java Class. You can use the [compile.sh](compile.sh) or [compile.bat](compile.bat) file as an example
5.	Run the Java Class. You can use the [run.sh](run.sh) or [run.bat](run.bat) file as an example.
