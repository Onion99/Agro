@file:OptIn(ExperimentalRichTextApi::class)

package org.onion.agro.ui.screen

import kotlin.OptIn
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.onion.model.ChatMessage
import com.onion.model.ChatMessageContent
import com.onion.model.ChatSessionMode
import com.onion.model.ConversationContextState
import com.onion.theme.state.ContentType
import com.onion.theme.style.MediumOutlinedTextField
import com.onion.theme.style.glassSurface
import com.onion.theme.style.watercolorGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.ai_image
import agro.composeapp.generated.resources.attachment
import agro.composeapp.generated.resources.chat_date_desktop
import agro.composeapp.generated.resources.chat_date_mobile
import agro.composeapp.generated.resources.chat_disclaimer
import agro.composeapp.generated.resources.chat_context_collapse
import agro.composeapp.generated.resources.chat_context_copy
import agro.composeapp.generated.resources.chat_context_current_instruction
import agro.composeapp.generated.resources.chat_context_default_description
import agro.composeapp.generated.resources.chat_context_default_title
import agro.composeapp.generated.resources.chat_context_expand
import agro.composeapp.generated.resources.chat_context_not_applied
import agro.composeapp.generated.resources.chat_context_svg_description
import agro.composeapp.generated.resources.chat_context_svg_title
import agro.composeapp.generated.resources.chat_input_hint_desktop
import agro.composeapp.generated.resources.chat_input_hint_mobile
import agro.composeapp.generated.resources.copy
import agro.composeapp.generated.resources.creating
import agro.composeapp.generated.resources.delete
import agro.composeapp.generated.resources.error_no_interrupt_api
import agro.composeapp.generated.resources.export
import agro.composeapp.generated.resources.history_empty
import agro.composeapp.generated.resources.history_exported
import agro.composeapp.generated.resources.feature_not_available
import agro.composeapp.generated.resources.history
import agro.composeapp.generated.resources.history_search_hint
import agro.composeapp.generated.resources.new_chat
import agro.composeapp.generated.resources.rename
import agro.composeapp.generated.resources.regenerate
import agro.composeapp.generated.resources.save_image
import agro.composeapp.generated.resources.save
import agro.composeapp.generated.resources.scroll_to_bottom
import agro.composeapp.generated.resources.send_message
import agro.composeapp.generated.resources.settings_back
import agro.composeapp.generated.resources.stop_generation
import agro.composeapp.generated.resources.svg_render_failed
import agro.composeapp.generated.resources.svg_save_failed
import agro.composeapp.generated.resources.svg_saved
import agro.composeapp.generated.resources.text_copied
import agro.composeapp.generated.resources.unsupported_message
import agro.composeapp.generated.resources.user_image
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.onion.agro.BuildConfig
import org.onion.agro.database.ChatSessionEntity
import org.onion.agro.utils.Animations
import org.onion.agro.viewmodel.ChatViewModel
import ui.theme.AppTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun ChatScreen(
    onSettingsClick: () -> Unit = {},
    onAdvancedSettingsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .safeDrawingPadding()
    ) {
        val chatViewModel = koinInject<ChatViewModel>()
        val chatMessages = chatViewModel.currentChatMessages
        val conversationContext by chatViewModel.conversationContext
        var text by remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val clipboardManager = LocalClipboardManager.current
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // Ambient Watercolor Background Effects
        Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
            // Top-Left Glow
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-100).dp, y = (-100).dp)
                    .size(500.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppTheme.colors.primaryContainer.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Bottom-Right Glow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 120.dp, y = 120.dp)
                    .size(600.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppTheme.colors.tertiaryContainer.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Center-Left Glow
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 150.dp, y = 0.dp)
                    .size(400.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AppTheme.colors.secondaryContainer.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            ConversationContextHeader(
                context = conversationContext,
                activeSessionId = chatViewModel.activeSessionId.value,
                onHistoryClick = { chatViewModel.setHistoryVisible(true) },
                onCopyInstruction = { instruction ->
                    clipboardManager.setText(AnnotatedString(instruction))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(getString(Res.string.text_copied))
                    }
                }
            )

            // Chat History Scrollable Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ChatMessagesList(
                    chatMessages = chatMessages,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Input Area
            InputArea(
                text = text,
                isGenerating = chatViewModel.isGenerating.value,
                onAttachClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(getString(Res.string.feature_not_available))
                    }
                },
                onSendClick = {
                    if (chatViewModel.isGenerating.value) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(getString(Res.string.error_no_interrupt_api))
                        }
                    } else {
                        if (text.isNotEmpty()) {
                            chatViewModel.sendMessage(text)
                            text = ""
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }
                },
                onNewChatClick = {
                    chatViewModel.startNewConversation()
                    text = ""
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                onTextChange = { text = it }
            )
        }

        AnimatedVisibility(
            visible = chatViewModel.isHistoryVisible.value,
            enter = Animations.slideFadeIn(),
            exit = Animations.slideFadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .zIndex(45f)
        ) {
            ChatHistoryPanel(
                sessions = chatViewModel.chatSessions,
                activeSessionId = chatViewModel.activeSessionId.value,
                searchQuery = chatViewModel.historySearchQuery.value,
                onSearchChange = chatViewModel::setHistorySearchQuery,
                onClose = { chatViewModel.setHistoryVisible(false) },
                onOpen = chatViewModel::openSession,
                onRename = chatViewModel::renameSession,
                onDelete = chatViewModel::deleteSession,
                onExport = { sessionId ->
                    coroutineScope.launch {
                        val exported = chatViewModel.exportSession(sessionId)
                        clipboardManager.setText(AnnotatedString(exported))
                        snackbarHostState.showSnackbar(getString(Res.string.history_exported))
                    }
                }
            )
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .zIndex(50f),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData,
                    modifier = Modifier
                        .widthIn(min = 100.dp, max = 300.dp)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .padding(8.dp),
                    shape = RoundedCornerShape(26.dp),
                    containerColor = AppTheme.colors.tertiaryContainer,
                    contentColor = AppTheme.colors.onTertiaryContainer
                )
            }
        )
    }
}

