package org.onion.agro.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["updated_at_millis"])]
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(defaultValue = "'default'") val mode: String = "default",
    @ColumnInfo(name = "system_instruction", defaultValue = "''")
    val systemInstruction: String = "",
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id", "created_at_millis"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,
    val content: String,
    @ColumnInfo(name = "tool_calls") val toolCallsJson: String,
    @ColumnInfo(name = "tool_responses") val toolResponsesJson: String,
    @ColumnInfo(name = "metadata") val metadataJson: String,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long
)

@Entity(
    tableName = "chat_message_contents",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["message_id", "position"], unique = true)
    ]
)
data class ChatMessageContentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    val position: Int,
    val type: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "payload_blob") val payloadBlob: ByteArray?
)

@Entity(
    tableName = "chat_tool_logs",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["message_id"])
    ]
)
data class ChatToolLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    val arguments: String,
    val response: String,
    val status: String,
    @ColumnInfo(name = "started_at_millis") val startedAtMillis: Long,
    @ColumnInfo(name = "completed_at_millis") val completedAtMillis: Long?
)
