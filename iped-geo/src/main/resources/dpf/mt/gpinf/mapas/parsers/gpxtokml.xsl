<?xml version="1.0"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:gpx="http://www.topografix.com/GPX/1/1" xmlns:gx="http://www.google.com/kml/ext/2.2">

<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" />

<xsl:template match="/">
  <kml xmlns="http://www.opengis.net/kml/2.2"
    xmlns:gx="http://www.google.com/kml/ext/2.2"
    xmlns:kml="http://www.opengis.net/kml/2.2"
    xmlns:atom="http://www.w3.org/2005/Atom">
    <xsl:apply-templates select="gpx"/>
  </kml>
</xsl:template>

<xsl:template match="gpx">
  <Document>
    <Style id="route">
      <LineStyle>
        <color>a02020ff</color>
         <width>4</width>
      </LineStyle>
    </Style>
    <xsl:apply-templates select="wpt"/>
    <xsl:apply-templates select="trk"/>
    <xsl:apply-templates select="rte"/>
  </Document>
</xsl:template>

<xsl:template match="wpt">
  <Placemark>
    <name><xsl:value-of select="name"/></name>
    <styleUrl>#route</styleUrl>
    <Point>
	 <coordinates><xsl:value-of select="@lon"/>,<xsl:value-of select="@lat"/>,<xsl:value-of select="ele"/></coordinates>
	</Point>
	<xsl:if test="time"><TimeStamp><when><xsl:value-of select="time"/></when></TimeStamp></xsl:if>
	<xsl:if test="desc"><description><xsl:value-of select="desc"/></description></xsl:if>
  </Placemark>
</xsl:template>

<xsl:template match="trk">
  <xsl:for-each select="trkseg">
	<Folder>
	<name>Trilha em <xsl:value-of select="trkpt/time"/></name>
	<xsl:for-each select="trkpt">
	<Placemark>
		<name><xsl:value-of select="time"/></name>
		<Point>
		<coordinates><xsl:value-of select="@lon"/>,<xsl:value-of select="@lat"/>,<xsl:value-of select="ele"/></coordinates>
		</Point>
		<xsl:if test="time"><TimeStamp><when><xsl:value-of select="time"/></when></TimeStamp></xsl:if>
	</Placemark>
	</xsl:for-each>
	</Folder>
  </xsl:for-each>
</xsl:template>

<xsl:template match="rte">
	<Folder>
	<name>Rota: <xsl:value-of select="name"/></name>
	<xsl:for-each select="rtept">
	<Placemark>
		<name><xsl:value-of select="time"/></name>
		<Point>
		<coordinates><xsl:value-of select="@lon"/>,<xsl:value-of select="@lat"/>,<xsl:value-of select="ele"/></coordinates>
		</Point>
		<xsl:if test="time"><TimeStamp><when><xsl:value-of select="time"/></when></TimeStamp></xsl:if>
	</Placemark>
	</xsl:for-each>
	</Folder>
</xsl:template>

</xsl:stylesheet>