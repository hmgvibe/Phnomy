package com.ray.phnomy.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ray.phnomy.PhnomyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** goAsync retains the receiver until its one repository transaction has finished. */
class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = PhnomyApp.graph(context)
                graph.repository.awaitReady()
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> graph.controller.interrupt("裝置已重新啟動，專注保護中斷。")
                    Intent.ACTION_MY_PACKAGE_REPLACED -> graph.controller.interrupt("應用程式已更新，專注保護中斷。")
                    else -> {
                        val status = graph.permissions.status()
                        when {
                            !status.accessibility -> graph.controller.interrupt("專注保護權限已關閉。")
                            !status.exactAlarms -> graph.controller.interrupt("準確計時權限已關閉。")
                            else -> graph.controller.reconcile()
                        }
                    }
                }
            } finally { pending.finish() }
        }
    }

    companion object { const val ACTION_DEADLINE = "com.ray.phnomy.action.SESSION_DEADLINE" }
}
