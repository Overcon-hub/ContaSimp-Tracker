package cl.contasimp.tracker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateDownloadReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action==DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            UpdateManager.onDownloadComplete(context,intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1))
        }
    }
}
