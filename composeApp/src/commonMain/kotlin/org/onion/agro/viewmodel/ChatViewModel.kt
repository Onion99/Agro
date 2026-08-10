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

    private var lmEngine: LmEngine? = null
    private var lmConversation: LmConversation? = null
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
            lmEngine != null &&
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
                try {
                    lmConversation?.close()
                    lmConversation = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    lmEngine?.close()
                    lmEngine = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                clearActiveLmEngineState()

                println("=== Model Path ===")
                println("Model Path: ${diffusionModelPath.value}")
                println("VAE Path: ${vaePath.value}")
                println("LLM Path: ${llmPath.value}")
                println("CLIP-L Path: ${clipLPath.value}")
                println("CLIP-G Path: ${clipGPath.value}")
                println("T5XXL Path: ${t5xxlPath.value}")
                println("cacheDir path is: ${FileKit.cacheDir.path}")
                isLlmModelLoading.value = true
                val currentLlmPath = llmPath.value
                lmEngine = LmEngine(
                    modelPath = currentLlmPath,
                    backend = lmBackend.value,
                    visionBackend = lmVisionBackend.value,
                    audioBackend = lmAudioBackend.value,
                    maxNumTokens = lmMaxNumTokens.value,
                    maxNumImages = lmMaxNumImages.value,
                    cacheDir = FileKit.cacheDir.path ?: "",
                    enableBenchmark = false,
                    enableSpeculativeDecoding = enableSpeculativeDecoding.value,
                    mainNpuNativeLibraryDir = "",
                    visionNpuNativeLibraryDir = "",
                    audioNpuNativeLibraryDir = "",
                    mainBackendNumThreads = lmMainBackendNumThreads.value,
                    audioBackendNumThreads = lmAudioBackendNumThreads.value
                )
                lmEngine?.initialize()

                lmConversation = lmEngine?.createConversation(
                    systemInstruction = currentSystemInstruction(),
                    toolsDescriptionJsonString = agentTools.getToolsDescriptionJson(),
                    enableConversationConstrainedDecoding = resolveConstrainedDecoding(
                        _conversationContext.value.mode.isStructuredGenerationMode()
                    ),
                    samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                        temperature = temperature.value.toDouble(),
                        topP = topP.value.toDouble(),
                        topK = topK.value
                    )
                )
                markConversationContextApplied(lmConversation != null)
                persistAppliedConversationContext()
                activeModelPath = currentLlmPath
                activeBackend = normalizeLmBackend(lmBackend.value)
                activeEnableSpeculativeDecoding = enableSpeculativeDecoding.value
                activeMaxNumTokens = lmMaxNumTokens.value
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    lmConversation?.close()
                } catch (closeError: Exception) {
                    closeError.printStackTrace()
                }
                lmConversation = null
                try {
                    lmEngine?.close()
                } catch (closeError: Exception) {
                    closeError.printStackTrace()
                }
                lmEngine = null
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

                val needsEngineReinit = lmEngine == null ||
                        activeModelPath != currentLlmPath ||
                        !isSameLmBackend(activeBackend, lmBackend.value) ||
                        activeEnableSpeculativeDecoding != enableSpeculativeDecoding.value ||
                        activeMaxNumTokens != lmMaxNumTokens.value
                
                if (needsEngineReinit) {
                    try {
                        lmConversation?.close()
                        lmConversation = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        lmEngine?.close()
                        lmEngine = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    clearActiveLmEngineState()
                    
                    lmEngine = LmEngine(
                        modelPath = currentLlmPath,
                        backend = lmBackend.value,
                        visionBackend = lmVisionBackend.value,
                        audioBackend = lmAudioBackend.value,
                        maxNumTokens = lmMaxNumTokens.value,
                        maxNumImages = lmMaxNumImages.value,
                        cacheDir = FileKit.cacheDir.path ?: "",
                        enableBenchmark = false,
                        enableSpeculativeDecoding = enableSpeculativeDecoding.value,
                        mainNpuNativeLibraryDir = "",
                        visionNpuNativeLibraryDir = "",
                        audioNpuNativeLibraryDir = "",
                        mainBackendNumThreads = lmMainBackendNumThreads.value,
                        audioBackendNumThreads = lmAudioBackendNumThreads.value
                    )
                    lmEngine?.initialize()
                    activeModelPath = currentLlmPath
                    activeBackend = normalizeLmBackend(lmBackend.value)
                    activeEnableSpeculativeDecoding = enableSpeculativeDecoding.value
                    activeMaxNumTokens = lmMaxNumTokens.value
                } else {
                    try {
                        lmConversation?.close()
                        lmConversation = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val engine = lmEngine
                if (engine != null) {
                    val instruction = instructionForMode(_conversationContext.value.mode)
                    selectConversationContext(
                        mode = _conversationContext.value.mode,
                        systemInstruction = instruction
                    )
                    lmConversation = engine.createConversation(
                        systemInstruction = instruction,
                        toolsDescriptionJsonString = agentTools.getToolsDescriptionJson(),
                        enableConversationConstrainedDecoding = resolveConstrainedDecoding(
                            _conversationContext.value.mode.isStructuredGenerationMode()
                        ),
                        samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                            temperature = temperature.value.toDouble(),
                            topP = topP.value.toDouble(),
                            topK = topK.value
                        )
                    )
                    markConversationContextApplied(true)
                    persistAppliedConversationContext()
                }
                _currentChatMessages.clear()
                val text = getString(Res.string.chat_system_parameters_applied)
                val sessionId = ensureActiveSession(text)
                chatHistoryRepository.clearSessionMessages(sessionId)
                val message = ChatMessage.text(text, role = ChatRole.SYSTEM)
                _currentChatMessages.add(message)
                chatHistoryRepository.saveMessage(sessionId, message)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    lmConversation?.close()
                } catch (closeError: Exception) {
                    closeError.printStackTrace()
                }
                lmConversation = null
                try {
                    lmEngine?.close()
                } catch (closeError: Exception) {
                    closeError.printStackTrace()
                }
                lmEngine = null
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
        try {
            lmConversation?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            lmEngine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            recreateLmConversation()
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
                recreateLmConversation()
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lmConversation = null
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
                recreateLmConversation(
                    systemInstruction = SVG_IMAGE_SYSTEM_INSTRUCTION,
                    enableConstrainedDecoding = true
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lmConversation = null
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
                recreateLmConversation(
                    systemInstruction = CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION,
                    enableConstrainedDecoding = true
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lmConversation = null
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
                recreateLmConversation(
                    systemInstruction = LOTTIE_ANIMATION_SYSTEM_INSTRUCTION,
                    enableConstrainedDecoding = true
                )
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lmConversation = null
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
        isGenerating.value = false
        if (lmConversation != null && llmPath.value.isNotBlank()) {
            lmConversation?.cancelProcess()
        }
        responseGenerationJob?.cancel()
        val lastIndex = _currentChatMessages.lastIndex
        if (lastIndex >= 0) {
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
            if (lmEngine != null) {
                recreateLmConversation()
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

    private fun resolveConstrainedDecoding(requested: Boolean): Boolean {
        return requested && !getPlatform().isIOS
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

        try {
            lmConversation?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lmConversation = null
        try {
            lmEngine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lmEngine = null
        clearActiveLmEngineState()

        val normalizedBackend = normalizeLmBackend(backend)
        val engine = LmEngine(
            modelPath = currentLlmPath,
            backend = normalizedBackend,
            visionBackend = lmVisionBackend.value,
            audioBackend = lmAudioBackend.value,
            maxNumTokens = lmMaxNumTokens.value,
            maxNumImages = lmMaxNumImages.value,
            cacheDir = FileKit.cacheDir.path ?: "",
            enableBenchmark = false,
            enableSpeculativeDecoding = enableSpeculativeDecoding.value,
            mainNpuNativeLibraryDir = "",
            visionNpuNativeLibraryDir = "",
            audioNpuNativeLibraryDir = "",
            mainBackendNumThreads = lmMainBackendNumThreads.value,
            audioBackendNumThreads = lmAudioBackendNumThreads.value
        )
        try {
            engine.initialize()
            lmEngine = engine
            activeModelPath = currentLlmPath
            activeBackend = normalizedBackend
            activeEnableSpeculativeDecoding = enableSpeculativeDecoding.value
            activeMaxNumTokens = lmMaxNumTokens.value
            recreateLmConversation()
            persistAppliedConversationContext()
            lmBackend.value = normalizedBackend
            return checkNotNull(lmConversation) {
                "Failed to recreate LiteRT LM conversation."
            }
        } catch (e: Throwable) {
            try {
                engine.close()
            } catch (closeError: Exception) {
                closeError.printStackTrace()
            }
            lmEngine = null
            lmConversation = null
            clearActiveLmEngineState()
            markConversationContextApplied(false)
            throw e
        }
    }

    private suspend fun recreateLmConversation(
        systemInstruction: String = currentSystemInstruction(),
        enableConstrainedDecoding: Boolean =
            _conversationContext.value.mode.isStructuredGenerationMode()
    ) {
        lmConversation?.close()
        lmConversation = lmEngine?.createConversation(
            systemInstruction = systemInstruction,
            toolsDescriptionJsonString = agentTools.getToolsDescriptionJson(),
            enableConversationConstrainedDecoding = resolveConstrainedDecoding(
                enableConstrainedDecoding
            ),
            samplerConfig = com.google.ai.edge.litertlm.SamplerConfig(
                temperature = temperature.value.toDouble(),
                topP = topP.value.toDouble(),
                topK = topK.value
            )
        )
        markConversationContextApplied(lmConversation != null)
    }
    
    @OptIn(ExperimentalTime::class)
    fun getTextTalkerResponse(query: String, onCancelled: () -> Unit, onError: (Throwable) -> Unit) {
        val activeConversation = lmConversation
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
            You are ${BuildConfig.APP_NAME}'s dedicated SVG image generator.

            Convert the user's visual request into a single self-contained SVG image. Respond only
            with a valid JSON object and do not wrap it in Markdown or add prose outside the JSON.

            Use this exact JSON structure:
            {
              "type": "svg_image",
              "svg": "<svg xmlns='http://www.w3.org/2000/svg' width='1024' height='1024' viewBox='0 0 1024 1024'>...</svg>"
            }

            Rules:
            - Use single quotes for every SVG/XML attribute inside the svg field. Do not output JSON-escaped
              double quote sequences like \" inside svg; users must be able to copy the svg field value directly.
            - Never append or repeat an opaque full-canvas element after foreground content. Before responding,
              verify that no later background rectangle, path, or group can cover the requested illustration.
        """.trimIndent()

        val CHIPTUNE_BGM_MML_SYSTEM_INSTRUCTION = """
            You are ${BuildConfig.APP_NAME}'s dedicated 8-bit chiptune BGM composer.

            Output exactly one raw valid JSON object containing a loopable 8-bit BGM score whose
            tracks are written in Music Macro Language. Do not use Markdown fences or add prose.
            Do not output WAV, PCM samples, base64 audio, MIDI, file paths, or scripts.

            Use this JSON structure exactly:
            {
              "type": "chiptune_bgm_mml",
              "schemaVersion": 1,
              "title": "BGM Title",
              "seed": 12345,
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
                  "mml": "T140 O5 L4 V12 C E G >C | <G E C D"
                },
                {
                  "channel": "pulse2",
                  "dutyCycle": 0.25,
                  "mml": "T140 O4 L4 V9 E G C G | F A C A"
                },
                {
                  "channel": "triangle",
                  "mml": "T140 O3 L4 C C G G | A A G G"
                },
                {
                  "channel": "noise",
                  "mml": "T140 L8 [K R H R S R H R]x2"
                }
              ]
            }

            Rules:
            - type must be "chiptune_bgm_mml" and schemaVersion must be 1.
            - bpm must be 60..200, timeSignature must be "4/4", and loopBars must be 2..16.
            - sampleRate must be 22050 unless the user asks for 11025 or 44100; bitDepth must be 8.
            - Use 1..4 unique tracks and prefer pulse1, pulse2, triangle, and noise.
            - pulse1 dutyCycle must be 0.5; pulse2 dutyCycle must be 0.25 or 0.125.
            - A track T value, when present, must equal the top-level bpm.
            - Melodic tracks may use O1..O7, L1/L2/L4/L8/L16/L32, V0..V15, A..G,
              # sharps, - flats, R/P rests, octave shifts < and >, |, and [ ... ]xN repeats.
            - The triangle track should use O2 or O3 for a clean bass line.
            - The noise track may use K, S, H, T, R, P, L8/L16, |, and [ ... ]xN repeats.
            - Compose exactly loopBars measures in 4/4 and make the phrase loop seamlessly.
            - Do not include comments, trailing commas, or text outside the JSON object.
        """.trimIndent()

        val LOTTIE_ANIMATION_SYSTEM_INSTRUCTION = """
            You are ${BuildConfig.APP_NAME}'s Lottie animation architect and motion designer.

            IMPORTANT OUTPUT CONTRACT
            - Output exactly ONE Native Lottie JSON object.

            SMALLEST WORKING EXAMPLE: ONE BREATHING CIRCLE
            This is the complete pattern to copy before attempting a complex idea. It has one shape layer, one
            circle, one fill, and one animated scale property. At frame 0 the circle is 90%, at frame 30 it is 100%,
            and at frame 60 it returns to 90%, so the two-second animation loops without a jump.
            {
              "v": "5.7.4",
              "fr": 30,
              "ip": 0,
              "op": 60,
              "w": 240,
              "h": 240,
              "nm": "Breathing Circle",
              "ddd": 0,
              "loop": true,
              "assets": [],
              "layers": [
                {
                  "ddd": 0,
                  "ind": 1,
                  "ty": 4,
                  "nm": "Circle Layer",
                  "sr": 1,
                  "ks": {
                    "o": { "a": 0, "k": 100 },
                    "r": { "a": 0, "k": 0 },
                    "p": { "a": 0, "k": [120, 120, 0] },
                    "a": { "a": 0, "k": [0, 0, 0] },
                    "s": { "a": 1, "k": [
                      { "t": 0, "s": [90, 90, 100], "e": [100, 100, 100] },
                      { "t": 30, "s": [100, 100, 100], "e": [90, 90, 100] },
                      { "t": 60, "s": [90, 90, 100], "e": [90, 90, 100] }
                    ] }
                  },
                  "ao": 0,
                  "shapes": [
                    {
                      "ty": "gr",
                      "nm": "Circle Group",
                      "it": [
                        {
                          "ty": "el",
                          "nm": "Circle Path",
                          "p": { "a": 0, "k": [0, 0] },
                          "s": { "a": 0, "k": [100, 100] },
                          "d": 1
                        },
                        {
                          "ty": "fl",
                          "nm": "Circle Fill",
                          "c": { "a": 0, "k": [0.12, 0.65, 0.95, 1] },
                          "o": { "a": 0, "k": 100 },
                          "r": 1
                        },
                        {
                          "ty": "tr",
                          "p": { "a": 0, "k": [0, 0] },
                          "a": { "a": 0, "k": [0, 0] },
                          "s": { "a": 0, "k": [100, 100] },
                          "r": { "a": 0, "k": 0 },
                          "o": { "a": 0, "k": 100 }
                        }
                      ]
                    }
                  ],
                  "ip": 0,
                  "op": 60,
                  "st": 0,
                  "bm": 0
                }
              ]
            }

            ROOT TIMELINE AND CANVAS VARIABLES
            - fr: frames per second. Use 30 for simple UI motion or 60 for very smooth motion.
            - ip: composition in-point, normally frame 0.
            - op: composition out-point. Total duration in seconds is (op - ip) / fr. Use op - ip <= 180 frames.
            - w and h: canvas dimensions in composition units. Use 240x240 by default; valid practical range is 64..512.
            - nm: human-readable animation title. It does not animate anything.
            - ddd: 3D switch. Always use 0; this animation is 2D.
            - loop: optional app metadata. Use true for a seamless activity loop and false for a one-shot event.
            - assets: always []. Do not reference images, precompositions, fonts, or external files.
            - layers: non-empty ordered array. Painter's order is important: background first, subject next, highlights last.

            LAYER VARIABLES
            - ddd: layer 3D switch; always 0.
            - ind: unique positive layer id. Do not reuse ids.
            - ty: layer type. Use 4 for a vector shape layer.
            - nm: layer name for humans.
            - sr: time stretch. Use 1.
            - ip/op: layer visible frame interval. Keep it inside the composition ip/op.
            - st: layer start offset. Normally 0.
            - bm: blend mode. Use 0 for normal blending.
            - ao: auto-orientation along a path. Use 0.
            - ks: the layer transform object. Its keys are o, r, p, a, and s.
              o is opacity in 0..100; r is rotation in degrees; p is absolute [x,y,z] position;
              a is the [x,y,z] anchor point; s is scale in percentages [x,y,z], not 0..1.
              A centered layer normally uses p=[w/2,h/2,0], a=[0,0,0], s=[100,100,100].

            SHAPE VARIABLES
            - ty="gr": a group. Its it array contains geometry, fill/stroke, and one final group transform.
            - ty="el": ellipse or circle. p is local center [x,y]; s is local [width,height].
            - ty="rc": rectangle. p is local center; s is [width,height]; r is corner radius.
            - ty="sh": custom path. ks.k contains v=vertices, i=in-tangents, o=out-tangents, c=closed boolean.
              Use few points and use a path only when an ellipse or rectangle is insufficient.
            - ty="fl": fill. c is normalized RGBA [red,green,blue,alpha], each 0..1; o is opacity 0..100.
              Example #1FA6F2 becomes approximately [0.12,0.65,0.95,1]. r=1 is the fill rule.
            - ty="st": stroke. c is normalized RGBA; o is opacity; w is width; lc is line cap (1 butt, 2 round,
              3 square); lj is line join (1 miter, 2 round, 3 bevel).
            - ty="tm": Trim Path. s is start percentage, e is end percentage, o is offset in degrees.
              For a draw-on effect keep s=0 and animate e from 0 to 100.
            - ty="tr": group transform. p is local position; a is anchor; s is percentage scale; r is degrees;
              o is opacity. Put tr after the visible items in it so it transforms the group.

            STATIC VALUE AND KEYFRAME VARIABLES
            - Every animatable property uses { "a": 0, "k": value } when static.
            - Use { "a": 1, "k": [ ... ] } when animated. The keyframe array must be chronological.
            - t is an absolute frame number, not milliseconds.
            - s is the value at the beginning of this keyframe segment.
            - e is the value at the end of this keyframe segment. Include e to make the intended interpolation explicit.
            - Scalar keyframes use one-element arrays such as [0] or [100]. Vector keyframes use [x,y] or [x,y,z].
            - h=1 means hold/step with no interpolation. Omit h or use h=0 for interpolation.
            - Optional i and o are Bezier easing handles. Do not confuse keyframe o with opacity or Trim Path offset.
            - Convert milliseconds to frames with frame = round(milliseconds * fr / 1000), then clamp to ip..op.

            CLEAR ANIMATION WORKFLOW
            1. Choose one main subject and one action: pulse, rotate, move, fade, draw, or reveal.
            2. Start with one layer and one primitive. Add a second layer only when it makes the action clearer.
            3. Build geometry first: place local shapes around [0,0], then place the layer with ks.p.
            4. Add style second: use one main color, one optional secondary color, and strong contrast.
            5. Add timing third: entrance/draw 0%..25%, main action 25%..75%, settle 75%..100%.
            6. Use 2..4 keyframes per animated property. Make motion deliberate, not random; do not animate every property.
            7. For a loop, the first and last visual values must match. For a one-shot, finish on a stable readable pose.
            8. Keep all content inside roughly 8%..92% of the canvas so strokes and scale overshoot are not clipped.
        """.trimIndent()
    }

    private fun ChatSessionMode.isStructuredGenerationMode(): Boolean {
        return this == ChatSessionMode.SVG_IMAGE ||
                this == ChatSessionMode.CHIPTUNE_BGM_MML ||
                this == ChatSessionMode.LOTTIE_ANIMATION
    }
}
