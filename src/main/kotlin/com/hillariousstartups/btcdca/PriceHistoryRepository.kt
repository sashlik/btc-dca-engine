package com.hillariousstartups.btcdca

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface PriceHistoryRepository {
    fun append(point: PricePoint)
    fun loadSince(since: Instant, until: Instant): List<PricePoint>
}

class FirestorePriceHistoryRepository(
    private val firestore: Firestore,
    private val strategyId: String,
    /** If true, append() writes to a deterministic docId (one point per slot). */
    private val idempotentBySlot: Boolean = true,
    /** Slot size used for deterministic docId (matches your “every 4 hours” requirement). */
    private val slotSeconds: Long = 4 * 3600,
) : PriceHistoryRepository {

    private val pointsCol = firestore
        .collection("price_points")
        .document(strategyId)
        .collection("points")

    override fun append(point: PricePoint) {
        val docRef = if (idempotentBySlot) {
            pointsCol.document(slotId(point.time))
        } else {
            pointsCol.document() // random id
        }

        val data = mapOf(
            "ts" to Timestamp.ofTimeSecondsAndNanos(point.time.epochSecond, point.time.nano),
            "priceUsd" to point.priceUsd.toPlainString(),
        )

        // set() is upsert; with deterministic id it becomes idempotent.
        docRef.set(data).get()
    }

    override fun loadSince(since: Instant, until: Instant): List<PricePoint> {
        val sinceTs = Timestamp.ofTimeSecondsAndNanos(since.epochSecond, since.nano)
        val untilTs = Timestamp.ofTimeSecondsAndNanos(until.epochSecond, until.nano)

        val snap = pointsCol
            .whereGreaterThanOrEqualTo("ts", sinceTs)
            .whereLessThanOrEqualTo("ts", untilTs)
            .orderBy("ts", Query.Direction.ASCENDING)
            .get()
            .get()

        return snap.documents.mapNotNull { doc ->
            val ts = doc.getTimestamp("ts")?.toDate()?.toInstant()
                ?: return@mapNotNull null
            val priceStr = doc.getString("priceUsd") ?: return@mapNotNull null
            PricePoint(time = ts, priceUsd = priceStr.toBigDecimal())
        }
    }

    /**
     * Rounds timestamp down to slot boundary and formats stable ID, e.g. "20260102T0400Z".
     */
    private fun slotId(ts: Instant): String {
        val roundedEpoch = (ts.epochSecond / slotSeconds) * slotSeconds
        val rounded = Instant.ofEpochSecond(roundedEpoch)

        val zdt = ZonedDateTime.ofInstant(rounded, ZoneOffset.UTC)
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm'Z'")
        return zdt.format(fmt)
    }
}