package com.nexxlabs.caddis.domain.onboarding

/**
 * The first-launch flow: five intro cards, then the question about reminders. The person moves through
 * it by swiping or with the main button; Skip on an intro card leaves it. Pages are counted from 0.
 */
object Onboarding {
    const val INTRO_PAGES = 5

    /** The reminders question comes right after the last intro card. */
    const val NUDGES_PAGE = INTRO_PAGES
    const val PAGE_COUNT = INTRO_PAGES + 1

    /** What the main button says. */
    enum class Primary { GET_STARTED, NEXT, CONTINUE }

    fun isIntro(page: Int): Boolean = page in 0 until INTRO_PAGES
    fun isLast(page: Int): Boolean = page == PAGE_COUNT - 1

    /** Skip is offered while there is still something to skip: on the intro cards, not on the reminders question. */
    fun canSkip(page: Int): Boolean = isIntro(page)

    /** Keeps a restored or computed page inside the flow. */
    fun clamp(page: Int): Int = page.coerceIn(0, PAGE_COUNT - 1)

    fun next(page: Int): Int = clamp(page + 1)
    fun previous(page: Int): Int = clamp(page - 1)

    /** "Get started" on the first card, "Next" in between, "Continue" where the next screen asks for something or ends the flow. */
    fun primary(page: Int): Primary = when {
        page <= 0 -> Primary.GET_STARTED
        page < INTRO_PAGES - 1 -> Primary.NEXT
        else -> Primary.CONTINUE
    }

    /** 1-based step for the progress line ("Step 2 of 5"); null on the reminders question, which has no dots. */
    fun step(page: Int): Int? = if (isIntro(page)) page + 1 else null
}
