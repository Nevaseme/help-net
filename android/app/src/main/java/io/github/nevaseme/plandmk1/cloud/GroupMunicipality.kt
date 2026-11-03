package io.github.nevaseme.plandmk1.cloud

import kotlinx.serialization.Serializable

@Serializable data class GroupMunicipality(
    val code:String, val name:String, val prefecture:String,
    val updatedAt:Long=0, val updatedBy:String="",
) {
    fun validate() {require(available.any {it.code==code&&it.name==name&&it.prefecture==prefecture}){"対応していない活動地域です"}}
    companion object {
        val available=listOf(GroupMunicipality("26100","京都市","京都府"),GroupMunicipality("22205","熱海市","静岡県"),GroupMunicipality("13103","港区","東京都"))
        fun forDemo(positionMode:String?,closed:Boolean):GroupMunicipality? = if(closed)null else when(positionMode){
            "KYOTO"->available.first{it.code=="26100"}
            "ACTUAL"->available.first{it.code=="13103"}
            else->null
        }
    }
}

/** The event's map selection does not overwrite the group's ordinary municipality. */
fun CloudState.mapMunicipalityOrNull():GroupMunicipality? =
    venue?.let {GroupMunicipality.forDemo(it.positionMode,it.closed)}?:municipality
