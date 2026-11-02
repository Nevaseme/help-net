package io.github.nevaseme.plandmk1.core
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs

@Serializable data class AddressSearchReference(val input:String,val label:String,val latitude:Double,val longitude:Double,val matchLevel:String,val precision:String,val source:String,val sourceVersion:String) {
 fun validate(){require(listOf(input,label,matchLevel,precision,source,sourceVersion).all{it.length<=240});require(latitude.isFinite()&&latitude in -85.0..85.0&&longitude.isFinite()&&longitude in -180.0..180.0)}
}
fun homeRegistrationEvents(profile:Resident,reference:BuildingLocationReference,rows:List<PlaceEvent>,at:Long):List<PlaceEvent>{
 reference.validate();val buildings=placeBuildings(rows);val old=validHome(rows,profile.originId);val previous=buildings.find{it.buildingId==old?.buildingId}
 val same=previous!=null&&abs(previous.latitude!!-profile.latitude)<1e-6&&abs(previous.longitude!!-profile.longitude)<1e-6
 val match=if(reference.plateauId!=null)buildings.find{it.locationReference?.plateauId==reference.plateauId}else if(same)previous else null
 val buildingId=match?.buildingId?:UUID.randomUUID().toString();var seq=rows.filter{it.authorId==profile.originId}.maxOfOrNull{it.sequence}?:0L;val out=mutableListOf<PlaceEvent>()
 if(match==null||match.authorId==profile.originId)out+=PlaceEvent(profile.groupId,if(match==null)buildingId else UUID.randomUUID().toString(),profile.originId,profile.name,++seq,at,PlaceKind.BUILDING,buildingId=buildingId,label=reference.address.take(80),latitude=profile.latitude,longitude=profile.longitude,locationReference=reference)
 out+=PlaceEvent(profile.groupId,UUID.randomUUID().toString(),profile.originId,profile.name,++seq,at,PlaceKind.HOME,buildingId=buildingId,unitId=old?.takeIf{it.buildingId==buildingId}?.unitId)
 out.forEach{it.validate()};return out
}
