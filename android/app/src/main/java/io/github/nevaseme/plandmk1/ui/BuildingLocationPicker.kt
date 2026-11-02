package io.github.nevaseme.plandmk1.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalFocusManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nevaseme.plandmk1.core.BuildingLocationReference
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.buildings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun BuildingLocationPicker(initial:MapPoint,onConfirm:(MapPoint,String?)->Unit,onCancel:()->Unit,initialQuery:String="",initialMessage:String="",initialCandidate:AddressCandidate?=null,onHomeConfirm:((MapPoint,BuildingLocationReference)->Unit)?=null,autoSearch:Boolean=false,initialReference:BuildingLocationReference?=null,locate:(suspend ()->MapPoint)?=null) {
 val app=LocalContext.current.applicationContext as PlanDApplication
 val repo=remember {CityAddresses(app)};val scope=rememberCoroutineScope();val keyboard=LocalSoftwareKeyboardController.current
 val focus=LocalFocusManager.current
 fun dismissKeyboard(){keyboard?.hide();focus.clearFocus()}
 var locating by remember {mutableStateOf(false)}
 var centerRequest by remember {mutableStateOf(0L)}
 var center by remember {mutableStateOf(initial)};var point by remember {mutableStateOf(initial)}
 var query by remember {mutableStateOf(initialQuery)};var searching by remember {mutableStateOf(false)};var message by remember {mutableStateOf(initialMessage)}
 var choices by remember {mutableStateOf<List<AssessedBuilding>>(emptyList())};var selected by remember {mutableStateOf<AssessedBuilding?>(null)}
 var address by remember {mutableStateOf(initialCandidate?.let {it.address+if(it.precision in listOf("town","block","area"))" 付近" else ""}?:initialReference?.address?:initialQuery)}
 var searchSource by remember {mutableStateOf(initialCandidate?.reference(initialQuery)?:initialReference?.searchReference)}
 var editingAddress by remember {mutableStateOf(false)}
 var addressPoint by remember {mutableStateOf(initial)}
 var matches by remember {mutableStateOf<List<AddressCandidate>>(emptyList())}
 fun useCandidate(c:AddressCandidate){center=MapPoint(c.latitude,c.longitude);point=center;centerRequest++;addressPoint=center;address=c.address+if(c.precision in listOf("town","area","block"))" 付近" else "";searchSource=c.reference(query);matches=emptyList();message=if(c.precision in listOf("town","area","block"))"住所付近を表示しています。ピンを住居に合わせてください" else ""}
 fun currentLocation(){if(locating)return;dismissKeyboard();scope.launch {locating=true;message="現在地を確認しています…";try {val fix=withTimeoutOrNull(15_000){locate?.invoke()?:io.github.nevaseme.plandmk1.location.AndroidPositionSource(app).acquire().let {MapPoint(it.latitude,it.longitude)}};if(fix==null)message="現在地を取得できません。住所検索か地図で選んでください" else {center=fix;point=fix;centerRequest++;message=""}}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"現在地を取得できません。住所検索か地図で選んでください"}finally{locating=false}}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.any{it})currentLocation()else message="現在地を使うには位置情報を許可してください。住所検索でも選べます"}
 fun requestLocation(){dismissKeyboard();if(locate!=null||listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any{ContextCompat.checkSelfPermission(app,it)==PackageManager.PERMISSION_GRANTED})currentLocation()else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
 var busy by remember {mutableStateOf(false)}
 fun search(){if(searching)return;dismissKeyboard();scope.launch {searching=true;message="";try{val found=repo.candidates(query,app.cloud.state.value.municipality?.code);if(found.isEmpty())message="場所が見つかりません。住所を詳しく入力してください" else if(found.size==1)useCandidate(found.single())else matches=found}catch(e:CancellationException){throw e}catch(_:Exception){message="検索できませんでした。もう一度お試しください"}finally{searching=false}}}
 LaunchedEffect(Unit){if(autoSearch&&query.isNotBlank())search()}
 LaunchedEffect(point) {if(kotlin.math.hypot((point.latitude-addressPoint.latitude)*111000,(point.longitude-addressPoint.longitude)*91000)>2){address="";addressPoint=point};busy=true;selected=null;choices=emptyList();try {choices=app.buildingAssessments.atPoint(point.latitude,point.longitude,6.2).map{it.building};if(choices.size==1)selected=choices.single()}catch(e:CancellationException){throw e}catch(_:Exception){message="建物の輪郭を読み込めません。ピンの位置で登録できます"}finally{busy=false}}
 Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
  Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("住居の位置を選ぶ",style=MaterialTheme.typography.titleLarge);TextButton(onClick=onCancel){Text("戻る")}}
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){AddressSearchField(query,{query=it},::search,Modifier.weight(1f));Button(enabled=!searching&&query.isNotBlank(),onClick={search()}){Text("検索")}}
  if(matches.isNotEmpty())Column(Modifier.fillMaxWidth().heightIn(max=200.dp).verticalScroll(rememberScrollState())){matches.forEach {c->TextButton(onClick={dismissKeyboard();useCandidate(c)}){Text(c.address+if(c.precision in listOf("town","area","block"))" 付近" else "")}}}
  Box(Modifier.weight(1f).fillMaxWidth().padding(top=8.dp).pointerInput(Unit){awaitEachGesture {awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);dismissKeyboard()}}) {
   ResidentMap(emptyList(),center,true,{point=it},{},{message=it},Modifier.fillMaxSize(),selectedFootprint=selected?.geometry,onCenterChanged={point=it},initialZoom=18.0,centerRequest=centerRequest)
   FilledTonalButton(onClick={requestLocation()},enabled=!locating,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)){PlanIcon("target",null);Spacer(Modifier.width(6.dp));Text(if(locating)"取得中…" else "現在地")}
   Canvas(Modifier.align(Alignment.Center).offset(y=(-20).dp).size(32.dp,40.dp)){val r=size.width/2;val path=Path().apply{moveTo(r,size.height);lineTo(2f,r);lineTo(size.width-2f,r);close()};drawPath(path,Color(0xFF155EEF));drawCircle(Color.White,r,androidx.compose.ui.geometry.Offset(r,r));drawCircle(Color(0xFF155EEF),r-3.dp.toPx(),androidx.compose.ui.geometry.Offset(r,r));drawCircle(Color.White,4.dp.toPx(),androidx.compose.ui.geometry.Offset(r,r))}
  }
  Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
   if(choices.size>1)choices.forEachIndexed {i,b->FilterChip(selected=b==selected,onClick={selected=b},label={Text("建物候補 ${i+1}")})}
   if(editingAddress)OutlinedTextField(address,{address=it.take(240)},label={Text("登録する住所・建物名")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={dismissKeyboard();editingAddress=false}),trailingIcon={if(address.isNotEmpty())IconButton(onClick={address=""}){PlanIcon("close","住所を消す")}})
   else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(address.ifBlank {"住所・建物名を入力（任意）"},Modifier.weight(1f),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);TextButton(onClick={editingAddress=true}){Text("編集")}}
   Button(enabled=!busy&&!searching&&(choices.size<=1||selected!=null),onClick={dismissKeyboard();val ref=BuildingLocationReference(address.trim().ifBlank{"地図で選択した住居"},selected?.id,if(selected!=null)"plateau-26100-kyoto-shi-2025" else null,searchSource);if(onHomeConfirm!=null)onHomeConfirm(point,ref)else onConfirm(point,selected?.id)},modifier=Modifier.fillMaxWidth()){Text("この位置にする")}
  }
 }}}
}
