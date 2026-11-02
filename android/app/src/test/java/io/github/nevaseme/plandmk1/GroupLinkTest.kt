package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.nearby.GroupLink
import org.junit.Assert.*
import org.junit.Test
class GroupLinkTest {
    private val a="00000000-0000-0000-0000-000000000001"
    private val b="00000000-0000-0000-0000-000000000002"
    @Test fun groupProofCannotBeReplayedAcrossConnectionsGroupsOrDirections() {
        val left=GroupLink("group",a,"ab".repeat(32));val right=GroupLink("group",b,"ab".repeat(32))
        val token=byteArrayOf(1,2,3);val proof=left.proof(token,a,b)
        assertTrue(right.verifies(token,a,proof))
        assertFalse(right.verifies(byteArrayOf(1,2,4),a,proof))
        assertFalse(left.verifies(token,b,proof))
        assertFalse(GroupLink("other",b,"ab".repeat(32)).verifies(token,a,proof))
        assertFalse(GroupLink("group",b,"cd".repeat(32)).verifies(token,a,proof))
        assertEquals(b to "相手",left.peer(right.endpoint("相手")))
        assertNull(left.peer(left.endpoint("自分")));assertNull(left.peer("old client"))
        assertTrue(left.initiates(b));assertFalse(right.initiates(a))
        assertEquals(left.serviceId,right.serviceId)
    }
}
