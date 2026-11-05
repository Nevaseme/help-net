package io.github.nevaseme.plandmk1.demo

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.Serializable

@Serializable data class DemoPlacement(val sessionId:String,val deviceId:String,val memberId:String,val name:String,val slotId:Int,val latitude:Double?,val longitude:Double?,val source:String="DEMO_ASSIGNED",val home:DemoHome?=null,val communityId:String?=null)
@Serializable data class DemoCommunity(val id:String,val nameJa:String,val nameEn:String,val latitude:Double?=null,val longitude:Double?=null,val boundary:List<List<Double>>?=null)
@Serializable data class DemoObservations(val impact:Boolean=false,val enclosed:Boolean=false)
@Serializable data class DemoPhonePosition(val latitude:Double,val longitude:Double)
@Serializable data class ScenarioPerson(val deviceId:String,val name:String,val safety:String,val injury:String,val note:String,val communityId:String,val home:DemoHome,val observations:DemoObservations?=null,val position:DemoPhonePosition?=null,val positionUnknown:Boolean=false)
@Serializable data class VenueScenario(val version:Int=1,val sessionId:String,val communities:List<DemoCommunity>,val people:List<ScenarioPerson>,val communityLongitudeCuts:List<Double>?=null)
@Serializable data class VenueSession(val version:Int=1,val mode:String="VENUE_DEMO",val groupId:String,val sessionId:String,val incidentId:String,val areaId:String,val areaName:String="",val closed:Boolean=false,val placements:List<DemoPlacement>,val inactiveMemberIds:List<String> = emptyList(),val recoveryAvailable:Boolean=false,val positionMode:String="KYOTO",val startedAt:Long=0,val simulationStartedAt:Long?=null,val scenario:VenueScenario?=null,val presentationMode:String="DRILL") {
    fun displaysAsDrill(incident:IncidentEvent)=if(incident.eventId==incidentId)presentationMode!="EARTHQUAKE" else incident.training
    fun active(group:String,incident:String?)=version==1&&mode=="VENUE_DEMO"&&groupId==group&&incidentId==incident&&!closed
    fun validate(roster:MemberRoster) {
        require(version==1&&mode=="VENUE_DEMO"&&groupId==roster.groupId&&sessionId==incidentId&&placements.size<=64)
        require(presentationMode in setOf("DRILL","EARTHQUAKE"))
        require(placements.map {it.deviceId}.distinct().size==placements.size)
        require(positionMode in setOf("KYOTO","ACTUAL")&&startedAt>=0&&(simulationStartedAt==null||simulationStartedAt>=0))
        scenario?.let {s->
            require(s.version==1&&s.sessionId==sessionId&&s.people.size<=66&&s.communities.size in 1..10)
            s.communityLongitudeCuts?.let {cuts->require(cuts.size==2&&cuts.all {it.isFinite()}&&cuts[0]<cuts[1])}
            require(s.communities.map{it.id}.distinct().size==s.communities.size)
            for(c in s.communities)c.boundary?.let {ring->require(ring.size in 3..100&&ring.all {p->p.size==2&&p[0].isFinite()&&p[0] in -180.0..180.0&&p[1].isFinite()&&p[1] in -85.0..85.0})}
            require(s.people.map{it.deviceId}.distinct().size==s.people.size)
            for(p in s.people){require(recordIdPattern.matches(p.deviceId)&&placements.none{it.deviceId==p.deviceId}&&roster.members.none{p.deviceId in it.deviceIds});require(p.name.isNotBlank()&&p.name.length<=60&&p.note.length<=500&&p.safety in listOf("SAFE","SOS","UNREPORTED")&&p.injury in listOf("UNKNOWN","INJURED","UNINJURED")&&s.communities.any{it.id==p.communityId});p.home.validate();p.position?.let {require(it.latitude.isFinite()&&it.latitude in -85.0..85.0&&it.longitude.isFinite()&&it.longitude in -180.0..180.0)}}
        }
        for(p in placements)p.communityId?.let {require(it in setOf("demo-west","demo-central","demo-east"))}
        for(p in placements)require(p.sessionId==sessionId&&p.name.isNotBlank()&&p.name.length<=60&&(if(positionMode=="ACTUAL")p.source=="DEVICE_LOCATION"&&p.latitude==null&&p.longitude==null else p.source=="DEMO_ASSIGNED"&&p.latitude?.isFinite()==true&&p.latitude in -85.0..85.0&&p.longitude?.isFinite()==true&&p.longitude in -180.0..180.0)&&roster.members.any {it.memberId==p.memberId&&p.deviceId in it.deviceIds})
    }
    /** Legacy drill metadata gets deterministic examples, never device sensor records. */
    fun observationTimes(deviceId:String):Pair<Long?,Long?> {
        val index=scenario?.people?.indexOfFirst {it.deviceId==deviceId}?:-1
        if(index<0||startedAt<=0)return null to null
        val flags=scenario!!.people[index].observations?:DemoObservations(index in setOf(4,6,8,9,10),index in setOf(4,5,6,7,8,10))
        return startedAt.takeIf {flags.impact} to startedAt.takeIf {flags.enclosed}
    }
    fun projectClock(view:IncidentView)=if(!closed&&simulationStartedAt!=null&&view.start.eventId==sessionId)view.copy(start=view.start.copy(createdAt=simulationStartedAt))else view
    fun allPlacements()=placements+(scenario?.people.orEmpty().mapIndexed {i,p->DemoPlacement(sessionId,p.deviceId,p.deviceId,p.name,64+i,if(p.positionUnknown)null else p.position?.latitude?:p.home.latitude,if(p.positionUnknown)null else p.position?.longitude?:p.home.longitude,"SCENARIO",p.home)})
    fun scenarioJournal()=scenario?.people.orEmpty().filter {it.safety in listOf("SAFE","SOS")}.map {p->IncidentEvent(groupId,p.deviceId,incidentId,p.deviceId,p.name,1,startedAt,IncidentKind.ANSWER,safety=Safety.valueOf(p.safety))}
    /** UI projection only; these values must never be saved as resident homes or GPS fixes. */
    fun displayResidents()=allPlacements().map {Resident(groupId,it.deviceId,1,0,it.home?.displayName?:it.name,it.latitude?:Double.NaN,it.longitude?:Double.NaN,it.home?.floor.orEmpty(),it.home?.room.orEmpty())}
    fun withHomes(records:List<DeviceRecord>)=if(positionMode!="KYOTO")this else copy(placements=placements.map {p->val home=latestDemoHome(records,groupId,sessionId,p.deviceId)?:p.home;home?.let {p.copy(home=it)}?:p})
    fun placeRecords():List<PlaceEvent> = buildList {
        val buildings=mutableSetOf<String>()
        allPlacements().filter {positionMode=="KYOTO"||it.source=="SCENARIO"}.forEach {p->p.home?.let {h->
            if(buildings.add(h.buildingId))add(PlaceEvent(groupId,h.buildingId,p.deviceId,p.name,1,startedAt,PlaceKind.BUILDING,buildingId=h.buildingId,label=h.address.take(80),latitude=h.latitude,longitude=h.longitude,locationReference=BuildingLocationReference(h.address,h.plateauId,if(h.plateauId!=null)"plateau-26100-kyoto-shi-2025" else null)))
            add(PlaceEvent(groupId,p.deviceId,p.deviceId,p.name,2,startedAt,PlaceKind.HOME,buildingId=h.buildingId))
        }}
    }
}
