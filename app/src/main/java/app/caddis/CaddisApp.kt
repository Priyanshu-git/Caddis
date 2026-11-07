package app.caddis

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.caddis.data.LibraryRepository
import app.caddis.notify.Notifier
import app.caddis.work.WorkScheduler
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class CaddisApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var repo: LibraryRepository
    @Inject lateinit var work: WorkScheduler
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var http: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        work.scheduleReminders()
        appScope.launch {
            repo.seedDefaultsIfNeeded()
            repo.purgeExpiredTrash()
            // Fallback when Android delayed background work: retry missing details on open.
            repo.scheduleMissingDetails()
        }
    }

    /** MET-04: thumbnails only, cached small and capped at 200 MB; clearable from Your data. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(THUMB_CACHE_DIR))
                    .maxSizeBytes(THUMB_CACHE_BYTES)
                    .build()
            }
            .build()

    companion object {
        const val THUMB_CACHE_DIR = "thumbnails"
        const val THUMB_CACHE_BYTES = 200L * 1024 * 1024
    }
}
