package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

/** A qualified local EEW receipt, independent of the number of human reports. */
@Serializable data class EewEvidence(val source:String,val eventId:String,val issuedAt:Long,val receivedAt:Long,val startsNewPeriod:Boolean=false) {
    fun validate() {
        require(source in setOf("wolfx","p2pquake") && eventId.matches(Regex("[0-9]{14}")))
        require(issuedAt in 0..MAX_SEQUENCE && receivedAt in 0..MAX_SEQUENCE && receivedAt-issuedAt<=180_000)
    }
    // Both adapters carry the JMA event identity. Provider switches and serials keep one earthquake.
    fun incidentId():String="ee000001-00${eventId.take(2)}-4000-8000-${eventId.takeLast(12)}"
}

fun eewIncidentStart(group:String,evidence:EewEvidence)=evidence.incidentId().let {id->
    // Source identity, not a person or an administrator. Actual receipt time is projected from proof.
    IncidentEvent(group,id,id,id,"緊急地震速報",1,0,IncidentKind.START,"地震","京都府南部",false)
}
