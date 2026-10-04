package app.lawnchair.search.algorithms

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitLoadedModelTest {
    @Test
    fun slowColdLoadStillDeliversWithoutQueuingDuplicateSearches() = runTest {
        var attempts = 0
        var delivered = false
        val search = launch {
            awaitLoadedModel { complete ->
                attempts++
                launch {
                    delay(5_000)
                    complete(true)
                }
            }
            delivered = true
        }
        advanceTimeBy(2_100)
        assertThat(search.isActive).isTrue()
        assertThat(delivered).isFalse()
        assertThat(attempts).isEqualTo(1)
        advanceUntilIdle()
        assertThat(delivered).isTrue()
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun tasksDroppedDuringReloadRetryUntilAccepted() = runTest {
        var attempts = 0
        var delivered = false
        launch {
            awaitLoadedModel { complete -> complete(++attempts == 4) }
            delivered = true
        }
        advanceUntilIdle()
        assertThat(attempts).isEqualTo(4)
        assertThat(delivered).isTrue()
    }

    @Test
    fun drawerCloseCancelsWaitAndLateAcknowledgementCannotDeliver() = runTest {
        var acknowledge: ((Boolean) -> Unit)? = null
        var delivered = false
        val search = launch {
            awaitLoadedModel { acknowledge = it }
            delivered = true
        }
        runCurrent()
        search.cancelAndJoin()
        acknowledge!!(true)
        advanceUntilIdle()
        assertThat(delivered).isFalse()
    }

    @Test
    fun queryChangeStopsRetryingDroppedTasks() = runTest {
        var attempts = 0
        val search = launch {
            awaitLoadedModel { complete ->
                attempts++
                complete(false)
            }
        }
        runCurrent()
        search.cancelAndJoin()
        advanceUntilIdle()
        assertThat(attempts).isEqualTo(1)
    }
}
