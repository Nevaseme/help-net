package io.github.nevaseme.plandmk1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ApprovedHeader(title:String,date:String?,back:Boolean=false,onNavigate:()->Unit,onSummary:(()->Unit)?=null,onDetails:(()->Unit)?=null,noInternet:Boolean=false,compact:Boolean=false) {
    val offlineLabel=uiText("internet_unavailable")
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        IconButton(onClick=onNavigate,modifier=Modifier.size(48.dp)){PlanIcon(if(back)"back" else "menu",uiText(if(back)"back" else "open_menu"))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,fontSize=if(compact)20.sp else 23.sp,lineHeight=if(compact)26.sp else 29.sp,fontWeight=FontWeight(750))
            date?.let {Text(it,fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        if(compact)Box(Modifier.width(if(LocalDensity.current.fontScale>1.3f)24.dp else 76.dp),contentAlignment=Alignment.CenterEnd){
            if(noInternet) {
                val ink=MaterialTheme.colorScheme.onSurfaceVariant
                if(LocalDensity.current.fontScale>1.3f)androidx.compose.foundation.Canvas(Modifier.size(20.dp).semantics {contentDescription=offlineLabel}) {
                    val stroke=androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx())
                    for(f in listOf(.9f,.6f,.3f))drawArc(ink,210f,120f,false,topLeft=androidx.compose.ui.geometry.Offset(size.width*(1-f)/2,size.height*(1-f)),size=androidx.compose.ui.geometry.Size(size.width*f,size.height*f),style=stroke)
                    drawLine(ink,androidx.compose.ui.geometry.Offset(2.dp.toPx(),2.dp.toPx()),androidx.compose.ui.geometry.Offset(size.width-2.dp.toPx(),size.height-2.dp.toPx()),1.8.dp.toPx())
                } else Text("No internet",fontSize=11.sp,lineHeight=16.sp,color=ink,maxLines=1,softWrap=false,modifier=Modifier.semantics {contentDescription=offlineLabel})
            }
        }
        if(onDetails!=null)TextButton(onClick=onDetails,contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.heightIn(min=48.dp)){Text(uiText("incident_details_action"),fontSize=14.sp)}
        if(onSummary!=null)TextButton(onClick=onSummary,contentPadding=PaddingValues(0.dp)){Text(uiText("area_summary"),fontSize=12.sp,fontWeight=FontWeight.Bold)}
    }
}

@Composable fun ApprovedMenu(groupLabel:String,onClose:()->Unit,onNavigate:(Int)->Unit,onSettings:()->Unit,administrator:Boolean=false) {
    ApprovedSheet(uiText("menu"),onClose) {
        Column {
            ApprovedMenuRow(uiText("my_registration"),"person",{onNavigate(3)},uiText("registration_summary"))
            HorizontalDivider()
            ApprovedMenuRow(uiText("group_sharing"),"response",{onNavigate(4)},groupLabel)
            HorizontalDivider()
            ApprovedMenuRow(uiText("training_incidents"),"list",{onNavigate(6)})
            HorizontalDivider()
            if(administrator){ApprovedMenuRow(menuText("管理者画面"),"settings",{onNavigate(12)});HorizontalDivider()}
            ApprovedMenuRow(uiText("menu_app"),"settings",onSettings)
        }
    }
}

