package com.morphlabs.morph.core

import com.morphlabs.morph.core.defaults.DefaultAccounts
import com.morphlabs.morph.core.model.Account
import com.morphlabs.morph.core.model.SystemRole
import java.time.LocalDateTime
import java.time.ZoneId

/** The default chart of accounts with ids 1..n, plus lookup helpers for tests. */
object TestBook {
    val zone: ZoneId = ZoneId.of("Africa/Nairobi")
    val accounts: List<Account> = DefaultAccounts.all().mapIndexed { i, a -> a.copy(id = i + 1L) }
    fun role(r: SystemRole): Long = accounts.first { it.role == r }.id
    fun hint(h: String): Long = accounts.first { it.hint == h }.id
    fun ts(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()
}
