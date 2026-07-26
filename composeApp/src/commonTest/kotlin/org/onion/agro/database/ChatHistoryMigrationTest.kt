package org.onion.agro.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatHistoryMigrationTest {

    @Test
    fun migratesPlainTextAndLegacySvgMessages() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createVersionOneSchema(connection)
            connection.execSQL(
                """
                INSERT INTO chat_sessions (
                    id, title, created_at_millis, updated_at_millis, message_count,
                    last_message_preview
                ) VALUES
                    ('plain_session', 'Plain', 1, 1, 1, 'not-json'),
                    ('svg_session', 'SVG', 1, 1, 1, 'legacy svg')
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO chat_messages (
                    id, session_id, role, content, tool_calls, tool_responses,
                    metadata, created_at_millis
                ) VALUES
                    ('plain_message', 'plain_session', 'assistant', 'not-json',
                        '[]', '[]', '{}', 1),
                    ('svg_message', 'svg_session', 'assistant',
                        '{"type":"svg_image","svg":"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"></svg>"}',
                        '[]', '[]', '{}', 1)
                """.trimIndent()
            )

            MIGRATION_1_2.migrate(connection)

            assertEquals(
                "text",
                queryText(
                    connection,
                    "SELECT type FROM chat_message_contents WHERE message_id = 'plain_message'"
                )
            )
            assertEquals(
                """{"text":"not-json"}""",
                queryText(
                    connection,
                    "SELECT payload_json FROM chat_message_contents WHERE message_id = 'plain_message'"
                )
            )
            assertEquals(
                "svg_image",
                queryText(
                    connection,
                    "SELECT type FROM chat_message_contents WHERE message_id = 'svg_message'"
                )
            )
            assertEquals(
                "svg_image",
                queryText(
                    connection,
                    "SELECT mode FROM chat_sessions WHERE id = 'svg_session'"
                )
            )
            assertEquals(
                "",
                queryText(
                    connection,
                    "SELECT content FROM chat_messages WHERE id = 'svg_message'"
                )
            )
        } finally {
            connection.close()
        }
    }

    private fun createVersionOneSchema(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE chat_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                created_at_millis INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL,
                message_count INTEGER NOT NULL,
                last_message_preview TEXT NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                tool_calls TEXT NOT NULL,
                tool_responses TEXT NOT NULL,
                metadata TEXT NOT NULL,
                created_at_millis INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun queryText(
        connection: SQLiteConnection,
        query: String
    ): String {
        val statement = connection.prepare(query)
        try {
            check(statement.step())
            return statement.getText(0)
        } finally {
            statement.close()
        }
    }
}
