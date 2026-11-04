package dev.rootiest.status

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.SignInNeeded
import dev.rootiest.status.data.SupportApi
import dev.rootiest.status.data.ticketNotice

// The support desk pushed "ticket <id>": fetch that ticket with the desk's sign-in and say what happened. The push itself carries nothing
// else, since it passes through ntfy; what the notification says comes from the desk, to this phone only.
class SupportReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TICKET, 0).takeIf { it > 0 } ?: return Result.failure()
        val api = SupportApi(AuthStore.forSupport(applicationContext)::credentials)
        return try {
            ticketNotice(api.thread(id))?.let { Notifier.ticket(applicationContext, id, it) }
            Result.success()
        } catch (_: SignInNeeded) {
            Result.failure() // signed out of the desk since: nothing to show
        } catch (e: Exception) {
            Log.w("SupportPush", "ticket $id: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val KEY_TICKET = "ticket"

        fun enqueue(context: Context, id: Long) {
            val request = OneTimeWorkRequestBuilder<SupportReplyWorker>()
                .setInputData(workDataOf(KEY_TICKET to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
            // a reply and a close pushed together are one look at the ticket
            WorkManager.getInstance(context).enqueueUniqueWork("support-ticket-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
