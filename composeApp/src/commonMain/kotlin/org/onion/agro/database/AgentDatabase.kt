package org.onion.agro.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChatMessageContentEntity::class,
        ChatToolLogEntity::class
    ],
    version = 2,
    exportSchema = true
)
@ConstructedBy(AgentDatabaseConstructor::class)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}

@Suppress("KotlinNoActualForExpect")
expect object AgentDatabaseConstructor : RoomDatabaseConstructor<AgentDatabase> {
    override fun initialize(): AgentDatabase
}

fun createAgentDatabase(builder: RoomDatabase.Builder<AgentDatabase>): AgentDatabase {
    return builder
        .addMigrations(MIGRATION_1_2)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}

expect fun createAgentDatabaseBuilder(): RoomDatabase.Builder<AgentDatabase>

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE chat_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT 'default'"
        )
        connection.execSQL(
            "ALTER TABLE chat_sessions ADD COLUMN system_instruction TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_message_contents (
                id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                type TEXT NOT NULL,
                schema_version INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                payload_blob BLOB,
                PRIMARY KEY(id),
                FOREIGN KEY(message_id) REFERENCES chat_messages(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_message_contents_message_id " +
                "ON chat_message_contents(message_id)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_chat_message_contents_message_id_position " +
                "ON chat_message_contents(message_id, position)"
        )
        connection.execSQL(
            """
            INSERT INTO chat_message_contents (
                id,
                message_id,
                position,
                type,
                schema_version,
                payload_json,
                payload_blob
            )
            SELECT
                id || ':content:0',
                id,
                0,
                CASE
                    WHEN role = 'assistant' AND json_valid(content) THEN
                        CASE
                            WHEN json_extract(content, '$.type') = 'svg_image'
                                AND typeof(json_extract(content, '$.svg')) = 'text'
                                AND length(json_extract(content, '$.svg')) > 0
                            THEN 'svg_image'
                            ELSE 'text'
                        END
                    ELSE 'text'
                END,
                1,
                CASE
                    WHEN role = 'assistant' AND json_valid(content) THEN
                        CASE
                            WHEN json_extract(content, '$.type') = 'svg_image'
                                AND typeof(json_extract(content, '$.svg')) = 'text'
                                AND length(json_extract(content, '$.svg')) > 0
                            THEN content
                            ELSE json_object('text', content)
                        END
                    ELSE json_object('text', content)
                END,
                NULL
            FROM chat_messages
            """.trimIndent()
        )
        connection.execSQL(
            """
            UPDATE chat_sessions
            SET mode = 'svg_image'
            WHERE EXISTS (
                SELECT 1
                FROM chat_messages
                INNER JOIN chat_message_contents
                    ON chat_message_contents.message_id = chat_messages.id
                WHERE chat_messages.session_id = chat_sessions.id
                    AND chat_message_contents.type = 'svg_image'
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            UPDATE chat_messages
            SET content = ''
            WHERE id IN (
                SELECT message_id
                FROM chat_message_contents
                WHERE type = 'svg_image'
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            UPDATE chat_sessions
            SET last_message_preview = COALESCE(
                (
                    SELECT content
                    FROM chat_messages
                    WHERE chat_messages.session_id = chat_sessions.id
                        AND content != ''
                    ORDER BY created_at_millis DESC
                    LIMIT 1
                ),
                ''
            )
            WHERE mode = 'svg_image'
            """.trimIndent()
        )
    }
}
