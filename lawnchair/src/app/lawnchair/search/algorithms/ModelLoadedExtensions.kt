package app.lawnchair.search.algorithms

import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wait for the app model without a deadline. A cold load after a low-memory process kill can
 * take longer than two seconds. Queue one callback behind the loader rather than abandoning
 * the query or accumulating timed-out tasks. The returned job owns the wait and is cancelled
 * when the query changes or the drawer closes.
 */
fun LauncherModel.runWhenModelLoaded(
    scope: CoroutineScope,
    block: (apps: AllAppsList, owner: Job) -> Unit,
): Job = scope.launch {
    val job = checkNotNull(coroutineContext[Job])
    awaitLoadedModel { complete ->
        loadAsync {
            if (job.isActive) {
                // loadAsync calls back on MODEL_EXECUTOR. enqueueModelUpdateTask executes
                // inline on that same executor, so its acknowledgement arrives before the
                // false fallback below. If a reload raced the callback, retry instead.
                enqueueModelUpdateTask { _, _, apps ->
                    if (job.isActive) {
                        block(apps, job)
                        complete(true)
                    }
                }
            }
            complete(false)
        }
    }
}
