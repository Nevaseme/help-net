package io.github.nevaseme.plandmk1.ui

fun tsunamiText(key:String,english:Boolean,height:String=""):String = when(key) {
    "title" -> if(english) "Tsunami" else "津波"
    "height" -> if(english) "Tsunami height (m)" else "津波の高さ（m）"
    "legend" -> if(english) "Tsunami inundation depth" else "津波の浸水深"
    "depth" -> if(english) "Maximum inundation depth" else "最大浸水深"
    "deep" -> if(english) "3m or more" else "3m以上"
    "preparing" -> if(english) "Preparing the map…" else "表示の準備中です"
    "enter" -> if(english) "Enter the tsunami height" else "津波の高さを入力してください"
    "range" -> if(english) "Enter a whole number from 0 to 35m" else "0〜35m・1m刻みで入力してください"
    "loading" -> if(english) "Loading saved results…" else "計算済みの結果を読み込んでいます"
    "result" -> if(english) "Showing results for a ${height}m tsunami" else "高さ ${height}m の事前計算結果"
    "cancelled" -> if(english) "Loading cancelled" else "読込を中止しました"
    "off" -> if(english) "Tsunami overlay is off" else "津波表示はオフです"
    else -> if(english) "Unable to load tsunami data. Try again." else "津波データを読み込めませんでした。もう一度お試しください。"
}
