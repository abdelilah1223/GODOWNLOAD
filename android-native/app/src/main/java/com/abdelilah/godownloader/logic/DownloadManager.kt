package com.abdelilah.godownloader.logic

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class Status(val label: String) {
    Queued("Queued"),
    Downloading("Downloading"),
    Paused("Paused"),
    Completed("Completed"),
    Error("Error")
}

data class Part(
    val id: Int,
    val start: Long,
    val end: Long,
    var downloaded: Long = 0,
    var speed: Float = 0f,
    var status: Status = Status.Queued
)

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val filePath: String,
    var size: Long = 0,
    var downloaded: Long = 0,
    var progress: Float = 0f,
    var speed: Float = 0f,
    var eta: String = "",
    var status: Status = Status.Queued,
    val threads: Int = 8,
    val createdAt: Long = System.currentTimeMillis(),
    var parts: List<Part> = emptyList(),
    var errorMessage: String = ""
)

object DownloadManager {
    private var client = buildClient(Config())
    private var config = Config()
    private val gson = Gson()
    private lateinit var tasksFile: File

    private fun buildClient(config: Config): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .apply {
                if (config.proxyEnabled && config.proxyUrl.isNotEmpty()) {
                    try {
                        val url = java.net.URL(config.proxyUrl)
                        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(url.host, url.port.let { if (it == -1) 8080 else it }))
                        proxy(proxy)
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Failed to set proxy: ${e.message}")
                    }
                }
            }
            .build()
    }

    fun init(context: Context) {
        ConfigManager.init(context)
        tasksFile = File(context.filesDir, "tasks.json")
        refreshConfig()
        loadTasks()
    }

    fun refreshConfig() {
        config = ConfigManager.loadConfig()
        client = buildClient(config)
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = mutableMapOf<String, Job>()

    fun addDownload(url: String, threads: Int? = null, startNow: Boolean = true) {
        val fileName = url.substringAfterLast("/").substringBefore("?").ifEmpty { "download_${System.currentTimeMillis()}" }
        val targetFile = File(config.downloadDir, fileName)
        if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()
        
        val task = DownloadTask(
            url = url,
            fileName = fileName,
            filePath = targetFile.absolutePath,
            threads = threads ?: config.maxThreads,
            status = if (startNow) Status.Queued else Status.Paused
        )
        
        _tasks.value = _tasks.value + task
        saveTasks()
        if (startNow) startTask(task.id)
    }

    fun startTask(id: String) {
        if (runningJobs.containsKey(id)) return
        
        // Concurrency Check
        val activeCount = _tasks.value.count { it.status == Status.Downloading }
        if (activeCount >= config.maxConcurrentDownloads) {
            updateTask(id) { it.status = Status.Queued }
            return
        }

        val job = scope.launch {
            val task = _tasks.value.find { it.id == id } ?: return@launch
            updateTask(id) { it.status = Status.Downloading; it.errorMessage = "" }

            try {
                // 1. Get File Size
                if (task.size <= 0) {
                    val request = Request.Builder().url(task.url).head().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val contentLength = response.header("Content-Length")?.toLong() ?: 0L
                            updateTask(id) { it.size = contentLength }
                        }
                    }
                }

                val currentTask = _tasks.value.find { it.id == id } ?: return@launch
                val size = currentTask.size

                // 2. Create Parts if not exist
                if (currentTask.parts.isEmpty()) {
                    val parts = createParts(size, currentTask.threads)
                    updateTask(id) { it.parts = parts }
                }

                // 3. Prepare File
                val file = File(currentTask.filePath)
                if (!file.exists()) {
                    file.createNewFile()
                    if (size > 0) {
                        RandomAccessFile(file, "rw").use { raf -> raf.setLength(size) }
                    }
                }

                // 4. Start Part Workers
                val updatedTask = _tasks.value.find { it.id == id } ?: return@launch
                val partJobs = updatedTask.parts.mapIndexed { index, part ->
                    launch { downloadPart(id, index, updatedTask.url, updatedTask.filePath) }
                }

                // 5. Progress & Speed Monitor
                launch { monitorProgress(id) }

                partJobs.joinAll()
                
                // Final Check
                val finalTask = _tasks.value.find { it.id == id } ?: return@launch
                if (finalTask.status == Status.Downloading) {
                    updateTask(id) { 
                        it.status = Status.Completed
                        it.progress = 1.0f
                        it.eta = "Finished"
                        it.speed = 0f
                    }
                }

            } catch (e: Exception) {
                Log.e("DownloadManager", "Task failed: ${e.message}", e)
                updateTask(id) { it.status = Status.Error; it.errorMessage = e.message ?: "Unknown error" }
            } finally {
                runningJobs.remove(id)
                checkQueuedTasks()
            }
        }
        runningJobs[id] = job
    }

    private fun checkQueuedTasks() {
        val activeCount = _tasks.value.count { it.status == Status.Downloading }
        if (activeCount < config.maxConcurrentDownloads) {
            _tasks.value.find { it.status == Status.Queued }?.let { startTask(it.id) }
        }
    }

    private suspend fun downloadPart(taskId: String, partIdx: Int, url: String, filePath: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        val part = task.parts[partIdx]
        
        if (part.downloaded >= (part.end - part.start + 1) && part.end > 0) {
            updatePartStatus(taskId, partIdx, Status.Completed)
            return
        }

        updatePartStatus(taskId, partIdx, Status.Downloading)
        
        val currentStart = part.start + part.downloaded
        val request = Request.Builder()
            .url(url)
            .apply {
                if (part.end > 0) {
                    addHeader("Range", "bytes=$currentStart-${part.end}")
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val body = response.body ?: throw Exception("Empty body")
                val input = body.byteStream()
                val raf = RandomAccessFile(File(filePath), "rw")
                raf.seek(currentStart)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (true) {
                    yield() // Check for cancellation
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    
                    raf.write(buffer, 0, bytesRead)
                    updatePartDownloaded(taskId, partIdx, bytesRead.toLong())
                }
                raf.close()
                updatePartStatus(taskId, partIdx, Status.Completed)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                updatePartStatus(taskId, partIdx, Status.Paused)
            } else {
                updatePartStatus(taskId, partIdx, Status.Error)
                throw e
            }
        }
    }

    private suspend fun monitorProgress(taskId: String) {
        var lastDownloaded = 0L
        while (coroutineContext.isActive) {
            val task = _tasks.value.find { it.id == taskId } ?: break
            if (task.status != Status.Downloading) break

            val currentDownloaded = task.parts.sumOf { it.downloaded }
            val diff = currentDownloaded - lastDownloaded
            val speed = diff.toFloat() / (1024 * 1024) // MB/s
            
            var eta = ""
            if (task.size > 0 && speed > 0) {
                val remaining = task.size - currentDownloaded
                val seconds = (remaining.toFloat() / (speed * 1024 * 1024)).toLong()
                eta = formatEta(seconds)
            }

            updateTask(taskId) {
                it.downloaded = currentDownloaded
                it.speed = speed
                it.eta = eta
                if (it.size > 0) it.progress = currentDownloaded.toFloat() / it.size
            }
            
            lastDownloaded = currentDownloaded
            delay(1000)
        }
    }

    fun pauseTask(id: String) {
        runningJobs[id]?.cancel()
        runningJobs.remove(id)
        updateTask(id) { 
            it.status = Status.Paused
            it.speed = 0f
            it.eta = ""
            it.parts.forEach { p -> if (p.status == Status.Downloading) p.status = Status.Paused }
        }
    }

    fun deleteTask(id: String, removeFile: Boolean = false) {
        pauseTask(id)
        val task = _tasks.value.find { it.id == id }
        if (removeFile && task != null) {
            File(task.filePath).delete()
        }
        _tasks.value = _tasks.value.filter { it.id != id }
        saveTasks()
    }

    fun openFile(context: Context, task: DownloadTask) {
        val file = File(task.filePath)
        if (!file.exists()) return
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        
        val mime = context.contentResolver.getType(uri)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Failed to open file: ${e.message}")
        }
    }

    fun startAll() {
        _tasks.value.forEach { if (it.status == Status.Paused || it.status == Status.Error) startTask(it.id) }
    }

    fun stopAll() {
        _tasks.value.forEach { if (it.status == Status.Downloading) pauseTask(it.id) }
    }

    private fun updateTask(id: String, block: (DownloadTask) -> Unit) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) {
                val newTask = it.copy()
                block(newTask)
                newTask
            } else it
        }
        saveTasks()
    }

    private fun updatePartStatus(taskId: String, partIdx: Int, status: Status) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val newParts = task.parts.toMutableList()
                newParts[partIdx] = newParts[partIdx].copy(status = status)
                task.copy(parts = newParts)
            } else task
        }
    }

    private fun updatePartDownloaded(taskId: String, partIdx: Int, diff: Long) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val newParts = task.parts.toMutableList()
                newParts[partIdx] = newParts[partIdx].copy(downloaded = newParts[partIdx].downloaded + diff)
                task.copy(parts = newParts)
            } else task
        }
    }

    private fun createParts(size: Long, threads: Int): List<Part> {
        if (size <= 0) return listOf(Part(0, 0, 0))
        val partSize = size / threads
        return List(threads) { i ->
            val start = i * partSize
            val end = if (i == threads - 1) size - 1 else (i + 1) * partSize - 1
            Part(i, start, end)
        }
    }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun saveTasks() {
        try {
            val json = gson.toJson(_tasks.value)
            tasksFile.writeText(json)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Failed to save tasks: ${e.message}")
        }
    }

    private fun loadTasks() {
        if (!tasksFile.exists()) return
        try {
            val json = tasksFile.readText()
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            val loadedTasks: List<DownloadTask> = gson.fromJson(json, type)
            // Reset transient states
            loadedTasks.forEach { 
                it.speed = 0f
                it.eta = ""
                if (it.status == Status.Downloading) it.status = Status.Queued
            }
            _tasks.value = loadedTasks
            checkQueuedTasks()
        } catch (e: Exception) {
            Log.e("DownloadManager", "Failed to load tasks: ${e.message}")
        }
    }
}
