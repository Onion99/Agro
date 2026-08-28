package org.onion.agro.native.llm

import com.google.ai.edge.litertlm.LiteRtLmJni
import com.google.ai.edge.litertlm.LiteRtLmJniException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonArray

import com.google.ai.edge.litertlm.SamplerConfig

class LmEngine(
    val modelPath: String,
    val backend: String = "cpu",
    val visionBackend: String = "",
    val audioBackend: String = "",
    val maxNumTokens: Int = -1,
    val maxNumImages: Int = -1,
    val cacheDir: String = "",
    val mainBackendNumThreads: Int = 4,
    val audioBackendNumThreads: Int = 4,
    val enableBenchmark: Boolean = true,
    val enableSpeculativeDecoding: Boolean? = null,
    val mainNpuNativeLibraryDir: String = "",
    val visionNpuNativeLibraryDir: String = "",
    val audioNpuNativeLibraryDir: String = ""
) : AutoCloseable {

    private val mutex = Mutex()
    private var handle: Long? = null

    fun isInitialized(): Boolean = handle != null

    suspend fun initialize() {
        mutex.withLock {
            check(!isInitialized()) { "Engine is already initialized." }
            val nativeHandle = LiteRtLmJni.loadLmEngine(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                maxNumTokens = maxNumTokens,
                maxNumImages = maxNumImages,
                cacheDir = cacheDir,
                enableBenchmark = enableBenchmark,
                enableSpeculativeDecoding = enableSpeculativeDecoding,
                mainNpuNativeLibraryDir = mainNpuNativeLibraryDir,
                visionNpuNativeLibraryDir = visionNpuNativeLibraryDir,
                audioNpuNativeLibraryDir = audioNpuNativeLibraryDir,
                mainBackendNumThreads = mainBackendNumThreads,
                audioBackendNumThreads = audioBackendNumThreads
            )
            if (nativeHandle == 0L) {
                throw LiteRtLmJniException(
                    "Failed to initialize LiteRT LM engine for backend '$backend'."
                )
            }
            handle = nativeHandle
        }
    }

    override fun close() {
        handle?.let {
            LiteRtLmJni.deleteLmEngine(it)
            handle = null
        }
    }

    suspend fun createConversation(
        systemInstruction: String? = null,
        initialMessages: List<Message> = emptyList(),
        toolsDescriptionJsonString: String = "[]",
        strategy: ContextStrategy = ContextStrategy.ChatSession(),
        samplerConfig: SamplerConfig? = null
    ): LmConversation {
        mutex.withLock {
            checkInitialized()
            val messageJsonString = buildConversationMessageJsonString(
                systemInstruction = systemInstruction,
                initialMessages = initialMessages,
            )

            val ptr = LiteRtLmJni.createLmConversation(
                enginePointer = handle!!,
                samplerConfig = samplerConfig,
                messageJsonString = messageJsonString,
                toolsDescriptionJsonString = toolsDescriptionJsonString,
                channelsJsonString = null,
                extraContextJsonString = "{}",
                enableConversationConstrainedDecoding = strategy.enableConstrainedDecoding,
                filterChannelContentFromKvCache = strategy.filterChannelContent,
                prefillPrefaceOnInit = strategy.prefillPrefaceOnInit,
                overwritePromptTemplate = null
            )
            if (ptr == 0L) {
                throw LiteRtLmJniException("Failed to create LiteRT LM conversation.")
            }
            return LmConversation(
                handle = ptr,
                maxOutputTokens = strategy.maxOutputTokens,
            )
        }
    }

    private fun checkInitialized() {
        check(isInitialized()) { "Engine is not initialized." }
    }
}

internal fun buildConversationMessageJsonString(
    systemInstruction: String?,
    initialMessages: List<Message>,
): String {
    if (systemInstruction == null && initialMessages.isEmpty()) return "[]"

    return buildJsonArray {
        systemInstruction?.let {
            add(Message.system(it).toJson())
        }
        initialMessages.forEach {
            add(it.toJson())
        }
    }.sanitizeForLmLite().toString()
}
