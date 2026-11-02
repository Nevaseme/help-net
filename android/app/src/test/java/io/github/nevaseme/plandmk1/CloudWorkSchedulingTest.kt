package io.github.nevaseme.plandmk1

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import io.github.nevaseme.plandmk1.cloud.cloudSyncWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudWorkSchedulingTest {
    @Test fun savedChangeDuringRunningUploadSchedulesOneFollowUp() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE,
            cloudSyncWorkPolicy(listOf(WorkInfo.State.RUNNING),afterChange=true))
    }

    @Test fun repeatedChangesCoalesceIntoAlreadyQueuedSuccessor() {
        for(waiting in listOf(WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)) {
            assertEquals(ExistingWorkPolicy.KEEP,
                cloudSyncWorkPolicy(listOf(WorkInfo.State.RUNNING,waiting),afterChange=true))
            assertEquals(ExistingWorkPolicy.KEEP,
                cloudSyncWorkPolicy(listOf(WorkInfo.State.RUNNING,waiting),afterEew=true))
        }
    }

    @Test fun notYetStartedWorkWillReadAlreadySavedChanges() {
        for(waiting in listOf(WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)) {
            assertEquals(ExistingWorkPolicy.KEEP,cloudSyncWorkPolicy(listOf(waiting),afterChange=true))
            assertEquals(ExistingWorkPolicy.KEEP,cloudSyncWorkPolicy(listOf(waiting),afterEew=true))
        }
    }

    @Test fun eewDuringRunningUploadGetsFollowUpAndOrdinaryRetryDoesNot() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE,
            cloudSyncWorkPolicy(listOf(WorkInfo.State.RUNNING),afterEew=true))
        assertEquals(ExistingWorkPolicy.KEEP,cloudSyncWorkPolicy(listOf(WorkInfo.State.RUNNING)))
    }

    @Test fun finishedFailedOrCancelledChainCanStartAgainWithoutInheritedFailure() {
        for(previous in listOf(emptyList(),listOf(WorkInfo.State.SUCCEEDED),
            listOf(WorkInfo.State.FAILED),listOf(WorkInfo.State.CANCELLED))) {
            assertEquals(ExistingWorkPolicy.KEEP,cloudSyncWorkPolicy(previous,afterChange=true))
            assertEquals(ExistingWorkPolicy.KEEP,cloudSyncWorkPolicy(previous,afterEew=true))
        }
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE,
            cloudSyncWorkPolicy(listOf(WorkInfo.State.FAILED,WorkInfo.State.CANCELLED,WorkInfo.State.RUNNING),afterChange=true))
    }
}
