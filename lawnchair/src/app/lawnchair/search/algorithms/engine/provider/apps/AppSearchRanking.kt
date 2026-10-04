package app.lawnchair.search.algorithms.engine.provider.apps

/** Frequency only competes within a name-match category. Preserve relevance for fuzzy matches. */
internal fun <T> appSearchComparator(
    match: (T) -> MatchResult,
    launchCount: (T) -> Int,
): Comparator<T> = compareBy<T>(
    { match(it).type.priority },
    { if (match(it).type == MatchType.FUZZY) -match(it).score else 0f },
    { -launchCount(it) },
    { -match(it).score },
)
