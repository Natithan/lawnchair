package app.lawnchair.search.algorithms.engine.provider.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSearchRankingTest {
    private data class Candidate(val name: String, val launches: Int)

    private fun rank(query: String, vararg candidates: Candidate): List<String> = candidates
        .sortedWith(appSearchComparator({ AppMatcher.match(it.name, query) }, { it.launches }))
        .map { it.name }

    @Test
    fun frequencyReordersPrefixesBeforeResultsAreLimited() {
        assertThat(
            rank("map", Candidate("Maps", 1), Candidate("MapQuest", 20)).take(1),
        ).containsExactly("MapQuest")
    }

    @Test
    fun frequentWordMatchCannotDisplaceDirectPrefix() {
        assertThat(
            rank("insta", Candidate("APKMirror Installer", 100), Candidate("Instagram", 0)),
        ).containsExactly("Instagram", "APKMirror Installer").inOrder()
    }

    @Test
    fun exactNameWinsEvenWithoutUsageHistory() {
        assertThat(
            rank("maps", Candidate("Maps Go", 100), Candidate("Maps", 0)),
        ).containsExactly("Maps", "Maps Go").inOrder()
    }

    @Test
    fun absentOrEqualHistoryPreservesExistingRelevanceOrder() {
        for (launches in listOf(0, 5)) {
            assertThat(
                rank("map", Candidate("MapQuest", launches), Candidate("Maps", launches)),
            ).containsExactly("Maps", "MapQuest").inOrder()
        }
    }

    @Test
    fun frequencyDoesNotPromotePoorerFuzzyMatches() {
        data class FuzzyCandidate(val score: Float, val launches: Int)
        val better = FuzzyCandidate(0.64f, 0)
        val worse = FuzzyCandidate(0.51f, 100)
        val comparator = appSearchComparator<FuzzyCandidate>(
            match = { MatchResult(it.score, MatchType.FUZZY) },
            launchCount = { it.launches },
        )
        assertThat(listOf(worse, better).sortedWith(comparator)).containsExactly(better, worse).inOrder()
    }
}
