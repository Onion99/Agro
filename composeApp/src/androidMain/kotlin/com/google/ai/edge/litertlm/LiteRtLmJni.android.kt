package com.google.ai.edge.litertlm

import androidx.core.net.toUri
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.context
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import java.io.File
import java.io.FileOutputStream

internal actual object LiteRtLmJni {

    init {
        loadOptionalAndroidLibrary("webgpu_dawn")
        loadOptionalAndroidLibrary("LiteRtGpuAccelerator")
        loadOptionalAndroidLibrary("LiteRtOpenClAccelerator")
        loadOptionalAndroidLibrary("LiteRtWebGpuAccelerator")
        loadOptionalAndroidLibrary("LiteRtTopKOpenClSampler")
        loadOptionalAndroidLibrary("LiteRtTopKWebGpuSampler")
        System.loadLibrary("GemmaModelConstraintProvider")
        System.loadLibrary("litertlm_jni")
    }

    private fun loadOptionalAndroidLibrary(libraryName: String) {
        try {
            System.loadLibrary(libraryName)
        } catch (e: UnsatisfiedLinkError) {
            println("Optional Android native library '$libraryName' is not available: ${e.message}")
        }
    }

    actual suspend fun getModelFilePath(): String {
        val androidFile = FileKit.openFilePicker(type = FileKitType.File(listOf(
            "litertlm"
        )))
        androidFile ?: return ""
        val file = File(FileKit.context.filesDir, androidFile!!.name)
        if(file.exists()) return file.absolutePath
        FileKit.context.contentResolver.openInputStream((androidFile?.absolutePath() ?: return "").toUri()).use { inputStream ->
            FileOutputStream(File(FileKit.context.filesDir, androidFile.name)).use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
        }
        return File(FileKit.context.filesDir, androidFile.name).absolutePath
    }


    // ========================================================================================
    //                              LiteRT LM API Implementations
    // ========================================================================================
    
    actual fun loadLmEngine(
        modelPath: String, backend: String, visionBackend: String, audioBackend: String,
        maxNumTokens: Int, maxNumImages: Int, cacheDir: String, enableBenchmark: Boolean,
        enableSpeculativeDecoding: Boolean?, mainNpuNativeLibraryDir: String,
        visionNpuNativeLibraryDir: String, audioNpuNativeLibraryDir: String,
        mainBackendNumThreads: Int, audioBackendNumThreads: Int
    ): Long {
        return nativeCreateEngine(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, enableBenchmark, enableSpeculativeDecoding, mainNpuNativeLibraryDir, visionNpuNativeLibraryDir, audioNpuNativeLibraryDir, mainBackendNumThreads, audioBackendNumThreads)
    }

    actual fun createLmConversation(
        enginePointer: Long, samplerConfig: Any?, messageJsonString: String, toolsDescriptionJsonString: String,
        channelsJsonString: String?, extraContextJsonString: String, enableConversationConstrainedDecoding: Boolean,
        filterChannelContentFromKvCache: Boolean, overwritePromptTemplate: String?
    ): Long {
        return nativeCreateConversation(
            enginePointer = enginePointer,
            samplerConfig = samplerConfig,
            messageJsonString = messageJsonString,
            toolsDescriptionJsonString = toolsDescriptionJsonString,
            channelsJsonString = channelsJsonString,
            extraContextJsonString = extraContextJsonString,
            enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
            filterChannelContentFromKvCache = filterChannelContentFromKvCache,
            overwritePromptTemplate = overwritePromptTemplate,
            loraPath = null,
            audioLoraPath = null,
            prefillPrefaceOnInit = false,
            maxOutputToken = -1,
            thinkingConfig = null,
            enableResponseFormat = false
        )
    }

    actual fun sendLmMessage(
        conversationPointer: Long, messageJsonString: String, extraContextJsonString: String
    ): String {
        return nativeSendMessage(
            conversationPointer = conversationPointer,
            messageJsonString = messageJsonString,
            extraContextJsonString = extraContextJsonString,
            visualTokenBudget = null,
            repetitionPenaltyConfig = null,
            noRepeatNgramConfig = null,
            suppressTokensConfig = null,
            maxOutputToken = -1,
            thinkingConfig = null,
            constraintType = 0,
            constraintString = null
        )
    }

    interface LmMessageCallback {
        fun onMessage(messageJsonString: String)
        fun onDone()
        fun onError(statusCode: Int, message: String)
    }

    actual fun sendLmMessageAsync(
        conversationPointer: Long, messageJsonString: String, extraContextJsonString: String,
        onMessage: (String) -> Unit, onDone: () -> Unit, onError: (Int, String) -> Unit,
        visualTokenBudget: Int?
    ) {
        nativeSendMessageAsync(
            conversationPointer = conversationPointer,
            messageJsonString = messageJsonString,
            extraContextJsonString = extraContextJsonString,
            callback = object : LmMessageCallback {
                override fun onMessage(messageJsonString: String) = onMessage(messageJsonString)
                override fun onDone() = onDone()
                override fun onError(statusCode: Int, message: String) = onError(statusCode, message)
            },
            visualTokenBudget = visualTokenBudget,
            repetitionPenaltyConfig = null,
            noRepeatNgramConfig = null,
            suppressTokensConfig = null,
            maxOutputToken = -1,
            thinkingConfig = null,
            constraintType = 0,
            constraintString = null
        )
    }

    actual fun cancelLmConversation(conversationPointer: Long) {
        nativeConversationCancelProcess(conversationPointer)
    }

    actual fun deleteLmConversation(conversationPointer: Long) {
        nativeDeleteConversation(conversationPointer)
    }

    actual fun deleteLmEngine(enginePointer: Long) {
        nativeDeleteEngine(enginePointer)
    }

    private external fun nativeCreateEngine(
        modelPath: String, backend: String, visionBackend: String, audioBackend: String,
        maxNumTokens: Int, maxNumImages: Int, cacheDir: String, enableBenchmark: Boolean,
        enableSpeculativeDecoding: Boolean?, mainNpuNativeLibraryDir: String,
        visionNpuNativeLibraryDir: String, audioNpuNativeLibraryDir: String,
        mainBackendNumThreads: Int, audioBackendNumThreads: Int
    ): Long

    private external fun nativeCreateConversation(
        enginePointer: Long, samplerConfig: Any?, messageJsonString: String, toolsDescriptionJsonString: String,
        channelsJsonString: String?, extraContextJsonString: String, enableConversationConstrainedDecoding: Boolean,
        filterChannelContentFromKvCache: Boolean?, overwritePromptTemplate: String?,
        loraPath: String?, audioLoraPath: String?, prefillPrefaceOnInit: Boolean,
        maxOutputToken: Int, thinkingConfig: Any?, enableResponseFormat: Boolean
    ): Long

    private external fun nativeSendMessage(
        conversationPointer: Long, messageJsonString: String, extraContextJsonString: String,
        visualTokenBudget: Int?, repetitionPenaltyConfig: Any?, noRepeatNgramConfig: Any?,
        suppressTokensConfig: Any?, maxOutputToken: Int, thinkingConfig: Any?,
        constraintType: Int, constraintString: String?
    ): String

    private external fun nativeSendMessageAsync(
        conversationPointer: Long, messageJsonString: String, extraContextJsonString: String,
        callback: LmMessageCallback, visualTokenBudget: Int?, repetitionPenaltyConfig: Any?,
        noRepeatNgramConfig: Any?, suppressTokensConfig: Any?, maxOutputToken: Int,
        thinkingConfig: Any?, constraintType: Int, constraintString: String?
    )

    private external fun nativeConversationCancelProcess(conversationPointer: Long)
    private external fun nativeDeleteConversation(conversationPointer: Long)
    private external fun nativeDeleteEngine(enginePointer: Long)
}
