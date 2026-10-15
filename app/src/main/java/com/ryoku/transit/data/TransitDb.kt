package com.ryoku.transit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Copia `transit.db` de assets a almacenamiento interno (una vez por versión de app) y la abre en solo lectura. */
object TransitDb {
    fun open(context: Context): SQLiteDatabase {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val target = File(context.filesDir, "transit-$version.db")
        if (!target.exists()) {
            context.filesDir.listFiles { f -> f.name.startsWith("transit-") }?.forEach { it.delete() }
            val tmp = File(context.filesDir, "transit.tmp")
            context.assets.open("transit.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "no se pudo instalar la base de datos" }
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }
}
