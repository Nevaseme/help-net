package io.github.nevaseme.plandmk1.eew

data class EewNotice(val tag: String, val title: String, val body: String, val eventId: String)

/** Synchronous durable boundary shared by FCM and isolated delivery tests. */
class EewReceiver(
    private val store:EewReceiptStore,
    private val target:()->EewTarget?={null},
    private val apply:(EewReceipt)->String?={null},
    private val select:(String,String)->Unit={_,_->},
    private val afterDurableSave:(EewReceipt)->Unit={},
    private val onDeferred:(Exception)->Unit={},
) {
    private fun process(receipt:EewReceipt,allowDeferredCloud:Boolean=false) {
        if(!store.pending(receipt))return
        val result=store.applied(receipt)?:EewReceiptStore.Applied(apply(receipt)).also {store.markApplied(receipt,it.incidentId)}
        if(!store.selected(receipt)) {
            result.incidentId?.let {select(requireNotNull(receipt.target).groupId,it)}
            store.markSelected(receipt)
        }
        try {afterDurableSave(receipt)}catch(error:Exception) {
            if(!allowDeferredCloud)throw error
            // The warning is already durable locally. A cloud-queue failure must not hide it.
            onDeferred(error);return
        }
        store.complete(receipt)
    }
    @Synchronized fun retryPending() {
        var failure:Exception?=null
        store.receipts().filter {store.pending(it)}.forEach {receipt->
            try {process(receipt)}catch(e:Exception){if(failure==null)failure=e}
        }
        failure?.let {throw it}
    }
    @Synchronized fun receive(data: Map<String, String>, receivedAt: Long, allowDiagnostics: Boolean = false): EewNotice? {
        val source = data["source"] ?: return null
        if (data["schema"] != "pland-eew-1" || source !in setOf("p2pquake", "wolfx") || data["region"] != REGION) return null
        val warning = EewWarning.parse(data["raw"] ?: return null, source) ?: return null
        val previous=store.latest(warning.eventId,warning.test,source)?.warning
        val latest=previous==null || compareValuesBy(warning,previous,{it.serial},{it.issuedAt},{it.cancelled},{it.messageId})>=0
        val actionable=latest && !warning.test && data["diagnostic"]!="true" && warning.warningFor(REGION)!=null && receivedAt-warning.issuedAt<=180_000
        val fresh=store.save(warning,receivedAt,if(store.receipt(warning)==null)target() else null,actionable)
        val receipt=requireNotNull(store.receipt(warning));val pending=store.pending(receipt)
        process(receipt,allowDeferredCloud=true)
        if(!fresh&&!pending)return null
        if (store.latest(warning.eventId, warning.test, source)?.warning != warning) return null
        // The relay validates upstream time. A phone clock slightly behind the relay
        // must not suppress an already validated warning that arrived immediately.
        if (source == "wolfx" && receipt.receivedAt - warning.issuedAt > 180_000) return null
        val tag = if (source == "wolfx") "eew:wolfx:${warning.test}:${warning.eventId}" else "eew:${warning.test}:${warning.eventId}"
        val sourceLabel = if (source == "wolfx") "Wolfx" else "P2P地震情報"
        if (warning.test) {
            return if (allowDiagnostics && data["diagnostic"] == "true")
                EewNotice(tag, "PlanD 受信試験", "消灯中の通知と端末内保存を確認する試験です。実際の地震ではありません。", warning.eventId)
            else null
        }
        if (warning.cancelled) return EewNotice(tag, "緊急地震速報 取消", "${REGION}への速報に取消が届きました。（$sourceLabel・第${warning.serial}報）", warning.eventId)
        val area = warning.warningFor(REGION)
        if (area == null) return EewNotice(tag, "緊急地震速報 更新", "${REGION}は警報の対象から外れました。（$sourceLabel・第${warning.serial}報）", warning.eventId)
        return EewNotice(tag, "緊急地震速報（警報）", "$REGION：予測${intensity(area)}（$sourceLabel・第${warning.serial}報）", warning.eventId)
    }

    companion object {
        const val REGION = "京都府南部"
        private val labels = mapOf(0 to "0", 10 to "1", 20 to "2", 30 to "3", 40 to "4", 45 to "5弱", 50 to "5強", 55 to "6弱", 60 to "6強", 70 to "7")
        private fun intensity(area: P2pArea): String {
            val from = labels[area.scaleFrom] ?: return "震度不明"
            if (area.scaleTo == 99) return "震度${from}以上"
            val to = labels[area.scaleTo] ?: return "震度不明"
            return if (area.scaleFrom == area.scaleTo) "震度$from" else "震度$from〜$to"
        }
    }
}
