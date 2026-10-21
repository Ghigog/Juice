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
import com.juice.data.ItemRepository
import com.juice.data.JuiceDatabase
import com.juice.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/** Parses a note with Groq in the background; retries with backoff while the request cannot reach Groq. */
class IntakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        val onDeviceOnly = SettingsRepository(applicationContext).current().onDeviceOnly
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val parser = IntakeParser(GroqClient(BuildConfig.GROQ_API_KEY), onDeviceOnly = { onDeviceOnly })
        val result = parser.parse(text)
        // A failed request still lands the local rules, so the card is not stuck as a Note meanwhile.
        ItemRepository(JuiceDatabase.open(applicationContext).itemDao()).land(itemId, result.item)
        return if (result.retryLater) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_ITEM_ID = "itemId"

        /** True when a Groq key was built in; without one, intake stays on device. */
        val cloudAvailable: Boolean get() = BuildConfig.GROQ_API_KEY.isNotBlank()

        /** Queues a Groq parse for [itemId]. */
        fun enqueue(context: Context, itemId: String, text: String) {
            val request = OneTimeWorkRequestBuilder<IntakeWorker>()
                .setInputData(workDataOf(KEY_TEXT to text, KEY_ITEM_ID to itemId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("intake-$itemId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
