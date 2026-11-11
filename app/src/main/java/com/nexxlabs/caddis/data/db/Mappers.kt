package com.nexxlabs.caddis.data.db

import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.IntentRule
import com.nexxlabs.caddis.domain.model.Item
import java.time.DayOfWeek

fun ItemEntity.toDomain(collectionIds: Set<String> = emptySet()) = Item(
    id = id, type = type, url = url, normalizedUrl = normalizedUrl, platform = platform, kind = kind,
    title = title, description = description, creator = creator, thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl,
    lengthSeconds = lengthSeconds, note = note, intentId = intentId, intentSource = intentSource,
    reason = reason, sourceApp = sourceApp, sourceFrom = sourceFrom, status = status,
    createdAt = createdAt, updatedAt = updatedAt, doneAt = doneAt, deletedAt = deletedAt,
    returnRule = returnRule, returnAt = returnAt, lastSurfacedAt = lastSurfacedAt,
    surfacedCount = surfacedCount, lastOpenedAt = lastOpenedAt, lastReviewedAt = lastReviewedAt,
    detailsFetchedAt = detailsFetchedAt, userEdited = userEdited, collectionIds = collectionIds,
    revision = revision,
)

fun Item.toEntity() = ItemEntity(
    id = id, type = type, url = url, normalizedUrl = normalizedUrl, platform = platform, kind = kind,
    title = title, description = description, creator = creator, thumbnailUrl = thumbnailUrl, faviconUrl = faviconUrl,
    lengthSeconds = lengthSeconds, note = note, intentId = intentId, intentSource = intentSource,
    reason = reason, sourceApp = sourceApp, sourceFrom = sourceFrom, status = status,
    createdAt = createdAt, updatedAt = updatedAt, doneAt = doneAt, deletedAt = deletedAt,
    returnRule = returnRule, returnAt = returnAt, lastSurfacedAt = lastSurfacedAt,
    surfacedCount = surfacedCount, lastOpenedAt = lastOpenedAt, lastReviewedAt = lastReviewedAt,
    detailsFetchedAt = detailsFetchedAt, userEdited = userEdited, revision = revision,
)

fun IntentEntity.toDomain() = Intent(id, name, doneLabel, isDefault, position)

fun IntentRuleEntity.toDomain() = IntentRule(intentId, daysFromMask(days), timeOfDay, maxPerDay)

fun IntentRule.toEntity() = IntentRuleEntity(intentId, daysToMask(days), timeOfDay, maxPerDay)

fun CollectionEntity.toDomain() = Collection(id, name, createdAt)

fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
