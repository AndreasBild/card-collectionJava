<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="2.0" xmlns:sitemap="http://www.sitemaps.org/schemas/sitemap/0.9"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml">
    <xsl:output omit-xml-declaration="no" method="xml" version="1.0" encoding="UTF-8" indent="yes"
                doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"
                doctype-system="https://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"/>
    <xsl:template match="/">
        <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>XML Sitemap for Domain www.maulmann.de</title>
                <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
                <link rel="stylesheet" type="text/css" href="../css/main.css"/>
            </head>
            <body>
                <h1>XML Sitemap for www.maulmann.de</h1>
                <div>
                    <p>This sitemap contains:
                        <xsl:value-of select="count(sitemap:urlset/sitemap:url)"/> Urls for the Domain www.maulmann.de.
                    </p>
                </div>
                <div>
                    <table cellspacing="0" cellpadding="0">
                        <thead>
                            <tr>
                                <th>Location</th>
                                <th>Priority</th>
                                <th>Change Frequency</th>
                                <th>Last Modified (GMT)</th>
                            </tr>
                        </thead>
                        <xsl:variable name="lower" select="'abcdefghijklmnopqrstuvwxyz'"/>
                        <xsl:variable name="upper" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZ'"/>
                        <tbody>
                            <xsl:for-each select="sitemap:urlset/sitemap:url">
                                <tr>
                                    <xsl:if test="position() mod 2 != 0">
                                        <xsl:attribute name="class">odd</xsl:attribute>
                                    </xsl:if>
                                    <td>
                                        <xsl:variable name="itemURL">
                                            <xsl:value-of select="sitemap:loc"/>
                                        </xsl:variable>
                                        <a href="{$itemURL}" target="_blank">
                                            <xsl:value-of select="sitemap:loc"/>
                                        </a>
                                    </td>
                                    <td>
                                        <xsl:value-of select="concat(sitemap:priority*100,'%')"/>
                                    </td>
                                    <td>
                                        <xsl:value-of
                                                select="concat(translate(substring(sitemap:changefreq, 1, 1),concat($lower, $upper),concat($upper, $lower)),substring(sitemap:changefreq, 2))"/>
                                    </td>
                                    <td>
                                        <xsl:value-of
                                                select="concat(substring(sitemap:lastmod, 0, 11),concat(' ', substring(sitemap:lastmod, 12, 5)))"/>
                                    </td>
                                </tr>
                            </xsl:for-each>
                        </tbody>
                    </table>
                    © Copyright 2024 Maulmann.de
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>