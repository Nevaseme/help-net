package io.github.nevaseme.plandmk1.maps

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import kotlin.math.cos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.maplibre.geojson.MultiPolygon

data class OfflineMapState(
    val exists: Boolean = false,
    val busy: Boolean = true,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val resources: Long = 0,
    val expected: Long = 0,
    val bytes: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val error: String? = null,
    val cityCode:String?=null,
    val cityName:String?=null,
)

class OfflineMapRepository(private val context: Context) {
    private val manager = OfflineManager.getInstance(context)
    private var region: OfflineRegion? = null
    private var downloadRequested = false
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var selectedCity:String?=null
    private var selectionGeneration=0
    private var preparation:Job?=null
    private val mutable = MutableStateFlow(OfflineMapState())
    val state = mutable.asStateFlow()

    init { restore() }

    /** Starts/resumes the event map once; completion and manual pause remain idempotent. */
    fun prepareCity(code:String) {
        if(preparation?.isActive==true&&selectedCity==code)return
        selectCity(code)
        preparation?.cancel()
        preparation=scope.launch {
            val ready=withTimeoutOrNull(30_000){state.first{!it.busy&&it.cityCode==code}}?:return@launch
            if(ready.complete||ready.downloading)return@launch
            if(ready.exists)resume() else downloadCity(code)
        }
    }

