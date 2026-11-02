package io.github.nevaseme.plandmk1.data

import androidx.room.*

@Entity(tableName="relay_proofs",primaryKeys=["groupId","identity"])
data class RelayProofRow(val groupId:String,val identity:String,val revision:Long,val bodyHash:String,val payload:String)
@Dao interface RelayProofDao {
    @Query("SELECT * FROM relay_proofs WHERE groupId=:group") suspend fun all(group:String):List<RelayProofRow>
    @Query("SELECT * FROM relay_proofs WHERE groupId=:group AND bodyHash=:hash") suspend fun byHash(group:String,hash:String):RelayProofRow?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(row:RelayProofRow)
}
