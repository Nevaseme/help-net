package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CloudWorkTest {
    @Test fun missingDependencyConflictsRetryButPermanentConflictsAndForbiddenProofsDoNot() {
        for(code in listOf("missing_incident","missing_formation_evidence","unknown_roster_revision"))
            assertTrue(code,retryCloudFailure(CloudHttpException(409,code)))
        for(code in listOf("record_conflict","sequence_conflict","roster_revision_mismatch","relay_key_conflict"))
            assertFalse(code,retryCloudFailure(CloudHttpException(409,code)))
        assertFalse(retryCloudFailure(CloudHttpException(403,"invalid_relay_proof")))
        assertFalse(retryCloudFailure(CloudHttpException(400,"invalid_record")))
    }
    @Test fun backgroundRetryKeepsOfflineStorageAndServerFailuresButNotInvalidCredentials() {
        assertTrue(retryCloudFailure(IOException("offline")))
        assertTrue(retryCloudFailure(IllegalStateException("storage unavailable")))
        for(status in listOf(429,500,503))assertTrue(retryCloudFailure(CloudHttpException(status,"fixture")))
        for(status in listOf(400,401,403,404,409,413))assertFalse(retryCloudFailure(CloudHttpException(status,"fixture")))
        assertFalse(retryCloudFailure(IllegalArgumentException("invalid page")))
    }
}
