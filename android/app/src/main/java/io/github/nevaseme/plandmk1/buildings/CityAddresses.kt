package io.github.nevaseme.plandmk1.buildings
import android.content.Context
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

private val addressPattern0=Regex("[\\s　]")
private val addressPattern1=Regex("[‐‑–—−]")
private val addressPattern2=Regex("(\\d)ー(?=\\d)")
private val addressPattern3=Regex("([〇零一二三四五六七八九十0-9]+)丁目")
private val addressPattern4=Regex("(\\d)(?:番地の|番地|番)")
private val addressPattern5=Regex("(\\d)号")
private val addressPattern6=Regex("^(京都府|静岡県|東京都)")
private val addressPattern7=Regex("^(京都市|熱海市)")
private val addressPattern8=Regex("^[^区]+区")
private val addressPattern9=Regex("^((?:京都府)?京都市[^区]+区|(?:左京|右京|上京|中京|下京|北|南|東山|山科|伏見|西京)区).+?通.*?(?:上[るル]|下[るル]|東入[るル]?|西入[るル]?)")
private val addressPattern10=Regex("^(京都府|静岡県|東京都|京都市|熱海市|港区)")
private val addressPattern11=Regex("東京都|港区|六本木")
private val addressPattern12=Regex("静岡県|熱海市")
private val addressPattern13=Regex("京都府|京都市|左京区|右京区|上京区|中京区|下京区|北区|南区|東山区|山科区|伏見区|西京区")
val addressCityCodes=listOf("26100","22205","13103")
data class AddressCandidate(val id:String,val address:String,val longitude:Double,val latitude:Double,val precision:String,val search:String=normalizeAddress(address),val matchLevel:String=precision,val source:String?=null,val sourceVersion:String?=null,val municipalityCode:String?=null) {
    val forms=addressForms(search)
}
private fun chome(s:String):String {if(s.all {it in '0'..'9'})return s;var value=0;var digit=0;for(c in s){if(c=='十'){value+=(if(digit==0)1 else digit)*10;digit=0}else digit=mapOf('〇' to 0,'零' to 0,'一' to 1,'二' to 2,'三' to 3,'四' to 4,'五' to 5,'六' to 6,'七' to 7,'八' to 8,'九' to 9)[c]?:0};return (value+digit).toString()}
fun normalizeAddress(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKC).lowercase().replace(addressPattern0,"").replace(addressPattern1,"-").replace(addressPattern2,"$1-").replace(addressPattern3){chome(it.groupValues[1])+"-"}.replace(addressPattern4,"$1-").replace(addressPattern5,"$1").trimEnd('-')
fun addressForms(s:String):List<String>{
    val city=when {s.startsWith("京都府")->s.substring(3);s.startsWith("静岡県")->s.substring(3);s.startsWith("東京都")->s.substring(3);else->s}
    val ward=when {city.startsWith("京都市")->city.substring(3);city.startsWith("熱海市")->city.substring(3);else->city}
    val i=ward.indexOf('区');val town=if(i>0)ward.substring(i+1)else ward
    return listOf(s,city,ward,town).distinct()
}
fun addressQueryForms(input:String):List<String>{val q=normalizeAddress(input);return listOf(q,q.replace(addressPattern9,"$1")).distinct()}
private fun townFallback(rows:List<AddressCandidate>,qs:List<String>):List<AddressCandidate>{var longest=0;val result=mutableListOf<AddressCandidate>();for(r in rows){if(r.precision!="town")continue;for(q in qs)for(s in r.forms)if(q.startsWith(s)&&q.drop(s.length).firstOrNull()?.let{it=='-'||it in '0'..'9'}==true){if(s.length>longest){result.clear();longest=s.length};if(s.length==longest)result+=r.copy(matchLevel="town")}};return result}
fun searchAddresses(rows:List<AddressCandidate>,query:String,code:String?=null):List<AddressCandidate>{val qs=addressQueryForms(query);if(qs[0].length<2)return emptyList();val explicit=addressPattern10.containsMatchIn(qs[0]);var matches=rows.mapNotNull {r->val rank=qs.minOf {q->when {q in r.forms->if(q==r.search)0 else 1;r.search.contains(q)&&!q.last().isDigit()->2;else->99}};if(rank<99)r to rank else null};if(matches.isEmpty())matches=townFallback(rows,qs).map{it to 3};return matches.sortedWith(compareBy<Pair<AddressCandidate,Int>>({it.second},{if(!explicit&&code!=null&&it.first.municipalityCode==code)0 else 1},{it.first.address.length},{it.first.address})).map{it.first}.distinctBy{Triple(it.address,it.latitude,it.longitude)}.take(12)}
fun resolveAddress(rows:List<AddressCandidate>,query:String,code:String?=null,hits:List<AddressCandidate> = searchAddresses(rows,query,code)):AddressCandidate? {val q=normalizeAddress(query);if(q.length<2)return null;if(hits.size==1)return hits.single();if(q.endsWith("市")||q.endsWith("区")){val area=rows.filter{it.precision=="town"&&it.search.contains(q)};if(area.isNotEmpty())return area.first().copy(id="area:$q",address=query,precision="area",matchLevel="area",latitude=(area.minOf{it.latitude}+area.maxOf{it.latitude})/2,longitude=(area.minOf{it.longitude}+area.maxOf{it.longitude})/2)};return null}
fun addressRegion(query:String):String?=when {addressPattern11.containsMatchIn(query)->"13103";addressPattern12.containsMatchIn(query)->"22205";addressPattern13.containsMatchIn(query)->"26100";else->null}
class CityAddresses(private val context:Context) {
    private val parcels=ParcelSearchRepository {context.assets.open(it)}
    private fun withParcels(hits:List<AddressCandidate>,query:String):List<AddressCandidate> = if(addressRegion(query)?.let{it!="26100"}==true)hits else try {mergeParcelCandidates(hits,parcels.search(query))}catch(_:java.io.FileNotFoundException){hits}
    companion object {private val lock=Mutex();private val cache=mutableMapOf<String,List<AddressCandidate>>()} 
    private fun load(code:String,kind:String)=cache.getOrPut("$code:$kind") {
        readPreparedAddresses(context.assets.open("cities/$code-$kind.prepared.gzip"),code)
    }
    private fun combined(codes:List<String>,places:Boolean=true)=cache.getOrPut("combined:${codes.joinToString()}:$places") {
        codes.flatMap { load(it,"addresses")+if(places)load(it,"places")else emptyList() }
    }
    suspend fun search(code:String,query:String,includePlaces:Boolean=false)=withContext(Dispatchers.IO) {lock.withLock {
        require(code in addressCityCodes) {"対応地域の住所を入力してください"}
        withParcels(searchAddresses(load(code,"addresses")+if(includePlaces)load(code,"places")else emptyList(),query),query)
    }}
    suspend fun searchAll(query:String,includePlaces:Boolean=true)=withContext(Dispatchers.IO) {lock.withLock {
        withParcels(searchAddresses(addressCityCodes.flatMap {load(it,"addresses")+if(includePlaces)load(it,"places")else emptyList()},query),query)
    }}
    suspend fun nearestTown(latitude:Double,longitude:Double)=withContext(Dispatchers.IO) {lock.withLock {
        addressCityCodes.flatMap {load(it,"addresses")}.filter {it.precision=="town"&&it.address.endsWith("町")}.minByOrNull {val dx=(it.longitude-longitude)*91000;val dy=(it.latitude-latitude)*111000;dx*dx+dy*dy}?.takeIf {kotlin.math.abs(it.latitude-latitude)<.02&&kotlin.math.abs(it.longitude-longitude)<.02}
    }}
    suspend fun candidates(query:String,code:String?=null)=withContext(Dispatchers.IO) {lock.withLock {
        val explicit=addressRegion(query);val region=explicit?:code;val facility=query.trim().length>=2&&!query.any{it.isDigit()}
        val targets=if(explicit!=null)listOf(explicit)else if(facility||region==null)addressCityCodes else listOf(region)
        var rows=combined(targets)
        var hits=searchAddresses(rows,query,region);val area=if(query.trim().endsWith("市")||query.trim().endsWith("区"))resolveAddress(rows,query,region,hits)else null;if(area?.precision=="area")hits=listOf(area)
        if(explicit==null&&!facility&&region!=null&&hits.isEmpty()){rows=combined(addressCityCodes);hits=searchAddresses(rows,query,region)}
        withParcels(hits,query)
    }}
    suspend fun geocode(query:String)=withContext(Dispatchers.IO) {lock.withLock {
        val rows=addressCityCodes.flatMap {load(it,"addresses")+load(it,"places")};resolveAddress(rows,query,hits=withParcels(searchAddresses(rows,query),query))
    }}

}

fun AddressCandidate.reference(input:String)=io.github.nevaseme.plandmk1.core.AddressSearchReference(input.take(240),address,latitude,longitude,matchLevel,precision,source?:"legacy",sourceVersion?:"unknown")