    fun selectCity(code:String?) {
        if(code==selectedCity)return
        pause();selectedCity=code;region=null;mutable.value=OfflineMapState(cityCode=code,cityName=cityName(code));restore()
    }
    private fun cityName(code:String?)=when(code){"26100"->"京都市";"22205"->"熱海市";"13103"->"港区";else->null}
    private fun restore() {
        val generation=++selectionGeneration
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                if(generation!=selectionGeneration)return
                val owned = regions.orEmpty().filter { if(selectedCity!=null)it.metadata.decodeToString()=="pland-city-v1:$selectedCity" else it.metadata.decodeToString().startsWith("pland-v1:") }
                region = owned.maxByOrNull { it.id }
                region?.let { bind(it); refresh(it) } ?: run { mutable.value = OfflineMapState(busy = false,cityCode=selectedCity,cityName=cityName(selectedCity)) }
            }
            override fun onError(error: String) { mutable.value = OfflineMapState(busy = false, cityCode=selectedCity,cityName=cityName(selectedCity), error = "地図の保存状態を確認できません：$error") }
        })
    }

    fun downloadCity(code:String) {
        if(mutable.value.busy||region!=null||code!=selectedCity)return
        if(cityName(code)==null){mutable.value=mutable.value.copy(error="活動地域を設定してください");return}
        if(StatFs(context.filesDir.path).availableBytes<160L*1024*1024){mutable.value=mutable.value.copy(error="地図を保存するには160MB以上の空き容量を確保してください");return}
        mutable.value=mutable.value.copy(busy=true,error=null);downloadRequested=true
        val generation=selectionGeneration
        scope.launch {
            try {
                val geometry=withContext(Dispatchers.IO){context.assets.open("cities/$code.json").bufferedReader().use {Json.parseToJsonElement(it.readText()).jsonObject.getValue("geometry").toString()}}
                if(generation!=selectionGeneration)return@launch
                val definition=OfflineGeometryRegionDefinition(STYLE,MultiPolygon.fromJson(geometry),8.0,18.0,context.resources.displayMetrics.density,false)
                manager.createOfflineRegion(definition,"pland-city-v1:$code".encodeToByteArray(),object:OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(value:OfflineRegion){if(generation!=selectionGeneration)return;region=value;bind(value);if(downloadRequested)resume() else refresh(value)}
                    override fun onError(error:String){if(generation==selectionGeneration)mutable.value=mutable.value.copy(busy=false,error="保存を開始できません：$error")}
                })
            }catch(e:Exception){if(generation==selectionGeneration)mutable.value=mutable.value.copy(busy=false,error="地域データを読み込めませんでした：${e.message}")}
        }
    }

    fun download(latitude: Double, longitude: Double) {
        if (mutable.value.busy || region != null) return
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -84.98..84.98 || longitude !in -179.89..179.89) {
            mutable.value = OfflineMapState(busy = false, error = "この位置は保存範囲外です。中心位置を変更してください"); return
        }
        if (StatFs(context.filesDir.path).availableBytes < 100L * 1024 * 1024) {
            mutable.value = OfflineMapState(busy = false, error = "地図を保存するには100MB以上の空き容量を確保してください"); return
        }
        mutable.value = OfflineMapState(busy = true, latitude = latitude, longitude = longitude)
        downloadRequested = true
        val dy = 1.0 / 111.32
        val dx = 1.0 / (111.32 * cos(Math.toRadians(latitude)))
        val bounds = LatLngBounds.from(latitude + dy, longitude + dx, latitude - dy, longitude - dx)
        val definition = OfflineTilePyramidRegionDefinition(STYLE, bounds, 14.0, 18.0, context.resources.displayMetrics.density)
        manager.createOfflineRegion(definition, "pland-v1:$latitude,$longitude".encodeToByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                region = offlineRegion; bind(offlineRegion)
                if (downloadRequested) resume() else refresh(offlineRegion)
            }
            override fun onError(error: String) { mutable.value = mutable.value.copy(busy = false, error = "保存を開始できません：$error") }
        })
    }

    private fun bind(value: OfflineRegion) {
        val definition = value.definition
        mutable.value = mutable.value.copy(exists = true, busy = false,
            latitude = definition?.bounds?.center?.latitude, longitude = definition?.bounds?.center?.longitude)
        value.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if(value!=region)return
                if(status.completedResourceSize>49_000_000L){value.setDownloadState(OfflineRegion.STATE_INACTIVE);mutable.value=mutable.value.copy(busy=false,downloading=false,complete=false,error="配信元の保存上限に達しました。地図は未完了です。",bytes=status.completedResourceSize);return}
                mutable.value = mutable.value.copy(busy = false, complete = status.isComplete,
                    downloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE,
                    resources = status.completedResourceCount, expected = status.requiredResourceCount,
                    bytes = status.completedResourceSize, error = if (status.isComplete) null else mutable.value.error)
                if (status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE) value.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) {
                if(value!=region)return
                value.setDownloadState(OfflineRegion.STATE_INACTIVE)
                mutable.value = mutable.value.copy(busy = false, downloading = false, error = "地図保存を中断しました：${error.message}。通信・空き容量を確認して再開してください")
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                value.setDownloadState(OfflineRegion.STATE_INACTIVE)
                mutable.value = mutable.value.copy(downloading = false, error = "地図の保存上限に達しました（$limit）")
            }
        })
    }
    private fun refresh(value: OfflineRegion) {
        value.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if(value!=region)return
                if (status == null) { mutable.value = mutable.value.copy(busy = false, error = "地図の状態を取得できません"); return }
                mutable.value = mutable.value.copy(busy = false, complete = status.isComplete, downloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE,
                    resources = status.completedResourceCount, expected = status.requiredResourceCount, bytes = status.completedResourceSize)
            }
            override fun onError(error: String?) { mutable.value = mutable.value.copy(busy = false, error = error ?: "地図の状態を取得できません") }
        })
    }
    fun resume() {
        if (region == null) return
        downloadRequested = true
        mutable.value = mutable.value.copy(error = null, downloading = true)
        region?.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
    fun pause() {
        preparation?.cancel();preparation=null
        downloadRequested = false
        region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        mutable.value = mutable.value.copy(downloading = false)
    }
    fun delete() {
        val value = region ?: return
        pause(); mutable.value = mutable.value.copy(busy = true)
        value.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { region = null; mutable.value = OfflineMapState(busy = false,cityCode=selectedCity,cityName=cityName(selectedCity)) }
            override fun onError(error: String) { mutable.value = mutable.value.copy(busy = false, error = "削除できません：$error") }
        })
    }
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/positron" }
}
