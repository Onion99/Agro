package org.onion.agro.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updated_at_millis DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE title LIKE '%' || :query || '%'
            OR last_message_preview LIKE '%' || :query || '%'
        ORDER BY updated_at_millis DESC
        """
    )
    fun searchSessions(query: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at_millis DESC LIMIT 1")
    suspend fun getMostRecentSession(): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at_millis ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Query(
        """
        SELECT chat_message_contents.* FROM chat_message_contents
        INNER JOIN chat_messages ON chat_messages.id = chat_message_contents.message_id
        WHERE chat_messages.session_id = :sessionId
        ORDER BY chat_messages.created_at_millis ASC, chat_message_contents.position ASC
        """
    )
    suspend fun getMessageContents(sessionId: String): List<ChatMessageContentEntity>

    @Query("SELECT * FROM chat_tool_logs WHERE session_id = :sessionId ORDER BY started_at_millis ASC")
    suspend fun getToolLogs(sessionId: String): List<ChatToolLogEntity>

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertMessageContents(contents: List<ChatMessageContentEntity>)

    @Upsert
    suspend fun upsertToolLog(toolLog: ChatToolLogEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId AND session_id = :sessionId")
    suspend fun deleteMessage(sessionId: String, messageId: String)

    @Query("DELETE FROM chat_message_contents WHERE message_id = :messageId")
    suspend fun deleteMessageContents(messageId: String)

    @Query("UPDATE chat_sessions SET title = :title, updated_at_millis = :updatedAtMillis WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String, updatedAtMillis: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun countMessages(sessionId: String): Int

    @Query(
        """
        SELECT content FROM chat_messages
        WHERE session_id = :sessionId AND content != ''
        ORDER BY created_at_millis DESC LIMIT 1
        """
    )
    suspend fun getLastMessagePreview(sessionId: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM chat_messages WHERE id = :messageId AND session_id = :sessionId)")
    suspend fun messageExists(sessionId: String, messageId: String): Boolean

    @Query(
        """
        UPDATE chat_sessions
        SET mode = :mode, system_instruction = :systemInstruction, updated_at_millis = :updatedAtMillis
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionContext(
        sessionId: String,
        mode: String,
        systemInstruction: String,
        updatedAtMillis: Long
    )

    @androidx.room.Transaction
    suspend fun upsertMessageWithContents(
        message: ChatMessageEntity,
        contents: List<ChatMessageContentEntity>
    ) {
        upsertMessage(message)
        deleteMessageContents(message.id)
        if (contents.isNotEmpty()) {
            upsertMessageContents(contents)
        }
    }
}
