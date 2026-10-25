package fr.opencarbu.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import fr.opencarbu.domain.model.HistorySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * The optional daily refresh in the background. No service runs permanently: the system's job
 * scheduler wakes the app about once a day, at a moment of its choosing (so that several apps'
 * jobs share one wake-up), when the network condition holds and the battery is not low. The job
 * downloads the prices, keeps them as the cache the app opens with, and records the price changes
 * when the history is on. It takes a few seconds.
 */
class DailyRefreshJobService : JobService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            var retry = false
            try {
                refresh(applicationContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // stopped by the system, which already knows (onStopJob)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                retry = true // network trouble: let the system try again later
            }
            jobFinished(params, retry)
        }
        return true // work continues on another thread
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel() // the system wants its conditions back (e.g. Wi-Fi lost)
        return true // reschedule
    }

    companion object {
        private const val JOB_ID = 4201
        private const val PERIOD_MILLIS = 24L * 60 * 60 * 1000

        /** The job may run in the last six hours of each 24: more room for the system to batch wake-ups. */
        private const val FLEX_MILLIS = 6L * 60 * 60 * 1000

        /** Data younger than this is not downloaded again (the app was opened, or the job ran, recently). */
        private const val SKIP_IF_YOUNGER_MILLIS = 12L * 60 * 60 * 1000

        /** Makes the scheduled job match [settings]: scheduled, changed or cancelled. Cheap to call often. */
        fun apply(context: Context, settings: HistorySettings) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (!settings.backgroundRefresh) {
                scheduler.cancel(JOB_ID)
                return
            }
            val network = if (settings.backgroundWifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
            // Leave a job that already matches alone: re-scheduling it would restart its 24 h period.
            if (scheduler.getPendingJob(JOB_ID)?.networkType == network) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, DailyRefreshJobService::class.java))
                .setPeriodic(PERIOD_MILLIS, FLEX_MILLIS)
                .setRequiredNetworkType(network)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true) // survives a reboot (needs the RECEIVE_BOOT_COMPLETED permission)
                .build()
            scheduler.schedule(job)
        }

        private const val KEY_LAST_REFRESH = "background_last_refresh_at"

        /**
         * When the background job last ran to the end (prices downloaded, or already recent enough), null if
         * never: the Settings page shows it, so the user can tell whether the daily refresh really happens.
         */
        fun lastRefreshMillis(context: Context): Long? =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong(KEY_LAST_REFRESH, 0L).takeIf { it > 0L }

        /** Downloads the prices (unless recent) and records their changes. Blocking. */
        private fun refresh(context: Context) {
            val cacheFile = File(context.cacheDir, "stations-feed.json")
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val repository = StationRepository(
                cacheFile = cacheFile,
                downloader = OkHttpFeedDownloader(onTimed = { FeedTimingPreference.write(prefs, TimedFeed.PRICES, it) }),
                parser = MoshiFeedParser,
                clock = System::currentTimeMillis,
            )
            val recent = cacheFile.isFile && System.currentTimeMillis() - cacheFile.lastModified() < SKIP_IF_YOUNGER_MILLIS
            if (!recent) {
                val snapshot = repository.refresh()
                val settings = HistorySettingsPreference.read(prefs)
                if (settings.historyEnabled) {
                    PriceHistoryStore.get(context).recordFrom(snapshot.stations, settings.retentionCutoff(System.currentTimeMillis()))
                }
            }
            // Reached only when nothing failed (a failure throws before): the job did its work.
            prefs.edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
        }
    }
}
