package com.ryoku.transit

import com.ryoku.transit.data.NoticeParser
import org.junit.Assert.*
import org.junit.Test

class NoticeParserTest {
    private val rss = """<?xml version="1.0"?><rss version="2.0"><channel><item>
        <title>Refuerzo &#8220;San Rafael&#8221;</title><link>https://aucorsa.es/x/</link>
        <pubDate>Wed, 14 Oct 2026 11:36:03 +0000</pubDate>
        <description><![CDATA[<p>Días 24 y 25</p> de octubre]]></description></item>
        <item><title></title></item></channel></rss>""".toByteArray()

    @Test fun parsesItemsAndDropsUntitled() {
        val n = NoticeParser.parse(rss)
        assertEquals(1, n.size)
        assertEquals("Refuerzo “San Rafael”", n[0].title)
        assertEquals("2026-10-14T11:36:03Z", n[0].published.toString())
        assertEquals("Días 24 y 25 de octubre", n[0].summary)
    }

    @Test fun rejectsDoctype() {
        val evil = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel/></rss>""".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { NoticeParser.parse(evil) }
    }
}
