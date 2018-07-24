<?xml version="1.0"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" xmlns:xmlutil="xalan://sitemap" exclude-result-prefixes="xmlutil xalan">
<xsl:param name="siteid" />
<xsl:template match="/">



<html>
<head><title>Search Index Links</title></head><body>

	<xsl:apply-templates select="site-map/segment"/>


</body>

</html>
</xsl:template>


<xsl:template match="segment">

<xsl:param name="file1" select="document($siteid)"/>



<xsl:variable name="brand" select="substring-before(substring-after($file1/Site/Description, '&lt;axis3&gt;'),'&lt;/axis3&gt;')" />

<xsl:variable name="device" select="substring-before(substring-after($file1/Site/Description, '&lt;axis2&gt;'),'&lt;/axis2&gt;')" />


	<xsl:apply-templates select="node">
	
		<xsl:with-param name="level">0</xsl:with-param>
		<xsl:with-param name="enParent"></xsl:with-param>
		<xsl:with-param name="frParent"></xsl:with-param>
		<xsl:with-param name="nlParent"></xsl:with-param>
		<xsl:with-param name="deParent"></xsl:with-param>
		<xsl:with-param name="brand"><xsl:value-of select="$brand"/></xsl:with-param>
		<xsl:with-param name="device"><xsl:value-of select="$device"/></xsl:with-param>
	</xsl:apply-templates>

</xsl:template>