@Composable
private fun ConversationContextHeader(
    context: ConversationContextState,
    activeSessionId: String?,
    onHistoryClick: () -> Unit,
    onCopyInstruction: (String) -> Unit
) {
    val isSingle = AppTheme.contentType == ContentType.Single
    var expanded by remember(activeSessionId, context.systemInstruction) {
        mutableStateOf(false)
    }
    val isSvgMode = context.mode == ChatSessionMode.SVG_IMAGE
    val title = stringResource(
        if (isSvgMode) {
            Res.string.chat_context_svg_title
        } else {
            Res.string.chat_context_default_title
        },
        BuildConfig.APP_NAME
    )
    val description = stringResource(
        if (isSvgMode) {
            Res.string.chat_context_svg_description
        } else {
            Res.string.chat_context_default_description
        }
    )
    val statusColor = if (context.isApplied) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.tertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isSingle) {
                    AppTheme.spacing.containerPaddingMobile
                } else {
                    AppTheme.spacing.containerPaddingDesktop
                },
                end = if (isSingle) {
                    AppTheme.spacing.containerPaddingMobile
                } else {
                    AppTheme.spacing.containerPaddingDesktop
                },
                top = AppTheme.spacing.sm
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = AppTheme.size.maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.size.iconButton)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = AppTheme.shape.full,
                            alpha = AppTheme.elevation.glassSurfaceAlpha,
                            borderAlpha = AppTheme.elevation.glassBorderAlpha
                        )
                        .background(
                            color = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.42f),
                            shape = AppTheme.shape.full
                        )
                        .clickable { expanded = !expanded }
                        .padding(
                            horizontal = AppTheme.spacing.md,
                            vertical = AppTheme.spacing.sm
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSvgMode) {
                            Icons.Filled.Photo
                        } else {
                            Icons.Filled.AutoAwesome
                        },
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(AppTheme.size.icon)
                    )
                    Box(
                        modifier = Modifier
                            .size(AppTheme.spacing.sm)
                            .background(statusColor, AppTheme.shape.full)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = AppTheme.typography.labelMedium,
                            color = AppTheme.colors.onSurface,
                            maxLines = 1
                        )
                        if (!isSingle) {
                            Text(
                                text = if (context.isApplied) {
                                    description
                                } else {
                                    stringResource(Res.string.chat_context_not_applied)
                                },
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.colors.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                Res.string.chat_context_collapse
                            } else {
                                Res.string.chat_context_expand
                            }
                        ),
                        tint = AppTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(AppTheme.size.icon)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .glassSurface(
                        shape = AppTheme.shape.full,
                        alpha = AppTheme.elevation.glassSurfaceAlpha,
                        borderAlpha = AppTheme.elevation.glassBorderAlpha
                    )
            ) {
                IconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.size(AppTheme.size.iconButton)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = stringResource(Res.string.history),
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(AppTheme.size.icon)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = Animations.slideFadeIn(),
            exit = Animations.slideFadeOut()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = AppTheme.spacing.sm)
                    .fillMaxWidth(if (isSingle) 1f else 0.72f)
                    .widthIn(max = AppTheme.size.maxContentWidth)
                    .glassSurface(
                        shape = AppTheme.shape.xl,
                        alpha = AppTheme.elevation.glassSurfaceAlpha,
                        borderAlpha = AppTheme.elevation.glassBorderAlpha
                    )
                    .background(
                        color = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.72f),
                        shape = AppTheme.shape.xl
                    )
                    .padding(AppTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.chat_context_current_instruction),
                        style = AppTheme.typography.labelMedium,
                        color = AppTheme.colors.primary
                    )
                    IconButton(
                        onClick = {
                            onCopyInstruction(context.systemInstruction)
                        },
                        modifier = Modifier.size(AppTheme.size.iconButtonSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(
                                Res.string.chat_context_copy
                            ),
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(AppTheme.size.iconSmall)
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        text = context.systemInstruction,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = AppTheme.size.cardMedium)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryPanel(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit
) {
    var renamingSession by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    val isSingle = AppTheme.contentType == ContentType.Single

    if (renamingSession != null) {
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = {
                Text(
                    text = stringResource(Res.string.rename),
                    style = AppTheme.typography.headlineMedium,
                    color = AppTheme.colors.onSurface
                )
            },
            text = {
                MediumOutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppTheme.shape.lg,
                    style = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingSession?.let { onRename(it.id, renameText) }
                        renamingSession = null
                    }
                ) {
                    Text(text = stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) {
                    Text(text = stringResource(Res.string.settings_back))
                }
            },
            containerColor = AppTheme.colors.surface,
            shape = AppTheme.shape.xxl
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = if (isSingle) 0.dp else 360.dp, max = if (isSingle) 420.dp else 420.dp)
            .padding(AppTheme.spacing.md)
            .glassSurface(
                shape = AppTheme.shape.xxl,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .background(
                color = AppTheme.colors.surface.copy(alpha = 0.82f),
                shape = AppTheme.shape.xxl
            )
            .padding(AppTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.history),
                    style = AppTheme.typography.headlineMedium,
                    color = AppTheme.colors.primary
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = AppTheme.colors.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.24f), AppTheme.shape.full)
                .padding(horizontal = AppTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(AppTheme.size.icon)
            )
            MediumOutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = AppTheme.shape.full,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                style = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface)
            )
            if (searchQuery.isEmpty()) {
                Text(
                    text = stringResource(Res.string.history_search_hint),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.history_empty),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
            ) {
                items(sessions, key = { it.id }) { session ->
                    ChatHistoryRow(
                        session = session,
                        selected = session.id == activeSessionId,
                        onOpen = { onOpen(session.id) },
                        onRename = {
                            renamingSession = session
                            renameText = session.title
                        },
                        onDelete = { onDelete(session.id) },
                        onExport = { onExport(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryRow(
    session: ChatSessionEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shape.lg)
            .background(
                color = if (selected) AppTheme.colors.primaryContainer.copy(alpha = 0.24f)
                else AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.48f),
                shape = AppTheme.shape.lg
            )
            .border(
                width = AppTheme.size.borderWidthThin,
                color = if (selected) AppTheme.colors.primary.copy(alpha = 0.28f)
                else AppTheme.colors.outlineVariant.copy(alpha = 0.18f),
                shape = AppTheme.shape.lg
            )
            .clickable(onClick = onOpen)
            .padding(AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
    ) {
        Text(
            text = session.title,
            style = AppTheme.typography.labelMedium,
            color = if (selected) AppTheme.colors.primary else AppTheme.colors.onSurface,
            maxLines = 1
        )
        if (session.lastMessagePreview.isNotBlank()) {
            Text(
                text = session.lastMessagePreview,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onSurfaceVariant,
                maxLines = 2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${session.messageCount} · ${formatHistoryTime(session.updatedAtMillis)}",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.62f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                HistoryActionIcon(Icons.Filled.DriveFileRenameOutline, stringResource(Res.string.rename), onRename)
                HistoryActionIcon(Icons.Filled.FileDownload, stringResource(Res.string.export), onExport)
                HistoryActionIcon(Icons.Filled.Delete, stringResource(Res.string.delete), onDelete)
            }
        }
    }
}

@Composable
private fun HistoryActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(AppTheme.size.iconButtonSmall)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AppTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(AppTheme.size.iconSmall)
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun formatHistoryTime(updatedAtMillis: Long): String {
    val diff = (Clock.System.now().toEpochMilliseconds() - updatedAtMillis).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        diff < minute -> "now"
        diff < hour -> "${diff / minute}m"
        diff < day -> "${diff / hour}h"
        else -> "${diff / day}d"
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ChatMessagesList(
    chatMessages: List<ChatMessage>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val chatViewModel = koinInject<ChatViewModel>()
    val clipboardManager = LocalClipboardManager.current
    var stickToBottom by remember { mutableStateOf(true) }
    var autoScrollInProgress by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableStateOf(chatMessages.size) }

    val showScrollButton by remember {
        derivedStateOf {
            lazyListState.canScrollForward
        }
    }

    suspend fun scrollToBottom(animate: Boolean) {
        val lastListItemIndex = chatMessages.size
        if (lastListItemIndex <= 0) return

        autoScrollInProgress = true
        try {
            withFrameNanos { }
            if (animate) {
                lazyListState.animateScrollToItem(
                    index = lastListItemIndex,
                    scrollOffset = Int.MAX_VALUE
                )
            } else {
                lazyListState.scrollToItem(
                    index = lastListItemIndex,
                    scrollOffset = Int.MAX_VALUE
                )
            }
        } finally {
            autoScrollInProgress = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (AppTheme.contentType == ContentType.Single) stringResource(Res.string.chat_date_mobile) else stringResource(Res.string.chat_date_desktop),
                        style = AppTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(
                                color = AppTheme.colors.surfaceVariant.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = AppTheme.colors.outlineVariant.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            items(chatMessages, key = { it.id }) { message ->
                Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                    ChatBubble(
                        message = message,
                        onSaveImage = { imageData ->
                            coroutineScope.launch(Dispatchers.Default) {
                                val fileName = "diffusion_${Clock.System.now().toEpochMilliseconds()}.png"
                                // val success = chatViewModel.diffusionLoader.saveImage(imageData, fileName, message.metadata)
                            }
                        },
                        onSaveSvg = { svg ->
                            coroutineScope.launch {
                                runCatching {
                                    val file = FileKit.openFileSaver(
                                        suggestedName = "svg_${message.id}",
                                        extension = "svg"
                                    ) ?: return@launch
                                    file.write(svg.encodeToByteArray())
                                }.onSuccess {
                                    snackbarHostState.showSnackbar(
                                        getString(Res.string.svg_saved)
                                    )
                                }.onFailure {
                                    snackbarHostState.showSnackbar(
                                        getString(Res.string.svg_save_failed)
                                    )
                                }
                            }
                        },
                        onRegenerate = if (message.metadata?.containsKey("prompt") == true) {
                            {
                                if (chatViewModel.isGenerating.value) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(getString(Res.string.error_no_interrupt_api))
                                    }
                                } else chatViewModel.reGenerateMessage(message)
                            }
                        } else null,
                        onCopyText = { textToCopy ->
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(Res.string.text_copied))
                            }
                        }
                    )
                }
            }
        }

        ScrollToBottomButton(
            onClick = {
                coroutineScope.launch {
                    stickToBottom = true
                    scrollToBottom(animate = true)
                }
            },
            visibility = showScrollButton,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 32.dp, end = 24.dp)
        )
    }

    LaunchedEffect(lazyListState.isScrollInProgress, showScrollButton, autoScrollInProgress) {
        if (lazyListState.isScrollInProgress && !autoScrollInProgress) {
            stickToBottom = !showScrollButton
        } else if (!showScrollButton) {
            stickToBottom = true
        }
    }

    val lastMessageScrollKey by remember {
        derivedStateOf {
            chatMessages.lastOrNull()?.let { message ->
                "${message.id}:${message.contents.hashCode()}:${message.metadata?.get("is_generating")}"
            }
        }
    }

    LaunchedEffect(chatMessages.size, lastMessageScrollKey, stickToBottom) {
        val messageCountChanged = previousMessageCount != chatMessages.size
        if (messageCountChanged) {
            stickToBottom = true
        }

        if (chatMessages.isNotEmpty()) {
            val shouldAutoScroll = stickToBottom || messageCountChanged
            if (shouldAutoScroll) {
                scrollToBottom(animate = false)
            }
        }

        previousMessageCount = chatMessages.size
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visibility: Boolean
) {
    AnimatedVisibility(
        visible = visibility,
        enter = Animations.slideFadeIn(),
        exit = Animations.slideFadeOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(AppTheme.size.buttonHeight)
                .shadow(6.dp, AppTheme.shape.full)
                .clip(AppTheme.shape.full)
                .background(
                    color = AppTheme.colors.primaryContainer,
                    shape = AppTheme.shape.full
                )
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                contentDescription = stringResource(Res.string.scroll_to_bottom),
                tint = AppTheme.colors.onPrimaryContainer,
                modifier = Modifier.size(AppTheme.size.iconLarge)
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onSaveImage: ((ByteArray) -> Unit)? = null,
    onSaveSvg: ((String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onCopyText: ((String) -> Unit)? = null
) {
    val isSingle = AppTheme.contentType == ContentType.Single
    val isUser = message.isUser

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) (if (isSingle) 48.dp else 88.dp) else 0.dp,
                end = if (isUser) 0.dp else (if (isSingle) 48.dp else 88.dp),
                top = 8.dp,
                bottom = 8.dp
            ),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User Message Bubble
            if (isSingle) {
                // Mobile User Bubble
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 4.dp, bottomStart = 24.dp),
                            spotColor = AppTheme.colors.primary.copy(alpha = 0.3f)
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AppTheme.colors.primary.copy(alpha = 0.9f),
                                    AppTheme.colors.surfaceTint.copy(alpha = 0.9f)
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 4.dp, bottomStart = 24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    MessageContentList(
                        message = message,
                        onSaveImage = onSaveImage,
                        onSaveSvg = onSaveSvg,
                        onRegenerate = onRegenerate,
                        onCopyText = onCopyText,
                        isSingle = true
                    )
                }
            } else {
                // Desktop User Bubble
                Box(
                    modifier = Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
                            alpha = AppTheme.elevation.glassSurfaceAlpha,
                            borderAlpha = AppTheme.elevation.glassBorderAlpha
                        )
                        .background(
                            color = AppTheme.colors.surfaceContainerHigh.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = AppTheme.colors.outlineVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    MessageContentList(
                        message = message,
                        onSaveImage = onSaveImage,
                        onSaveSvg = onSaveSvg,
                        onRegenerate = onRegenerate,
                        onCopyText = onCopyText,
                        isSingle = false
                    )
                }
            }
        } else {
            // AI Message Bubble
            if (isSingle) {
                // Mobile AI Bubble
                Box(
                    modifier = Modifier
                        .glassSurface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 4.dp),
                            alpha = AppTheme.elevation.glassSurfaceAlpha,
                            borderAlpha = AppTheme.elevation.glassBorderAlpha
                        )
                        .background(
                            color = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = AppTheme.colors.surfaceContainerHigh,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 4.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    MessageContentList(
                        message = message,
                        onSaveImage = onSaveImage,
                        onSaveSvg = onSaveSvg,
                        onRegenerate = onRegenerate,
                        onCopyText = onCopyText,
                        isSingle = true
                    )
                }
            } else {
                // Desktop AI Bubble with Avatar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // AI Avatar
                    /*Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = AppTheme.colors.primary.copy(alpha = 0.2f)
                            )
                            .background(
                                color = AppTheme.colors.primaryContainer.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = AppTheme.colors.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI Avatar",
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }*/

                    // AI Bubble Container
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            /*.glassSurface(
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
                                alpha = AppTheme.elevation.glassSurfaceAlpha,
                                borderAlpha = AppTheme.elevation.glassBorderAlpha
                            )
                            .background(
                                color = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = AppTheme.colors.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
                            )*/
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp))
                    ) {
                        // Soft internal glow simulating watercolor bleeding
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            AppTheme.colors.primaryContainer.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .align(Alignment.TopCenter)
                        )

                        Box(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
                            MessageContentList(
                                message = message,
                                onSaveImage = onSaveImage,
                                onSaveSvg = onSaveSvg,
                                onRegenerate = onRegenerate,
                                onCopyText = onCopyText,
                                isSingle = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContentList(
    message: ChatMessage,
    onSaveImage: ((ByteArray) -> Unit)?,
    onSaveSvg: ((String) -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopyText: ((String) -> Unit)?,
    isSingle: Boolean
) {
    val isGenerating = message.metadata?.get("is_generating") == "true"
    val hasVisibleContent = message.contents.any { content ->
        content !is ChatMessageContent.Text || content.text.isNotEmpty()
    }
    val textColor = if (message.isUser && isSingle) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
    ) {
        if (!hasVisibleContent && isGenerating) {
            GeneratingMessageContent(isSingle)
        } else {
            message.contents.forEach { content ->
                when (content) {
                    is ChatMessageContent.Text -> {
                        if (content.text.isNotEmpty() || isGenerating) {
                            TextMessageContent(
                                text = content.text,
                                isGenerating = isGenerating,
                                isSingle = isSingle,
                                textColor = textColor,
                                copyTint = if (message.isUser && isSingle) {
                                    AppTheme.colors.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    AppTheme.colors.primary
                                },
                                onCopyText = onCopyText
                            )
                        }
                    }
                    is ChatMessageContent.RasterImage -> {
                        RasterImageMessageContent(
                            content = content,
                            onSaveImage = onSaveImage,
                            onRegenerate = onRegenerate
                        )
                    }
                    is ChatMessageContent.SvgImage -> {
                        SvgImageMessageContent(
                            content = content,
                            onSaveSvg = onSaveSvg,
                            onCopyText = onCopyText
                        )
                    }
                    is ChatMessageContent.Unsupported -> {
                        UnsupportedMessageContent(
                            content = content,
                            onCopyText = onCopyText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingMessageContent(isSingle: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.sm),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(AppTheme.size.icon),
            color = AppTheme.colors.primary,
            strokeWidth = AppTheme.size.borderWidth
        )
        Text(
            text = stringResource(Res.string.creating) + "...",
            style = if (isSingle) {
                AppTheme.typography.bodyMedium
            } else {
                AppTheme.typography.bodyLarge
            },
            color = AppTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = AppTheme.spacing.sm)
        )
    }
}

@Composable
private fun TextMessageContent(
    text: String,
    isGenerating: Boolean,
    isSingle: Boolean,
    textColor: Color,
    copyTint: Color,
    onCopyText: ((String) -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        val displayText = if (isGenerating) "$text ▌" else text
        val richTextState = rememberRichTextState()
        val primaryColor = AppTheme.colors.primary
        val codeBackground = AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
        LaunchedEffect(primaryColor, codeBackground) {
            richTextState.config.linkColor = primaryColor
            richTextState.config.codeSpanColor = primaryColor
            richTextState.config.codeSpanBackgroundColor = codeBackground
        }
        LaunchedEffect(displayText) {
            richTextState.setMarkdown(displayText)
        }
        RichText(
            state = richTextState,
            style = if (isSingle) {
                AppTheme.typography.bodyMedium
            } else {
                AppTheme.typography.bodyLarge
            },
            color = textColor.copy(alpha = 0.9f),
            modifier = Modifier
                .padding(top = AppTheme.spacing.xs, end = AppTheme.spacing.sm)
                .weight(1f)
        )
        if (onCopyText != null && !isGenerating && text.isNotEmpty()) {
            IconButton(
                onClick = { onCopyText(text) },
                modifier = Modifier.size(AppTheme.size.iconButtonSmall)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(Res.string.copy),
                    tint = copyTint,
                    modifier = Modifier.size(AppTheme.size.iconSmall)
                )
            }
        }
    }
}

@Composable
private fun RasterImageMessageContent(
    content: ChatMessageContent.RasterImage,
    onSaveImage: ((ByteArray) -> Unit)?,
    onRegenerate: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val width = content.width
        val height = content.height
        val ratio = if (
            width != null &&
            height != null &&
            height > 0
        ) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }
        AsyncImage(
            model = content.bytes,
            contentDescription = stringResource(Res.string.ai_image),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .let { modifier ->
                    if (ratio != null) {
                        modifier.aspectRatio(ratio)
                    } else {
                        modifier.wrapContentHeight()
                    }
                }
                .clip(AppTheme.shape.md)
        )
        ImageContentActions(
            onRegenerate = onRegenerate,
            onCopy = null,
            onSave = onSaveImage?.let { save -> { save(content.bytes) } },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun SvgImageMessageContent(
    content: ChatMessageContent.SvgImage,
    onSaveSvg: ((String) -> Unit)?,
    onCopyText: ((String) -> Unit)?
) {
    val svgBytes = remember(content.svg) { content.svg.encodeToByteArray() }
    var renderFailed by remember(content.svg) { mutableStateOf(false) }
    val ratio = (content.width / content.height).takeIf {
        it.isFinite() && it > 0f
    } ?: 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(AppTheme.shape.md)
            .background(AppTheme.colors.surfaceContainerLow.copy(alpha = 0.46f))
    ) {
        AsyncImage(
            model = svgBytes,
            contentDescription = stringResource(Res.string.ai_image),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit,
            onSuccess = { renderFailed = false },
            onError = { renderFailed = true },
            modifier = Modifier.fillMaxSize()
        )
        if (renderFailed) {
            Text(
                text = stringResource(Res.string.svg_render_failed),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(AppTheme.spacing.md)
            )
        }
        ImageContentActions(
            onRegenerate = null,
            onCopy = onCopyText?.let { copy -> { copy(content.svg) } },
            onSave = onSaveSvg?.let { save -> { save(content.svg) } },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun UnsupportedMessageContent(
    content: ChatMessageContent.Unsupported,
    onCopyText: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = AppTheme.colors.errorContainer.copy(alpha = 0.35f),
                shape = AppTheme.shape.md
            )
            .border(
                width = AppTheme.size.borderWidthThin,
                color = AppTheme.colors.error.copy(alpha = 0.35f),
                shape = AppTheme.shape.md
            )
            .padding(AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.unsupported_message),
                style = AppTheme.typography.labelMedium,
                color = AppTheme.colors.error
            )
            if (onCopyText != null) {
                IconButton(
                    onClick = { onCopyText(content.rawPayload) },
                    modifier = Modifier.size(AppTheme.size.iconButtonSmall)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(Res.string.copy),
                        tint = AppTheme.colors.error,
                        modifier = Modifier.size(AppTheme.size.iconSmall)
                    )
                }
            }
        }
        SelectionContainer {
            Text(
                text = content.rawPayload,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.onErrorContainer,
                modifier = Modifier.heightIn(max = AppTheme.size.cardMedium)
            )
        }
    }
}

@Composable
private fun ImageContentActions(
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onSave: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onRegenerate == null && onCopy == null && onSave == null) return
    Row(
        modifier = modifier
            .padding(AppTheme.spacing.sm)
            .background(
                color = AppTheme.colors.surface.copy(alpha = 0.78f),
                shape = AppTheme.shape.full
            )
            .padding(AppTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onRegenerate != null) {
            IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(AppTheme.size.iconButtonSmall)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.regenerate),
                    tint = AppTheme.colors.onSurface,
                    modifier = Modifier.size(AppTheme.size.icon)
                )
            }
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(AppTheme.size.iconButtonSmall)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(Res.string.copy),
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(AppTheme.size.icon)
                )
            }
        }
        if (onSave != null) {
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(AppTheme.size.iconButtonSmall)
            ) {
                Icon(
                    imageVector = Icons.Filled.SaveAlt,
                    contentDescription = stringResource(Res.string.save_image),
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(AppTheme.size.icon)
                )
            }
        }
    }
}

