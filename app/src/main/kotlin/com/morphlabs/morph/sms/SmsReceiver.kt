package com.morphlabs.morph.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.morphlabs.morph.core.sms.MpesaParser
import com.morphlabs.morph.data.MorphRepo
import com.morphlabs.morph.notify.Notifier

/** Wakes up for every incoming SMS; only M-Pesa messages are read, everything else is ignored. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in several parts; join them per sender.
        val bySender = parts.filterNotNull().groupBy { it.originatingAddress ?: it.displayOriginatingAddress }
        val mpesa = bySender.filterKeys { MpesaParser.isMpesaSender(it) }
        if (mpesa.isEmpty()) return

        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val repo = MorphRepo.get(app)
                // Before setup the history import will pick these up, in the right order.
                if (!repo.onboarded) return@Thread
                for ((_, msgs) in mpesa) {
                    val body = msgs.joinToString("") { it.messageBody ?: "" }
                    val time = msgs.first().timestampMillis
                    val result = repo.ingest(body, time) ?: continue
                    if (time > repo.lastSmsDate) repo.lastSmsDate = time
                    Notifier.onNewTransaction(app, result)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
