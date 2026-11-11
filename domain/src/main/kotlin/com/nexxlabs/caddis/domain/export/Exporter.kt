package com.nexxlabs.caddis.domain.export

import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** ACC-02: export the whole library from the local database. */
object Exporter {
    const val FORMAT_VERSION = 1

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class ExportFile(
        val format: String = "caddis-export",
        val version: Int = FORMAT_VERSION,
        val exportedAt: String,
        val intents: List<ExportIntent>,
        val collections: List<ExportCollection>,
        val items: List<ExportItem>,
    )

    @Serializable
    data class ExportIntent(val id: String, val name: String, val doneLabel: String, val isDefault: Boolean)

    @Serializable
    data class ExportCollection(val id: String, val name: String, val createdAt: String)

    @Serializable
    data class ExportItem(
        val id: String,
        val type: String,
        val url: String?,
        val platform: String,
        val kind: String,
        val title: String,
        val description: String,
        val creator: String,
        val thumbnailUrl: String?,
        val lengthSeconds: Int?,
        val note: String,
        val intent: String,
        val reason: String,
        val sourceApp: String,
        val sourceFrom: String,
        val status: String,
        val createdAt: String,
        val doneAt: String?,
        val deletedAt: String?,
        val returnAt: String?,
        val collections: List<String>,
    )

    fun toJson(items: List<Item>, intents: List<Intent>, collections: List<Collection>, now: Instant): String {
        val file = ExportFile(
            exportedAt = now.toString(),
            intents = intents.map { ExportIntent(it.id, it.name, it.doneLabel, it.isDefault) },
            collections = collections.map { ExportCollection(it.id, it.name, it.createdAt.toString()) },
            items = items.map { i ->
                ExportItem(
                    id = i.id, type = i.type.name.lowercase(), url = i.url, platform = i.platform.label,
                    kind = i.kind.label, title = i.title, description = i.description, creator = i.creator,
                    thumbnailUrl = i.thumbnailUrl, lengthSeconds = i.lengthSeconds, note = i.note,
                    intent = i.intentId, reason = i.reason, sourceApp = i.sourceApp, sourceFrom = i.sourceFrom,
                    status = i.status.name.lowercase(), createdAt = i.createdAt.toString(),
                    doneAt = i.doneAt?.toString(), deletedAt = i.deletedAt?.toString(),
                    returnAt = i.returnAt?.toString(), collections = i.collectionIds.sorted(),
                )
            },
        )
        return json.encodeToString(ExportFile.serializer(), file)
    }

    /**
     * Netscape bookmark file, importable by every browser. Links only (notes have no URL),
     * deleted items left out. One folder per collection, then a folder for the rest.
     */
    fun toHtmlBookmarks(items: List<Item>, collections: List<Collection>, intents: List<Intent>): String {
        val links = items.filter { it.url != null && it.status != ItemStatus.DELETED }
        val intentName = intents.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Caddis</TITLE>\n<H1>Caddis</H1>\n<DL><p>\n")
        fun entry(i: Item) {
            val added = i.createdAt.epochSecond
            val tags = listOfNotNull(intentName[i.intentId], if (i.status == ItemStatus.DONE) "done" else null).joinToString(",")
            sb.append("        <DT><A HREF=\"").append(esc(i.url!!)).append("\" ADD_DATE=\"").append(added)
                .append("\" TAGS=\"").append(esc(tags)).append("\">").append(esc(i.displayTitle)).append("</A>\n")
            if (i.reason.isNotBlank()) sb.append("        <DD>").append(esc(i.reason)).append("\n")
        }
        for (c in collections) {
            val inC = links.filter { c.id in it.collectionIds }
            if (inC.isEmpty()) continue
            sb.append("    <DT><H3>").append(esc(c.name)).append("</H3>\n    <DL><p>\n")
            inC.forEach(::entry)
            sb.append("    </DL><p>\n")
        }
        val loose = links.filter { it.collectionIds.isEmpty() }
        if (loose.isNotEmpty()) {
            sb.append("    <DT><H3>All saves</H3>\n    <DL><p>\n")
            loose.forEach(::entry)
            sb.append("    </DL><p>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
