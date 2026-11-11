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
import com.juice.notify.ReminderScheduler
import com.juice.data.JuiceDatabase
import com.juice.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Parses a note with Groq in the background. The rules result always lands first. While Groq cannot be
 * reached (offline, 429, 5xx) it retries with backoff, but only while `runAttemptCount < MAX_ATTEMPTS`;
 * at the cap it succeeds, so an outage ends with the card keeping the rules parse instead of retrying forever.
 */
class IntakeWorker(
    context: Context,
    params: WorkerParameters,
    private val settings: SettingsRepository,
    private val groq: GroqClient,
    private val items: ItemRepository,
    private val clock: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now() },
) : CoroutineWorker(context, params) {
    /** The constructor WorkManager's default factory uses: the app's own settings, Groq client and database. */
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        SettingsRepository(context),
        GroqClient(BuildConfig.GROQ_API_KEY),
        ItemRepository(JuiceDatabase.open(context).itemDao(), reminders = ReminderScheduler(context, java.time.Clock.systemDefaultZone())),
    )

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        // The note text stays in Room; WorkManager's own database only ever holds the id.
        val text = items.get(itemId)?.title ?: return Result.success()
        val onDeviceOnly = settings.current().onDeviceOnly
        val result = IntakeParser(groq, onDeviceOnly = { onDeviceOnly }, clock = clock).parse(text)
        // A failed request still lands the local rules, so the card is not stuck as a Note meanwhile.
        items.land(itemId, result.item)
        return if (result.retryLater && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        /** Attempts allowed before a transient failure stops retrying and the card keeps the rules parse. */
        const val MAX_ATTEMPTS = 5

        private const val KEY_ITEM_ID = "itemId"

        /** True when a Groq key was built in; without one, intake stays on device. */
        val cloudAvailable: Boolean get() = BuildConfig.GROQ_API_KEY.isNotBlank()

        /** Queues a Groq parse for [itemId]. */
        fun enqueue(context: Context, itemId: String) {
            val request = OneTimeWorkRequestBuilder<IntakeWorker>()
                .setInputData(workDataOf(KEY_ITEM_ID to itemId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("intake-$itemId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
