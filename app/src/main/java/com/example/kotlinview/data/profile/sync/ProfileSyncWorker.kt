package com.example.kotlinview.data.profile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kotlinview.core.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            ServiceLocator.init(applicationContext)
            val repo = ServiceLocator.provideProfileRepository(applicationContext)
            repo.syncPendingNow()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
