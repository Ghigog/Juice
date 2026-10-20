package com.juice.intake

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.juice.BuildConfig
import java.util.concurrent.TimeUnit

/** Parses a note with Groq in the background; retries with backoff while the request cannot reach Groq. */
class IntakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        val parser = IntakeParser(GroqClient(BuildConfig.GROQ_API_KEY), onDeviceOnly = { false })
        return if (parser.parse(text).retryLater) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_TEXT = "text"

        /** Queues a parse for [itemId]. Does nothing when [onDeviceOnly] is set. */
        fun enqueue(context: Context, itemId: String, text: String, onDeviceOnly: Boolean) {
            if (onDeviceOnly) return
            val request = OneTimeWorkRequestBuilder<IntakeWorker>()
                .setInputData(workDataOf(KEY_TEXT to text))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("intake-$itemId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