@Composable private fun MenuSection(title:String,content:@Composable ColumnScope.()->Unit) {
    Column {
        Text(title,Modifier.padding(bottom=4.dp).semantics { heading() },fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable fun ApprovedTabs(selected: Int, select: (Int) -> Unit) {
    val tabHeight = (72 + ((LocalDensity.current.fontScale - 1f).coerceAtLeast(0f) * 48)).dp
    Column {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().height(tabHeight).selectableGroup().background(MaterialTheme.colorScheme.surface)) {
            listOf(Triple(1,"map","tab_map"), Triple(8,"list","tab_people"), Triple(10,"response","tab_response"), Triple(0,"person","tab_safety")).forEach { (id,icon,label) ->
                val active=selected==id
                val color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).selectable(selected=active,role=Role.Tab,onClick={select(id)}),horizontalAlignment=Alignment.CenterHorizontally) {
                    CompositionLocalProviderContentColor(color) {
                        Column(Modifier.weight(1f).padding(top=9.dp,bottom=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            PlanIcon(icon,null,Modifier.size(27.dp))
                            Text(uiText(label),fontSize=13.sp,lineHeight=16.sp,fontWeight=FontWeight(750),modifier=Modifier.padding(horizontal=3.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if(active)color else Color.Transparent))
                }
            }
        }
    }
}

@Composable private fun CompositionLocalProviderContentColor(color:Color,content:@Composable ()->Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides color, content=content)
}

/** Content-scoped sheet keeps navigation visible and follows the drag handle. */
@Composable fun ApprovedSheet(title:String,onClose:()->Unit,footer:(@Composable ()->Unit)?=null,scrollContent:Boolean=true,maxHeightFraction:Float=1f,content:@Composable ColumnScope.()->Unit) {
    val handleLabel=if(LocalUiLanguage.current=="en")"Close panel" else "パネルを閉じる"
    val closeSheet by rememberUpdatedState(onClose)
    val swipeThreshold=with(LocalDensity.current){80.dp.toPx()}
    val offset=remember {Animatable(0f)}
    var dragDistance by remember {mutableFloatStateOf(0f)}
    var dragging by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    BackHandler(onBack=onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dismissDistance=with(LocalDensity.current){maxHeight.toPx()}
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=.45f)).clickable(onClick=onClose))
        Surface(Modifier.fillMaxWidth().heightIn(max=maxHeight*maxHeightFraction).align(Alignment.BottomCenter).offset {IntOffset(0,(if(dragging)dragDistance else offset.value).roundToInt())}.testTag("approved-sheet"),shape=RoundedCornerShape(topStart=25.dp,topEnd=25.dp),shadowElevation=10.dp,color=MaterialTheme.colorScheme.surface) {
            Column {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width=72.dp,height=48.dp)
                    .testTag("sheet-drag-handle").semantics {contentDescription=handleLabel}
                    .clickable(role=Role.Button,onClick={closeSheet()})
                    .pointerInput(Unit) {detectVerticalDragGestures(
                        onDragStart={dragDistance=offset.value;dragging=true;scope.launch {offset.stop()}},
                        onDragCancel={scope.launch {offset.snapTo(dragDistance);dragging=false;offset.animateTo(0f,tween(180))}},
                        onDragEnd={scope.launch {offset.snapTo(dragDistance);dragging=false;if(dragDistance>swipeThreshold){offset.animateTo(dismissDistance,tween(180));closeSheet()}else offset.animateTo(0f,tween(180))}},
                        onVerticalDrag={change,amount->change.consume();dragDistance=(dragDistance+amount).coerceAtLeast(0f)})},contentAlignment=Alignment.Center) {
                    Box(Modifier.size(34.dp,4.dp).background(MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(3.dp)))
                }
                if(title.isNotBlank())Row(Modifier.fillMaxWidth().padding(start=21.dp,end=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f),fontSize=23.sp,fontWeight=FontWeight.ExtraBold,lineHeight=29.sp)
                    ApprovedClose(onClose)
                }
                Column(Modifier.weight(1f,fill=false).fillMaxWidth().then(if(scrollContent)Modifier.verticalScroll(rememberScrollState()).padding(start=21.dp,end=21.dp,top=8.dp,bottom=24.dp)else Modifier),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
                if(footer!=null) { HorizontalDivider();Column(Modifier.fillMaxWidth().padding(16.dp),content={footer()}) }
            }
        }
    }
}

