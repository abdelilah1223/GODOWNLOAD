package com.abdelilah.godownloader.ui

import androidx.lifecycle.ViewModel
import com.abdelilah.godownloader.logic.DownloadManager
import com.abdelilah.godownloader.logic.DownloadTask
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadViewModel : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = DownloadManager.tasks

    fun addDownload(url: String, context: android.content.Context, startNow: Boolean = true) {
        DownloadManager.addDownload(url, startNow = startNow)
    }

    fun openFile(context: android.content.Context, task: DownloadTask) {
        DownloadManager.openFile(context, task)
    }

    fun pauseTask(id: String) {
        DownloadManager.pauseTask(id)
    }

    fun resumeTask(id: String) {
        DownloadManager.startTask(id)
    }

    fun deleteTask(id: String, removeFile: Boolean = false) {
        DownloadManager.deleteTask(id, removeFile)
    }

    fun startAll() {
        DownloadManager.startAll()
    }

    fun stopAll() {
        DownloadManager.stopAll()
    }

    fun onSettingsChanged() {
        DownloadManager.refreshConfig()
    }
}
