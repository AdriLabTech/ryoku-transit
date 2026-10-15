package com.ryoku.transit.data

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** Noticia o aviso del feed RSS público de aucorsa.es. No está asociado a líneas ni a paradas. */
data class Notice(val title: String, val link: String, val published: Instant?, val summary: String)

object NoticeParser {
    fun parse(xml: ByteArray): List<Notice> {
        // Contenido de red: sin DTD. Se rechaza por texto porque no todos los parsers (JVM/Android) admiten la feature.
        require(!String(xml, Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE no permitido" }
        val f = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val items = doc.getElementsByTagName("item")
        return List(items.length) { i ->
            val el = items.item(i) as org.w3c.dom.Element
            fun text(tag: String) = el.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            Notice(
                text("title"), text("link"),
                runCatching { ZonedDateTime.parse(text("pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull(),
                text("description").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim(),
            )
        }.filter { it.title.isNotEmpty() }
    }
}

sealed interface NoticesState {
    data object Loading : NoticesState
    /** [fetchedAt] es el momento de ingestión local; [stale] indica que se sirve caché por fallo de red. */
    data class Loaded(val notices: List<Notice>, val fetchedAt: Instant, val stale: Boolean) : NoticesState
    data class Failed(val reason: String) : NoticesState
}

class NoticesSource(private val cacheFile: java.io.File, private val url: String = "https://aucorsa.es/feed/") {
    fun fetch(now: Instant = Instant.now()): NoticesState {
        var last: Exception? = null
        repeat(2) { // reintento acotado
            try {
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000; readTimeout = 8000
                    setRequestProperty("User-Agent", "RyokuTransit/0.1 (uso personal)")
                }
                if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
                val body = c.inputStream.use { it.readBytes() }
                val notices = NoticeParser.parse(body)
                cacheFile.writeBytes(body)
                return NoticesState.Loaded(notices, now, stale = false)
            } catch (e: Exception) { last = e }
        }
        if (cacheFile.exists()) {
            runCatching {
                return NoticesState.Loaded(NoticeParser.parse(cacheFile.readBytes()), Instant.ofEpochMilli(cacheFile.lastModified()), stale = true)
            }
        }
        return NoticesState.Failed(last?.message ?: "sin conexión")
    }
}
