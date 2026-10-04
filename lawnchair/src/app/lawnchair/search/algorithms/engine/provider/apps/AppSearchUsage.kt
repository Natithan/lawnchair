package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import app.lawnchair.predictions.LawnchairPredictionManager
import app.lawnchair.predictions.PredictionAppKey
import app.lawnchair.predictions.PredictionEventResolver
import com.android.launcher3.logger.LauncherAtom.ItemInfo
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_DRAGDROP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.quickstep.logging.StatsLogCompatManager
import org.json.JSONArray

/**
 * Learns from launcher app launches, including search, independently of prediction settings.
 * History is bounded and persisted on the model executor; searches only read a memory snapshot.
 */
class AppSearchUsage private constructor(context: Context) : StatsLogCompatManager.StatsLogConsumer {
    private val userCache = UserCache.INSTANCE.get(context)
    private val resolver = PredictionEventResolver(userCache)
    private val preferences = context.getSharedPreferences("app_search_usage", Context.MODE_PRIVATE)
    private val history = loadHistory(context).toMutableList()

    @Volatile
    private var counts = history.groupingBy { it }.eachCount()

    fun register() {
        StatsLogCompatManager.LOGS_CONSUMER.addIfAbsent(this)
    }

    fun unregister() {
        StatsLogCompatManager.LOGS_CONSUMER.remove(this)
    }

    fun snapshot(apps: List<AppInfo>): Map<AppInfo, Int> {
        val currentCounts = counts
        return apps.associateWith { app ->
            val component = app.componentName ?: return@associateWith 0
            val key = PredictionAppKey.create(component, userCache.getSerialNumberForUser(app.user).toString())
            currentCounts[key] ?: 0
        }
    }

    override fun consume(event: EventEnum?, atomInfo: ItemInfo?) {
        if (event != LAUNCHER_APP_LAUNCH_TAP && event != LAUNCHER_APP_LAUNCH_DRAGDROP) return
        MODEL_EXECUTOR.execute {
            val resolved = resolver.resolve(atomInfo) ?: return@execute
            val component = resolved.componentName ?: return@execute
            val key = PredictionAppKey.create(component, userCache.getSerialNumberForUser(resolved.user).toString())
            history.add(0, key)
            if (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
            counts = history.groupingBy { it }.eachCount()
            preferences.edit().putString(HISTORY_KEY, JSONArray(history).toString()).apply()
        }
    }

    private fun loadHistory(context: Context): List<String> {
        val saved = preferences.getString(HISTORY_KEY, null)
        if (saved != null) {
            return runCatching {
                val array = JSONArray(saved)
                (0 until array.length()).map { array.getString(it) }.take(MAX_HISTORY)
            }.getOrDefault(emptyList())
        }
        // Existing launcher history provides an initial ranking without usage-access permission.
        val predictions = LawnchairPredictionManager.getInstance(context)
        return (predictions.allAppsStore.getEntries() + predictions.hotseatStore.getEntries()).take(MAX_HISTORY)
    }

    companion object {
        private const val HISTORY_KEY = "launches"
        private const val MAX_HISTORY = 500

        @Volatile
        private var instance: AppSearchUsage? = null

        /** Initialize on the model executor, where the initial preferences read is safe. */
        fun getInstance(context: Context): AppSearchUsage = instance ?: synchronized(this) {
            instance ?: AppSearchUsage(context.applicationContext).also { instance = it }
        }
    }
}
