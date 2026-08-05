package org.onion.agro.utils

import io.github.vinceglb.filekit.FileKit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object NativeLibraryLoader {

    init {
        FileKit.init("native_libs")
    }

    private val loadedLibraries = mutableSetOf<String>()
    private val extractedLibraries = mutableMapOf<String, File>()
    
    val tempDir: File by lazy {
        val dir = java.nio.file.Files.createTempDirectory("native_libs_").toFile()
        dir.deleteOnExit()
        dir
    }

    val tempDirectoryPath: String
        get() = tempDir.absolutePath

    @Synchronized
    fun loadLiteRtLmDesktopLibraries() {
        val plan = LiteRtLmDesktopLibraryPlan.current()
        plan.prepareOnly.forEach(::extractOptionalFromResources)
        plan.loadBeforeJni.forEach(::loadOptionalFromResources)
        loadFromResources("litertlm_jni")
    }

    @Synchronized
    fun extractFromResources(baseName: String): File {
        extractedLibraries[baseName]?.let { return it }

        val nativeLibrary = resolveNativeLibrary(baseName)
        println("Attempting to extract '${nativeLibrary.fileName}' from resources path: '${nativeLibrary.resourcePath}'")

        val libFileStream: InputStream = NativeLibraryLoader::class.java.getResourceAsStream(nativeLibrary.resourcePath)
            ?: throw UnsatisfiedLinkError(
                "Native library '${nativeLibrary.fileName}' not found in resources at path '${nativeLibrary.resourcePath}'. " +
                        "Ensure it's in 'src/jvmMain/resources/libs/'."
            )
        val libFileLibraryStream: InputStream? =
            NativeLibraryLoader::class.java.getResourceAsStream("${nativeLibrary.resourcePath}.a")

        val tempLibFile: File
        try {
            tempLibFile = File(tempDir, nativeLibrary.fileName)
            tempLibFile.deleteOnExit()
            println("tempFile Name  ${tempLibFile.absolutePath}")
            FileOutputStream(tempLibFile).use { outputStream ->
                libFileStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            if (libFileLibraryStream != null) {
                val tempLibLibraryFile = File(tempDir, "${nativeLibrary.fileName}.a")
                tempLibLibraryFile.deleteOnExit()
                FileOutputStream(tempLibLibraryFile).use { outputStream ->
                    libFileLibraryStream.use { input ->
                        input.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            throw UnsatisfiedLinkError("Failed to create temporary file for library '${nativeLibrary.fileName}': ${e.message}").initCause(e) as UnsatisfiedLinkError
        } finally {
            try {
                libFileStream.close()
                libFileLibraryStream?.close()
            } catch (e: Exception) {
                // Ignore cleanup failures.
            }
        }

        extractedLibraries[baseName] = tempLibFile
        return tempLibFile
    }

    @Synchronized // Ensure thread safety
    fun loadFromResources(baseName: String) {
        if (baseName in loadedLibraries) {
            println("Native library '$baseName' already loaded.")
            return
        }

        val osName = System.getProperty("os.name").lowercase()
        val tempLibFile = extractFromResources(baseName)

        try {
            System.load(tempLibFile.absolutePath)
            loadedLibraries.add(baseName)
            println("Successfully loaded native library '$baseName' ('${tempLibFile.name}') from temporary file: ${tempLibFile.absolutePath}")
        } catch (e: UnsatisfiedLinkError) {
            println("ERROR: Failed to load native library '$baseName' from ${tempLibFile.absolutePath}: ${e.message}")
            // Add more debug info if needed, e.g., if the DLL has other dependencies not found
            if (osName.contains("win") && e.message?.contains("Can't find dependent libraries") == true) {
                println("This error on Windows might indicate that '${tempLibFile.name}' has other DLL dependencies that are not in the system PATH or alongside the loaded DLL.")
            }
            throw e // Re-throw the original error
        }
    }

    private fun extractOptionalFromResources(baseName: String) {
        try {
            val file = extractFromResources(baseName)
            println("Prepared optional native library '$baseName' at: ${file.absolutePath}")
        } catch (e: UnsatisfiedLinkError) {
            println("Optional native library '$baseName' is not available for this platform: ${e.message}")
        }
    }

    private fun loadOptionalFromResources(baseName: String) {
        try {
            loadFromResources(baseName)
        } catch (e: UnsatisfiedLinkError) {
            println("Optional native library '$baseName' could not be loaded for this platform: ${e.message}")
        }
    }

    private fun resolveNativeLibrary(baseName: String): NativeLibraryResource {
        val osName = System.getProperty("os.name").lowercase()
        val libFileName = when {
            osName.contains("win") -> if (baseName == "dxcompiler" || baseName == "dxil") "$baseName.dll" else "lib$baseName.dll"
            osName.contains("mac") -> "lib$baseName.dylib"
            osName.contains("nix") || osName.contains("nux") -> "lib$baseName.so"
            else -> throw UnsatisfiedLinkError("Unsupported OS: $osName for library '$baseName'")
        }
        return NativeLibraryResource(
            fileName = libFileName,
            resourcePath = "/libs/$libFileName"
        )
    }

    private data class NativeLibraryResource(
        val fileName: String,
        val resourcePath: String
    )

    private data class LiteRtLmDesktopLibraryPlan(
        val prepareOnly: List<String>,
        val loadBeforeJni: List<String>
    ) {
        companion object {
            fun current(): LiteRtLmDesktopLibraryPlan {
                val osName = System.getProperty("os.name").lowercase()
                val arch = System.getProperty("os.arch").lowercase()
                return when {
                    osName.contains("win") && arch.isX64() -> LiteRtLmDesktopLibraryPlan(
                        prepareOnly = listOf(
                            "dxil",
                            "dxcompiler",
                            "webgpu_dawn",
                            "LiteRtWebGpuAccelerator",
                            "LiteRtTopKWebGpuSampler"
                        ),
                        loadBeforeJni = listOf(
                            "LiteRt",
                            "GemmaModelConstraintProvider"
                        )
                    )
                    osName.contains("linux") && (arch.isX64() || arch.isArm64()) -> LiteRtLmDesktopLibraryPlan(
                        prepareOnly = emptyList(),
                        loadBeforeJni = listOf(
                            "webgpu_dawn",
                            "LiteRt",
                            "LiteRtWebGpuAccelerator",
                            "LiteRtTopKWebGpuSampler",
                            "GemmaModelConstraintProvider"
                        )
                    )
                    osName.contains("mac") && arch.isArm64() -> LiteRtLmDesktopLibraryPlan(
                        prepareOnly = emptyList(),
                        loadBeforeJni = listOf(
                            "webgpu_dawn",
                            "LiteRt",
                            "LiteRtMetalAccelerator",
                            "LiteRtTopKMetalSampler",
                            "LiteRtWebGpuAccelerator",
                            "LiteRtTopKWebGpuSampler",
                            "GemmaModelConstraintProvider"
                        )
                    )
                    else -> LiteRtLmDesktopLibraryPlan(
                        prepareOnly = emptyList(),
                        loadBeforeJni = listOf("GemmaModelConstraintProvider")
                    )
                }
            }

            private fun String.isX64(): Boolean {
                return this == "x86_64" || this == "amd64"
            }

            private fun String.isArm64(): Boolean {
                return this == "aarch64" || this == "arm64"
            }
        }
    }
}
