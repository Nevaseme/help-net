package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevaseme.plandmk1.tsunami.*
import java.util.Locale

@Composable
fun TsunamiMapCalculation(enabled:Boolean=true, height:String="", onResult:(TsunamiComputedResult?)->Unit={}, onStatus:(String)->Unit={}) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val visible=LocalMapVisible.current
    var ready by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("表示の準備中です") }
    var result by remember { mutableStateOf<TsunamiComputedResult?>(null) }
    val computer=remember(context) { TsunamiComputer(context, onEvent={ event -> when(event) {
        TsunamiComputer.Event.Ready -> {ready=true;status="高さを入力して表示してください"}
        is TsunamiComputer.Event.Progress -> status=event.message
        is TsunamiComputer.Event.Result -> {running=false;result=event.value;status="高さ ${event.value.height.toInt()}m の事前計算結果"}
        is TsunamiComputer.Event.Error -> {running=false;result=null;status=event.message}
    }}) }
    fun cancel() { computer.cancel();if(running){running=false;status="読込を中止しました"} }
    DisposableEffect(computer,owner) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP)cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);computer.close()}
    }
    LaunchedEffect(visible) {if(!visible)cancel()}
    LaunchedEffect(status) {onStatus(status)}
    LaunchedEffect(result,enabled) {onResult(if(enabled)result else null)}
    LaunchedEffect(height,enabled,ready,visible) {
        cancel()
        val number=TsunamiPrecomputedHeight.parse(height)
        if(result?.height!=number)result=null
        if(number==null) {
            result=null
            status=if(height.isBlank()) "津波の高さを入力してください" else "0〜35m・1m刻みで入力してください"
        } else if(enabled && ready && visible && result?.height!=number) {
            result=null
            kotlinx.coroutines.delay(350)
            running=true;status="計算済みの結果を読み込んでいます"
            computer.compute(number)
        }
    }
}

/** Color key only; calculation details are not part of the map legend. */
@Composable
fun TsunamiLegend() {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("津波の浸水深",style=MaterialTheme.typography.titleMedium)
        TsunamiDepth.labels.forEachIndexed { i,label ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width=36.dp,height=20.dp).background(Color(android.graphics.Color.parseColor(TsunamiDepth.colors[i])),androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Text(if(i==0) "0.1〜0.5m" else label,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
