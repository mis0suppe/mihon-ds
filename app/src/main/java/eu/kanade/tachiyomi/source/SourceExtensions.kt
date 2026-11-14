package eu.kanade.tachiyomi.source

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Source.getNameForMangaInfo(): String {
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> toString()
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> toString()
    }
}

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

/**
 * Fetches only the manga details through the combined tachiyomix 1.6 API.
 */
suspend fun Source.getMangaDetails(manga: SManga): SManga {
    return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
}

/**
 * Fetches only the chapter list through the combined tachiyomix 1.6 API.
 */
suspend fun Source.getChapterList(manga: SManga): List<SChapter> {
    return getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true).chapters
}
