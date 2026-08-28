package com.google.ai.edge.litertlm

import cnames.structs.LiteRtLmConversation
import cnames.structs.LiteRtLmConversationOptionalArgs
import cnames.structs.LiteRtLmEngine
import cnames.structs.LiteRtLmSessionConfig
import cnames.structs.LiteRtLmStreamChunk
import com.google.ai.edge.litertlm.cinterop.kLiteRtLmSamplerTypeTopP
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_cancel_process
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_enable_constrained_decoding
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_extra_context
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_filter_channel_content_from_kv_cache
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_messages
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_prompt_template
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_session_config
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_config_set_tools
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_optional_args_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_optional_args_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_optional_args_set_max_output_tokens
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_optional_args_set_visual_token_budget
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_send_message
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_send_message_stream
import com.google.ai.edge.litertlm.cinterop.litert_lm_conversation_get_token_count
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_enable_benchmark
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_set_cache_dir
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_set_enable_speculative_decoding
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_set_litert_dispatch_lib_dir
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_set_max_num_images
import com.google.ai.edge.litertlm.cinterop.litert_lm_engine_settings_set_max_num_tokens
import com.google.ai.edge.litertlm.cinterop.litert_lm_json_response_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_json_response_get_string
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_set_seed
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_set_temperature
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_set_top_k
import com.google.ai.edge.litertlm.cinterop.litert_lm_sampler_params_set_top_p
import com.google.ai.edge.litertlm.cinterop.litert_lm_session_config_create
import com.google.ai.edge.litertlm.cinterop.litert_lm_session_config_delete
import com.google.ai.edge.litertlm.cinterop.litert_lm_session_config_set_sampler_params
import com.google.ai.edge.litertlm.cinterop.litert_lm_stream_chunk_get_error
import com.google.ai.edge.litertlm.cinterop.litert_lm_stream_chunk_get_text
import com.google.ai.edge.litertlm.cinterop.litert_lm_stream_chunk_is_final
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong

