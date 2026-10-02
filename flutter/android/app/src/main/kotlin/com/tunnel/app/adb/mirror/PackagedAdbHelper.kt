package com.tunnel.app.adb.mirror

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Only the APK's own packaged asset can become an executable helper. No URL/path from Dart. */
internal object PackagedAdbHelper {
    private const val UPSTREAM = "2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0"
    const val ENTRY_POINT = "com.tunnel.adbhelper.Server"
    private const val MAX_JAR = 4 * 1024 * 1024

    class Artifact(val file: File, val sha256: String)

    fun load(context: Context): Artifact {
        val manifestBytes = context.assets.open("adb-mirror-p0/manifest.json").use { input ->
            val bytes = ByteArray(16 * 1024)
            var size = 0
            while (size < bytes.size) {
                val n = input.read(bytes, size, bytes.size - size)
                if (n < 0) break
                size += n
            }
            if (input.read() != -1) throw IOException("HELPER_MANIFEST_INVALID")
            bytes.copyOf(size)
        }
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        val hash = manifest.getString("sha256")
        if (manifest.getInt("schema") != 1 || manifest.getInt("protocol") != 1 ||
            manifest.getString("upstreamCommit") != UPSTREAM ||
            manifest.getString("entryPoint") != ENTRY_POINT ||
            !Regex("[0-9a-f]{64}").matches(hash)) throw IOException("HELPER_MANIFEST_INVALID")
        val dir = File(context.cacheDir, "adb-mirror-p0")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("HELPER_CACHE_FAILED")
        val file = File.createTempFile("helper-", ".jar", dir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0
            context.assets.open("adb-mirror-p0/helper.jar").use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_JAR) throw IOException("HELPER_TOO_LARGE")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0 || digest.digest().hex() != hash) throw IOException("HELPER_HASH_INVALID")
            return Artifact(file, hash)
        } catch (e: Exception) {
            file.delete() // Only the temporary file created by this call.
            throw e
        }
    }

    internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}
