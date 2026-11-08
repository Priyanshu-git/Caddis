package app.caddis.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.caddis.BuildConfig
import app.caddis.data.DetailsScheduler
import app.caddis.data.db.CaddisDatabase
import app.caddis.domain.analytics.AnalyticsSink
import app.caddis.work.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.caddis.data.details.UserAgentInterceptor
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CaddisDatabase =
        Room.databaseBuilder(context, CaddisDatabase::class.java, CaddisDatabase.NAME)
            .addMigrations(*CaddisDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    /** Outlives screens: a save started from the share sheet finishes even if the sheet closes. */
    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(UserAgentInterceptor())
        .build()

    /**
     * Client-only phase: events go to Logcat in debug builds and nowhere in release. Firebase
     * Analytics replaces this later; the event types already enforce the allowed fields.
     */
    @Provides
    @Singleton
    fun analytics(): AnalyticsSink = AnalyticsSink { e ->
        if (BuildConfig.DEBUG) Log.d("CaddisAnalytics", "${e.name} ${e.params()}")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun detailsScheduler(impl: WorkScheduler): DetailsScheduler
}
