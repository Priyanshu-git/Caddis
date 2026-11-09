package app.caddis.domain.model

import app.caddis.domain.platform.Contact
import app.caddis.domain.platform.PlatformRegistry
import app.caddis.domain.platform.PlatformSpec

/**
 * Where a save came from. Not a closed list: a platform exists because a saved link has it. Recognised
 * services (see [PlatformRegistry]) get a proper name and behaviour; every other site is its own
 * platform named after its domain, e.g. `nytimes.com`.
 *
 * Only [id] is stored and compared. [fromId] rebuilds everything else, so ids must never be renamed
 * without a migration.
 */
class Platform internal constructor(
    /** Stable, lower-case: `instagram`, `google-drive`, `nytimes.com`, `note`. */
    val id: String,
    /** What a person sees: "Instagram", "Google Drive", or the domain for an unrecognised site. */
    val label: String,
    internal val spec: PlatformSpec?,
) {
    /** MET-01: the app never calls Meta for these (no details fetch, no site icon). */
    val neverContact: Boolean get() = spec?.contact is Contact.Never

    val isNote: Boolean get() = id == NOTE_ID

    /** True for a service the registry names; false for an ordinary site known only by its domain. */
    val isKnownService: Boolean get() = spec != null

    /** True when the registry also reads the kind and handle from the link (not just a nicer name). */
    val readsLinks: Boolean get() = spec?.isRich == true

    /**
     * What analytics may say about this platform. Unrecognised domains are never reported, because a
     * domain a person browses is theirs (tech arch 1.3).
     */
    val analyticsId: String get() = if (isNote || spec != null) id else OTHER

    /** Key for learning from intent corrections: recognised ids, or one shared bucket for the open web. */
    val learningKey: String get() = if (isNote || spec != null) id else WEB_KEY

    override fun equals(other: Any?): Boolean = other is Platform && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Platform($id)"

    companion object {
        const val NOTE_ID = "note"
        private const val OTHER = "other"
        private const val WEB_KEY = "web"

        val NOTE: Platform = Platform(NOTE_ID, "Note", null)

        val INSTAGRAM: Platform get() = fromId("instagram")
        val FACEBOOK: Platform get() = fromId("facebook")
        val REDDIT: Platform get() = fromId("reddit")
        val YOUTUBE: Platform get() = fromId("youtube")
        val MEDIUM: Platform get() = fromId("medium")
        val X: Platform get() = fromId("x")

        fun of(spec: PlatformSpec): Platform = Platform(spec.id, spec.label, spec)

        /** A site the registry does not know, named by its domain. */
        fun site(domain: String): Platform = Platform(domain, domain, null)

        /** Rebuilds a stored platform. Unknown ids are ordinary sites; they never fail to load. */
        fun fromId(id: String): Platform {
            if (id == NOTE_ID) return NOTE
            PlatformRegistry.byId(id)?.let { return of(it) }
            val label = if (id.contains('.')) id else id.replaceFirstChar { it.uppercase() }
            return Platform(id, label, null)
        }
    }
}
