package io.github.nevaseme.plandmk1.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.ui.*
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme

/** Shell-only visual fixture. Uses production components; never writes records or changes groups. */
class DesignFidelityActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        enableEdgeToEdge()
        val initial=intent.getStringExtra("screen")?:"people"
        setContent {PlanDMk1Theme {PlanLocalization {
            val now=remember {System.currentTimeMillis()}
            val samples=remember {loadDesignSamples(this,now)}
            var tab by remember {mutableIntStateOf(if(initial=="safety")0 else if(initial=="map")1 else 8)}
            var menu by remember {mutableStateOf(initial=="menu")}
            var settings by remember {mutableStateOf(initial=="settings")}
            Scaffold(topBar={Column(Modifier.background(Color.White).statusBarsPadding()){
                ApprovedHeader("地震発生","10月26日 14:32",onNavigate={menu=true})
                Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFF3F7FB)).padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Text("一般住民 · 東町内会",Modifier.weight(1f),fontSize=12.sp,color=Color(0xFF155EEF));Text("経過 4:00",fontSize=12.sp)
                }
            }},bottomBar={Column(Modifier.background(Color.White).navigationBarsPadding()){ApprovedTabs(tab){tab=it;menu=false;settings=false}}}) {padding->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if(tab==0)AnswerPanel(false,MapSafety.SOS,"",false,save={_,_->},saveMemo={})
                    else SituationScreen(samples.people,samples.homes,MapPoint(32.7904,130.819),now,preview=true,listOnly=tab!=1,canRescue=false,statisticsLabel="東町内会",onAddReport={})
                    if(menu)ApprovedMenu("東町内会",{menu=false},{menu=false},{menu=false;settings=true})
                    if(settings)LanguageSettings {settings=false}
                }
            }
        }}}
    }
}
