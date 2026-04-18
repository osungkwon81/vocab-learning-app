package com.gwon.vocablearning.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class LearningDatabaseProtection(
    context: Context,
    private val databaseName: String,
    private val targetVersion: Int,
    private val maxPreopenBackups: Int = 5,
    private val maxFailureSnapshots: Int = 3,
) {
    private val databaseFile = context.getDatabasePath(databaseName)
    private val backupRoot = File(context.filesDir, "database-backups/$databaseName")
    private val lastPreparedFingerprintFile = File(backupRoot, "last-preopen-fingerprint.txt")

    fun backupBeforeVersionChange() {
        if (!databaseFile.exists()) {
            return
        }

        val currentVersion = readUserVersion(databaseFile) ?: return
        if (currentVersion == targetVersion) {
            return
        }

        backupRoot.mkdirs()

        val fingerprint = buildFingerprint(currentVersion)
        if (lastPreparedFingerprintFile.readTextOrNull() == fingerprint) {
            return
        }

        val backupDirectory = File(
            backupRoot,
            "preopen-${timestamp()}-v$currentVersion-to-v$targetVersion",
        )

        try {
            copyDatabaseFiles(backupDirectory)
            writeMetadata(
                directory = backupDirectory,
                reason = "preopen_version_change",
                sourceVersion = currentVersion,
            )
            lastPreparedFingerprintFile.writeText(fingerprint)
            pruneDirectories(prefix = "preopen-", keepCount = maxPreopenBackups)
        } catch (exception: IOException) {
            throw IllegalStateException(
                "Refusing to open $databaseName without a safety backup for version $currentVersion -> $targetVersion.",
                exception,
            )
        }
    }

    fun restoreLatestBackupIfDatabaseMissing(): Boolean {
        if (databaseFile.exists()) {
            return false
        }

        val latestBackup = backupRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("preopen-") }
            ?.maxByOrNull(File::lastModified)
            ?: return false

        return runCatching {
            restoreDatabaseFiles(latestBackup)
            true
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to restore the latest backup for $databaseName", throwable)
            false
        }
    }

    fun recordOpenFailure(
        throwable: Throwable,
        restoredBackup: Boolean,
    ) {
        backupRoot.mkdirs()

        runCatching {
            val failureDirectory = File(backupRoot, "failure-${timestamp()}")
            copyDatabaseFiles(failureDirectory)
            writeMetadata(
                directory = failureDirectory,
                reason = "open_failure",
                sourceVersion = readUserVersion(databaseFile),
                restoredBackup = restoredBackup,
                throwable = throwable,
            )
            pruneDirectories(prefix = "failure-", keepCount = maxFailureSnapshots)
        }.onFailure { snapshotError ->
            Log.e(TAG, "Failed to capture a failure snapshot for $databaseName", snapshotError)
        }
    }

    private fun restoreDatabaseFiles(sourceDirectory: File) {
        databaseFile.parentFile?.mkdirs()
        databaseFiles().forEach { sourceFile ->
            val backupFile = File(sourceDirectory, sourceFile.name)
            if (backupFile.exists()) {
                backupFile.copyTo(sourceFile, overwrite = true)
            } else if (sourceFile.exists()) {
                sourceFile.delete()
            }
        }
    }

    private fun copyDatabaseFiles(targetDirectory: File) {
        targetDirectory.mkdirs()
        databaseFiles()
            .filter(File::exists)
            .forEach { sourceFile ->
                sourceFile.copyTo(File(targetDirectory, sourceFile.name), overwrite = true)
            }
    }

    private fun databaseFiles(): List<File> =
        listOf(
            databaseFile,
            File(databaseFile.parentFile, "$databaseName-wal"),
            File(databaseFile.parentFile, "$databaseName-shm"),
        )

    private fun buildFingerprint(currentVersion: Int): String =
        buildString {
            append(currentVersion)
            append("->")
            append(targetVersion)
            append(':')
            append(databaseFile.length())
            append(':')
            append(databaseFile.lastModified())
        }

    private fun pruneDirectories(
        prefix: String,
        keepCount: Int,
    ) {
        backupRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(prefix) }
            ?.sortedByDescending(File::lastModified)
            ?.drop(keepCount)
            ?.forEach { directory ->
                directory.deleteRecursively()
            }
    }

    private fun readUserVersion(database: File): Int? =
        runCatching {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { sqliteDb ->
                sqliteDb.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else null
                }
            }
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to read user_version for ${database.path}", throwable)
            null
        }

    private fun writeMetadata(
        directory: File,
        reason: String,
        sourceVersion: Int?,
        restoredBackup: Boolean = false,
        throwable: Throwable? = null,
    ) {
        File(directory, "metadata.txt").writeText(
            buildString {
                appendLine("database_name=$databaseName")
                appendLine("reason=$reason")
                appendLine("created_at=${Date()}")
                appendLine("source_version=${sourceVersion ?: "unknown"}")
                appendLine("target_version=$targetVersion")
                appendLine("restored_backup=$restoredBackup")
                throwable?.let {
                    appendLine("exception_type=${it::class.qualifiedName}")
                    appendLine("exception_message=${it.message.orEmpty()}")
                }
            },
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun File.readTextOrNull(): String? =
        if (exists()) readText() else null

    private companion object {
        const val TAG = "LearningDbProtection"
    }
}