@Composable
fun InputArea(
    text: String,
    isGenerating: Boolean,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val isSingle = AppTheme.contentType == ContentType.Single

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        AppTheme.colors.background.copy(alpha = 0.8f),
                        AppTheme.colors.background
                    )
                )
            )
            .padding(
                start = if (isSingle) AppTheme.spacing.containerPaddingMobile else 48.dp,
                end = if (isSingle) AppTheme.spacing.containerPaddingMobile else 48.dp,
                top = 24.dp,
                bottom = if (isSingle) 16.dp else 32.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
        ) {
            if (isSingle) {
                // Mobile Input Area
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NewConversationAction(onClick = onNewChatClick)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(32.dp),
                                spotColor = AppTheme.colors.primary.copy(alpha = 0.15f)
                            )
                            .glassSurface(
                                shape = RoundedCornerShape(32.dp),
                                alpha = AppTheme.elevation.glassSurfaceAlpha,
                                borderAlpha = AppTheme.elevation.glassBorderAlpha
                            )
                            .background(
                                color = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(32.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = AppTheme.colors.surfaceContainerHigh,
                                shape = RoundedCornerShape(32.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onAttachClick,
                            modifier = Modifier.size(40.dp).align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = stringResource(Res.string.attachment),
                                tint = AppTheme.colors.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = 120.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.chat_input_hint_mobile),
                                    style = AppTheme.typography.bodyMedium,
                                    color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            MediumOutlinedTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                style = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface)
                            )
                        }

                        IconButton(
                            onClick = onSendClick,
                            modifier = Modifier
                                .background(
                                    color = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ).align(Alignment.CenterVertically).size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                                contentDescription = if (isGenerating) stringResource(Res.string.stop_generation) else stringResource(
                                    Res.string.send_message
                                ),
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // Desktop Input Area
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NewConversationAction(
                        onClick = onNewChatClick,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomEnd = 0.dp,
                                    bottomStart = 0.dp
                                ),
                                alpha = AppTheme.elevation.glassSurfaceAlpha,
                                borderAlpha = AppTheme.elevation.glassBorderAlpha
                            )
                            .background(
                                color = AppTheme.colors.surfaceContainerLowest.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomEnd = 0.dp,
                                    bottomStart = 0.dp
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = AppTheme.colors.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomEnd = 0.dp,
                                    bottomStart = 0.dp
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        //horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onAttachClick,
                            modifier = Modifier.size(40.dp).align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = stringResource(Res.string.attachment),
                                tint = AppTheme.colors.outline,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 30.dp, max = 150.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.chat_input_hint_desktop),
                                    style = AppTheme.typography.bodyMedium,
                                    color = AppTheme.colors.outline.copy(alpha = 0.5f)
                                )
                            }
                            MediumOutlinedTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                style = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface)
                            )
                        }

                        IconButton(
                            onClick = onSendClick,
                            modifier = Modifier
                                .background(
                                    color = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ).align(Alignment.CenterVertically).size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                                contentDescription = if (isGenerating) stringResource(Res.string.stop_generation) else stringResource(
                                    Res.string.send_message
                                ),
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.chat_disclaimer, BuildConfig.APP_NAME),
                        style = AppTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = AppTheme.colors.outline.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}


@Composable
private fun NewConversationAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(AppTheme.shape.full)
            .glassSurface(
                shape = AppTheme.shape.full,
                alpha = AppTheme.elevation.glassSurfaceAlpha,
                borderAlpha = AppTheme.elevation.glassBorderAlpha
            )
            .background(
                color = AppTheme.colors.surfaceContainerLow.copy(alpha = 0.6f),
                shape = AppTheme.shape.full
            )
            .border(
                width = 1.dp,
                color = AppTheme.colors.outlineVariant.copy(alpha = 0.2f),
                shape = AppTheme.shape.full
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = stringResource(Res.string.new_chat),
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(Res.string.new_chat),
            style = AppTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.2.sp),
            color = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
