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
    val english=LocalUiLanguage.current=="en"
    val owner=LocalLifecycleOwner.current
    val visible=LocalMapVisible.current
    var ready by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("preparing") }
    var result by remember { mutableStateOf<TsunamiComputedResult?>(null) }
    val computer=remember(context) { TsunamiComputer(context, onEvent={ event -> when(event) {
        TsunamiComputer.Event.Ready -> {ready=true;status="enter"}
        is TsunamiComputer.Event.Progress -> status="loading"
        is TsunamiComputer.Event.Result -> {running=false;result=event.value;status="result"}
        is TsunamiComputer.Event.Error -> {running=false;result=null;status="error"}
    }}) }
    fun cancel() { computer.cancel();if(running){running=false;status="cancelled"} }
    DisposableEffect(computer,owner) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP)cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);computer.close()}
    }
    LaunchedEffect(visible) {if(!visible)cancel()}
    LaunchedEffect(status,english,result,enabled) {onStatus(tsunamiText(if(!enabled) "off" else status,english,result?.height?.toInt()?.toString().orEmpty()))}
    LaunchedEffect(result,enabled) {onResult(if(enabled)result else null)}
    LaunchedEffect(height,enabled,ready,visible) {
        cancel()
        val number=TsunamiPrecomputedHeight.parse(height)
        if(result?.height!=number)result=null
        if(number==null) {
            result=null
            status=if(height.isBlank()) "enter" else "range"
        } else if(enabled && ready && visible && result?.height!=number) {
            result=null
            kotlinx.coroutines.delay(350)
            running=true;status="loading"
            computer.compute(number)
        }
    }
}

/** Color key only; calculation details are not part of the map legend. */
@Composable
fun TsunamiLegend() {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tsunamiText("legend",LocalUiLanguage.current=="en"),style=MaterialTheme.typography.titleMedium)
        TsunamiDepth.labels.forEachIndexed { i,label ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width=36.dp,height=20.dp).background(Color(android.graphics.Color.parseColor(TsunamiDepth.colors[i])),androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Text(if(i==0) "0.1–0.5m" else if(i==4)tsunamiText("deep",LocalUiLanguage.current=="en")else label,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
