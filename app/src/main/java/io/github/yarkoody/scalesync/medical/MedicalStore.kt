package io.github.yarkoody.scalesync.medical

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** The medical record and the uploaded documents, kept in the app's private storage only. */
class MedicalStore(context: Context) {
    private val app = context.applicationContext
    private val recordFile = File(app.filesDir, "medical.json")
    val docsDir = File(app.filesDir, "records").also { it.mkdirs() }
    private val indexFile = File(docsDir, "index.json")

    fun load(): MedicalRecord =
        if (!recordFile.exists()) MedicalRecord()
        else runCatching { MedicalRecord.parse(recordFile.readText()) }.getOrDefault(MedicalRecord())

    fun save(r: MedicalRecord) {
        val tmp = File(recordFile.path + ".tmp")
        tmp.writeText(r.toJson())
        tmp.renameTo(recordFile)
    }

    /** Parses [text] and merges it in. Returns the merged record and how many entries came in. */
    fun importText(text: String): Pair<MedicalRecord, Int> {
        val incoming = MedicalRecord.parse(text)
        val n = incoming.labs.size + incoming.medications.size + incoming.care.size +
            incoming.openItems.size + incoming.history.size + incoming.vitals.size
        val merged = load().merge(incoming)
        if (n > 0) save(merged)
        return merged to n
    }

    fun readUri(uri: Uri): String =
        app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    fun clear() { recordFile.delete() }

    // Uploaded documents

    fun documents(): List<Document> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            (Json.parse(indexFile.readText()) as List<*>).mapNotNull { Document.fromMap(it as? Map<*, *>) }
        }.getOrDefault(emptyList()).filter { File(docsDir, it.file).exists() }.sortedByDescending { it.date ?: LocalDate.MIN }
    }

    private fun saveIndex(docs: List<Document>) = indexFile.writeText(Json.write(docs.map { it.toMap() }, " "))

    fun addDocument(uri: Uri, kind: String, date: LocalDate?): Document {
        val resolver = app.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var name = "Document"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val ext = name.substringAfterLast('.', "").ifEmpty {
            when {
                mime.contains("pdf") -> "pdf"
                mime.startsWith("image/") -> mime.substringAfter('/')
                else -> "bin"
            }
        }
        val id = System.currentTimeMillis().toString()
        val file = "$id.$ext"
        resolver.openInputStream(uri)?.use { input -> File(docsDir, file).outputStream().use { input.copyTo(it) } }
        val doc = Document(id, name.substringBeforeLast('.'), kind, date ?: LocalDate.now(), Instant.now(), mime, file, false)
        saveIndex(documents() + doc)
        return doc
    }

    fun update(doc: Document) = saveIndex(documents().map { if (it.id == doc.id) doc else it })

    fun delete(doc: Document) {
        File(docsDir, doc.file).delete()
        saveIndex(documents().filter { it.id != doc.id })
    }

    fun fileOf(doc: Document) = File(docsDir, doc.file)
}

data class Document(
    val id: String,
    val title: String,
    val kind: String,
    val date: LocalDate?,
    val added: Instant,
    val mime: String,
    val file: String,
    /** True once its contents were sent through Claude and merged into the record. */
    val imported: Boolean,
) {
    fun toMap() = linkedMapOf(
        "id" to id, "title" to title, "kind" to kind, "date" to date?.toString(), "added" to added.toString(),
        "mime" to mime, "file" to file, "imported" to imported,
    )

    companion object {
        val KINDS = listOf("Lab results", "Medical summary", "Prescription", "Imaging", "Letter or referral", "Other")

        fun fromMap(m: Map<*, *>?): Document? {
            m ?: return null
            return Document(
                id = m["id"] as? String ?: return null,
                title = m["title"] as? String ?: "",
                kind = m["kind"] as? String ?: "Other",
                date = MedicalRecord.parseDate(m["date"] as? String),
                added = runCatching { Instant.parse(m["added"] as String) }.getOrDefault(Instant.EPOCH),
                mime = m["mime"] as? String ?: "application/octet-stream",
                file = m["file"] as? String ?: return null,
                imported = m["imported"] == true,
            )
        }
    }
}