@Composable fun LanguageSettings(onNavigate:((Int)->Unit)?=null,onAutostart:(()->Unit)?=null,onClose:()->Unit) {
    val selected=LocalUiLanguage.current
    val change=LocalSetUiLanguage.current
    val appearance=io.github.nevaseme.plandmk1.ui.theme.LocalAppearance.current
    val changeAppearance=io.github.nevaseme.plandmk1.ui.theme.LocalSetAppearance.current
    ApprovedSheet(uiText("menu_app"),onClose) {
        Text(uiText("appearance"),fontSize=16.sp,fontWeight=FontWeight.Bold)
        Column(Modifier.selectableGroup()) {
            listOf("system" to "appearance_system","light" to "appearance_light","dark" to "appearance_dark").forEach {(value,key)->
                Row(Modifier.fillMaxWidth().selectable(appearance==value,role=Role.RadioButton,onClick={changeAppearance(value)}).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiText(key),Modifier.weight(1f));RadioButton(appearance==value,onClick=null)
                }
            }
        }
        HorizontalDivider()
        Text(menuText("言語 / Language"),fontSize=16.sp,fontWeight=FontWeight(750))
        listOf("ja" to if(selected=="en")"Japanese" else "日本語","en" to "English").forEach { (tag,label) ->
            OutlinedCard(onClick={change(tag)},modifier=Modifier.fillMaxWidth(),border=androidx.compose.foundation.BorderStroke(1.dp,if(selected==tag)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                colors=CardDefaults.outlinedCardColors(containerColor=if(selected==tag)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().heightIn(min=58.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(label,Modifier.weight(1f),fontSize=16.sp);RadioButton(selected==tag,onClick=null,modifier=Modifier.size(22.dp))
                }
            }
        }
        if(onNavigate!=null) {
            HorizontalDivider()
            Column {
                if(onAutostart!=null) ApprovedMenuRow(uiText("autostart_settings"),"settings",onAutostart)
                ApprovedMenuRow(uiText("position_history"),"target",{onNavigate(9)})
                ApprovedMenuRow(uiText("offline_map"),"map",{onNavigate(5)})
                ApprovedMenuRow(uiText("app_information"),"settings",{onNavigate(7)})
            }
        }
    }
}

@Composable fun ApprovedClose(onClose:()->Unit,description:String=uiText("close")) {
    Surface(onClick=onClose,shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.size(48.dp)) {
        Box(contentAlignment=Alignment.Center){PlanIcon("close",description,Modifier.size(20.dp))}
    }
}

@Composable fun ApprovedSwitch(checked:Boolean,onChange:(Boolean)->Unit,label:String) {
    Box(Modifier.size(48.dp)
        .toggleable(value=checked,role=Role.Switch,onValueChange=onChange)
        .then(Modifier.semantics { contentDescription=label }),contentAlignment=Alignment.Center) {
      Box(Modifier.size(45.dp,28.dp).background(if(checked)Color(0xFF155EEF)else Color(0xFF98A2B3),androidx.compose.foundation.shape.CircleShape).padding(3.dp)) {
        Box(Modifier.size(22.dp).align(if(checked)Alignment.CenterEnd else Alignment.CenterStart).background(Color.White,androidx.compose.foundation.shape.CircleShape))
      }
    }
}

@Composable fun ApprovedMenuRow(label:String,icon:String,onClick:()->Unit,detail:String?=null) {
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick).heightIn(min=52.dp).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        PlanIcon(icon,null,Modifier.size(22.dp))
        Column(Modifier.weight(1f)){Text(label,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium);detail?.let {Text(it,fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}}
        CompositionLocalProviderContentColor(MaterialTheme.colorScheme.onSurfaceVariant){PlanIcon("chevron",null,Modifier.size(16.dp))}
    }
}

/** An external label and a plain input match the approved forms without floating labels. */
@Composable fun ApprovedField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,enabled:Boolean=true,singleLine:Boolean=false,minLines:Int=1,placeholder:String?=null) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=14.sp,fontWeight=FontWeight.Bold)
        OutlinedTextField(value,onValueChange,modifier=Modifier.fillMaxWidth().heightIn(min=if(minLines>1)100.dp else 48.dp).semantics {contentDescription=label},enabled=enabled,singleLine=singleLine,minLines=minLines,
            shape=RoundedCornerShape(7.dp),textStyle=LocalTextStyle.current.copy(fontSize=16.sp,lineHeight=24.sp),
            placeholder=placeholder?.let {{Text(it,fontSize=14.sp)}},
            colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outline))
    }
}
