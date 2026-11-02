package io.github.nevaseme.plandmk1.drops
import io.github.nevaseme.plandmk1.core.DeviceRecord
import java.util.UUID
fun DropRecord.sharedImpact(device:String):DeviceRecord {
    val at=(reading.receivedAt-(reading.receivedElapsedNanos-reading.eventElapsedNanos)/1_000_000).coerceAtMost(reading.receivedAt)
    return DeviceRecord(groupId,UUID.nameUUIDFromBytes("$device:$sessionId:${reading.eventElapsedNanos}".toByteArray(Charsets.UTF_8)).toString(),device,1,reading.receivedAt,"IMPACT",observedAt=at,source="VIVO_DROP").also {it.validate()}
}
