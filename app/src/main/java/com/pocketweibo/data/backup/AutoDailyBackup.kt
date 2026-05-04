package com.pocketweibo.data.backup

import android.content.Context
import com.pocketweibo.data.prefs.BackupPreferences
import com.pocketweibo.data.repository.WeiboRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AutoDailyBackup {

    const val REL_DIR = "auto_text_backups"
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private const val KEEP_DAYS = 3

    fun directory(context: Context): File =
        File(context.filesDir, REL_DIR).apply { mkdirs() }

    /**
     * One encrypted backup per local calendar day, on first app open that day.
     * Keeps the newest [KEEP_DAYS] calendar days; older files under [REL_DIR] are deleted.
     */
    suspend fun runIfDue(context: Context, repository: WeiboRepository) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStr = today.format(DATE_FMT)
        prune(context, today)
        if (BackupPreferences.getLastAutoTextBackupDay(context) == todayStr) return@withContext

        val json = repository.exportTextOnlyForAutoBackup()
        val encrypted = AutoBackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), context)
        val name = "pw_auto_$todayStr.pwb"
        File(directory(context), name).writeBytes(encrypted)
        BackupPreferences.setLastAutoTextBackupDay(context, todayStr)
        prune(context, today)
    }

    fun prune(context: Context, today: LocalDate) {
        val oldestKeep = today.minusDays((KEEP_DAYS - 1).toLong())
        directory(context).listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".pwb")) return@forEach
            val d = parseDateFromFileName(f.name) ?: return@forEach
            if (d.isBefore(oldestKeep)) f.delete()
        }
    }

    fun parseDateFromFileName(name: String): LocalDate? {
        if (!name.startsWith("pw_auto_") || !name.endsWith(".pwb")) return null
        val core = name.removePrefix("pw_auto_").removeSuffix(".pwb")
        return runCatching { LocalDate.parse(core, DATE_FMT) }.getOrNull()
    }

    fun listBackupFilesNewestFirst(context: Context): List<File> =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".pwb") }
            ?.sortedByDescending { parseDateFromFileName(it.name) ?: LocalDate.MIN }
            ?: emptyList()
}
