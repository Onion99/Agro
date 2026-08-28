package com.google.ai.edge.litertlm

internal expect object LiteRtLmJni {
    suspend fun getModelFilePath(): String

    // ========================================================================================
    //                              LiteRT LM (Text Generation) Methods
    // ========================================================================================
    
    fun loadLmEngine(
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
    ): Long

    fun createLmConversation(
        enginePointer: Long,
        samplerConfig: Any?,
        messageJsonString: String,
        toolsDescriptionJsonString: String,
        channelsJsonString: String?,
        extraContextJsonString: String,
        enableConversationConstrainedDecoding: Boolean,
        filterChannelContentFromKvCache: Boolean = false,
        prefillPrefaceOnInit: Boolean = false,
        overwritePromptTemplate: String? = null
    ): Long

    fun sendLmMessage(
        conversationPointer: Long,
        messageJsonString: String,
        extraContextJsonString: String
    ): String

    fun sendLmMessageAsync(
        conversationPointer: Long,
        messageJsonString: String,
        extraContextJsonString: String,
        onMessage: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Int, String) -> Unit,
        visualTokenBudget: Int? = null,
        maxOutputToken: Int = -1
    )

    fun getLmConversationTokenCount(conversationPointer: Long): Int
    fun getLmConversationBenchmarkInfo(conversationPointer: Long): BenchmarkInfo

    fun cancelLmConversation(conversationPointer: Long)
    fun deleteLmConversation(conversationPointer: Long)
    fun deleteLmEngine(enginePointer: Long)
}
