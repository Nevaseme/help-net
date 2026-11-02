package io.github.nevaseme.plandmk1.cloud

import android.content.Context
import androidx.work.*
import io.github.nevaseme.plandmk1.PlanDApplication
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

fun retryCloudFailure(error:Exception):Boolean=when(error) {
    is CloudHttpException->error.status==429||error.status>=500||
        error.status==409&&error.code in setOf("missing_incident","missing_formation_evidence","unknown_roster_revision")
    is IOException->true
    is IllegalArgumentException->false
    else->true // Includes local persistence failure; records/cursor remain available for replay.
}

internal fun cloudSyncWorkPolicy(states:List<WorkInfo.State>,afterEew:Boolean=false,afterChange:Boolean=false):ExistingWorkPolicy {
    val waiting=states.any {it==WorkInfo.State.ENQUEUED||it==WorkInfo.State.BLOCKED}
    return if((afterEew||afterChange)&&WorkInfo.State.RUNNING in states&&!waiting)
        ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
}

object CloudSyncWork {
    private val enqueueLock=Any()
    /** Call off the main thread. Return only after WorkManager's queue transaction commits. */
    fun enqueue(context:Context,group:String,afterEew:Boolean=false,afterChange:Boolean=false):UUID? {
        require(group.matches(Regex("[A-Za-z0-9_-]{1,40}")))
        if(CloudSettings(context).session(group)==null)return null
        val request=OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(workDataOf("group" to group))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .build()
        val manager=WorkManager.getInstance(context)
        // Callers save records before enqueueing. A waiting worker will see those records;
        // a running worker may have taken its snapshot already and needs one successor.
        // Serialize the read and committed enqueue so concurrent receipts cannot grow a chain.
        synchronized(enqueueLock) {
            val name="cloud-sync:$group"
            val states=manager.getWorkInfosForUniqueWork(name).get(5,TimeUnit.SECONDS).map {it.state}
            manager.enqueueUniqueWork(name,cloudSyncWorkPolicy(states,afterEew,afterChange),request)
                .result.get(5,TimeUnit.SECONDS)
        }
        return request.id
    }
}

class CloudSyncWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val group=inputData.getString("group")?:return Result.failure()
        if(!group.matches(Regex("[A-Za-z0-9_-]{1,40}")))return Result.failure()
        return try {
            val result=(applicationContext as PlanDApplication).cloud.syncStoredGroup(group)
            Result.success(workDataOf("uploaded" to (result?.uploaded?:0),"rejected" to (result?.rejected?:0)))
        }catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){if(retryCloudFailure(error))Result.retry() else Result.failure()}
    }
}
