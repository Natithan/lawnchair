package app.lawnchair.search.algorithms

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Each attempt must acknowledge execution or a dropped task. Keep only one attempt pending. */
internal suspend fun awaitLoadedModel(attempt: ((Boolean) -> Unit) -> Unit) {
    var retryDelay = 100L
    while (true) {
        coroutineContext.ensureActive()
        val completed = CompletableDeferred<Boolean>()
        attempt { completed.complete(it) }
        if (completed.await()) return
        delay(retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(1_000L)
    }
}
