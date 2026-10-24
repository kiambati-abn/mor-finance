package com.morphlabs.morph.core.sms

import com.morphlabs.morph.core.model.TxKind
import java.time.LocalDateTime

/** Everything Morph could read out of one M-Pesa SMS. Amounts in cents. */
data class MpesaSms(
    val code: String?,
    val kind: TxKind,
    /** Principal amount, without the fee. */
    val amount: Long,
    /** M-Pesa's own charge (transaction cost, withdrawal charge, or Fuliza access fee). */
    val fee: Long = 0,
    /** M-Pesa balance after this transaction, when the SMS states it. */
    val balance: Long? = null,
    val counterparty: String? = null,
    val phone: String? = null,
    /** Paybill account number ("for account 79668"). */
    val accountRef: String? = null,
    val time: LocalDateTime? = null,
    /** For reversals: the code of the transaction that was undone. */
    val reversedCode: String? = null,
    val raw: String,
) {
    /** True when money left the M-Pesa wallet. UNKNOWN is resolved later from the balance change. */
    val outgoing: Boolean get() = kind.outgoing

    /** Stable key that lets the learner recognise "the same place/person" next time. */
    val counterpartyKey: String? get() = Counterparty.key(kind, counterparty, phone)
}