@OptIn(ExperimentalForeignApi::class)
internal actual object LiteRtLmJni {
    private const val STREAM_ERROR_CODE = -1

    actual suspend fun getModelFilePath(): String {
        return FileKit.openFilePicker(
            type = FileKitType.File(listOf("litertlm"))
        )?.path ?: ""
    }

    actual fun loadLmEngine(
        modelPath: String,
        backend: String,
        visionBackend: String,
        audioBackend: String,
        maxNumTokens: Int,
        maxNumImages: Int,
        cacheDir: String,
        enableBenchmark: Boolean,
        enableSpeculativeDecoding: Boolean?,
        mainNpuNativeLibraryDir: String,
        visionNpuNativeLibraryDir: String,
        audioNpuNativeLibraryDir: String,
        mainBackendNumThreads: Int,
        audioBackendNumThreads: Int
    ): Long {
        require(modelPath.isNotBlank()) { "Model path must not be blank." }

        val requestedBackend = backend.ifBlank { "cpu" }
        return try {
            createEngine(
                modelPath = modelPath,
                backend = requestedBackend,
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                maxNumTokens = maxNumTokens,
                maxNumImages = maxNumImages,
                cacheDir = cacheDir,
                enableBenchmark = enableBenchmark,
                enableSpeculativeDecoding = enableSpeculativeDecoding,
                dispatchLibraryDir = firstNonBlank(
                    mainNpuNativeLibraryDir,
                    visionNpuNativeLibraryDir,
                    audioNpuNativeLibraryDir
                )
            )
        } catch (e: LiteRtLmJniException) {
            if (requestedBackend.equals("cpu", ignoreCase = true)) {
                throw e
            }
            createEngine(
                modelPath = modelPath,
                backend = "cpu",
                visionBackend = visionBackend,
                audioBackend = audioBackend,
                maxNumTokens = maxNumTokens,
                maxNumImages = maxNumImages,
                cacheDir = cacheDir,
                enableBenchmark = enableBenchmark,
                enableSpeculativeDecoding = enableSpeculativeDecoding,
                dispatchLibraryDir = null
            )
        }
    }

    actual fun createLmConversation(
        enginePointer: Long,
        samplerConfig: Any?,
        messageJsonString: String,
        toolsDescriptionJsonString: String,
        channelsJsonString: String?,
        extraContextJsonString: String,
        enableConversationConstrainedDecoding: Boolean,
        filterChannelContentFromKvCache: Boolean,
        prefillPrefaceOnInit: Boolean,
        overwritePromptTemplate: String?
    ): Long = memScoped {
        val engine = enginePointer.toNativePointer<LiteRtLmEngine>("LiteRT LM engine")
        val config = litert_lm_conversation_config_create()
            ?: throw LiteRtLmJniException("Failed to create LiteRT LM conversation config.")
        val sessionConfig = createSessionConfig(samplerConfig)
        try {
            if (sessionConfig != null) {
                litert_lm_conversation_config_set_session_config(config, sessionConfig)
            }
            litert_lm_conversation_config_set_messages(
                config,
                messageJsonString.ifBlank { "[]" }
            )
            litert_lm_conversation_config_set_tools(
                config,
                toolsDescriptionJsonString.ifBlank { "[]" }
            )
            litert_lm_conversation_config_set_extra_context(
                config,
                extraContextJsonString.ifBlank { "{}" }
            )
            litert_lm_conversation_config_set_enable_constrained_decoding(
                config,
                enableConversationConstrainedDecoding
            )
            litert_lm_conversation_config_set_filter_channel_content_from_kv_cache(
                config,
                filterChannelContentFromKvCache
            )
            overwritePromptTemplate?.takeIf { it.isNotBlank() }?.let { promptTemplate ->
                litert_lm_conversation_config_set_prompt_template(config, promptTemplate)
            }

            litert_lm_conversation_create(engine, config)
                .toHandle("LiteRT LM conversation")
        } finally {
            sessionConfig?.let { litert_lm_session_config_delete(it) }
            litert_lm_conversation_config_delete(config)
        }
    }

    actual fun sendLmMessage(
        conversationPointer: Long,
        messageJsonString: String,
        extraContextJsonString: String
    ): String = memScoped {
        val conversation = conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        val response = litert_lm_conversation_send_message(
            conversation,
            messageJsonString,
            extraContextJsonString.ifBlank { "{}" },
            null
        ) ?: throw LiteRtLmJniException("LiteRT LM message send failed.")
        try {
            litert_lm_json_response_get_string(response)?.toKString()
                ?: throw LiteRtLmJniException("LiteRT LM returned an empty response.")
        } finally {
            litert_lm_json_response_delete(response)
        }
    }

    actual fun sendLmMessageAsync(
        conversationPointer: Long,
        messageJsonString: String,
        extraContextJsonString: String,
        onMessage: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Int, String) -> Unit,
        visualTokenBudget: Int?,
        maxOutputToken: Int
    ) {
        val conversation = conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        val optionalArgs = createOptionalArgs(visualTokenBudget, maxOutputToken)
        val callbackState = StableRef.create(
            StreamCallbackState(
                onMessage = onMessage,
                onDone = onDone,
                onError = onError
            )
        )

        val status = try {
            memScoped {
                litert_lm_conversation_send_message_stream(
                    conversation,
                    messageJsonString,
                    extraContextJsonString.ifBlank { "{}" },
                    optionalArgs,
                    streamCallback,
                    callbackState.asCPointer()
                )
            }
        } finally {
            optionalArgs?.let { litert_lm_conversation_optional_args_delete(it) }
        }

        if (status != 0) {
            callbackState.dispose()
            onError(status, "LiteRT LM failed to start streaming response.")
        }
    }

    actual fun cancelLmConversation(conversationPointer: Long) {
        if (conversationPointer == 0L) return
        litert_lm_conversation_cancel_process(
            conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        )
    }

    actual fun deleteLmConversation(conversationPointer: Long) {
        if (conversationPointer == 0L) return
        litert_lm_conversation_delete(
            conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        )
    }

    actual fun deleteLmEngine(enginePointer: Long) {
        if (enginePointer == 0L) return
        litert_lm_engine_delete(enginePointer.toNativePointer<LiteRtLmEngine>("LiteRT LM engine"))
    }

    actual fun getLmConversationTokenCount(conversationPointer: Long): Int =
        litert_lm_conversation_get_token_count(
            conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        )

    actual fun getLmConversationBenchmarkInfo(conversationPointer: Long): BenchmarkInfo {
        if (conversationPointer == 0L) {
            return BenchmarkInfo(0.0, 0.0, 0, 0, 0.0, 0.0)
        }
        val conversation = conversationPointer.toNativePointer<LiteRtLmConversation>("LiteRT LM conversation")
        val info = litert_lm_conversation_get_benchmark_info(conversation)
            ?: return BenchmarkInfo(0.0, 0.0, 0, 0, 0.0, 0.0)
        return try {
            val initTime = litert_lm_benchmark_info_get_total_init_time_in_second(info)
            val ttft = litert_lm_benchmark_info_get_time_to_first_token(info)
            val numPrefill = litert_lm_benchmark_info_get_num_prefill_turns(info)
            val lastPrefillCount = if (numPrefill > 0) litert_lm_benchmark_info_get_prefill_token_count_at(info, numPrefill - 1) else 0
            val lastPrefillSpeed = if (numPrefill > 0) litert_lm_benchmark_info_get_prefill_tokens_per_sec_at(info, numPrefill - 1) else 0.0
            val numDecode = litert_lm_benchmark_info_get_num_decode_turns(info)
            val lastDecodeCount = if (numDecode > 0) litert_lm_benchmark_info_get_decode_token_count_at(info, numDecode - 1) else 0
            val lastDecodeSpeed = if (numDecode > 0) litert_lm_benchmark_info_get_decode_tokens_per_sec_at(info, numDecode - 1) else 0.0
            BenchmarkInfo(
                totalInitTimeMs = initTime * 1000.0,
                timeToFirstToken = ttft,
                lastPrefillTokenCount = lastPrefillCount,
                lastDecodeTokenCount = lastDecodeCount,
                lastPrefillTokensPerSecond = lastPrefillSpeed,
                lastDecodeTokensPerSecond = lastDecodeSpeed
            )
        } finally {
            litert_lm_benchmark_info_delete(info)
        }
    }

    private fun createEngine(
        modelPath: String,
        backend: String,
        visionBackend: String,
        audioBackend: String,
        maxNumTokens: Int,
        maxNumImages: Int,
        cacheDir: String,
        enableBenchmark: Boolean,
        enableSpeculativeDecoding: Boolean?,
        dispatchLibraryDir: String?
    ): Long = memScoped {
        val settings = litert_lm_engine_settings_create(
            modelPath,
            backend,
            visionBackend.takeIf { it.isNotBlank() },
            audioBackend.takeIf { it.isNotBlank() }
        ) ?: throw LiteRtLmJniException("Failed to create LiteRT LM engine settings for backend `$backend`.")

        try {
            if (maxNumTokens >= 0) {
                litert_lm_engine_settings_set_max_num_tokens(settings, maxNumTokens)
            }
            if (maxNumImages >= 0) {
                litert_lm_engine_settings_set_max_num_images(settings, maxNumImages)
            }
            if (cacheDir.isNotBlank()) {
                litert_lm_engine_settings_set_cache_dir(settings, cacheDir)
            }
            if (dispatchLibraryDir != null) {
                litert_lm_engine_settings_set_litert_dispatch_lib_dir(
                    settings,
                    dispatchLibraryDir
                )
            }
            if (enableBenchmark) {
                litert_lm_engine_settings_enable_benchmark(settings)
            }
            enableSpeculativeDecoding?.let {
                litert_lm_engine_settings_set_enable_speculative_decoding(settings, it)
            }

            litert_lm_engine_create(settings).toHandle("LiteRT LM engine")
        } finally {
            litert_lm_engine_settings_delete(settings)
        }
    }

    private fun createSessionConfig(samplerConfig: Any?): CPointer<LiteRtLmSessionConfig>? {
        val sampler = samplerConfig as? SamplerConfig ?: return null
        val sessionConfig = litert_lm_session_config_create()
            ?: throw LiteRtLmJniException("Failed to create LiteRT LM session config.")
        val samplerParams = litert_lm_sampler_params_create(kLiteRtLmSamplerTypeTopP)
            ?: run {
                litert_lm_session_config_delete(sessionConfig)
                throw LiteRtLmJniException("Failed to create LiteRT LM sampler parameters.")
            }
        try {
            litert_lm_sampler_params_set_top_k(samplerParams, sampler.topK)
            litert_lm_sampler_params_set_top_p(samplerParams, sampler.topP.toFloat())
            litert_lm_sampler_params_set_temperature(samplerParams, sampler.temperature.toFloat())
            litert_lm_sampler_params_set_seed(samplerParams, sampler.seed)
            litert_lm_session_config_set_sampler_params(sessionConfig, samplerParams)
            return sessionConfig
        } catch (throwable: Throwable) {
            litert_lm_session_config_delete(sessionConfig)
            throw throwable
        } finally {
            litert_lm_sampler_params_delete(samplerParams)
        }
    }

    private fun createOptionalArgs(
        visualTokenBudget: Int?,
        maxOutputToken: Int
    ): CPointer<LiteRtLmConversationOptionalArgs>? {
        if (visualTokenBudget == null && maxOutputToken <= 0) return null
        val optionalArgs = litert_lm_conversation_optional_args_create()
            ?: throw LiteRtLmJniException("Failed to create LiteRT LM conversation optional args.")
        visualTokenBudget?.let {
            litert_lm_conversation_optional_args_set_visual_token_budget(optionalArgs, it)
        }
        if (maxOutputToken > 0) {
            litert_lm_conversation_optional_args_set_max_output_tokens(optionalArgs, maxOutputToken)
        }
        return optionalArgs
    }

    private fun firstNonBlank(vararg values: String): String? {
        return values.firstOrNull { it.isNotBlank() }
    }

    private fun <T : CPointed> Long.toNativePointer(name: String): CPointer<T> {
        return takeIf { it != 0L }?.toCPointer()
            ?: throw LiteRtLmJniException("$name pointer is null.")
    }

    private fun <T : CPointed> CPointer<T>?.toHandle(name: String): Long {
        val handle = toLong()
        if (handle == 0L) {
            throw LiteRtLmJniException("Failed to create $name.")
        }
        return handle
    }

    private class StreamCallbackState(
        val onMessage: (String) -> Unit,
        val onDone: () -> Unit,
        val onError: (Int, String) -> Unit
    )

    private val streamCallback = staticCFunction {
            callbackData: COpaquePointer?,
            chunk: CPointer<LiteRtLmStreamChunk>? ->
        val ref = callbackData?.asStableRef<StreamCallbackState>() ?: return@staticCFunction
        val state = ref.get()
        try {
            val error = chunk?.let { litert_lm_stream_chunk_get_error(it)?.toKString() }
            val isFinal = chunk?.let { litert_lm_stream_chunk_is_final(it) } ?: true
            val text = chunk?.let { litert_lm_stream_chunk_get_text(it)?.toKString() }
            when {
                error != null -> state.onError(STREAM_ERROR_CODE, error)
                isFinal -> state.onDone()
                text != null -> state.onMessage(text)
            }
        } catch (throwable: Throwable) {
            runCatching {
                state.onError(
                    STREAM_ERROR_CODE,
                    throwable.message ?: "LiteRT LM stream callback failed."
                )
            }
        } finally {
            val isTerminal = chunk == null ||
                litert_lm_stream_chunk_is_final(chunk) ||
                litert_lm_stream_chunk_get_error(chunk) != null
            if (isTerminal) {
                ref.dispose()
            }
        }
    }
}
