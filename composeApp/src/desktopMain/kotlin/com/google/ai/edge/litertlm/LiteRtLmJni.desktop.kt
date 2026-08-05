package com.google.ai.edge.litertlm

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import org.onion.agro.utils.NativeLibraryLoader

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString

interface Kernel32 : Library {
    fun SetDllDirectoryW(path: WString): Boolean
    fun AddDllDirectory(path: WString): com.sun.jna.Pointer?
    fun SetDefaultDllDirectories(directoryFlags: Int): Boolean

    companion object {
        val INSTANCE: Kernel32 by lazy {
            Native.load("kernel32", Kernel32::class.java)
        }
        const val LOAD_LIBRARY_SEARCH_DEFAULT_DIRS = 0x00001000
        const val LOAD_LIBRARY_SEARCH_USER_DIRS = 0x00000400
    }
}

internal actual object LiteRtLmJni {

    init {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("win")) {
            configureWindowsDllSearchPath()
        }
        NativeLibraryLoader.loadLiteRtLmDesktopLibraries()
    }

    private fun configureWindowsDllSearchPath() {
        try {
            val tempDir = NativeLibraryLoader.tempDirectoryPath
            println("Setting DLL directory to: $tempDir")
            val wTempDir = WString(tempDir)
            Kernel32.INSTANCE.SetDefaultDllDirectories(
                Kernel32.LOAD_LIBRARY_SEARCH_DEFAULT_DIRS or Kernel32.LOAD_LIBRARY_SEARCH_USER_DIRS
            )
            Kernel32.INSTANCE.AddDllDirectory(wTempDir)
            Kernel32.INSTANCE.SetDllDirectoryW(wTempDir)
        } catch (e: Exception) {
            println("Failed to set DLL directory via JNA: $e")
        }
    }

    actual suspend fun getModelFilePath(): String {
        return FileKit.openFilePicker()?.file?.absolutePath ?: ""
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
        return try {
            val ptr = nativeCreateEngine(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, enableBenchmark, enableSpeculativeDecoding, mainNpuNativeLibraryDir, visionNpuNativeLibraryDir, audioNpuNativeLibraryDir, mainBackendNumThreads, audioBackendNumThreads)
            if (ptr == 0L && backend.lowercase() != "cpu") {
                println("Warning: Engine creation returned 0 for backend '$backend'. Falling back to CPU backend...")
                nativeCreateEngine(modelPath, "cpu", visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, enableBenchmark, enableSpeculativeDecoding, mainNpuNativeLibraryDir, visionNpuNativeLibraryDir, audioNpuNativeLibraryDir, mainBackendNumThreads, audioBackendNumThreads)
            } else {
                ptr
            }
        } catch (e: Exception) {
            println("Warning: GPU/NPU environment initialization failed ($e). Falling back to CPU backend...")
            nativeCreateEngine(modelPath, "cpu", visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, enableBenchmark, enableSpeculativeDecoding, mainNpuNativeLibraryDir, visionNpuNativeLibraryDir, audioNpuNativeLibraryDir, mainBackendNumThreads, audioBackendNumThreads)
        }
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

    interface JniMessageCallback {
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
            callback = object : JniMessageCallback {
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
        callback: JniMessageCallback, visualTokenBudget: Int?, repetitionPenaltyConfig: Any?,
        noRepeatNgramConfig: Any?, suppressTokensConfig: Any?, maxOutputToken: Int,
        thinkingConfig: Any?, constraintType: Int, constraintString: String?
    )

    private external fun nativeConversationCancelProcess(conversationPointer: Long)
    private external fun nativeDeleteConversation(conversationPointer: Long)
    private external fun nativeDeleteEngine(enginePointer: Long)
}