<xsl:template match="node">
	<xsl:param name="level"/>
	<xsl:param name="enParent"/>
	<xsl:param name="frParent"/>
	<xsl:param name="nlParent"/>
	<xsl:param name="deParent"/>
	<xsl:param name="brand"/>
	<xsl:param name="device"/>
	
	<xsl:variable name="enTitle"/>
	<xsl:variable name="frTitle"/>
	<xsl:variable name="nlTitle"/>
	<xsl:variable name="deTitle"/> 
	
	<xsl:variable name="enUrl"/>
	<xsl:variable name="frUrl"/>
	<xsl:variable name="nlUrl"/>
	<xsl:variable name="deUrl"/>
			
	<xsl:variable name="pagelink">
	<xsl:value-of select="link/value"/>
	</xsl:variable>
	<!-- <xsl:copy-of select="description"/> -->
		 <xsl:if test='description != ""'> 
		
			<xsl:variable name="sfSettings">
				<sfSettingsParent>
					<sfSettings>						
						 <xsl:copy-of select="xmlutil:parseText(description)"/> 
					</sfSettings>
				</sfSettingsParent>
			</xsl:variable>

				  <!-- <xsl:copy-of select="$sfSettings"/>   -->
				 
			<xsl:variable name="enTitle">
				<xsl:value-of select="xmlutil:formatString(xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/display[@lang='en']/title)"/>
			</xsl:variable>	
			<xsl:variable name="frTitle">
				<xsl:value-of select="xmlutil:formatString(xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/display[@lang='fr']/title)"/>
			</xsl:variable>
			<xsl:variable name="nlTitle">
				<xsl:value-of select="xmlutil:formatString(xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/display[@lang='nl']/title)"/>
			</xsl:variable>
			<xsl:variable name="deTitle">
				<xsl:value-of select="xmlutil:formatString(xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/display[@lang='de']/title)"/>
			</xsl:variable> 
			

			 <xsl:variable name="axesURLs">
			 <urls>
			 <!-- <xsl:for-each select="xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/xmlSiteMap/axes3//*"> -->
			 <xsl:variable name="temp">axes3=<xsl:value-of select="$brand" />&amp;</xsl:variable>
			 <xsl:for-each select="xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/xmlSiteMap/axes4//*">
			 <url><xsl:value-of select="$temp" />axes4=<xsl:value-of select="." /></url>
			<!-- </xsl:for-each>	-->
			 </xsl:for-each>  
			</urls>			 
			</xsl:variable>	 

			
			<xsl:variable name="axesURLs2">
			<urls>
			<!-- <xsl:for-each select="xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/xmlSiteMap/axes2//*" > -->
			 <xsl:variable name="temp">axes2=<xsl:value-of select="$device" />&amp;</xsl:variable>
			 <xsl:for-each select="xalan:nodeset($axesURLs)/urls//*" >
			 <url><xsl:value-of select="$temp" /><xsl:value-of select="." /></url>
			 </xsl:for-each>	
			<!-- </xsl:for-each> -->
			</urls>			 
			</xsl:variable>	
				
			 <xsl:if test="normalize-space(./link/@type) = 'page' and normalize-space(./link/value) != ''"> 
			
			<xsl:choose> 
			
			
			
			<xsl:when test='$frParent != ""'>
		
				<xsl:variable name="enUrl" select="concat('--URL--/en/',$enParent,'/',$enTitle,'?axes1=en')" />
				<xsl:variable name="frUrl" select="concat('--URL--/fr/',$frParent,'/',$frTitle,'?axes1=fr')" />
				<xsl:variable name="nlUrl" select="concat('--URL--/nl/',$nlParent,'/',$nlTitle,'?axes1=nl')" />
				<xsl:variable name="deUrl" select="concat('--URL--/de/',$deParent,'/',$deTitle,'?axes1=de')" />
				
				
				
				<xsl:for-each select="xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/xmlSiteMap/axes1//*" >
					<xsl:if test="$brand!='kn'">
					
					<xsl:if test=".='en'">
					
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$enUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$enUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					</xsl:if>
					
					<xsl:if test=".='nl'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$nlUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$nlUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					<xsl:if test=".='fr'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$frUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$frUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					<xsl:if test="$brand != 'kn'">
					<xsl:if test=".='de'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$deUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$deUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if> 
					</xsl:if>
				</xsl:for-each>

			</xsl:when>
			<xsl:otherwise>


					<xsl:variable name="enUrl" select="concat('--URL--/en/',$enTitle,'?axes1=en')"/>
					<xsl:variable name="frUrl" select="concat('--URL--/fr/',$frTitle,'?axes1=fr')"/>
					<xsl:variable name="nlUrl" select="concat('--URL--/nl/',$nlTitle,'?axes1=nl')"/>
					<xsl:variable name="deUrl" select="concat('--URL--/de/',$deTitle,'?axes1=de')"/>
					<xsl:for-each select="xalan:nodeset($sfSettings)/sfSettingsParent/sfSettings/settings/xmlSiteMap/axes1//*" >
					<xsl:if test="$brand != 'kn'">
					<xsl:if test=".='en'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$enUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$enUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					</xsl:if>
					<xsl:if test=".='nl'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$nlUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$nlUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					<xsl:if test=".='fr'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$frUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$frUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					<xsl:if test="$brand != 'kn'">
					<xsl:if test=".='de'">
					<xsl:for-each select="xalan:nodeset($axesURLs2)/urls//*" >
					<a>
					<xsl:attribute name="href">
					<xsl:value-of select="$deUrl"/>&amp;<xsl:value-of select="."/>
					</xsl:attribute>
					<xsl:value-of select="$deUrl"/>&amp;<xsl:value-of select="."/>
					</a><xsl:text>&#xa;</xsl:text>
					</xsl:for-each>
					</xsl:if>
					</xsl:if>
				</xsl:for-each>
				
			 </xsl:otherwise>				
			</xsl:choose> 
		 </xsl:if>	
		

			
		 	<xsl:choose>
				<xsl:when test='$frParent != ""'>
					<xsl:apply-templates select="node">
						<xsl:with-param name="level" select="$level+1"/>
						<xsl:with-param name="enParent" select="concat($enParent,'/',$enTitle)"/>
						<xsl:with-param name="frParent" select="concat($frParent,'/',$frTitle)"/>
						<xsl:with-param name="nlParent" select="concat($nlParent,'/',$nlTitle)"/>
						<xsl:with-param name="deParent" select="concat($deParent,'/',$deTitle)"/>
						<xsl:with-param name="brand" select="$brand"/>
						<xsl:with-param name="device" select="$device"/>
					</xsl:apply-templates>
				</xsl:when>
				<xsl:otherwise>
					 <xsl:apply-templates select="node">
						<xsl:with-param name="level" select="$level+1"/>
						<xsl:with-param name="enParent" select="$enTitle"/>
						<xsl:with-param name="frParent" select="$frTitle"/>
						<xsl:with-param name="nlParent" select="$nlTitle"/>
						<xsl:with-param name="deParent" select="$deTitle"/>
						<xsl:with-param name="brand" select="$brand"/>
						<xsl:with-param name="device" select="$device"/>
					  </xsl:apply-templates>
				</xsl:otherwise>				
				</xsl:choose> 
			
		 </xsl:if> 	
	

	
</xsl:template>





</xsl:stylesheet>