package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.core.*

/** Aggregate-only model: no person identifiers or drill-through to personal records. */
data class MapNeighborhood(val row:CommunityRow,val point:MapPoint?,val activeHigh:Int,val reports:List<Pair<String,String>> = emptyList())

fun activeHighReports(changes:List<ResponseChange>,regionId:String):Int =
    changes.filterIsInstance<ResponseChange.Report>().filterNot {it.cancelled}
        .groupBy {it.reportId}.count {(id,revisions)->
            val target=ResponseTarget(TargetKind.REPORT,id)
            val progress=changes.filterIsInstance<ResponseChange.Progress>().filter {it.target==target}
            revisions.any {regionId in it.regionIds} &&
                changes.filterIsInstance<ResponseChange.Priority>().any {it.target==target&&it.urgency==Urgency.HIGH} &&
                (progress.isEmpty()||progress.any {it.status!=WorkStatus.DONE})
        }

@Composable fun NeighborhoodStatistics(rows:List<CommunityRow>) {
    val en=LocalUiLanguage.current=="en"
    Column(Modifier.fillMaxWidth()) {
        Text(if(en)"Safety by neighborhood" else "町内会別の安否統計",fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=12.dp))
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.widthIn(min=if(en)420.dp else 324.dp).padding(vertical=10.dp)) {
                Text(if(en)"Neighborhood" else "町内会",Modifier.width(if(en)132.dp else 100.dp),fontSize=12.sp)
                listOf("sos","unreported","safe","rescued").forEach {Text(communityLabel(it,en),Modifier.width(if(en)72.dp else 56.dp),fontSize=12.sp)}
            }
            rows.forEach {r->
                HorizontalDivider()
                Row(Modifier.padding(vertical=12.dp)) {
                    Text(if(en)r.nameEn else r.nameJa,Modifier.width(if(en)132.dp else 100.dp),fontSize=13.sp,fontWeight=FontWeight.Bold)
                    listOf(r.sos,r.unreported,r.safe,r.rescued).forEach {Text(it.toString(),Modifier.width(if(en)72.dp else 56.dp),fontSize=13.sp)}
                }
            }
        }
    }
}

@Composable fun NeighborhoodDetail(neighborhood:MapNeighborhood,details:Boolean,onZoom:()->Unit,onDetails:()->Unit,onReport:((String)->Unit)?=null) {
    val en=LocalUiLanguage.current=="en";val r=neighborhood.row
    listOf("sos" to r.sos,"unreported" to r.unreported,"safe" to r.safe,"rescued" to r.rescued).chunked(2).forEach {pair->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            pair.forEach {(key,count)->Column(Modifier.weight(1f)) {Text(communityLabel(key,en),fontSize=13.sp);Text(count.toString(),fontSize=24.sp,fontWeight=FontWeight.Bold);HorizontalDivider()}}
        }
    }
    Text((if(en)"Active high-urgency reports: " else "未完了の高緊急度通報：")+neighborhood.activeHigh,fontWeight=FontWeight.Bold)
    Text(if(en)"Not started · Planned · In progress" else "未対応・対応予定・対応中",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if(r.materials.isEmpty())if(en)"Materials: no shortfall" else "資材：不足なし" else if(en)"Materials: shortfall" else "資材：不足あり",fontWeight=FontWeight.Bold)
    if(details) {
        r.materials.forEach {m->Text("${m.name} · ${m.shortfall} ${m.unit}");if(m.conflict)Text(communityLabel("conflict",en),fontSize=12.sp)}
        if(neighborhood.reports.isNotEmpty())Text(if(en)"Accessible reports" else "閲覧できる通報",fontWeight=FontWeight.Bold)
        neighborhood.reports.forEach {(id,title)->TextButton(onClick={onReport?.invoke(id)},enabled=onReport!=null){Text(title);Text(" ›")}}
        Text(if(en)"Only neighborhood totals are shown here." else "この画面では町内会の集計情報を表示します。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(neighborhood.point!=null)TextButton(onClick=onZoom){Text(if(en)"Zoom to this neighborhood" else "この町内会を拡大")}
    if(!details)Button(onClick=onDetails,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(en)"View details" else "詳細を見る")}
}

fun communityNamesFor(venue:io.github.nevaseme.plandmk1.demo.VenueSession?,groupName:String):List<CommunityName> =
    venue?.scenario?.communities?.map {CommunityName(it.id,it.nameJa.removeSuffix("（デモ）").removeSuffix("（訓練）"),it.nameEn.removeSuffix(" (demo)").removeSuffix(" (drill)"),it.boundary)}?.let {if(venue.positionMode=="ACTUAL")it+CommunityName("venue","会場参加者","Venue participants")else it}?:listOf(CommunityName("group",groupName,groupName))

fun communityPeopleFor(residents:List<Resident>,care:List<CareEvent>,venue:io.github.nevaseme.plandmk1.demo.VenueSession?,roster:MemberRoster?=null):List<CommunityPerson> = residents.map {p->
    val scenarioPerson=venue?.scenario?.people?.find {it.deviceId==p.originId}
    val placement=venue?.allPlacements()?.find {it.deviceId==p.originId}
    val region=when {venue?.scenario==null->"group";scenarioPerson!=null->scenarioPerson.communityId;venue.positionMode=="ACTUAL"->"venue";else->placement?.communityId?:placement?.home?.let {demoCommunityAtPoint(it.latitude,it.longitude,venue.scenario)}}
    CommunityPerson(p.originId,p.name,region,residentMapState(p,care,roster).name,scenarioPerson!=null,placement?.home?.buildingId)
}

fun reportCategoryGroup(category:String)=when(category){"道路閉塞","ROAD_BLOCKED"->"ROAD";"建物倒壊","BUILDING_COLLAPSE"->"BUILDING";else->"OTHER"}

/** Baseline neighborhood shortfalls describe unfinished response work only. */
fun activeMaterialChanges(changes:List<ResponseChange>)=changes.filter {change->
    if(change !is ResponseChange.Material)true else {
        val progress=changes.filterIsInstance<ResponseChange.Progress>().filter {it.target==change.target}
        progress.isEmpty()||progress.any {it.status!=WorkStatus.DONE}
    }
}

fun demoCommunityAtPoint(latitude:Double,longitude:Double,scenario:io.github.nevaseme.plandmk1.demo.VenueScenario):String? {
    val bounded=scenario.communities.filter {(it.boundary?.size?:0)>=3}
    if(bounded.isEmpty())return demoCommunityFor(longitude,scenario.communityLongitudeCuts)
    return bounded.firstOrNull {c->val ring=c.boundary!!;var inside=false;var j=ring.lastIndex
        for(i in ring.indices){val a=ring[i];val b=ring[j];if((a[1]>latitude)!=(b[1]>latitude)&&longitude<(b[0]-a[0])*(latitude-a[1])/(b[1]-a[1])+a[0])inside=!inside;j=i};inside
    }?.id
}
