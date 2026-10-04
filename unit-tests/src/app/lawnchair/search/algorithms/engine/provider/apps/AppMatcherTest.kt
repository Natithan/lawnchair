package app.lawnchair.search.algorithms.engine.provider.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppMatcherTest {

    @Test
    fun directPrefixRanksBeforeLaterWordPrefix() {
        val instagram = AppMatcher.match("Instagram", "insta")
        val apkMirrorInstaller = AppMatcher.match("APKMirror Installer", "insta")

        assertThat(instagram.type).isEqualTo(MatchType.DIRECT_PREFIX)
        assertThat(apkMirrorInstaller.type).isEqualTo(MatchType.TOKEN_PREFIX_ORDERED)
        assertThat(instagram.type.priority).isLessThan(apkMirrorInstaller.type.priority)
    }

    @Test
    fun wordPrefixCanStartAfterFirstAppNameToken() {
        val result = AppMatcher.match("Google Maps", "map")

        assertThat(result.type).isEqualTo(MatchType.TOKEN_PREFIX_ORDERED)
    }

    @Test
    fun orderedTokenPrefixCanStartAfterFirstAppNameToken() {
        val result = AppMatcher.match("Microsoft Office OneNote", "office one")

        assertThat(result.type).isEqualTo(MatchType.TOKEN_PREFIX_ORDERED)
    }

    @Test
    fun substringStillMatchesWhenItIsNotAWordPrefix() {
        val result = AppMatcher.match("Pinterest", "interest")

        assertThat(result.type).isEqualTo(MatchType.SUBSTRING)
    }
}
