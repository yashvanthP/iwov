<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" indent="yes"/>
	<xsl:param name="domain" />
	<xsl:param name="lastmod" />
	<xsl:param name="changefreq">daily</xsl:param>
	<xsl:param name="priority">0.5</xsl:param>
	<xsl:template match="/">
		<urlset>
			<xsl:apply-templates select="site-map/segment"/>
		</urlset>
	</xsl:template>
	<xsl:template match="segment">
		<xsl:apply-templates select="//node"/>
	</xsl:template>
	<xsl:template match="node">
		<xsl:for-each select="description/settings/xmlSiteMap/axes1/axis">
			<xsl:variable name="axes1" select="."/>
			<xsl:choose>
				<xsl:when test="../../axes4/axis">
					<xsl:for-each select="../../axes4/axis">
						<url>
							<loc>
								<xsl:value-of select="$domain"/>
								<xsl:text>/</xsl:text>
								<xsl:value-of select="$axes1"/>
								<xsl:text>/</xsl:text>
								<xsl:for-each select="../../../../../ancestor::node">
									<xsl:value-of select="translate(description/settings/display[@lang=$axes1]/title, ' áàâäéèêëíìîïóòôöúùûü', '-aaaaeeeeiiiioooouuuu')"/>
									<xsl:text>/</xsl:text>
								</xsl:for-each>
								<xsl:value-of select="translate(../../../display[@lang=$axes1]/title, ' áàâäéèêëíìîïóòôöúùûü', '-aaaaeeeeiiiioooouuuu')"/>
								<xsl:text>?axes4=</xsl:text>
								<xsl:value-of select="."/>
							</loc>
							<lastmod><xsl:value-of select="$lastmod"/></lastmod>
							<changefreq><xsl:value-of select="$changefreq"/></changefreq>
							<priority><xsl:value-of select="$priority"/></priority>
						</url>
					</xsl:for-each>
				</xsl:when>
				<xsl:otherwise>
					<url>
						<loc>
							<xsl:value-of select="$domain"/>
							<xsl:text>/</xsl:text>
							<xsl:value-of select="."/>
							<xsl:text>/</xsl:text>
							<xsl:for-each select="../../../../../ancestor::node">
								<xsl:value-of select="translate(description/settings/display[@lang=$axes1]/title, ' áàâäéèêëíìîïóòôöúùûü', '-aaaaeeeeiiiioooouuuu')"/>
								<xsl:text>/</xsl:text>
							</xsl:for-each>
							<xsl:value-of select="translate(../../../display[@lang=$axes1]/title, ' áàâäéèêëíìîïóòôöúùûü', '-aaaaeeeeiiiioooouuuu')"/>
						</loc>
						<lastmod><xsl:value-of select="$lastmod"/></lastmod>
						<changefreq><xsl:value-of select="$changefreq"/></changefreq>
						<priority><xsl:value-of select="$priority"/></priority>
					</url>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:for-each>
	</xsl:template>
</xsl:stylesheet>