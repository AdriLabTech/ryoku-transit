package com.ryoku.transit.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ryoku.transit.domain.Version
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Dónde se publican las versiones: Releases de GitHub. El repositorio debe ser público (la API se consulta sin credenciales). */
object UpdateConfig {
    const val REPO = "AdriLabTech/ryoku-transit"
    const val CHECK_EVERY_MS = 6 * 3_600_000L
    /** Solo se aceptan APK servidos desde GitHub. */
    const val APK_URL_PREFIX = "https://github.com/$REPO/releases/download/"
}

/** Versión publicada. [sha256] viene del campo `digest` de GitHub y puede faltar en versiones antiguas. */
data class ReleaseInfo(val version: Version, val notes: String, val apkUrl: String, val apkSize: Long, val sha256: String?)

object ReleaseParser {
    /** `null` si la release es borrador/prerelease, el tag no es semver o no hay un `.apk` en los assets. */
    fun parse(json: String): ReleaseInfo? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = Version.parse(o.optString("tag_name")) ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val url = a.optString("browser_download_url")
            if (!a.optString("name").endsWith(".apk") || !url.startsWith(UpdateConfig.APK_URL_PREFIX)) continue
            val digest = a.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }?.lowercase()
            return ReleaseInfo(version, o.optString("body").trim(), url, a.optLong("size"), digest)
        }
        return null
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: Version) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val percent: Int) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

class UpdateSource(private val context: Context) {
    private val dir = File(context.cacheDir, "updates")

    fun current(): Pair<Version, Long> {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        return (Version.parse(p.versionName.orEmpty()) ?: Version(0, 0, 0)) to p.longVersionCode
    }

    /** Última release publicada, o `null` si no hay ninguna válida. Lanza [IOException] sin red. */
    fun latest(): ReleaseInfo? {
        val c = open("https://api.github.com/repos/${UpdateConfig.REPO}/releases/latest")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode == 404) return null // sin releases todavía
        if (c.responseCode != 200) throw IOException("GitHub respondió HTTP ${c.responseCode}")
        return ReleaseParser.parse(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    fun isMetered(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    /** Descarga y verifica tamaño y SHA-256 (si GitHub lo da). Reutiliza el fichero si ya está completo. */
    fun download(info: ReleaseInfo, onProgress: (Int) -> Unit): File {
        dir.mkdirs()
        val target = File(dir, "ryoku-transit-${info.version}.apk")
        if (target.exists() && matches(target, info)) return target
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "${target.name}.part")
        val c = open(info.apkUrl)
        if (c.responseCode != 200) throw IOException("Descarga: HTTP ${c.responseCode}")
        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
        c.inputStream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                    if (pct != last) { last = pct; onProgress(pct) }
                }
            }
        }
        if (!matches(part, info)) { part.delete(); throw IOException("El APK descargado no coincide con la release (tamaño o SHA-256)") }
        check(part.renameTo(target)) { "No se pudo guardar el APK" }
        return target
    }

    /** `null` si el APK es válido para instalar encima; si no, el motivo. La firma la comprueba Android al instalar. */
    fun verifyArchive(file: File, info: ReleaseInfo): String? {
        val p = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return "El fichero descargado no es un APK válido"
        if (p.packageName != context.packageName) return "El APK es de otra aplicación"
        if (p.longVersionCode <= current().second) return "El APK no es más nuevo que la versión instalada"
        if (Version.parse(p.versionName.orEmpty()) != info.version) return "La versión del APK no coincide con la release"
        return null
    }

    fun cleanup() { dir.deleteRecursively() }

    private fun matches(f: File, info: ReleaseInfo): Boolean {
        if (info.apkSize > 0 && f.length() != info.apkSize) return false
        val want = info.sha256 ?: return true
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) } == want
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000; readTimeout = 15000
        setRequestProperty("User-Agent", "RyokuTransit-updater")
    }
}

object ApkInstaller {
    const val PREF_ERROR = "update_error"

    /** `false` si Android aún no permite a esta app instalar paquetes: abre el ajuste para concederlo (una sola vez). */
    fun ensurePermission(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return false
    }

    /**
     * Instala [apk] con PackageInstaller. En Android 12+ pide que no haya confirmación cuando es una actualización de la
     * misma app y esta fue su instalador; si el sistema la exige, [UpdateInstallReceiver] muestra su diálogo.
     */
    fun install(context: Context, apk: File) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { i -> s.openWrite("ryoku-transit.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            // Mutable: el instalador rellena el estado en el intent.
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(pending.intentSender)
        }
    }
}

/** Resultado de la sesión de instalación: lanza la confirmación del sistema si hace falta y guarda el error si falla. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> prefs.edit().remove(ApkInstaller.PREF_ERROR).apply()
            else -> prefs.edit().putString(
                ApkInstaller.PREF_ERROR,
                "Instalación fallida (código $status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}",
            ).apply()
        }
    }
}
