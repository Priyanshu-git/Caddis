package com.nexxlabs.caddis.data

import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the Library needs, read once at app start and kept current, so the tab already has its data the
 * first time it opens (no empty flash, no jump). `null` means "Room has not answered yet", which is not
 * the same as an empty library (`emptyList()`).
 *
 * The lists live in memory for the life of the process. That is one `Item` per save, which is what Home
 * already holds while it is on screen.
 */
@Singleton
class LibraryFeed @Inject constructor(repo: LibraryRepository, scope: CoroutineScope) {
    val items: StateFlow<List<Item>?> = repo.observeLibrary().stateIn(scope, SharingStarted.Eagerly, null)
    val intents: StateFlow<List<Intent>?> = repo.observeIntents().stateIn(scope, SharingStarted.Eagerly, null)
    val collections: StateFlow<List<Collection>?> = repo.observeCollections().stateIn(scope, SharingStarted.Eagerly, null)
}
