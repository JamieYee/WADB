package moe.haruue.wadb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.haruue.wadb.events.GlobalRequestHandler

class TurnOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GlobalRequestHandler.stopWadb()
    }
}
