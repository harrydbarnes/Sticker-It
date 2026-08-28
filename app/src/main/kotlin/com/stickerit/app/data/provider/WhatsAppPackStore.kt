package com.stickerit.app.data.provider

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Legacy manifest kept only so the Room pack migration can recover v3 selections. */
@Singleton
class WhatsAppPackStore @Inject constructor(private val context: Context) {
    data class Pack(val identifier: String, val name: String, val fileNames: List<String>, val imageDataVersion: String)
    private val manifest get() = AtomicFile(File(context.filesDir, "whatsapp_pack.json"))
    fun writePack(pack: Pack) {
        val bytes = JSONObject().apply {
            put("identifier", pack.identifier); put("name", pack.name); put("imageDataVersion", pack.imageDataVersion); put("files", JSONArray(pack.fileNames))
        }.toString().encodeToByteArray()
        val stream = manifest.startWrite()
        try {
            stream.write(bytes)
            manifest.finishWrite(stream)
        } catch (error: Exception) {
            manifest.failWrite(stream)
            throw error
        }
    }
    fun readPack(): Pack? = runCatching {
        val json = JSONObject(manifest.readFully().decodeToString()); val files = json.getJSONArray("files")
        Pack(json.getString("identifier"), json.getString("name"), List(files.length()) { files.getString(it) }, json.getString("imageDataVersion"))
    }.getOrNull()
}
