package io.github.nevaseme.plandmk1

import android.app.Application
import io.github.nevaseme.plandmk1.drops.sharedImpact
import androidx.room.Room
import io.github.nevaseme.plandmk1.data.PlanDatabase
import io.github.nevaseme.plandmk1.data.RoomRecordStore
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.nevaseme.plandmk1.data.InstallationSettings
import io.github.nevaseme.plandmk1.nearby.NearbyPort
import io.github.nevaseme.plandmk1.nearby.NearbyTransport
import io.github.nevaseme.plandmk1.sharing.SharingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PlanDApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, PlanDatabase::class.java, "pland.db").addMigrations(PlanDatabase.MIGRATION_1_2, PlanDatabase.MIGRATION_2_3, PlanDatabase.MIGRATION_3_4, PlanDatabase.MIGRATION_4_5, PlanDatabase.MIGRATION_5_6, PlanDatabase.MIGRATION_6_7, PlanDatabase.MIGRATION_7_8, PlanDatabase.MIGRATION_8_9).build() }
    val store by lazy { RoomRecordStore(database,installation.localId) }
    val quakes by lazy { io.github.nevaseme.plandmk1.data.QuakeStore(database) }
    val responses by lazy { io.github.nevaseme.plandmk1.data.ResponseStore(database){group,incident,id->cloud.venue(group)?.takeIf{it.active(group,incident)}?.allPlacements()?.any{it.deviceId==id}==true} }
    val offlineMaps by lazy { OfflineMapRepository(this) }
    val buildingAssessments by lazy { io.github.nevaseme.plandmk1.buildings.BuildingAssessmentRepository(additionalPackages=listOf("toranomon-building-assessment/")) { assets.open(it) } }
    val installation by lazy { InstallationSettings(this) }
    val sharing by lazy { SharingController(this) }
    val position by lazy { io.github.nevaseme.plandmk1.location.PositionController(this) }
    val eewReceipts by lazy { io.github.nevaseme.plandmk1.eew.EewReceiptStore(java.io.File(filesDir, "eew-receipts")) }
    val eewReceiver by lazy { io.github.nevaseme.plandmk1.eew.EewReceiver(eewReceipts,
        target={
            val group=installation.group.value;val device=installation.localId
            runBlocking(Dispatchers.IO) {
                quakes.roster(group)?.takeIf {r->r.members.any {device in it.deviceIds}}?.let {io.github.nevaseme.plandmk1.eew.EewTarget(group,device)}
            }
        },
        apply={receipt->runBlocking(Dispatchers.IO) {quakes.receiveEew(receipt)}},
        select=installation::selectIncident,
        afterDurableSave={receipt->receipt.target?.let {target->
            runBlocking(Dispatchers.IO) {io.github.nevaseme.plandmk1.cloud.CloudSyncWork.enqueue(this@PlanDApplication,target.groupId,afterEew=true)}
        }},
        onDeferred={android.util.Log.w("PlanD-EEW","速報は保存済みです。共有の予約を後で再試行します",it)},
    ) }
    val deviceRecords by lazy { io.github.nevaseme.plandmk1.data.DeviceRecordStore(database) }
    val dropEvents by lazy { io.github.nevaseme.plandmk1.drops.DropEventStore(java.io.File(filesDir, "drop-notifications")) }
    val drops by lazy { io.github.nevaseme.plandmk1.drops.DropRecording(android.os.SystemClock::elapsedRealtimeNanos) {record->
        dropEvents.save(record)
        runBlocking(Dispatchers.IO) {deviceRecords.append(record.sharedImpact(installation.localId))}
        maintenance.launch {withContext(Dispatchers.Main){recordsChanged()}}
    } }
    var dropSourceFactory: () -> io.github.nevaseme.plandmk1.drops.DropSource = { io.github.nevaseme.plandmk1.drops.AndroidDropSource(this) }
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val motion by lazy { io.github.nevaseme.plandmk1.motion.MotionRecorder(System::currentTimeMillis, android.os.SystemClock::elapsedRealtimeNanos) }
    val impactMonitoring by lazy { io.github.nevaseme.plandmk1.impact.ImpactMonitoring(android.os.SystemClock::elapsedRealtimeNanos) }
    val impactWakePolicy by lazy { io.github.nevaseme.plandmk1.impact.AndroidImpactWakePolicy(this) }
    // Explicit in-process calibration tests only; never enabled by normal recording or persisted.
    var motionCalibrationEnabled = false
    var motionSourceFactory: () -> io.github.nevaseme.plandmk1.motion.MotionSource = {
        io.github.nevaseme.plandmk1.motion.AndroidMotionSource(this)
    }
    var positionSourceFactory: () -> (suspend () -> io.github.nevaseme.plandmk1.location.PositionFix) = {
        val source = io.github.nevaseme.plandmk1.location.AndroidPositionSource(this)
        val acquire: suspend () -> io.github.nevaseme.plandmk1.location.PositionFix = { source.acquire() }
        acquire
    }
    val lab by lazy { io.github.nevaseme.plandmk1.lab.LabController(store, installation.localId) {position.state.value.active} }
    val cloud by lazy { io.github.nevaseme.plandmk1.cloud.CloudController(this) }
    fun recordsChanged() {sharing.changed();lab.changed();cloud.changed()}
    // In-process injection for lifecycle tests, never exposed through IPC or UI.
    var sharingPortFactory: (CoroutineScope) -> NearbyPort = { NearbyTransport(this, it) }
    override fun onCreate() {
        super.onCreate()
        io.github.nevaseme.plandmk1.eew.EewRegistration.schedule(this)
        maintenance.launch { runCatching { dropEvents.records().forEach {deviceRecords.append(it.sharedImpact(installation.localId))} }.onFailure { android.util.Log.w("PlanD-drops", "Retention cleanup failed", it) } }
        retryEewReceipts()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { sharing.setForeground(true); lab.setVisible(true); cloud.setVisible(true); position.foreground = true; retryEewReceipts() }
            override fun onStop(owner: LifecycleOwner) { sharing.setForeground(false); lab.setVisible(false); cloud.setVisible(false); position.foreground = false }
        })
    }
    private fun retryEewReceipts() {maintenance.launch {
        runCatching {eewReceiver.retryPending()}.onFailure {android.util.Log.e("PlanD-EEW","速報の保存を再試行できませんでした",it)}
        withContext(Dispatchers.Main) {recordsChanged()}
    }}
}
