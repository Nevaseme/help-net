package io.github.nevaseme.plandmk1.demo

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.Serializable

@Serializable data class DemoPlacement(val sessionId:String,val deviceId:String,val memberId:String,val name:String,val slotId:Int,val latitude:Double?,val longitude:Double?,val source:String="DEMO_ASSIGNED",val home:DemoHome?=null)
@Serializable data class VenueSession(val version:Int=1,val mode:String="VENUE_DEMO",val groupId:String,val sessionId:String,val incidentId:String,val areaId:String,val areaName:String="",val closed:Boolean=false,val placements:List<DemoPlacement>,val inactiveMemberIds:List<String> = emptyList(),val recoveryAvailable:Boolean=false,val positionMode:String="KYOTO",val startedAt:Long=0) {
    fun active(group:String,incident:String?)=version==1&&mode=="VENUE_DEMO"&&groupId==group&&incidentId==incident&&!closed
    fun validate(roster:MemberRoster) {
        require(version==1&&mode=="VENUE_DEMO"&&groupId==roster.groupId&&sessionId==incidentId&&placements.size<=64)
        require(placements.map {it.deviceId}.distinct().size==placements.size)
        require(positionMode in setOf("KYOTO","ACTUAL")&&startedAt>=0)
        for(p in placements)require(p.sessionId==sessionId&&p.name.isNotBlank()&&p.name.length<=60&&(if(positionMode=="ACTUAL")p.source=="DEVICE_LOCATION"&&p.latitude==null&&p.longitude==null else p.source=="DEMO_ASSIGNED"&&p.latitude?.isFinite()==true&&p.latitude in -85.0..85.0&&p.longitude?.isFinite()==true&&p.longitude in -180.0..180.0)&&roster.members.any {it.memberId==p.memberId&&p.deviceId in it.deviceIds})
    }
    /** UI projection only; these values must never be saved as resident homes or GPS fixes. */
    fun displayResidents()=placements.map {Resident(groupId,it.deviceId,1,0,it.home?.displayName?:it.name,it.latitude?:Double.NaN,it.longitude?:Double.NaN,it.home?.floor.orEmpty(),it.home?.room.orEmpty())}
    fun withHomes(records:List<DeviceRecord>)=if(positionMode!="KYOTO")this else copy(placements=placements.map {p->val home=latestDemoHome(records,groupId,sessionId,p.deviceId)?:p.home;home?.let {p.copy(home=it,latitude=it.latitude,longitude=it.longitude)}?:p})
    fun placeRecords():List<PlaceEvent> = if(positionMode!="KYOTO")emptyList() else buildList {
        val buildings=mutableSetOf<String>()
        placements.forEach {p->p.home?.let {h->
            if(buildings.add(h.buildingId))add(PlaceEvent(groupId,h.buildingId,p.deviceId,p.name,1,startedAt,PlaceKind.BUILDING,buildingId=h.buildingId,label=h.address.take(80),latitude=h.latitude,longitude=h.longitude,locationReference=BuildingLocationReference(h.address,h.plateauId,if(h.plateauId!=null)"plateau-26100-kyoto-shi-2025" else null)))
            add(PlaceEvent(groupId,p.deviceId,p.deviceId,p.name,2,startedAt,PlaceKind.HOME,buildingId=h.buildingId))
        }}
    }
}
