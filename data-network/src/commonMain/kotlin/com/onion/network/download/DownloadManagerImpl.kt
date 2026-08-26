package com.onion.network.download

import com.onion.network.http.PlatformFileWriter
import com.onion.network.http.PlatformFileUtil
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.HttpTimeout

class DownloadManagerImpl(
    private val httpClient: HttpClient,
    private val maxConcurrentDownloads: Int = 3
) : DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // In-memory tasks map
    private val tasks = mutableMapOf<String, DownloadTask>()
    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasksFlow: StateFlow<List<DownloadTask>> = _tasksFlow.asStateFlow()

    // Active download coroutine jobs
    private val activeJobs = mutableMapOf<String, Job>()
    private val queueMutex = Mutex()

    override fun download(url: String, filePath: String): String {
        val id = generateId(url, filePath)
        scope.launch {
            mutex.withLock {
                if (tasks.containsKey(id)) {
                    val existing = tasks[id]!!
                    if (existing.status == DownloadStatus.COMPLETED) {
                        val actualSize = PlatformFileUtil.getFileSize(filePath)
                        if (actualSize > 0) {
                            return@launch
                        }
                    }

                    if (existing.status == DownloadStatus.DOWNLOADING || existing.status == DownloadStatus.QUEUED) {
                        return@launch
                    }

                    tasks[id] = existing.copy(status = DownloadStatus.QUEUED, errorMessage = null)
                } else {
                    tasks[id] = DownloadTask(id = id, url = url, filePath = filePath, status = DownloadStatus.QUEUED)
                }
                _tasksFlow.value = tasks.values.toList()
            }
            checkQueue()
        }
        return id
    }

    override fun pause(id: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[id] ?: return@launch
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                    tasks[id] = task.copy(status = DownloadStatus.PAUSED)
                    _tasksFlow.value = tasks.values.toList()
                    activeJobs[id]?.cancel()
                }
            }
            checkQueue()
        }
    }

    override fun resume(id: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[id] ?: return@launch
                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELLED) {
                    tasks[id] = task.copy(status = DownloadStatus.QUEUED, errorMessage = null)
                    _tasksFlow.value = tasks.values.toList()
                }
            }
            checkQueue()
        }
    }

    override fun cancel(id: String) {
        scope.launch {
            mutex.withLock {
                val task = tasks[id] ?: return@launch
                if (task.status != DownloadStatus.COMPLETED) {
                    tasks[id] = task.copy(
                        status = DownloadStatus.CANCELLED,
                        progress = 0f,
                        downloadedBytes = 0,
                        speedBytesPerSecond = 0,
                        etaSeconds = -1,
                        errorMessage = null
                    )
                    _tasksFlow.value = tasks.values.toList()
                    activeJobs[id]?.cancel()
                    PlatformFileUtil.deleteFile(task.filePath)
                }
            }
            checkQueue()
        }
    }

    override fun remove(id: String, deleteFile: Boolean) {
        scope.launch {
            var filePathToDelete: String? = null
            mutex.withLock {
                val task = tasks[id] ?: return@launch
                activeJobs[id]?.cancel()
                activeJobs.remove(id)
                tasks.remove(id)
                _tasksFlow.value = tasks.values.toList()
                if (deleteFile) {
                    filePathToDelete = task.filePath
                }
            }
            filePathToDelete?.let {
                PlatformFileUtil.deleteFile(it)
            }
            checkQueue()
        }
    }

    override fun getTask(id: String): DownloadTask? {
        return tasks[id]
    }

    override fun getTaskFlow(id: String): Flow<DownloadTask?> {
        return tasksFlow.map { list -> list.firstOrNull { it.id == id } }
    }

    private fun generateId(url: String, filePath: String): String {
        val raw = "$url|$filePath"
        var hash = 0
        for (char in raw) {
            hash = 31 * hash + char.code
        }
        val positiveHash = if (hash == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(hash)
        return positiveHash.toString(16)
    }

    private fun checkQueue() {
        scope.launch {
            queueMutex.withLock {
                val currentActive = tasks.values.count { it.status == DownloadStatus.DOWNLOADING }
                if (currentActive >= maxConcurrentDownloads) return@launch

                val nextTask = tasks.values.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return@launch

                startDownloadJob(nextTask)
            }
        }
    }

    private fun startDownloadJob(task: DownloadTask) {
        val job = scope.launch(Dispatchers.Default) {
            executeDownload(task)
        }
        activeJobs[task.id] = job
    }

    private suspend fun updateTask(id: String, transformer: (DownloadTask) -> DownloadTask) {
        mutex.withLock {
            tasks[id]?.let { current ->
                val updated = transformer(current)
                tasks[id] = updated
                _tasksFlow.value = tasks.values.toList()
            }
        }
    }

    private suspend fun executeDownload(task: DownloadTask) {
        val id = task.id
        val url = task.url
        val filePath = task.filePath

        var existingSize = PlatformFileUtil.getFileSize(filePath)
        var useRange = existingSize > 0

        try {
            var responseHandled = false
            var retryWithoutRange = false

            suspend fun tryDownload() {
                val statement = httpClient.prepareGet(url) {
                    if (useRange && existingSize > 0) {
                        header(HttpHeaders.Range, "bytes=$existingSize-")
                    }
                    timeout {
                        requestTimeoutMillis = Long.MAX_VALUE
                        socketTimeoutMillis = Long.MAX_VALUE
                    }
                }

                statement.execute { response ->
                    val statusCode = response.status.value

                    if (statusCode == 416) {
                        retryWithoutRange = true
                        return@execute
                    }

                    if (statusCode !in 200..299) {
                        throw Exception("HTTP Error: $statusCode")
                    }

                    responseHandled = true
                    val isResuming = statusCode == 206
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                    val total = if (isResuming) {
                        if (contentLength != -1L) existingSize + contentLength else -1L
                    } else {
                        contentLength
                    }

                    var downloaded = if (isResuming) existingSize else 0L
                    updateTask(id) {
                        it.copy(
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            progress = if (total > 0) downloaded.toFloat() / total else 0f
                        )
                    }

                    val writer = PlatformFileWriter(filePath)
                    try {
                        if (isResuming) {
                            writer.seek(existingSize)
                        } else {
                            writer.seek(0)
                        }

                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(16384)
                        var lastUpdate = io.ktor.util.date.getTimeMillis()
                        var lastDownloaded = downloaded
                        var speed = 0L

                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read < 0) break
                            if (read > 0) {
                                writer.write(buffer, 0, read)
                                downloaded += read

                                val now = io.ktor.util.date.getTimeMillis()
                                val timeDiff = now - lastUpdate
                                if (timeDiff >= 800) {
                                    speed = ((downloaded - lastDownloaded) * 1000) / timeDiff
                                    lastUpdate = now
                                    lastDownloaded = downloaded
                                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                                    val remainingBytes = total - downloaded
                                    val eta = if (speed > 0 && remainingBytes > 0) remainingBytes / speed else -1L

                                    updateTask(id) {
                                        it.copy(
                                            status = DownloadStatus.DOWNLOADING,
                                            progress = progress,
                                            downloadedBytes = downloaded,
                                            totalBytes = total,
                                            speedBytesPerSecond = speed,
                                            etaSeconds = eta
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        writer.close()
                    }

                    updateTask(id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 1.0f,
                            downloadedBytes = downloaded,
                            totalBytes = downloaded,
                            speedBytesPerSecond = 0,
                            etaSeconds = 0,
                            errorMessage = null
                        )
                    }
                }
            }

            tryDownload()

            if (retryWithoutRange && !responseHandled) {
                PlatformFileUtil.deleteFile(filePath)
                existingSize = 0L
                useRange = false
                tryDownload()
            }

        } catch (e: CancellationException) {
            val currentTask = getTask(id)
            val nextStatus = if (currentTask?.status == DownloadStatus.PAUSED) {
                DownloadStatus.PAUSED
            } else {
                DownloadStatus.CANCELLED
            }
            updateTask(id) {
                it.copy(
                    status = nextStatus,
                    speedBytesPerSecond = 0,
                    etaSeconds = -1
                )
            }
            throw e
        } catch (e: Exception) {
            println("Download failed for task $id: ${e.message}")
            e.printStackTrace()
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    speedBytesPerSecond = 0,
                    etaSeconds = -1,
                    errorMessage = e.message ?: e.toString()
                )
            }
        } finally {
            activeJobs.remove(id)
            checkQueue()
        }
    }
}
