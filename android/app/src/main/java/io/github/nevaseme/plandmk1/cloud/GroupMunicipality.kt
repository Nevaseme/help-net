package io.github.nevaseme.plandmk1.cloud

import kotlinx.serialization.Serializable

@Serializable data class GroupMunicipality(
    val code:String, val name:String, val prefecture:String,
    val updatedAt:Long=0, val updatedBy:String="",
) {
    fun validate() {require(available.any {it.code==code&&it.name==name&&it.prefecture==prefecture}){"対応していない活動地域です"}}
    companion object {
        val available=listOf(GroupMunicipality("26100","京都市","京都府"),GroupMunicipality("22205","熱海市","静岡県"),GroupMunicipality("13103","港区","東京都"))
    }
}
