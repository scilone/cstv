package com.cstv.app.data.local.storage

import com.cstv.app.data.sync.AccountKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T20: the `accountKey` state repositories filter on when reading/writing through `media_refs` /
 * `category_refs`. Without it, a reference from a previous Xtream account whose numeric id
 * happens to match the current catalogue would display an unrelated media (rule 7 of T20).
 *
 * Falls back to the empty sentinel `''` when no credentials are saved yet (first launch, signed
 * out) — the same sentinel `MIGRATION_27_28` uses for inherited identities. Nothing under an
 * unresolved account is visible either way, matching the "cloud restored before catalogue"
 * behaviour the ticket specifies.
 */
interface CurrentAccountKeyProvider {
    fun current(): String
}

@Singleton
class CurrentAccountKeyProviderImpl @Inject constructor(
    private val credentialsManager: CredentialsManager
) : CurrentAccountKeyProvider {
    override fun current(): String = credentialsManager.getCredentials()?.let { AccountKey.from(it) } ?: ""
}
