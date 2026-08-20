package org.onion.agro.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onion.model.ChatMessage
import com.onion.model.ChatMessageContent
import com.onion.model.ChatRole
import com.onion.model.ChatSessionMode
import com.onion.model.ConversationContextState
import com.onion.model.LoraConfig
import com.onion.model.PersistentToolCall
import com.onion.model.PersistentToolResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.ai.edge.litertlm.LiteRtLmJni
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.math.roundToInt
import org.onion.agro.getPlatform
import org.onion.agro.native.llm.AgentLoopConfig
import org.onion.agro.native.llm.AgentLoopEvent
import org.onion.agro.native.llm.AgentLoopRunner
import org.onion.agro.native.llm.AgentTools
import org.onion.agro.native.llm.LiteRtLmModelMetadata
import org.onion.agro.native.llm.LiteRtLmInferenceException
import org.onion.agro.native.llm.LmConversation
import org.onion.agro.native.llm.LmEngine
import org.onion.agro.native.llm.ContextBudgetLevel
import org.onion.agro.native.llm.ContextBudgetPolicy
import org.onion.agro.native.llm.ContextBudgetSnapshot
import org.onion.agro.native.llm.ContextCoordinator
import org.onion.agro.native.llm.ContextStrategy
import org.onion.agro.native.llm.contextStrategy
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.*
import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.getString
import org.onion.agro.BuildConfig
import org.onion.agro.database.ChatHistoryRepository
import org.onion.agro.database.ChatSessionEntity
import org.onion.agro.database.ChatToolLogEntity
import org.onion.agro.native.llm.KEY_THINK_MODE
import org.onion.agro.message.SvgMessageParser
import org.onion.agro.message.ChiptuneBgmMessageParser
import org.onion.agro.message.LottieMessageParser

