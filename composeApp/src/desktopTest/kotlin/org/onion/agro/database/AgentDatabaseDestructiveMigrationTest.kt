package org.onion.agro.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentDatabaseDestructiveMigrationTest {
    @Test
    fun versionTwoDatabaseIsRecreatedWithoutLegacyTables() = runTest {
        val databasePath = Files.createTempFile("agent-database-v2-", ".db")
        try {
            BundledSQLiteDriver().open(databasePath.toString()).use { connection ->
                connection.execSQL(
                    "CREATE TABLE legacy_marker (id INTEGER NOT NULL PRIMARY KEY)"
                )
                connection.execSQL("PRAGMA user_version = 2")
            }

            val database = createAgentDatabase(
                Room.databaseBuilder<AgentDatabase>(databasePath.toString())
            )
            try {
                database.chatHistoryDao().getMostRecentSession()
            } finally {
                database.close()
            }

            BundledSQLiteDriver().open(databasePath.toString()).use { connection ->
                assertFalse(connection.tableExists("legacy_marker"))
                assertTrue(connection.tableExists("chat_sessions"))
            }
        } finally {
            deleteDatabaseFiles(databasePath)
        }
    }

    private fun SQLiteConnection.tableExists(tableName: String): Boolean {
        val statement = prepare(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?"
        )
        try {
            statement.bindText(1, tableName)
            check(statement.step())
            return statement.getLong(0) > 0
        } finally {
            statement.close()
        }
    }

    private fun deleteDatabaseFiles(databasePath: Path) {
        Files.deleteIfExists(databasePath)
        Files.deleteIfExists(Path.of("$databasePath-wal"))
        Files.deleteIfExists(Path.of("$databasePath-shm"))
    }
}
