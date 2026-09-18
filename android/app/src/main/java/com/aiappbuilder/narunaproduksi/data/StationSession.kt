package com.aiappbuilder.narunaproduksi.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Resolves the signed-in station's own stasiun_id from its ID token custom
 * claims (set once at account creation by createStationAccount, see
 * functions/src/naruna.ts) — read fresh from Firebase Auth's cached token
 * rather than persisted separately, since the token is already the single
 * source of truth and re-fetching it (cached, so effectively free unless
 * expired) avoids a second thing to keep in sync.
 */
object StationSession {
    suspend fun currentStasiunId(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val result = user.getIdToken(false).await()
        return result.claims["stasiun_id"] as? String
    }

    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
}
