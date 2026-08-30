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
import com.onion.model.LlmEngineStatus
import com.onion.model.LoraConfig
import com.onion.model.PersistentToolCall
import com.onion.model.PersistentToolResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.onion.agro.native.llm.LmInferenceGate
import org.onion.agro.native.llm.LmConversation
import org.onion.agro.native.llm.LmEngine
import org.onion.agro.native.llm.ContextBudgetLevel
import org.onion.agro.native.llm.ContextBudgetPolicy
import org.onion.agro.native.llm.ContextBudgetSnapshot
import org.onion.agro.native.llm.ContextCoordinator
import org.onion.agro.native.llm.ContextStrategy
import org.onion.agro.native.llm.GenerationOutputPolicy
import org.onion.agro.native.llm.contextStrategy
import org.onion.agro.native.llm.estimateTokenCount
import agro.composeapp.generated.resources.Res
import agro.composeapp.generated.resources.*
import kotlinx.coroutines.IO
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
import org.onion.agro.lottie.LottieSceneContract
import org.onion.agro.utils.getAppMemoryUsageMb

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

    suspend fun showToast(message: String) {
        _toastEvent.emit(message)
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
    private val _llmEngineStatus = MutableStateFlow(LlmEngineStatus.UNINITIALIZED)
    val llmEngineStatus: StateFlow<LlmEngineStatus> = _llmEngineStatus
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
    var temperature = mutableStateOf(1.0f)
    var topP = mutableStateOf(0.9f)
    var topK = mutableStateOf(70)
    var enableThinking = mutableStateOf(false)
    var enableSpeculativeDecoding = mutableStateOf(false)
    var enableBenchmark = mutableStateOf(false)
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
            enableBenchmark.value = false
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

    // ========================================================================================
    //                              Benchmark State & Actions
    // ========================================================================================
    private var benchmarkJob: Job? = null
    private var benchmarkConversation: LmConversation? = null
    private val lmInferenceGate = LmInferenceGate()
    private val _benchmarkUiState = MutableStateFlow(BenchmarkUiState())
    val benchmarkUiState: StateFlow<BenchmarkUiState> = _benchmarkUiState

    fun updateBenchmarkPrompt(prompt: String) {
        _benchmarkUiState.value = _benchmarkUiState.value.copy(testPrompt = prompt)
    }

    fun setEnableBenchmark(enabled: Boolean) {
        if (enableBenchmark.value == enabled) return
        enableBenchmark.value = enabled
        if (
            contextCoordinator.isEngineReady() &&
            !isGenerating.value &&
            !_benchmarkUiState.value.isRunning &&
            !isLlmModelLoading.value &&
            !isInitializing
        ) {
            applyConversationSettings()
        }
    }

    fun refreshHardwareStats() {
        val (usedRam, maxRam) = getAppMemoryUsageMb()
        _benchmarkUiState.value = _benchmarkUiState.value.copy(
            usedRamMb = usedRam,
            maxRamMb = maxRam,
            contextTokens = lmMaxNumTokens.value
        )
    }

    private var isFirstWarmup = true
    fun runBenchmarkTest(customPrompt: String? = null) {
        if (_benchmarkUiState.value.isRunning) return

        val currentLlmPath = llmPath.value
        if (currentLlmPath.isBlank()) {
            viewModelScope.launch {
                showToast(getString(Res.string.error_select_correct_llm_model))
            }
            return
        }

        if (isLlmModelLoading.value || _llmEngineStatus.value == LlmEngineStatus.INITIALIZING) {
            viewModelScope.launch {
                showToast(getString(Res.string.llm_benchmark_model_loading))
            }
            return
        }

        if (!contextCoordinator.isEngineReady() || _llmEngineStatus.value == LlmEngineStatus.UNINITIALIZED) {
            viewModelScope.launch {
                showToast(getString(Res.string.llm_benchmark_no_model))
            }
            return
        }

        if (isGenerating.value || _llmEngineStatus.value == LlmEngineStatus.GENERATING) {
            viewModelScope.launch {
                showToast(getString(Res.string.llm_benchmark_chat_busy))
            }
            return
        }

        val inferenceLease = lmInferenceGate.tryAcquire()
        if (inferenceLease == null) {
            viewModelScope.launch {
                showToast(getString(Res.string.llm_benchmark_chat_busy))
            }
            return
        }

        val currentEngine = contextCoordinator.currentEngine()
        if (currentEngine == null) {
            inferenceLease.release()
            viewModelScope.launch {
                showToast(getString(Res.string.llm_benchmark_no_model))
            }
            return
        }

        val promptContent = (customPrompt ?: _benchmarkUiState.value.testPrompt).ifBlank {
            "Explain the theory of relativity and its core principles concisely."
        }

        val (usedRam, maxRam) = getAppMemoryUsageMb()

        _benchmarkUiState.value = _benchmarkUiState.value.copy(
            isRunning = true,
            isWarmingUp = isFirstWarmup,
            errorMessage = null,
            liveOutputText = if(isFirstWarmup) "Warming up engine..." else "",
            contextTokens = lmMaxNumTokens.value,
            usedRamMb = usedRam,
            maxRamMb = maxRam
        )
        _llmEngineStatus.value = LlmEngineStatus.GENERATING

        benchmarkJob = viewModelScope.launch(Dispatchers.IO) {
            var activeSession: LmConversation? = null
            var wasCancelled = false
            try {
                // Ensure engine has enableBenchmark = true for litertlm.cc native metrics
                val benchmarkEngine = if (!currentEngine.enableBenchmark || activeEnableBenchmark != true) {
                    enableBenchmark.value = true
                    _benchmarkUiState.value = _benchmarkUiState.value.copy(
                        liveOutputText = "Preparing benchmark engine..."
                    )
                    contextCoordinator.closeAll()
                    clearActiveLmEngineState()
                    val newEngine = createLmEngine(currentLlmPath, lmBackend.value)
                    newEngine.initialize()
                    contextCoordinator.attachEngine(newEngine)
                    updateActiveLmEngineState(currentLlmPath, lmBackend.value)
                    newEngine
                } else {
                    currentEngine
                }

                if(isFirstWarmup){
                    // ==========================================
                    // PHASE 1: WARM-UP (预热跑测并抛弃结果)
                    // ==========================================
                    // 端侧推理在首次推理时存在 GPU Shaders/Vulkan 管线编译、内核 JIT、内存分页与线程池启动开销。
                    // 先行执行一段短预热会话并彻底丢弃其输出与耗时，消除首轮冷启动与第二轮热机之间的显著方差。
                    _benchmarkUiState.value = _benchmarkUiState.value.copy(
                        isWarmingUp = true,
                        liveOutputText = "Warming up compute pipeline & GPU kernels..."
                    )

                    val warmupSession = benchmarkEngine.createConversation(
                        systemInstruction = null,
                        initialMessages = emptyList(),
                        toolsDescriptionJsonString = "[]",
                        strategy = ContextStrategy.ChatSession(maxOutputTokens =256),
                        samplerConfig = null // Greedy decoding
                    )
                    activeSession = warmupSession
                    benchmarkConversation = warmupSession

                    // 收集预热流并丢弃输出
                    val warmupFlow = warmupSession.sendMessageAsync(
                        message = org.onion.agro.native.llm.Message.user(promptContent),
                        extraContext = emptyMap()
                    )
                    warmupFlow.collect {
                        // Discard warmup chunks
                    }

                    // 预热完成，安全关闭预热会话
                    try {
                        warmupSession.close()
                    } catch (e: Throwable) {
                        // ignore
                    }
                    activeSession = null
                    benchmarkConversation = null

                    // ==========================================
                    // PHASE 2: FORMAL BENCHMARK (正式基准测试)
                    // ==========================================
                    _benchmarkUiState.value = _benchmarkUiState.value.copy(
                        isWarmingUp = false,
                        liveOutputText = ""
                    )
                    isFirstWarmup = false
                }

                val session = benchmarkEngine.createConversation(
                    systemInstruction = null,
                    initialMessages = emptyList(),
                    toolsDescriptionJsonString = "[]",
                    strategy = ContextStrategy.ChatSession(maxOutputTokens = 256),
                    samplerConfig = null
                )
                activeSession = session
                benchmarkConversation = session

                val startTime = Clock.System.now().toEpochMilliseconds()
                var firstTokenTime: Long? = null
                var lastUiUpdateTime = 0L
                val stringBuilder = StringBuilder()

                val messageFlow = session.sendMessageAsync(
                    message = org.onion.agro.native.llm.Message.user(promptContent),
                    extraContext = emptyMap()
                )

                messageFlow.collect { message ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    val tokenTime = firstTokenTime ?: now.also { firstTokenTime = it }
                    val text = message.contents.toString()
                    if (text.isNotEmpty()) {
                        stringBuilder.append(text)
                    }

                    // Throttle UI updates (every 80ms) to avoid Compose recomposition storm
                    // which degrades CPU inference performance and causes timing jitter
                    if (now - lastUiUpdateTime >= 80L || firstTokenTime == now) {
                        lastUiUpdateTime = now
                        val currentText = stringBuilder.toString()
                        val estimatedTokens = estimateTokenCount(currentText)
                        val elapsedSinceFirstToken = (now - tokenTime).coerceAtLeast(1)
                        val liveTokensPerSec = if (estimatedTokens > 0 && elapsedSinceFirstToken > 0) {
                            (estimatedTokens.toDouble() / (elapsedSinceFirstToken.toDouble() / 1000.0))
                        } else 0.0

                        _benchmarkUiState.value = _benchmarkUiState.value.copy(
                            liveOutputText = currentText,
                            decodeTokensPerSecond = (liveTokensPerSec * 10).roundToInt() / 10.0,
                            latencyMs = -1,
                            decodeTokenCount = estimatedTokens
                        )
                    }
                }

                val finishTime = Clock.System.now().toEpochMilliseconds()
                val ttftDuration = ((firstTokenTime ?: finishTime) - startTime).coerceAtLeast(0)
                val generationDuration = (finishTime - (firstTokenTime ?: finishTime)).coerceAtLeast(1)
                val fullOutputText = stringBuilder.toString()
                val finalEstimatedTokens = estimateTokenCount(fullOutputText)

                // Query C++ native benchmark info from litertlm.cc (nativeConversationGetBenchmarkInfo)
                val nativeBenchmarkInfo = runCatching { session.getBenchmarkInfo() }.getOrNull()

                val finalDecodeTokens: Int
                val finalDecodeTokensPerSec: Double
                val finalPrefillTokens: Int
                val finalPrefillTokensPerSec: Double
                val finalLatencyMs: Long
                val initTime: Double

                if (nativeBenchmarkInfo != null && nativeBenchmarkInfo.lastDecodeTokensPerSecond > 0) {
                    // Authoritative C++ steady_clock hardware-level metrics from litertlm.cc
                    finalDecodeTokens = nativeBenchmarkInfo.lastDecodeTokenCount
                    finalDecodeTokensPerSec = nativeBenchmarkInfo.lastDecodeTokensPerSecond
                    finalPrefillTokens = nativeBenchmarkInfo.lastPrefillTokenCount
                    finalPrefillTokensPerSec = nativeBenchmarkInfo.lastPrefillTokensPerSecond
                    finalLatencyMs = (nativeBenchmarkInfo.timeToFirstToken * 1000).toLong()
                    initTime = if (nativeBenchmarkInfo.totalInitTimeMs < 100.0) {
                        nativeBenchmarkInfo.totalInitTimeMs * 1000.0
                    } else {
                        nativeBenchmarkInfo.totalInitTimeMs
                    }
                } else {
                    // Robust fallback: query session.tokenCount() (session_->GetCurrentStep())
                    val totalStepCount = runCatching { session.tokenCount() }.getOrDefault(0)
                    val promptTokens = estimateTokenCount(promptContent)
                    finalDecodeTokens = if (totalStepCount > 0) {
                        (totalStepCount - promptTokens).coerceAtLeast(finalEstimatedTokens)
                    } else {
                        finalEstimatedTokens
                    }
                    finalDecodeTokensPerSec = if (finalDecodeTokens > 0) {
                        finalDecodeTokens.toDouble() / (generationDuration.toDouble() / 1000.0)
                    } else 0.0
                    finalPrefillTokens = promptTokens
                    finalPrefillTokensPerSec = 0.0
                    finalLatencyMs = ttftDuration
                    initTime = 0.0
                }

                val (currentUsedRam, currentMaxRam) = getAppMemoryUsageMb()

                _benchmarkUiState.value = _benchmarkUiState.value.copy(
                    isRunning = false,
                    isWarmingUp = false,
                    hasCompletedTest = true,
                    decodeTokensPerSecond = (finalDecodeTokensPerSec * 10).roundToInt() / 10.0,
                    prefillTokensPerSecond = (finalPrefillTokensPerSec * 10).roundToInt() / 10.0,
                    latencyMs = finalLatencyMs,
                    prefillTokenCount = finalPrefillTokens,
                    decodeTokenCount = finalDecodeTokens,
                    initTimeMs = initTime,
                    liveOutputText = fullOutputText,
                    usedRamMb = currentUsedRam,
                    maxRamMb = currentMaxRam
                )
            } catch (e: CancellationException) {
                wasCancelled = true
                _benchmarkUiState.value = _benchmarkUiState.value.copy(
                    isRunning = false,
                    isWarmingUp = false,
                    errorMessage = "Benchmark cancelled."
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                _benchmarkUiState.value = _benchmarkUiState.value.copy(
                    isRunning = false,
                    isWarmingUp = false,
                    errorMessage = e.message ?: "Benchmark failed."
                )
            } finally {
                // If not cancelled, close the conversation cleanly here.
                // If cancelled, stopBenchmarkAndWait() safely owns closing the conversation
                // only AFTER activeBenchmarkJob.cancelAndJoin() has completed.
                if (!wasCancelled) {
                    try {
                        activeSession?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (benchmarkConversation === activeSession) {
                        benchmarkConversation = null
                    }
                }
                if (benchmarkJob === coroutineContext[Job]) {
                    benchmarkJob = null
                }
                if (_llmEngineStatus.value == LlmEngineStatus.GENERATING) {
                    _llmEngineStatus.value = if (
                        contextCoordinator.isEngineReady() &&
                        _conversationContext.value.isApplied
                    ) {
                        LlmEngineStatus.READY
                    } else {
                        LlmEngineStatus.UNINITIALIZED
                    }
                }
                inferenceLease.release()
            }
        }
    }

    private val benchmarkStopMutex = Mutex()

    fun cancelBenchmarkTest() {
        viewModelScope.launch {
            stopBenchmarkAndWait()
        }
    }

    private suspend fun stopBenchmarkAndWait() = benchmarkStopMutex.withLock {
        val activeBenchmarkJob = benchmarkJob
        val activeConversation = benchmarkConversation
        if (activeBenchmarkJob == null && activeConversation == null) {
            return@withLock
        }

        withContext(Dispatchers.Main) {
            if (_benchmarkUiState.value.isRunning) {
                _benchmarkUiState.value = _benchmarkUiState.value.copy(
                    isRunning = false,
                    isWarmingUp = false,
                    errorMessage = "Benchmark cancelled."
                )
            }
        }

        withContext(Dispatchers.IO) {
            // 1. Native cancel first
            try {
                activeConversation?.cancelProcess()
            } catch (e: Exception) {
                // ignore
            }

            // 2. Cancel and join the coroutine job so in-flight tasks and streaming finish cleanly
            try {
                activeBenchmarkJob?.cancelAndJoin()
            } catch (e: Exception) {
                // ignore
            }

            // 3. Safe barrier: only delete/close the native conversation after the job has completely joined!
            try {
                activeConversation?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (benchmarkJob === activeBenchmarkJob) {
            benchmarkJob = null
        }
        if (benchmarkConversation === activeConversation) {
            benchmarkConversation = null
        }
    }

    private val contextCoordinator = ContextCoordinator()
    private var activeBackend: String? = null
    private var activeEnableSpeculativeDecoding: Boolean? = null
    private var activeEnableBenchmark: Boolean? = null
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
            contextCoordinator.currentConversation() != null &&
            _conversationContext.value.isApplied &&
            llmPath.value == activeModelPath &&
            isSameLmBackend(activeBackend, lmBackend.value) &&
            activeEnableSpeculativeDecoding == enableSpeculativeDecoding.value &&
            activeEnableBenchmark == enableBenchmark.value &&
            activeMaxNumTokens == lmMaxNumTokens.value
        ) {
            _llmEngineStatus.value = LlmEngineStatus.READY
            return
        }
        if (llmPath.value.isBlank()) {
            _llmEngineStatus.value = LlmEngineStatus.UNINITIALIZED
            loadingModelState.value = 0
            return
        }
        isInitializing = true
        _llmEngineStatus.value = LlmEngineStatus.INITIALIZING
        viewModelScope.launch(Dispatchers.IO) {
            loadingModelState.emit(1)
            var initialized = false
            try {
                stopGenerationAndWait()
                _llmEngineStatus.value = LlmEngineStatus.INITIALIZING
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
                initialized = true
                _llmEngineStatus.value = LlmEngineStatus.READY
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeAll()
                clearActiveLmEngineState()
                markConversationContextApplied(false)
                _llmEngineStatus.value = LlmEngineStatus.ERROR
            } finally {
                isInitializing = false
                isLlmModelLoading.value = false
            }
            loadingModelState.emit(if (initialized) 2 else 0)
        }
    }

    fun applyConversationSettings() {
        val currentLlmPath = llmPath.value
        if (currentLlmPath.isBlank()) {
            _llmEngineStatus.value = LlmEngineStatus.UNINITIALIZED
            return
        }
        _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
        viewModelScope.launch(Dispatchers.IO) {
            isLlmModelLoading.value = true
            loadingModelState.emit(1)
            var applied = false
            try {
                stopGenerationAndWait()

                val needsEngineReinit = !contextCoordinator.isEngineReady() ||
                        activeModelPath != currentLlmPath ||
                        !isSameLmBackend(activeBackend, lmBackend.value) ||
                        activeEnableSpeculativeDecoding != enableSpeculativeDecoding.value ||
                        activeEnableBenchmark != enableBenchmark.value ||
                        activeMaxNumTokens != lmMaxNumTokens.value

                if (needsEngineReinit) {
                    _llmEngineStatus.value = LlmEngineStatus.INITIALIZING
                    contextCoordinator.closeAll()
                    clearActiveLmEngineState()
                    val engine = createLmEngine(currentLlmPath, lmBackend.value)
                    engine.initialize()
                    contextCoordinator.attachEngine(engine)
                    updateActiveLmEngineState(currentLlmPath, lmBackend.value)
                }
                _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
                val instruction = instructionForMode(_conversationContext.value.mode)
                selectConversationContext(
                    mode = _conversationContext.value.mode,
                    systemInstruction = instruction
                )
                val text = getString(Res.string.chat_system_parameters_applied)
                val sessionId = ensureActiveSession(text)
                chatHistoryRepository.clearSessionMessages(sessionId)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                }

                checkNotNull(recreateLmConversation(forceRecreate = true)) {
                    "Failed to apply the conversation context."
                }
                markConversationContextApplied(contextCoordinator.currentConversation() != null)
                persistAppliedConversationContext()
                val message = ChatMessage.text(text, role = ChatRole.SYSTEM)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.add(message)
                }
                chatHistoryRepository.saveMessage(sessionId, message)
                applied = true
                _llmEngineStatus.value = LlmEngineStatus.READY
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeAll()
                clearActiveLmEngineState()
                markConversationContextApplied(false)
                _llmEngineStatus.value = LlmEngineStatus.ERROR
                val text = getString(Res.string.chat_system_parameters_apply_failed, e.message ?: "")
                val sessionId = ensureActiveSession(text)
                chatHistoryRepository.clearSessionMessages(sessionId)
                val message = ChatMessage.text(text, role = ChatRole.SYSTEM)
                withContext(Dispatchers.Main) {
                    _currentChatMessages.clear()
                    _currentChatMessages.add(message)
                }
                chatHistoryRepository.saveMessage(sessionId, message)
            } finally {
                isLlmModelLoading.value = false
                loadingModelState.emit(if (applied) 2 else 0)
            }
        }
    }

    override fun onCleared() {
        try {
            benchmarkConversation?.cancelProcess()
        } catch (e: Exception) {
            // ignore
        }
        try {
            benchmarkConversation?.close()
        } catch (e: Exception) {
            // ignore
        }
        benchmarkConversation = null
        benchmarkJob?.cancel()
        benchmarkJob = null

        super.onCleared()
        contextCoordinator.closeAll()
    }

    private var responseGenerationJob: Job? = null
    private val generationStopMutex = Mutex()

    private data class CancelledGenerationCleanup(
        val sessionId: String?,
        val assistantMessage: ChatMessage?,
        val userMessage: ChatMessage?,
        val recreateConversation: Boolean,
    )

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
        _llmEngineStatus.value = if (contextCoordinator.isEngineReady()) {
            LlmEngineStatus.APPLYING_CONTEXT
        } else {
            LlmEngineStatus.UNINITIALIZED
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopGenerationAndWait()
                val session = chatHistoryRepository.getSession(sessionId) ?: run {
                    _llmEngineStatus.value = if (_conversationContext.value.isApplied) {
                        LlmEngineStatus.READY
                    } else {
                        LlmEngineStatus.UNINITIALIZED
                    }
                    return@launch
                }
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
                val conversation = recreateLmConversation(forceRecreate = true)
                persistAppliedConversationContext()
                _llmEngineStatus.value = if (conversation != null) {
                    LlmEngineStatus.READY
                } else {
                    LlmEngineStatus.UNINITIALIZED
                }
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                _llmEngineStatus.value = LlmEngineStatus.ERROR
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatHistoryRepository.renameSession(sessionId, title)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
        if (message.isBlank()) return
        val isConversationReady = _conversationContext.value.isApplied &&
            contextCoordinator.currentConversation() != null &&
            _llmEngineStatus.value == LlmEngineStatus.READY
        if (!isConversationReady) {
            viewModelScope.launch {
                showToast(getString(Res.string.chat_context_wait_until_ready))
            }
            return
        }
        val inferenceLease = lmInferenceGate.tryAcquire()
        if (inferenceLease == null) {
            viewModelScope.launch {
                showToast(getString(Res.string.chat_context_wait_until_ready))
            }
            return
        }
        _llmEngineStatus.value = LlmEngineStatus.GENERATING

        viewModelScope.launch {
            var inferenceStarted = false
            try {
                stopGenerationAndWait()
                val sessionId = ensureActiveSession(message)
                val messageDraft = ChatMessage.text(
                    text = message,
                    role = if (isUser) ChatRole.USER else ChatRole.ASSISTANT
                )
                val turnId = messageDraft.id.toString()
                val userMessage = messageDraft.copy(
                    metadata = mapOf(METADATA_TURN_ID to turnId)
                )
                _currentChatMessages.add(userMessage)
                chatHistoryRepository.saveMessage(sessionId, userMessage)
                val meta = mapOf(
                    METADATA_IS_GENERATING to "true",
                    METADATA_TURN_ID to turnId,
                )
                val assistantMessage = ChatMessage.text(
                    text = "",
                    role = ChatRole.ASSISTANT,
                    metadata = meta
                )
                _currentChatMessages.add(assistantMessage)
                chatHistoryRepository.saveMessage(sessionId, assistantMessage)
                isGenerating.value = true
                val generationJob = getTextTalkerResponse(message, {}, {
                    println(it.message)
                })
                if (generationJob != null) {
                    inferenceStarted = true
                    generationJob.invokeOnCompletion {
                        inferenceLease.release()
                    }
                }
            } finally {
                if (!inferenceStarted) {
                    isGenerating.value = false
                    if (_llmEngineStatus.value == LlmEngineStatus.GENERATING) {
                        _llmEngineStatus.value = if (
                            contextCoordinator.isEngineReady() &&
                            _conversationContext.value.isApplied
                        ) {
                            LlmEngineStatus.READY
                        } else {
                            LlmEngineStatus.UNINITIALIZED
                        }
                    }
                    inferenceLease.release()
                }
            }
        }

    }

    fun reGenerateMessage(message: ChatMessage) {
        val prompt = message.metadata?.get("prompt") ?: return
        val negativePrompt = message.metadata?.get("negative_prompt") ?: ""
    }

    fun startNewConversation() = startConversation(ChatSessionMode.DEFAULT)

    fun startSvgImageConversation() = startConversation(ChatSessionMode.SVG_IMAGE)

    fun startChiptuneBgmConversation() = startConversation(ChatSessionMode.CHIPTUNE_BGM_MML)

    fun startLottieAnimationConversation() = startConversation(ChatSessionMode.LOTTIE_ANIMATION)

    private fun startConversation(mode: ChatSessionMode) {
        _llmEngineStatus.value = if (contextCoordinator.isEngineReady()) {
            LlmEngineStatus.APPLYING_CONTEXT
        } else {
            LlmEngineStatus.UNINITIALIZED
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopGenerationAndWait()
                if (contextCoordinator.isEngineReady()) {
                    _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
                }
                val instruction = instructionForMode(mode)
                selectConversationContext(
                    mode = mode,
                    systemInstruction = instruction,
                )
                val newSessionId = when (mode) {
                    ChatSessionMode.DEFAULT -> chatHistoryRepository.createSession(
                        mode = mode,
                        systemInstruction = instruction,
                    )
                    ChatSessionMode.SVG_IMAGE -> chatHistoryRepository.createSession(
                        title = getString(Res.string.library_svg_image),
                        mode = mode,
                        systemInstruction = instruction,
                    )
                    ChatSessionMode.CHIPTUNE_BGM_MML -> chatHistoryRepository.createSession(
                        title = getString(Res.string.library_chiptune_bgm),
                        mode = mode,
                        systemInstruction = instruction,
                    )
                    ChatSessionMode.LOTTIE_ANIMATION -> chatHistoryRepository.createSession(
                        title = getString(Res.string.library_lottie_animation),
                        mode = mode,
                        systemInstruction = instruction,
                    )
                }
                withContext(Dispatchers.Main) {
                    activeSessionId.value = newSessionId
                    _currentChatMessages.clear()
                }
                val conversation = recreateLmConversation(
                    systemInstruction = instruction,
                    forceRecreate = true,
                )
                persistAppliedConversationContext()
                _llmEngineStatus.value = if (conversation != null) {
                    LlmEngineStatus.READY
                } else {
                    LlmEngineStatus.UNINITIALIZED
                }
            } catch (e: Exception) {
                e.printStackTrace()
                contextCoordinator.closeActiveConversation()
                markConversationContextApplied(false)
                _llmEngineStatus.value = LlmEngineStatus.ERROR
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

    /**
     * Requests cancellation without blocking the UI caller. Conversation
     * transitions use [stopGenerationAndWait] directly as a cancellation
     * barrier before replacing native resources.
     */
    fun stopGeneration() {
        viewModelScope.launch {
            stopGenerationAndWait()
        }
    }

    private suspend fun stopGenerationAndWait() {
        stopBenchmarkAndWait()
        generationStopMutex.withLock {
        val generationJob = responseGenerationJob
        val cleanup = withContext(Dispatchers.Main) {
            val shouldStop = isGenerating.value || generationJob?.isActive == true
            if (!shouldStop) return@withContext null
            val recreateConversation =
                _llmEngineStatus.value == LlmEngineStatus.GENERATING

            // Flip observable state before waiting so repeated stop requests
            // cannot clean up more than one turn.
            isGenerating.value = false
            isInferenceOn = false
            if (recreateConversation) {
                // Prevent another inference from starting after the generation
                // job releases its lease but before cancellation recovery ends.
                _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
            }

            val assistantIndex = _currentChatMessages.indexOfLast { message ->
                message.role == ChatRole.ASSISTANT &&
                    message.metadata?.get(METADATA_IS_GENERATING) == "true"
            }
            if (assistantIndex < 0) {
                return@withContext CancelledGenerationCleanup(
                    sessionId = activeSessionId.value,
                    assistantMessage = null,
                    userMessage = null,
                    recreateConversation = recreateConversation,
                )
            }

            val assistantMessage = _currentChatMessages.removeAt(assistantIndex)
            val turnId = assistantMessage.metadata?.get(METADATA_TURN_ID)
            val userIndex = _currentChatMessages.indexOfLast { message ->
                message.role == ChatRole.USER &&
                    turnId != null &&
                    message.metadata?.get(METADATA_TURN_ID) == turnId
            }
            val userMessage = if (userIndex >= 0) {
                _currentChatMessages.removeAt(userIndex)
            } else {
                null
            }
            CancelledGenerationCleanup(
                sessionId = activeSessionId.value,
                assistantMessage = assistantMessage,
                userMessage = userMessage,
                recreateConversation = recreateConversation,
            )
        } ?: return@withLock

        withContext(Dispatchers.IO) {
            contextCoordinator.cancelActive()
            generationJob?.cancelAndJoin()
        }
        if (responseGenerationJob === generationJob) {
            responseGenerationJob = null
        }

        withContext(Dispatchers.IO) {
            val sessionId = cleanup.sessionId ?: return@withContext
            cleanup.assistantMessage?.let { message ->
                chatHistoryRepository.deleteMessage(sessionId, message.id)
            }
            cleanup.userMessage?.let { message ->
                chatHistoryRepository.deleteMessage(sessionId, message.id)
            }
        }

        if (cleanup.recreateConversation) {
            recreateConversationAfterCancellation()
        }
        }
    }

    private suspend fun recreateConversationAfterCancellation() {
        withContext(Dispatchers.Main) {
            markConversationContextApplied(false)
            _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
        }

        try {
            val conversation = withContext(Dispatchers.IO) {
                recreateLmConversation(forceRecreate = true)
            }
            withContext(Dispatchers.Main) {
                _llmEngineStatus.value = if (conversation != null) {
                    LlmEngineStatus.READY
                } else {
                    LlmEngineStatus.UNINITIALIZED
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.IO) {
                contextCoordinator.closeActiveConversation()
            }
            withContext(Dispatchers.Main) {
                markConversationContextApplied(false)
                _llmEngineStatus.value = LlmEngineStatus.ERROR
            }
        }
    }

    private fun observeChatSessions(query: String = historySearchQuery.value) {
        sessionCollectionJob?.cancel()
        sessionCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            chatHistoryRepository.observeSessions(query).collectLatest { sessions ->
                withContext(Dispatchers.Main) {
                    _chatSessions.clear()
                    _chatSessions.addAll(sessions)
                }
            }
        }
    }

    private fun restoreMostRecentSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = chatHistoryRepository.getMostRecentSession() ?: return@launch
            if (contextCoordinator.isEngineReady()) {
                _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
            }
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
                val conversation = recreateLmConversation(forceRecreate = true)
                persistAppliedConversationContext()
                _llmEngineStatus.value = if (conversation != null) {
                    LlmEngineStatus.READY
                } else {
                    LlmEngineStatus.ERROR
                }
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

    private fun samplerConfigForMode(mode: ChatSessionMode): SamplerConfig {
        if (mode != ChatSessionMode.LOTTIE_ANIMATION) {
            return SamplerConfig(
                temperature = temperature.value.toDouble(),
                topP = topP.value.toDouble(),
                topK = topK.value,
            )
        }
        return SamplerConfig(
            temperature = temperature.value.coerceIn(0f, 1.0f).toDouble(),
            topP = topP.value.coerceIn(0.1f, 0.9f).toDouble(),
            topK = topK.value.coerceIn(1, 70),
        )
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
        val strategy = mode.contextStrategy()
        val conversation = if (strategy is ContextStrategy.StructuredGeneration) {
            // Structured output is request-scoped. Never let a previous JSON
            // response become the prefix of the next generation.
            contextCoordinator.openConversation(
                key = activeSessionId.value ?: "pending:${mode.name}",
                mode = mode,
                systemInstruction = currentSystemInstruction(),
                toolsJson = agentTools.getToolsDescriptionJson(),
                initialMessages = emptyList(),
                samplerConfig = samplerConfigForMode(mode),
                forceRecreate = true,
            )
        } else {
            checkNotNull(contextCoordinator.currentConversation()) {
                "LM conversation is not initialized."
            }
        }
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
            initialMessages = if (strategy is ContextStrategy.StructuredGeneration) {
                emptyList()
            } else {
                ContextCoordinator.compact(
                    messages = _currentChatMessages.toList(),
                    retainTurns = (strategy as ContextStrategy.ChatSession).historyRetainWindow,
                )
            },
            samplerConfig = samplerConfigForMode(mode),
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
        activeEnableBenchmark = null
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
            enableBenchmark = enableBenchmark.value,
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
        activeEnableBenchmark = enableBenchmark.value
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

        _llmEngineStatus.value = LlmEngineStatus.INITIALIZING
        contextCoordinator.closeAll()
        clearActiveLmEngineState()

        val normalizedBackend = normalizeLmBackend(backend)
        val engine = createLmEngine(currentLlmPath, normalizedBackend)
        try {
            engine.initialize()
            contextCoordinator.attachEngine(engine)
            updateActiveLmEngineState(currentLlmPath, normalizedBackend)
            _llmEngineStatus.value = LlmEngineStatus.APPLYING_CONTEXT
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
            _llmEngineStatus.value = LlmEngineStatus.ERROR
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
            initialMessages = ContextCoordinator.initialMessages(
                mode = mode,
                messages = _currentChatMessages.toList(),
            ),
            samplerConfig = samplerConfigForMode(mode),
            forceRecreate = forceRecreate,
        )
        markConversationContextApplied(true)
        return conversation
    }
    
    @OptIn(ExperimentalTime::class)
    fun getTextTalkerResponse(
        query: String,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit,
    ): Job? {
        val activeConversation = contextCoordinator.currentConversation()
        if (activeConversation == null) {
            isGenerating.value = false
            isInferenceOn = false
            markConversationContextApplied(false)
            _llmEngineStatus.value = LlmEngineStatus.ERROR
            viewModelScope.launch {
                if (_currentChatMessages.isNotEmpty()) {
                    val lastIdx = _currentChatMessages.lastIndex
                    val meta = _currentChatMessages[lastIdx].metadata.orEmpty() + mapOf(
                        METADATA_IS_GENERATING to "false",
                        METADATA_EXCLUDE_FROM_CONTEXT to "true",
                    )
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
            return null
        }
        
        val generationJob = viewModelScope.launch(Dispatchers.IO) {
            isInferenceOn = true
            _llmEngineStatus.value = LlmEngineStatus.GENERATING
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
                    val meta = msgToUpdate.metadata.orEmpty() +
                        (METADATA_IS_GENERATING to isGeneratingNow.toString())
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
                    val currentMessage = _currentChatMessages[messageIndex]
                    val meta = currentMessage.metadata.orEmpty() + mapOf(
                        METADATA_IS_GENERATING to "false",
                        METADATA_EXCLUDE_FROM_CONTEXT to "true",
                    )
                    val errMsg = error.message.orEmpty()
                    val displayMsg = if (isTokenLimitErrorMessage(errMsg)) {
                        getString(Res.string.chat_system_error_token_limit_exceeded)
                    } else {
                        getString(
                            Res.string.chat_system_error_prefix,
                            errMsg.ifEmpty { getString(Res.string.unknown_error) }
                        )
                    }
                    val updated = currentMessage.copy(
                        contents = listOf(ChatMessageContent.Text(displayMsg)),
                        metadata = meta
                    )
                    _currentChatMessages[messageIndex] = updated
                    sessionId?.let { chatHistoryRepository.saveMessage(it, updated) }
                }
                val recovered = runCatching {
                    recreateLmConversation(forceRecreate = true)
                }.getOrNull() != null
                _llmEngineStatus.value = if (recovered) {
                    LlmEngineStatus.READY
                } else {
                    markConversationContextApplied(false)
                    LlmEngineStatus.ERROR
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
                                val toolArguments = event.toolCall.arguments
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
                                            argumentsJson = toolArguments.toString(),
                                            responseJson = "",
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
                                val result = event.result.toJson()
                                val toolArguments = event.toolCall.arguments
                                val toolStatus = if (event.result.success) "completed" else "failed"

                                persistentToolResponses.add(
                                    PersistentToolResponse(
                                        name = event.toolCall.name,
                                        response = result,
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
                                            argumentsJson = toolArguments.toString(),
                                            responseJson = result.toString(),
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
                        if (_llmEngineStatus.value == LlmEngineStatus.GENERATING) {
                            _llmEngineStatus.value = if (_conversationContext.value.isApplied) {
                                LlmEngineStatus.READY
                            } else {
                                LlmEngineStatus.UNINITIALIZED
                            }
                        }
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
                        _llmEngineStatus.value = LlmEngineStatus.GENERATING
                        updateAssistantMessage(isGeneratingNow = true)
                        continue
                    }

                    showGenerationError(e)
                    onError(e)
                    return@launch
                }
            }

            if (!GenerationOutputPolicy.hasUsableContent(generatedResult)) {
                val emptyResponseError = IllegalStateException(
                    getString(Res.string.chat_system_empty_response)
                )
                isGenerating.value = false
                isInferenceOn = false
                showGenerationError(emptyResponseError)
                onError(emptyResponseError)
                return@launch
            }

            val generationDuration = Clock.System.now().toEpochMilliseconds() - startTime
            val messageIndex = _currentChatMessages.indexOfFirst {
                it.id.toString() == assistantMessageId
            }
            if (messageIndex >= 0) {
                val currentMessage = _currentChatMessages[messageIndex]
                val meta = currentMessage.metadata.orEmpty().toMutableMap().apply {
                    put("prompt", promptContent)
                    put("time_taken", formatDuration(generationDuration))
                    put(METADATA_IS_GENERATING, "false")
                    put("agent_turn_count", terminalTurnCount.toString())
                    put("agent_transition", terminalTransition)
                    put("lm_backend", attemptedBackend)
                }
                if (retriedOnCpuAfterGpuFailure) {
                    meta["gpu_decode_cpu_retry"] = "true"
                }
                val finalContents = try {
                    when (sessionMode) {
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
                } catch (e: Throwable) {
                    isGenerating.value = false
                    isInferenceOn = false
                    showGenerationError(e)
                    onError(e)
                    return@launch
                }
                val updated = currentMessage.copy(
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
            _llmEngineStatus.value = LlmEngineStatus.READY
        }
        responseGenerationJob = generationJob
        return generationJob
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
            restoreMostRecentSession()
        }
        observeChatSessions()
    }

    private companion object {
        const val ABSEIL_STATUS_INTERNAL = 13
        const val LM_BACKEND_CPU = "CPU"
        const val LM_BACKEND_GPU = "GPU"
        const val DEFAULT_LM_MAX_NUM_TOKENS = 8192
        const val MIN_LM_MAX_NUM_TOKENS = 128
        const val LM_MAX_NUM_TOKENS_STEP = 128
        const val METADATA_IS_GENERATING = "is_generating"
        const val METADATA_TURN_ID = "turn_id"
        const val METADATA_EXCLUDE_FROM_CONTEXT = "exclude_from_context"

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

        val LOTTIE_ANIMATION_SYSTEM_INSTRUCTION =
            LottieSceneContract.systemInstruction(BuildConfig.APP_NAME)
    }

    private fun ChatSessionMode.isStructuredGenerationMode(): Boolean {
        return this == ChatSessionMode.SVG_IMAGE ||
                this == ChatSessionMode.CHIPTUNE_BGM_MML ||
                this == ChatSessionMode.LOTTIE_ANIMATION
    }
}

data class BenchmarkUiState(
    val isRunning: Boolean = false,
    val isWarmingUp: Boolean = false,
    val decodeTokensPerSecond: Double = 0.0,
    val prefillTokensPerSecond: Double = 0.0,
    val latencyMs: Long = 0,
    val contextTokens: Int = 0,
    val prefillTokenCount: Int = 0,
    val decodeTokenCount: Int = 0,
    val initTimeMs: Double = 0.0,
    val liveOutputText: String = "",
    val errorMessage: String? = null,
    val hasCompletedTest: Boolean = false,
    val testPrompt: String = "Explain the theory of relativity and its core principles concisely.",
    val usedRamMb: Long = 0,
    val maxRamMb: Long = 0,
)