class ChatViewModel(
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {

    /** Format milliseconds into human-readable duration: "0.85s" / "12.3s" / "2m 15s" */
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000.0
        return when {
            totalSeconds < 1.0 -> {
                val hundredths = (totalSeconds * 100).roundToInt()
                "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}s"
            }
            totalSeconds < 60.0 -> {
                val tenths = (totalSeconds * 10).roundToInt()
                "${tenths / 10}.${tenths % 10}s"
            }
            else -> {
                val minutes = (totalSeconds / 60).toInt()
                val seconds = (totalSeconds % 60).toInt()
                "${minutes}m ${seconds}s"
            }
        }
    }


    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    var diffusionModelPath = mutableStateOf("")
    var vaePath = mutableStateOf("")
    var llmPath = mutableStateOf("")
    var clipLPath = mutableStateOf("")
    var clipGPath = mutableStateOf("")
    var t5xxlPath = mutableStateOf("")
    private var isInitializing = false
    private var activeModelPath: String? = null
    // 0 default,1 loading,2 loading completely
    var loadingModelState = MutableStateFlow(0)
    var isDiffusionModelLoading = mutableStateOf(false)
    var isVaeModelLoading = mutableStateOf(false)
    var isLlmModelLoading = mutableStateOf(false)
    var isClipLModelLoading = mutableStateOf(false)
    var isClipGModelLoading = mutableStateOf(false)
    var isT5xxlModelLoading = mutableStateOf(false)
    
    // ========================================================================================
    //                              Image Generation Settings
    // ========================================================================================
    /** Image width - options: 128, 256, 512, 768, 1024 */
    var imageWidth = mutableStateOf(512)
    
    /** Image height - options: 128, 256, 512, 768, 1024 */
    var imageHeight = mutableStateOf(512)
    
    /** Batch count - number of images to generate */
    var batchCount = mutableStateOf(1)
    
    /** Steps for generation - range: 1-50 */
    var generationSteps = mutableStateOf(5)
    
    /** CFG Scale - range: 1.0-15.0 */
    var cfgScale = mutableStateOf(2f)

    /** Flash Attention - optimize memory usage */
    var diffusionFlashAttn = mutableStateOf(false)

    /** Quantization Type - -1: Auto/Default, 0: F32, 1: F16, 2: Q4_0, etc. */
    var wtype = mutableStateOf(-1)

    /** Offload to CPU - offload model computations to CPU */
    var offloadToCpu = mutableStateOf(getPlatform().isIOS)

    /** Keep CLIP on CPU - keep CLIP model on CPU (enabled by default on macOS and iOS) */
    var keepClipOnCpu = mutableStateOf(getPlatform().isMacOS || getPlatform().isIOS)

    /** Keep VAE on CPU - keep VAE decoder on CPU */
    var keepVaeOnCpu = mutableStateOf(false)
    
    /** Enable MMAP - memory map the model weights */
    var enableMmap = mutableStateOf(false)


    /** Direct Convolution - optimize convolution in diffusion model */
    var diffusionConvDirect = mutableStateOf(false)

    /** Sampling Method - default is -1 (Auto/Euler) */
    var sampleMethod = mutableStateOf(-1)

    // ========================================================================================
    //                              Video Generation Settings
    // ========================================================================================
    /** Video frames - number of frames to generate */
    var videoFrames = mutableStateOf(33)

    /** Flow Shift - controls temporal flow for video generation models (e.g. Wan2.1) */
    var flowShift = mutableStateOf(3.0f)

    // ========================================================================================
    //                              LLM Settings (Gemma 4 LiteRT)
    // ========================================================================================
    var lmBackend = mutableStateOf("GPU")//NPU,CPU,GPU
    var lmVisionBackend = mutableStateOf("")
    var lmAudioBackend = mutableStateOf("")
    var lmMaxNumTokens = mutableStateOf(DEFAULT_LM_MAX_NUM_TOKENS)
    private val _lmModelMaxNumTokens = mutableStateOf<Int?>(null)
    val lmModelMaxNumTokens: State<Int?> = _lmModelMaxNumTokens
    private var lmMetadataModelPath: String? = null
    var lmMaxNumImages = mutableStateOf(-1)
    var lmMainBackendNumThreads = mutableStateOf(2)
    var lmAudioBackendNumThreads = mutableStateOf(-1)

    // Model Parameter Adjustments
    var temperature = mutableStateOf(0.7f)
    var topP = mutableStateOf(0.9f)
    var topK = mutableStateOf(40)
    var enableThinking = mutableStateOf(false)
    var enableSpeculativeDecoding = mutableStateOf(false)
    var systemPrompt = mutableStateOf("You are  ${BuildConfig.APP_NAME}, an analytical and precise local intelligence. Prioritize factual accuracy and concise formatting. Maintain a calm, neutral tone.")
    var systemContextShift = mutableStateOf(false)
    private val _conversationContext = mutableStateOf(
        ConversationContextState(
            mode = ChatSessionMode.DEFAULT,
            systemInstruction = systemPrompt.value,
            isApplied = false
        )
    )
    val conversationContext: State<ConversationContextState> = _conversationContext



    fun resetSettings() {
        viewModelScope.launch {
            temperature.value = 0.7f
            topP.value = 0.9f
            topK.value = 40
            enableThinking.value = false
            enableSpeculativeDecoding.value = false
            lmMaxNumTokens.value =
                _lmModelMaxNumTokens.value ?: DEFAULT_LM_MAX_NUM_TOKENS
            systemContextShift.value = true
            try {
                systemPrompt.value = getString(Res.string.llm_setting_system_prompt_default,
                    BuildConfig.APP_NAME)
            } catch (e: Exception) {
                systemPrompt.value = "You are ${BuildConfig.APP_NAME}, an analytical and precise local intelligence. Prioritize factual accuracy and concise formatting. Maintain a calm, neutral tone."
            }
        }
    }

    private val contextCoordinator = ContextCoordinator()
    private var activeBackend: String? = null
    private var activeEnableSpeculativeDecoding: Boolean? = null
    private var activeMaxNumTokens: Int? = null
    private val agentTools = AgentTools()

    // ========================================================================================
    //                              LoRA Settings
    // ========================================================================================
    val loraList = mutableStateListOf<LoraConfig>()

    fun addLora(path: String) {
        // Prevent duplicates
        if (loraList.any { it.path == path }) return
        
        // Extract filename for name
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        loraList.add(LoraConfig(path = path, name = name))
    }

    fun removeLora(lora: LoraConfig) {
        loraList.remove(lora)
    }


    suspend fun selectLoraFile(): String {
        return LiteRtLmJni.getModelFilePath()
    }

    suspend fun selectDiffusionModelFile(): String{
        isDiffusionModelLoading.value = true
        val diffusionModelPath = LiteRtLmJni.getModelFilePath()
        this.diffusionModelPath.value = diffusionModelPath
        isDiffusionModelLoading.value = false
        return diffusionModelPath
    }

    suspend fun selectVaeFile(): String{
        isVaeModelLoading.value = true
        val path = LiteRtLmJni.getModelFilePath()
        vaePath.value = path
        isVaeModelLoading.value = false
        return path
    }

    suspend fun selectLlmFile(): String{
        isLlmModelLoading.value = true
        val path = LiteRtLmJni.getModelFilePath()
        if (path != llmPath.value) {
            lmMetadataModelPath = null
            _lmModelMaxNumTokens.value = null
            lmMaxNumTokens.value = DEFAULT_LM_MAX_NUM_TOKENS
        }
        llmPath.value = path
        isLlmModelLoading.value = false
        return path
    }

    fun adjustLmMaxNumTokens(increase: Boolean) {
        val delta = if (increase) LM_MAX_NUM_TOKENS_STEP else -LM_MAX_NUM_TOKENS_STEP
        val upperBound = _lmModelMaxNumTokens.value ?: DEFAULT_LM_MAX_NUM_TOKENS
        val lowerBound = MIN_LM_MAX_NUM_TOKENS.coerceAtMost(upperBound)
        lmMaxNumTokens.value = (lmMaxNumTokens.value + delta).coerceIn(
            minimumValue = lowerBound,
            maximumValue = upperBound
        )
    }

    private fun resolveLmMaxNumTokens(modelPath: String) {
        if (lmMetadataModelPath == modelPath) return

        val modelMaxNumTokens = runCatching {
            LiteRtLmModelMetadata.getLmMaxNumTokens(modelPath)
        }.onFailure { error ->
            println(
                "Unable to read lmMaxNumTokens from LiteRT-LM model " +
                    "'$modelPath': ${error.message}"
            )
        }.getOrNull()
        println(
            "lmMaxNumTokens from LiteRT-LM model : $modelMaxNumTokens"
        )
        _lmModelMaxNumTokens.value = modelMaxNumTokens
        lmMaxNumTokens.value = modelMaxNumTokens ?: DEFAULT_LM_MAX_NUM_TOKENS
        lmMetadataModelPath = modelPath
    }

    suspend fun selectClipLFile(): String{
        isClipLModelLoading.value = true
        val path = LiteRtLmJni.getModelFilePath()
        clipLPath.value = path
        isClipLModelLoading.value = false
        return path
    }

    suspend fun selectClipGFile(): String{
        isClipGModelLoading.value = true
        val path = LiteRtLmJni.getModelFilePath()
        clipGPath.value = path
        isClipGModelLoading.value = false
        return path
    }

    suspend fun selectT5xxlFile(): String{
        isT5xxlModelLoading.value = true
        val path = LiteRtLmJni.getModelFilePath()
        t5xxlPath.value = path
        isT5xxlModelLoading.value = false
        return path
    }

    fun initLLM() {
        if (isInitializing) return
        if (
            contextCoordinator.isEngineReady() &&
            llmPath.value == activeModelPath &&
            isSameLmBackend(activeBackend, lmBackend.value) &&
            activeEnableSpeculativeDecoding == enableSpeculativeDecoding.value &&
            activeMaxNumTokens == lmMaxNumTokens.value
        ) {
            return
        }
        isInitializing = true
        viewModelScope.launch(Dispatchers.Default) {
            loadingModelState.emit(1)
            try {
                if (isGenerating.value) {
                    stopGeneration()
                }
                isLlmModelLoading.value = true
                val currentLlmPath = llmPath.value
                contextCoordinator.closeAll()
                val engine = createLmEngine(currentLlmPath, lmBackend.value)
                engine.initialize()
                contextCoordinator.attachEngine(engine)
                updateActiveLmEngineState(currentLlmPath, lmBackend.value)
                recreateLmConversation(forceRecreate = true)
                markConversationContextApplied(contextCoordinator.currentConversation() != null)
                persistAppliedConversationContext()
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeAll()
                clearActiveLmEngineState()
                markConversationContextApplied(false)
            } finally {
                isInitializing = false
                isLlmModelLoading.value = false
            }
            loadingModelState.emit(2)
        }
    }

    fun applyConversationSettings() {
        val currentLlmPath = llmPath.value
        if (currentLlmPath.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            isLlmModelLoading.value = true
            loadingModelState.emit(1)
            try {
                if (isGenerating.value) {
                    stopGeneration()
                }

                val needsEngineReinit = !contextCoordinator.isEngineReady() ||
                        activeModelPath != currentLlmPath ||
                        !isSameLmBackend(activeBackend, lmBackend.value) ||
                        activeEnableSpeculativeDecoding != enableSpeculativeDecoding.value ||
                        activeMaxNumTokens != lmMaxNumTokens.value

                if (needsEngineReinit) {
                    contextCoordinator.closeAll()
                    clearActiveLmEngineState()
                    val engine = createLmEngine(currentLlmPath, lmBackend.value)
                    engine.initialize()
                    contextCoordinator.attachEngine(engine)
                    updateActiveLmEngineState(currentLlmPath, lmBackend.value)
                }
                val instruction = instructionForMode(_conversationContext.value.mode)
                selectConversationContext(
                    mode = _conversationContext.value.mode,
                    systemInstruction = instruction
                )
                recreateLmConversation(forceRecreate = true)
                markConversationContextApplied(contextCoordinator.currentConversation() != null)
                persistAppliedConversationContext()
                _currentChatMessages.clear()
                val text = getString(Res.string.chat_system_parameters_applied)
                val sessionId = ensureActiveSession(text)
                chatHistoryRepository.clearSessionMessages(sessionId)
                val message = ChatMessage.text(text, role = ChatRole.SYSTEM)
                _currentChatMessages.add(message)
                chatHistoryRepository.saveMessage(sessionId, message)
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeAll()
                clearActiveLmEngineState()
                markConversationContextApplied(false)
                _currentChatMessages.clear()
                val text = getString(Res.string.chat_system_parameters_apply_failed, e.message ?: "")
                val sessionId = ensureActiveSession(text)
                chatHistoryRepository.clearSessionMessages(sessionId)
                val message = ChatMessage.text(text, role = ChatRole.SYSTEM)
                _currentChatMessages.add(message)
                chatHistoryRepository.saveMessage(sessionId, message)
            } finally {
                isLlmModelLoading.value = false
                loadingModelState.emit(2)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contextCoordinator.closeAll()
    }

    private var responseGenerationJob: Job? = null
    private var isInferenceOn: Boolean = false
    private val defaultNegative = ""
    // ========================================================================================
    //                              Chat Message State
    // ========================================================================================

    /** Current active chat conversation messages */
    private val _currentChatMessages = mutableStateListOf<ChatMessage>()
    val currentChatMessages: SnapshotStateList<ChatMessage> = _currentChatMessages

    private val _chatSessions = mutableStateListOf<ChatSessionEntity>()
    val chatSessions: SnapshotStateList<ChatSessionEntity> = _chatSessions
    val activeSessionId = mutableStateOf<String?>(null)
    val historySearchQuery = mutableStateOf("")
    val isHistoryVisible = mutableStateOf(false)
    private var sessionCollectionJob: Job? = null

    /** Flag indicating if response generation is in progress */
    val isGenerating = mutableStateOf(false)

    // region Message Handling & Generation
    // ========================================================================================
    //                          Public Message Methods
    // ========================================================================================
    fun setHistoryVisible(visible: Boolean) {
        isHistoryVisible.value = visible
    }

    fun setHistorySearchQuery(query: String) {
        historySearchQuery.value = query
        observeChatSessions(query)
    }

    fun openSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            if (isGenerating.value) stopGeneration()
            val session = chatHistoryRepository.getSession(sessionId) ?: return@launch
            val mode = session.mode.toSessionMode()
            val instruction = session.systemInstruction.ifBlank {
                instructionForMode(mode)
            }
            val loadedMessages = chatHistoryRepository.loadMessages(sessionId)
            withContext(Dispatchers.Main) {
                selectConversationContext(mode, instruction)
                activeSessionId.value = sessionId
                _currentChatMessages.clear()
                _currentChatMessages.addAll(loadedMessages)
                isHistoryVisible.value = false
            }
            recreateLmConversation(forceRecreate = true)
            persistAppliedConversationContext()
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch(Dispatchers.Default) {
            chatHistoryRepository.renameSession(sessionId, title)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            chatHistoryRepository.deleteSession(sessionId)
            if (activeSessionId.value == sessionId) {
                withContext(Dispatchers.Main) {
                    activeSessionId.value = null
                    _currentChatMessages.clear()
                }
                restoreMostRecentSession()
            }
        }
    }

    suspend fun exportSession(sessionId: String): String {
        return chatHistoryRepository.exportSessionMarkdown(sessionId)
    }

    fun sendMessage(message: String, isUser: Boolean = true) {
        viewModelScope.launch {
            if(isGenerating.value) stopGeneration()
            if(message.isBlank()) return@launch
            val sessionId = ensureActiveSession(message)
            val userMessage = ChatMessage.text(
                text = message,
                role = if (isUser) ChatRole.USER else ChatRole.ASSISTANT
            )
            _currentChatMessages.add(userMessage)
            chatHistoryRepository.saveMessage(sessionId, userMessage)
            val meta = mapOf("is_generating" to "true")
            val assistantMessage = ChatMessage.text(
                text = "",
                role = ChatRole.ASSISTANT,
                metadata = meta
            )
            _currentChatMessages.add(assistantMessage)
            chatHistoryRepository.saveMessage(sessionId, assistantMessage)
            isGenerating.value = true
            getTextTalkerResponse(message, {}, {
                println(it.message)
            })
        }

    }

    fun reGenerateMessage(message: ChatMessage) {
        val prompt = message.metadata?.get("prompt") ?: return
        val negativePrompt = message.metadata?.get("negative_prompt") ?: ""
    }

    fun startNewConversation() {
        viewModelScope.launch(Dispatchers.Default) {
            if (isGenerating.value) {
                stopGeneration()
            }
            try {
                selectConversationContext(
                    mode = ChatSessionMode.DEFAULT,
                    systemInstruction = systemPrompt.value
                )
                val newSessionId = chatHistoryRepository.createSession(
                    mode = ChatSessionMode.DEFAULT,
                    systemInstruction = appliedSystemInstructionOrEmpty()
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
                recreateLmConversation(forceRecreate = true)
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating.value = false
                    isInferenceOn = false
                }
            }
        }
    }

    fun startSvgImageConversation() {
        viewModelScope.launch(Dispatchers.Default) {
            if (isGenerating.value) {
                stopGeneration()
            }
            try {
                selectConversationContext(
                    mode = ChatSessionMode.SVG_IMAGE,
                    systemInstruction = SVG_IMAGE_SYSTEM_INSTRUCTION
                )
                val newSessionId = chatHistoryRepository.createSession(
                    title = getString(Res.string.library_svg_image),
                    mode = ChatSessionMode.SVG_IMAGE,
                    systemInstruction = appliedSystemInstructionOrEmpty()
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
                recreateLmConversation(
                    systemInstruction = SVG_IMAGE_SYSTEM_INSTRUCTION,
                    forceRecreate = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating.value = false
                    isInferenceOn = false
                }
            }
        }
    }

    fun startChiptuneBgmConversation() {
        viewModelScope.launch(Dispatchers.Default) {
            if (isGenerating.value) {
                stopGeneration()
            }
            try {
                selectConversationContext(
                    mode = ChatSessionMode.CHIPTUNE_BGM_MML,
                    systemInstruction = CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION
                )
                val newSessionId = chatHistoryRepository.createSession(
                    title = getString(Res.string.library_chiptune_bgm),
                    mode = ChatSessionMode.CHIPTUNE_BGM_MML,
                    systemInstruction = appliedSystemInstructionOrEmpty()
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
                recreateLmConversation(
                    systemInstruction = CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION,
                    forceRecreate = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating.value = false
                    isInferenceOn = false
                }
            }
        }
    }

    fun startLottieAnimationConversation() {
        viewModelScope.launch(Dispatchers.Default) {
            if (isGenerating.value) {
                stopGeneration()
            }
            try {
                selectConversationContext(
                    mode = ChatSessionMode.LOTTIE_ANIMATION,
                    systemInstruction = LOTTIE_ANIMATION_SYSTEM_INSTRUCTION
                )
                val newSessionId = chatHistoryRepository.createSession(
                    title = getString(Res.string.library_lottie_animation),
                    mode = ChatSessionMode.LOTTIE_ANIMATION,
                    systemInstruction = appliedSystemInstructionOrEmpty()
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
                recreateLmConversation(
                    systemInstruction = LOTTIE_ANIMATION_SYSTEM_INSTRUCTION,
                    forceRecreate = true
                )
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating.value = false
                    isInferenceOn = false
                }
            }
        }
    }

    fun stopGeneration() {
        val wasGenerating = isGenerating.value
        isGenerating.value = false
        if (wasGenerating && llmPath.value.isNotBlank()) {
            contextCoordinator.cancelActive()
        }
        responseGenerationJob?.cancel()
        val lastIndex = _currentChatMessages.lastIndex
        if (wasGenerating && lastIndex >= 0) {
            val removedMessage = _currentChatMessages.removeAt(lastIndex)
            activeSessionId.value?.let { sessionId ->
                viewModelScope.launch(Dispatchers.Default) {
                    chatHistoryRepository.deleteMessage(sessionId, removedMessage.id)
                }
            }
        }
    }

    private fun observeChatSessions(query: String = historySearchQuery.value) {
        sessionCollectionJob?.cancel()
        sessionCollectionJob = viewModelScope.launch(Dispatchers.Default) {
            chatHistoryRepository.observeSessions(query).collectLatest { sessions ->
                withContext(Dispatchers.Main) {
                    _chatSessions.clear()
                    _chatSessions.addAll(sessions)
                }
            }
        }
    }

    private fun restoreMostRecentSession() {
        viewModelScope.launch(Dispatchers.Default) {
            val session = chatHistoryRepository.getMostRecentSession() ?: return@launch
            val mode = session.mode.toSessionMode()
            val instruction = session.systemInstruction.ifBlank {
                instructionForMode(mode)
            }
            selectConversationContext(mode, instruction)
            val loadedMessages = chatHistoryRepository.loadMessages(session.id)
            withContext(Dispatchers.Main) {
                activeSessionId.value = session.id
                _currentChatMessages.clear()
                _currentChatMessages.addAll(loadedMessages)
            }
            if (contextCoordinator.isEngineReady()) {
                recreateLmConversation(forceRecreate = true)
                persistAppliedConversationContext()
            }
        }
    }

    private suspend fun ensureActiveSession(firstMessage: String): String {
        activeSessionId.value?.let { return it }
        return chatHistoryRepository.createSession(
            title = firstMessage.take(36),
            mode = _conversationContext.value.mode,
            systemInstruction = appliedSystemInstructionOrEmpty()
        ).also {
            activeSessionId.value = it
        }
    }

    private fun selectConversationContext(
        mode: ChatSessionMode,
        systemInstruction: String
    ) {
        contextCoordinator.onModeSwitched(mode)
        _conversationContext.value = ConversationContextState(
            mode = mode,
            systemInstruction = systemInstruction,
            isApplied = false
        )
    }

    private fun markConversationContextApplied(applied: Boolean) {
        _conversationContext.value = _conversationContext.value.copy(isApplied = applied)
    }

    private suspend fun persistAppliedConversationContext() {
        val context = _conversationContext.value
        val sessionId = activeSessionId.value
        if (!context.isApplied || sessionId == null) return
        chatHistoryRepository.updateSessionContext(
            sessionId = sessionId,
            mode = context.mode,
            systemInstruction = context.systemInstruction
        )
    }

    private fun appliedSystemInstructionOrEmpty(): String {
        return _conversationContext.value
            .takeIf { it.isApplied }
            ?.systemInstruction
            .orEmpty()
    }

    private fun instructionForMode(mode: ChatSessionMode): String {
        return when (mode) {
            ChatSessionMode.DEFAULT -> systemPrompt.value
            ChatSessionMode.SVG_IMAGE -> SVG_IMAGE_SYSTEM_INSTRUCTION
            ChatSessionMode.CHIPTUNE_BGM_MML -> CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION
            ChatSessionMode.LOTTIE_ANIMATION -> LOTTIE_ANIMATION_SYSTEM_INSTRUCTION
        }
    }

    private fun String.toSessionMode(): ChatSessionMode {
        return when (this) {
            "svg_image" -> ChatSessionMode.SVG_IMAGE
            "chiptune_bgm_mml" -> ChatSessionMode.CHIPTUNE_BGM_MML
            "lottie_animation" -> ChatSessionMode.LOTTIE_ANIMATION
            else -> ChatSessionMode.DEFAULT
        }
    }

    private fun currentSystemInstruction(): String {
        return _conversationContext.value.systemInstruction.ifBlank {
            instructionForMode(_conversationContext.value.mode)
        }
    }

    private fun updateContextBudget(snapshot: ContextBudgetSnapshot) {
        val context = _conversationContext.value
        _conversationContext.value = context.copy(
            usedTokens = snapshot.usedTokens,
            maxTokens = snapshot.capacityTokens ?: 0,
            projectedTokens = snapshot.projectedTokens,
            budgetRatio = snapshot.ratio,
            budgetLevel = snapshot.level.name,
            compactionCount = context.compactionCount + if (snapshot.didCompact) 1 else 0,
        )
    }

    /**
     * Checks the hard boundary before native decoding.  If the current KV
     * cache is near the limit, rebuild it from a summary plus recent turns;
     * durable chat history is never deleted.
     */
    private suspend fun prepareConversationForPrompt(
        prompt: String,
        mode: ChatSessionMode,
    ): LmConversation {
        val conversation = checkNotNull(contextCoordinator.currentConversation()) {
            "LM conversation is not initialized."
        }
        val strategy = mode.contextStrategy()
        val before = ContextBudgetPolicy.inspect(
            usedTokens = runCatching { conversation.tokenCount() }.getOrNull(),
            capacityTokens = lmMaxNumTokens.value,
            incomingPrompt = prompt,
            strategy = strategy,
        )
        updateContextBudget(before)

        if (before.level != ContextBudgetLevel.COMPACTION_REQUIRED &&
            before.level != ContextBudgetLevel.OVERFLOW
        ) {
            return conversation
        }

        val compactedConversation = contextCoordinator.openConversation(
            key = activeSessionId.value ?: "pending:${mode.name}",
            mode = mode,
            systemInstruction = currentSystemInstruction(),
            toolsJson = agentTools.getToolsDescriptionJson(),
            initialMessages = ContextCoordinator.compact(
                messages = _currentChatMessages.toList(),
                retainTurns = when (strategy) {
                    is ContextStrategy.ChatSession -> strategy.historyRetainWindow
                    is ContextStrategy.StructuredGeneration -> 1
                },
            ),
            samplerConfig = SamplerConfig(
                temperature = temperature.value.toDouble(),
                topP = topP.value.toDouble(),
                topK = topK.value,
            ),
            forceRecreate = true,
        )
        val after = ContextBudgetPolicy.inspect(
            usedTokens = runCatching { compactedConversation.tokenCount() }.getOrNull(),
            capacityTokens = lmMaxNumTokens.value,
            incomingPrompt = prompt,
            strategy = strategy,
        ).copy(didCompact = true)
        updateContextBudget(after)
        check(after.isUsable) {
            "Context remains over the model limit after compaction."
        }
        return compactedConversation
    }

    private fun normalizeLmBackend(backend: String): String {
        return backend.trim().uppercase()
    }

    private fun isGpuBackend(backend: String?): Boolean {
        return backend.equals(LM_BACKEND_GPU, ignoreCase = true)
    }

    private fun isSameLmBackend(left: String?, right: String): Boolean {
        return left.equals(normalizeLmBackend(right), ignoreCase = true)
    }

    private fun clearActiveLmEngineState() {
        activeModelPath = null
        activeBackend = null
        activeEnableSpeculativeDecoding = null
        activeMaxNumTokens = null
    }

    private fun createLmEngine(modelPath: String, backend: String): LmEngine {
        return LmEngine(
            modelPath = modelPath,
            backend = normalizeLmBackend(backend),
            visionBackend = lmVisionBackend.value,
            audioBackend = lmAudioBackend.value,
            maxNumTokens = lmMaxNumTokens.value,
            maxNumImages = lmMaxNumImages.value,
            cacheDir = FileKit.cacheDir.path,
            enableBenchmark = false,
            enableSpeculativeDecoding = enableSpeculativeDecoding.value,
            mainNpuNativeLibraryDir = "",
            visionNpuNativeLibraryDir = "",
            audioNpuNativeLibraryDir = "",
            mainBackendNumThreads = lmMainBackendNumThreads.value,
            audioBackendNumThreads = lmAudioBackendNumThreads.value
        )
    }

    private fun updateActiveLmEngineState(modelPath: String, backend: String) {
        activeModelPath = modelPath
        activeBackend = normalizeLmBackend(backend)
        activeEnableSpeculativeDecoding = enableSpeculativeDecoding.value
        activeMaxNumTokens = lmMaxNumTokens.value
    }

    private fun isTokenLimitErrorMessage(message: String): Boolean {
        return message.contains("kv-cache", ignoreCase = true) ||
                message.contains("too long", ignoreCase = true) ||
                message.contains("exceeding", ignoreCase = true) ||
                message.contains("token", ignoreCase = true)
    }

    private fun shouldRetryWithCpuAfterGpuDecodeError(
        error: Throwable,
        attemptedBackend: String
    ): Boolean {
        val lmError = error as? LiteRtLmInferenceException ?: return false
        return lmError.statusCode == ABSEIL_STATUS_INTERNAL &&
                isGpuBackend(attemptedBackend) &&
                !isTokenLimitErrorMessage(lmError.nativeMessage)
    }

    private suspend fun switchLmBackendAndRecreateConversation(
        backend: String
    ): LmConversation {
        val currentLlmPath = llmPath.value
        check(currentLlmPath.isNotBlank()) { "LM model path is empty." }

        contextCoordinator.closeAll()
        clearActiveLmEngineState()

        val normalizedBackend = normalizeLmBackend(backend)
        val engine = createLmEngine(currentLlmPath, normalizedBackend)
        try {
            engine.initialize()
            contextCoordinator.attachEngine(engine)
            updateActiveLmEngineState(currentLlmPath, normalizedBackend)
            recreateLmConversation(forceRecreate = true)
            persistAppliedConversationContext()
            lmBackend.value = normalizedBackend
            return checkNotNull(contextCoordinator.currentConversation()) {
                "Failed to recreate LiteRT LM conversation."
            }
        } catch (e: Throwable) {
            contextCoordinator.closeAll()
            clearActiveLmEngineState()
            markConversationContextApplied(false)
            throw e
        }
    }

    private suspend fun recreateLmConversation(
        systemInstruction: String = currentSystemInstruction(),
        forceRecreate: Boolean = false,
    ): LmConversation? {
        if (!contextCoordinator.isEngineReady()) {
            markConversationContextApplied(false)
            return null
        }
        val mode = _conversationContext.value.mode
        val conversation = contextCoordinator.openConversation(
            key = activeSessionId.value ?: "pending:${mode.name}",
            mode = mode,
            systemInstruction = systemInstruction,
            toolsJson = agentTools.getToolsDescriptionJson(),
            initialMessages = ContextCoordinator.replay(_currentChatMessages.toList()),
            samplerConfig = SamplerConfig(
                temperature = temperature.value.toDouble(),
                topP = topP.value.toDouble(),
                topK = topK.value
            ),
            forceRecreate = forceRecreate,
        )
        markConversationContextApplied(true)
        return conversation
    }
    
    @OptIn(ExperimentalTime::class)
    fun getTextTalkerResponse(query: String, onCancelled: () -> Unit, onError: (Throwable) -> Unit) {
        val activeConversation = contextCoordinator.currentConversation()
        if (activeConversation == null) {
            isGenerating.value = false
            isInferenceOn = false
            viewModelScope.launch {
                if (_currentChatMessages.isNotEmpty()) {
                    val lastIdx = _currentChatMessages.lastIndex
                    val meta = mapOf("is_generating" to "false")
                    val updated = _currentChatMessages[lastIdx].copy(
                        contents = listOf(
                            ChatMessageContent.Text(
                                getString(Res.string.chat_system_engine_not_initialized)
                            )
                        ),
                        metadata = meta
                    )
                    _currentChatMessages[lastIdx] = updated
                    activeSessionId.value?.let { chatHistoryRepository.saveMessage(it, updated) }
                }
            }
            onError(IllegalStateException("LM Engine not initialized"))
            return
        }
        
        responseGenerationJob = viewModelScope.launch(Dispatchers.Default) {
            isInferenceOn = true
            val promptContent = query
            val startTime = Clock.System.now().toEpochMilliseconds()
            
            var generatedResult = ""
            var generatedThought = ""
            val persistentToolCalls = mutableListOf<PersistentToolCall>()
            val persistentToolResponses = mutableListOf<PersistentToolResponse>()
            val sessionId = activeSessionId.value
            val assistantMessageId = _currentChatMessages.lastOrNull()?.id?.toString().orEmpty()
            val sessionMode = _conversationContext.value.mode
            val toolLogIds = mutableMapOf<String, String>()
            var terminalTransition = "RUNNING"
            var terminalTurnCount = 0

            suspend fun displayText(): String {
                val thinkingPrefix = if (generatedThought.isNotEmpty()) {
                    getString(Res.string.chat_thinking_prefix)
                } else {
                    ""
                }
                return buildString {
                    if (thinkingPrefix.isNotEmpty()) {
                        append(thinkingPrefix)
                        generatedThought.lineSequence().forEach { line ->
                            append("> ").append(line).append("\n")
                        }
                        append("\n")
                    }
                    append(generatedResult)
                }.trim()
            }

            suspend fun updateAssistantMessage(isGeneratingNow: Boolean) {
                val messageIndex = _currentChatMessages.indexOfFirst {
                    it.id.toString() == assistantMessageId
                }
                if (messageIndex >= 0) {
                    val msgToUpdate = _currentChatMessages[messageIndex]
                    val meta = mapOf("is_generating" to isGeneratingNow.toString())
                    val contents = if (sessionMode.isStructuredGenerationMode()) {
                        listOf(ChatMessageContent.Text(""))
                    } else {
                        listOf(ChatMessageContent.Text(displayText()))
                    }
                    _currentChatMessages[messageIndex] = msgToUpdate.copy(
                        contents = contents,
                        metadata = meta
                    )
                }
            }

            suspend fun showGenerationError(error: Throwable) {
                val messageIndex = _currentChatMessages.indexOfFirst {
                    it.id.toString() == assistantMessageId
                }
                if (messageIndex >= 0) {
                    val meta = mapOf("is_generating" to "false")
                    val errMsg = error.message.orEmpty()
                    val displayMsg = if (isTokenLimitErrorMessage(errMsg)) {
                        getString(Res.string.chat_system_error_token_limit_exceeded)
                    } else {
                        getString(
                            Res.string.chat_system_error_prefix,
                            errMsg.ifEmpty { getString(Res.string.unknown_error) }
                        )
                    }
                    val updated = _currentChatMessages[messageIndex].copy(
                        contents = listOf(ChatMessageContent.Text(displayMsg)),
                        metadata = meta
                    )
                    _currentChatMessages[messageIndex] = updated
                    sessionId?.let { chatHistoryRepository.saveMessage(it, updated) }
                }
            }

            var runnerConversation: LmConversation = activeConversation
            try {
                runnerConversation = prepareConversationForPrompt(promptContent, sessionMode)
            } catch (e: Throwable) {
                isGenerating.value = false
                isInferenceOn = false
                showGenerationError(e)
                onError(e)
                return@launch
            }
            var attemptedBackend = activeBackend ?: normalizeLmBackend(lmBackend.value)
            var retriedOnCpuAfterGpuFailure = false

            while (true) {
                val runner = AgentLoopRunner(
                    session = runnerConversation,
                    toolExecutor = agentTools,
                    config = AgentLoopConfig(maxToolTurns = 10)
                )

                try {
                    runner.run(
                        initialMessage = org.onion.agro.native.llm.Message.user(promptContent),
                        extraContextProvider = {
                            if (enableThinking.value) mapOf(KEY_THINK_MODE to "true") else emptyMap()
                        }
                    ).collect { event ->
                        terminalTransition = event.state.transition.name
                        terminalTurnCount = event.state.turnCount

                        when (event) {
                            is AgentLoopEvent.TextDelta -> {
                                generatedResult += event.text
                                updateAssistantMessage(isGeneratingNow = true)
                            }
                            is AgentLoopEvent.ThoughtDelta -> {
                                generatedThought += event.text
                                updateAssistantMessage(isGeneratingNow = true)
                            }
                            is AgentLoopEvent.ToolCallsReceived -> {
                                println("ChatViewModel: Received ${event.toolCalls.size} tool calls.")
                            }
                            is AgentLoopEvent.ToolStarted -> {
                                println("ChatViewModel: Executing tool '${event.toolCall.name}' with args: ${event.toolCall.arguments}")
                                val toolStartedAt = Clock.System.now().toEpochMilliseconds()
                                val toolLogId = ChatHistoryRepository.newId("tool")
                                val toolArguments = event.toolCall.arguments.toString()
                                val toolKey = "${event.turnIndex}:${event.callIndex}"
                                toolLogIds[toolKey] = toolLogId

                                persistentToolCalls.add(
                                    PersistentToolCall(
                                        name = event.toolCall.name,
                                        arguments = toolArguments,
                                        createdAtMillis = toolStartedAt
                                    )
                                )

                                if (sessionId != null && assistantMessageId.isNotBlank()) {
                                    chatHistoryRepository.upsertToolLog(
                                        ChatToolLogEntity(
                                            id = toolLogId,
                                            sessionId = sessionId,
                                            messageId = assistantMessageId,
                                            toolName = event.toolCall.name,
                                            arguments = toolArguments,
                                            response = "",
                                            status = "running",
                                            startedAtMillis = toolStartedAt,
                                            completedAtMillis = null
                                        )
                                    )
                                }

                                if (!sessionMode.isStructuredGenerationMode()) {
                                    generatedResult += getString(
                                        Res.string.chat_running_tool,
                                        event.toolCall.name
                                    )
                                }
                                updateAssistantMessage(isGeneratingNow = true)
                            }
                            is AgentLoopEvent.ToolFinished -> {
                                val toolKey = "${event.turnIndex}:${event.callIndex}"
                                val toolLogId = toolLogIds[toolKey] ?: ChatHistoryRepository.newId("tool")
                                val resultStr = event.response.response
                                val toolArguments = event.toolCall.arguments.toString()
                                val toolStatus = if (event.result.success) "completed" else "failed"

                                persistentToolResponses.add(
                                    PersistentToolResponse(
                                        name = event.toolCall.name,
                                        response = resultStr,
                                        createdAtMillis = event.result.completedAtMillis
                                    )
                                )

                                if (sessionId != null && assistantMessageId.isNotBlank()) {
                                    chatHistoryRepository.upsertToolLog(
                                        ChatToolLogEntity(
                                            id = toolLogId,
                                            sessionId = sessionId,
                                            messageId = assistantMessageId,
                                            toolName = event.toolCall.name,
                                            arguments = toolArguments,
                                            response = resultStr,
                                            status = toolStatus,
                                            startedAtMillis = event.result.startedAtMillis,
                                            completedAtMillis = event.result.completedAtMillis
                                        )
                                    )
                                }

                                if (!sessionMode.isStructuredGenerationMode()) {
                                    generatedResult += getString(
                                        Res.string.chat_tool_completed,
                                        event.toolCall.name
                                    )
                                }
                                updateAssistantMessage(isGeneratingNow = true)
                            }
                            is AgentLoopEvent.Completed -> {
                                terminalTransition = event.state.transition.name
                                terminalTurnCount = event.state.turnCount
                            }
                            is AgentLoopEvent.MaxTurnsReached -> {
                                terminalTransition = event.state.transition.name
                                terminalTurnCount = event.state.turnCount
                            }
                        }
                    }
                    break
                } catch (e: Throwable) {
                    terminalTransition = "ERROR"
                    terminalTurnCount = 0
                    isGenerating.value = false
                    isInferenceOn = false

                    if (e is CancellationException) {
                        onCancelled()
                        return@launch
                    }

                    if (
                        !retriedOnCpuAfterGpuFailure &&
                        shouldRetryWithCpuAfterGpuDecodeError(e, attemptedBackend)
                    ) {
                        println(
                            "ChatViewModel: GPU decode failed with ${e.message}. " +
                                "Switching LiteRT LM backend to CPU and retrying once."
                        )
                        showToast(getString(Res.string.chat_gpu_decode_failed_fallback_cpu))
                        val cpuConversation = runCatching {
                            switchLmBackendAndRecreateConversation(LM_BACKEND_CPU)
                        }.getOrElse { fallbackError ->
                            if (fallbackError is CancellationException) {
                                onCancelled()
                                return@launch
                            }
                            val combinedError = RuntimeException(
                                "GPU decode failed and CPU fallback failed. " +
                                    "GPU error: ${e.message}. " +
                                    "CPU error: ${fallbackError.message}",
                                fallbackError
                            )
                            showGenerationError(combinedError)
                            onError(combinedError)
                            return@launch
                        }

                        generatedResult = ""
                        generatedThought = ""
                        persistentToolCalls.clear()
                        persistentToolResponses.clear()
                        toolLogIds.clear()
                        terminalTransition = "GPU_DECODE_CPU_RETRY"
                        terminalTurnCount = 0
                        runnerConversation = cpuConversation
                        attemptedBackend = LM_BACKEND_CPU
                        retriedOnCpuAfterGpuFailure = true
                        isGenerating.value = true
                        isInferenceOn = true
                        updateAssistantMessage(isGeneratingNow = true)
                        continue
                    }

                    showGenerationError(e)
                    onError(e)
                    return@launch
                }
            }
            
            println("ChatViewModel: collect finished!")
            val generationDuration = Clock.System.now().toEpochMilliseconds() - startTime
            val messageIndex = _currentChatMessages.indexOfFirst {
                it.id.toString() == assistantMessageId
            }
            if (messageIndex >= 0) {
                val meta = mutableMapOf(
                    "prompt" to promptContent,
                    "time_taken" to formatDuration(generationDuration),
                    "is_generating" to "false",
                    "agent_turn_count" to terminalTurnCount.toString(),
                    "agent_transition" to terminalTransition,
                    "lm_backend" to attemptedBackend
                )
                if (retriedOnCpuAfterGpuFailure) {
                    meta["gpu_decode_cpu_retry"] = "true"
                }
                val finalContents = when (sessionMode) {
                    ChatSessionMode.SVG_IMAGE -> {
                        listOf(SvgMessageParser.parseCompletedResponse(generatedResult.trim()))
                    }
                    ChatSessionMode.CHIPTUNE_BGM_MML -> {
                        listOf(ChiptuneBgmMessageParser.parseCompletedResponse(generatedResult.trim()))
                    }
                    ChatSessionMode.LOTTIE_ANIMATION -> {
                        listOf(LottieMessageParser.parseCompletedResponse(generatedResult.trim()))
                    }
                    ChatSessionMode.DEFAULT -> {
                        listOf(ChatMessageContent.Text(displayText()))
                    }
                }
                val updated = _currentChatMessages[messageIndex].copy(
                    contents = finalContents,
                    metadata = meta,
                    toolCalls = persistentToolCalls,
                    toolResponses = persistentToolResponses
                )
                _currentChatMessages[messageIndex] = updated
                sessionId?.let { chatHistoryRepository.saveMessage(it, updated) }
            }
            
            isGenerating.value = false
            isInferenceOn = false
        }
    }

    init {
        viewModelScope.launch {
            try {
                systemPrompt.value = getString(Res.string.llm_setting_system_prompt_default,BuildConfig.APP_NAME)
                if (
                    activeSessionId.value == null &&
                    _conversationContext.value.mode == ChatSessionMode.DEFAULT &&
                    !_conversationContext.value.isApplied
                ) {
                    selectConversationContext(
                        mode = ChatSessionMode.DEFAULT,
                        systemInstruction = systemPrompt.value
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        observeChatSessions()
        restoreMostRecentSession()
    }

    private companion object {
        const val ABSEIL_STATUS_INTERNAL = 13
        const val LM_BACKEND_CPU = "CPU"
        const val LM_BACKEND_GPU = "GPU"
        const val DEFAULT_LM_MAX_NUM_TOKENS = 16384
        const val MIN_LM_MAX_NUM_TOKENS = 128
        const val LM_MAX_NUM_TOKENS_STEP = 128

        val SVG_IMAGE_SYSTEM_INSTRUCTION = """
            You are ${BuildConfig.APP_NAME}'s dedicated SVG vector graphic architect.

            Convert the user's visual request into a single self-contained, valid SVG image.
            Respond ONLY with a single raw JSON object. Do not wrap in Markdown fences (no ```json). Do not add any text before or after the JSON.

            Use this exact JSON structure:
            {
              "type": "svg_image",
              "svg": "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512' width='100%' height='100%'>...</svg>"
            }

            CRITICAL RULES TO PREVENT XML CORRUPTION:
            - STRICTLY FORBIDDEN TAGS: NEVER generate <filter>, <feGaussianBlur>, <feBlend>, <feMerge>, or <feMergeIn>. Do NOT write filter='url(#...)'.
            - LIGHTING & GLOW: Use <radialGradient>, <linearGradient>, or layered semi-transparent shapes with opacity='0.2' ~ opacity='0.6' instead of filters.
            - FLATTENED STRUCTURE: Prefer direct coordinates on elements. Do not deeply nest <g> tags or use <g transform='translate(...)'>. Every shape tag (<rect/>, <circle/>, <ellipse/>, <path/>, <line/>, <stop/>) MUST be self-closing.
            - XML ATTRIBUTES: Inside the svg string, ALL attributes MUST use single quotes ('). In url references, write fill='url(#gradId)' without internal quotes.
            - COORDINATE BOUNDS: Canvas viewBox is '0 0 512 512' with center (256, 256). All coordinates must stay strictly within [0, 512].
            - COLORS: Use valid 6-digit hex (#RRGGBB) or 3-digit hex (#RGB). Never output 5 or 7 hex digits.
        """.trimIndent()

        val CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION = """
            You are ${BuildConfig.APP_NAME}'s dedicated 8-bit chiptune BGM composer.

            Compose a loopable 8-bit BGM score in Music Macro Language (MML) matching the user's mood or game scene.
            Respond ONLY with a single raw JSON object. Do not wrap in Markdown fences (no ```json). Do not output any text before or after the JSON.

            Use this exact JSON structure:
            {
              "type": "chiptune_bgm_mml",
              "schemaVersion": 1,
              "title": "Retro Adventure",
              "seed": 48271,
              "bpm": 140,
              "timeSignature": "4/4",
              "loopBars": 2,
              "sampleRate": 22050,
              "bitDepth": 8,
              "masterVolume": 0.8,
              "tracks": [
                {
                  "channel": "pulse1",
                  "dutyCycle": 0.5,
                  "mml": "T140 O5 L8 V12 C E G >C <B A G E | C E G A G E D R"
                },
                {
                  "channel": "pulse2",
                  "dutyCycle": 0.25,
                  "mml": "T140 O4 L8 V9 E G B >D <G B >D <B | E G B >C <B G F# R"
                },
                {
                  "channel": "triangle",
                  "dutyCycle": 0.5,
                  "mml": "T140 O3 L4 C G E G | A E F G"
                },
                {
                  "channel": "noise",
                  "dutyCycle": 0.5,
                  "mml": "T140 L8 [K R H R S R H R]x2"
                }
              ]
            }

            CRITICAL CHIPTUNE RULES:
            1. CHANNEL ROLES & OCTAVES:
               - pulse1 (Lead Melody): dutyCycle MUST be 0.5. Range O4..O6, Volume V10..V14.
               - pulse2 (Harmony/Counter-line): dutyCycle MUST be 0.25. Range O3..O5, Volume V7..V10.
               - triangle (Bass Line): dutyCycle 0.5. Range O2..O3 (deep smooth bass).
               - noise (Drums/Percussion): dutyCycle 0.5. Use K (Kick), S (Snare), H (Hi-hat), T (Tom), R (Rest). NEVER use note letters A-G in noise track.

            2. TIME SIGNATURE & BAR TIMING MATH (CRITICAL):
               - In 4/4 time: 1 bar = 4x L4 notes OR 8x L8 notes OR 16x L16 notes.
               - For loopBars: 2 -> write exactly 2 bars (8x L4 or 16x L8) separated by '|'.
               - For loopBars: 4 -> write exactly 4 bars separated by '|'.
               - Every track must have the exact same total duration to loop seamlessly.

            3. TEMPO SYNCHRONIZATION:
               - If a track begins with 'T<number>', that number MUST be identical to the top-level "bpm". (e.g. "bpm": 140 -> "T140 ...").

            4. MML SYNTAX & REPEATS:
               - Notes: C, D, E, F, G, A, B. Sharps (# or +), Flats (-).
               - Lengths: L1, L2, L4, L8, L16, L32. Dotted note: L4. (1.5x length).
               - Rests: R or P. Octave shifts: > (up one octave), < (down one octave).
               - Repeats: [ ... ]x2 or [ ... ]x4. Do NOT use * 2 or parentheses.
        """.trimIndent()

        val LOTTIE_ANIMATION_SYSTEM_INSTRUCTION = """
            You are ${BuildConfig.APP_NAME}'s dedicated Lottie animation architect and motion designer.

            Generate a single valid, lightweight, self-contained Native Lottie JSON animation.
            Respond ONLY with a single raw JSON object. Do not wrap in Markdown fences (no ```json). Do not output any conversational prose.

            CANVAS & TIMELINE SPECS:
            - Root: "w": 240, "h": 240, "fr": 30, "ip": 0, "op": 60 (2s loop) or "op": 90 (3s loop), "ddd": 0, "loop": true, "assets": [].
            - Layers: "ind": 1..N, "ty": 4 (shape layer), "sr": 1, "ao": 0, "st": 0, "bm": 0, "ip": 0, "op": 60, "ddd": 0.
            - Inside shapes: "ty": "gr", "it": [<geometry>, <paint>, <transform "tr">].

            CRITICAL MOTION RULES FOR 4B MODELS:
            1. MANDATORY MOTION ("a": 1):
               - NEVER output completely static JSON where all properties have "a": 0.
               - You MUST animate at least one property with 2 to 3 chronological keyframes ("k": [{ "t": 0, ... }, ...]).
               - Choose the motion archetype matching the user request:
                 * TRANSLATION / FALLING / SLIDING (Raindrops, Snow, Falling Leaves, Bouncing Ball, Rocket, Moving Object):
                   Animate position `ks.p` along Y axis (e.g. from top [120, 20, 0] to bottom [120, 220, 0]) and animate opacity `ks.o` (0 -> 100 -> 0).
                 * ROTATION / SPIN (Loading Spinner, Gear, Radar, Orbiting Planet, Sun Rays, Fan):
                   Animate rotation `ks.r` from 0 to 360 (e.g. { "t": 0, "s": [0], "e": [360] }, { "t": 60, "s": [360], "e": [360] }).
                 * SCALE / PULSE / POP (Heartbeat, Alert Icon, Breathing Circle, Star Sparkle, Badge Pop):
                   Animate scale `ks.s` (e.g. 80 -> 120 -> 80) with matching start/end values for seamless loop.
                 * OPACITY / BLINK / FADE (Glow, Blinking Light, Flash, Strobe):
                   Animate opacity `ks.o` (e.g. 100 -> 20 -> 100 or 0 -> 100 -> 0).
                 * PATH DRAW (Checkmark, Progress Ring, Line Drawing):
                   Use Trim Path `ty: "tm"` with animated end percentage `"e"` (0 -> 100).

            2. KEYFRAME CONTINUITY (s -> e):
               - Keyframe format: { "t": <frame>, "s": [<startValues>], "e": [<endValues>] }.
               - For keyframe i, "e" MUST equal keyframe i+1's "s".
               - For seamless loop, values at t=0 and t=op must match (e.g. t=0 s:[80] -> t=30 s:[120] -> t=60 s:[80]).

            3. VISIBILITY, VIVID COLORS & SIZES:
               - Foreground shapes must use bright, vivid RGBA colors ("c": { "a": 0, "k": [R, G, B, 1] }).
                 Examples: Rain/Water Blue [0.22, 0.65, 1.0, 1], Cyan [0.0, 0.85, 0.95, 1], Orange [1.0, 0.5, 0.1, 1], Green [0.15, 0.82, 0.45, 1], Purple [0.65, 0.35, 0.95, 1].
                 NEVER use muddy dark colors where all R,G,B < 0.2.
               - Shapes must be clearly visible on the 240x240 canvas:
                 * Raindrops / Particles: Elongated ellipse with width 8..14 and height 24..45 (e.g. "s": { "a": 0, "k": [10, 30] }).
                 * Circles / Icons: Diameter 60..120 (e.g. "s": { "a": 0, "k": [90, 90] }).
                 * Rectangles / Cards: Width/height 60..140.

            4. MULTI-ELEMENT & PARTICLE PATTERNS:
               - For rain, snow, or particle streams, create 2 to 3 layers with different X coordinates (e.g. X=70, X=120, X=170) and staggered falling times (e.g. start at t=0, t=20) so multiple items fall continuously across the screen.

            FALLING MOTION ARCHETYPE (Follow this structure for Rain / Falling Particles):
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Falling Rain",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Raindrop 1",
                  "sr": 1,
                  "ao": 0,
                  "st": 0,
                  "bm": 0,
                  "ip": 0,
                  "op": 60,
                  "ks": {
                    "p": {
                      "a": 1,
                      "k": [
                        { "t": 0, "s": [80, 20, 0], "e": [80, 220, 0] },
                        { "t": 60, "s": [80, 220, 0], "e": [80, 220, 0] }
                      ]
                    },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [100, 100, 100] },
                    "r": { "a": 0, "k": 0 },
                    "o": {
                      "a": 1,
                      "k": [
                        { "t": 0, "s": [0], "e": [100] },
                        { "t": 10, "s": [100], "e": [100] },
                        { "t": 50, "s": [100], "e": [0] },
                        { "t": 60, "s": [0], "e": [0] }
                      ]
                    }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Drop Group",
                      "it": [
                        { "ty": "el", "nm": "Drop Shape", "p": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [10, 30] }, "d": 1 },
                        { "ty": "fl", "nm": "Drop Fill", "c": { "a": 0, "k": [0.22, 0.65, 1.0, 1] }, "o": { "a": 0, "k": 100 }, "r": 1 },
                        { "ty": "tr", "p": { "a": 0, "k": [0, 0] }, "a": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [100, 100] }, "r": { "a": 0, "k": 0 }, "o": { "a": 0, "k": 100 } }
                      ]
                    }
                  ]
                },
                {
                  "ddd": 0,
                  "ind": 2,
                  "ty": 4,
                  "nm": "Raindrop 2",
                  "sr": 1,
                  "ao": 0,
                  "st": 0,
                  "bm": 0,
                  "ip": 0,
                  "op": 60,
                  "ks": {
                    "p": {
                      "a": 1,
                      "k": [
                        { "t": 20, "s": [160, 20, 0], "e": [160, 220, 0] },
                        { "t": 60, "s": [160, 150, 0], "e": [160, 150, 0] }
                      ]
                    },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 0, "k": [100, 100, 100] },
                    "r": { "a": 0, "k": 0 },
                    "o": {
                      "a": 1,
                      "k": [
                        { "t": 0, "s": [0], "e": [0] },
                        { "t": 20, "s": [0], "e": [100] },
                        { "t": 55, "s": [100], "e": [0] },
                        { "t": 60, "s": [0], "e": [0] }
                      ]
                    }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Drop Group",
                      "it": [
                        { "ty": "el", "nm": "Drop Shape", "p": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [8, 26] }, "d": 1 },
                        { "ty": "fl", "nm": "Drop Fill", "c": { "a": 0, "k": [0.35, 0.75, 1.0, 1] }, "o": { "a": 0, "k": 100 }, "r": 1 },
                        { "ty": "tr", "p": { "a": 0, "k": [0, 0] }, "a": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [100, 100] }, "r": { "a": 0, "k": 0 }, "o": { "a": 0, "k": 100 } }
                      ]
                    }
                  ]
                }
              ]
            }

            PULSE / ROTATION MOTION ARCHETYPE (Follow this structure for Pulse / Spin / Center Icons):
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Pulse Circle",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Pulse Layer",
                  "sr": 1,
                  "ao": 0,
                  "st": 0,
                  "bm": 0,
                  "ip": 0,
                  "op": 60,
                  "ks": {
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": {
                      "a": 1,
                      "k": [
                        { "t": 0, "s": [85, 85, 100], "e": [115, 115, 100] },
                        { "t": 30, "s": [115, 115, 100], "e": [85, 85, 100] },
                        { "t": 60, "s": [85, 85, 100], "e": [85, 85, 100] }
                      ]
                    },
                    "r": { "a": 0, "k": 0 },
                    "o": { "a": 0, "k": 100 }
                  },
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Circle Group",
                      "it": [
                        { "ty": "el", "nm": "Circle", "p": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [90, 90] }, "d": 1 },
                        { "ty": "fl", "nm": "Fill", "c": { "a": 0, "k": [0.38, 0.45, 0.95, 1] }, "o": { "a": 0, "k": 100 }, "r": 1 },
                        { "ty": "tr", "p": { "a": 0, "k": [0, 0] }, "a": { "a": 0, "k": [0, 0] }, "s": { "a": 0, "k": [100, 100] }, "r": { "a": 0, "k": 0 }, "o": { "a": 0, "k": 100 } }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun ChatSessionMode.isStructuredGenerationMode(): Boolean {
        return this == ChatSessionMode.SVG_IMAGE ||
                this == ChatSessionMode.CHIPTUNE_BGM_MML ||
                this == ChatSessionMode.LOTTIE_ANIMATION
    }
}
