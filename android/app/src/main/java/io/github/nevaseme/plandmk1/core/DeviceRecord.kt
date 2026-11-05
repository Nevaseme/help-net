package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class DemoHome(val buildingId:String,val address:String,val latitude:Double,val longitude:Double,val plateauId:String?,val floor:String="",val room:String="",@EncodeDefault(EncodeDefault.Mode.NEVER) val displayName:String?=null,@EncodeDefault(EncodeDefault.Mode.NEVER) val searchReference:AddressSearchReference?=null,@EncodeDefault(EncodeDefault.Mode.NEVER) val householdId:String?=null) {
    fun validate(){searchReference?.validate();require(recordIdPattern.matches(buildingId)&&address.isNotBlank()&&address.length<=240);require(latitude.isFinite()&&latitude in -85.0..85.0&&longitude.isFinite()&&longitude in -180.0..180.0);require(plateauId==null||plateauId.isNotBlank()&&plateauId.length<=160);require(displayName==null||displayName.isNotBlank()&&displayName.length<=60);require(floor.length<=40&&room.length<=240)}
}
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class DeviceRecord(val groupId:String,val eventId:String,val authorId:String,val sequence:Long,val createdAt:Long,val kind:String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val observedAt:Long?=null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val source:String?=null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sessionId:String?=null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val home:DemoHome?=null,
) {
    fun validate(){
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,40}"))&&recordIdPattern.matches(eventId)&&recordIdPattern.matches(authorId));require(sequence in 1..MAX_SEQUENCE&&createdAt in 0..MAX_SEQUENCE)
        when(kind){"IMPACT"->require(observedAt!=null&&observedAt in 1..createdAt&&source=="VIVO_DROP"&&home==null&&sessionId==null)
            "DEMO_HOME"->{require(sessionId!=null&&recordIdPattern.matches(sessionId)&&observedAt==null&&source==null);requireNotNull(home).validate()}
            else->error("端末記録の種類が不正です")}
    }
}
fun latestDemoHome(rows:List<DeviceRecord>,group:String,session:String,device:String)=rows.filter {it.groupId==group&&it.sessionId==session&&it.authorId==device&&it.kind=="DEMO_HOME"}.maxWithOrNull(compareBy<DeviceRecord>({it.sequence},{it.eventId}))?.home
fun latestImpact(rows:List<DeviceRecord>,group:String,device:String,now:Long)=rows.filter {it.groupId==group&&it.authorId==device&&it.kind=="IMPACT"&&it.observedAt!=null&&it.observedAt<=now&&it.observedAt>now-30L*86400000}.maxOfOrNull {it.observedAt!!}
