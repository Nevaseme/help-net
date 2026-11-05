package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName="device_records",primaryKeys=["groupId","eventId"])
data class DeviceRecordRow(val groupId:String,val eventId:String,val payload:String){fun record()=Json.decodeFromString<DeviceRecord>(payload)}
@Dao interface DeviceRecordDao {
    @Query("SELECT * FROM device_records WHERE groupId=:group")fun observe(group:String):Flow<List<DeviceRecordRow>>
    @Query("SELECT * FROM device_records WHERE groupId=:group")suspend fun all(group:String):List<DeviceRecordRow>
    @Query("SELECT * FROM device_records WHERE groupId=:group AND eventId=:id")suspend fun get(group:String,id:String):DeviceRecordRow?
    @Insert(onConflict=OnConflictStrategy.ABORT)suspend fun put(row:DeviceRecordRow)
}
class DeviceRecordStore(private val db:PlanDatabase){
    fun observe(group:String)=db.deviceRecords().observe(group).map {it.map(DeviceRecordRow::record)}
    suspend fun all(group:String)=db.deviceRecords().all(group).map(DeviceRecordRow::record)
    suspend fun merge(r:DeviceRecord):Boolean=db.withTransaction {r.validate();val old=db.deviceRecords().get(r.groupId,r.eventId)?.record();if(old!=null){require(old==r){"同じ端末記録の内容が一致しません"};false}else{db.deviceRecords().put(DeviceRecordRow(r.groupId,r.eventId,Json.encodeToString(r)));true}}
    suspend fun append(r:DeviceRecord):DeviceRecord=db.withTransaction {
        db.deviceRecords().get(r.groupId,r.eventId)?.let {return@withTransaction it.record()}
        r.copy(sequence=Math.addExact(all(r.groupId).filter {it.authorId==r.authorId}.maxOfOrNull {it.sequence}?:0,1)).also {merge(it)}
    }
}
