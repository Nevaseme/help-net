package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class ParcelSearchRepositoryTest {
 @Test fun exactSearchRetainsAmbiguityAndNeverGuessesNumbers() {
  val body="""{"schemaVersion":1,"rows":[["a","京都府京都市左京区讃州寺町203",135.77,35.01,"parcel-area",{"matchLevel":"parcel","source":"fixture","sourceVersion":"unknown"}],["b","京都府京都市上京区讃州寺町203",135.76,35.02,"parcel-area",{"matchLevel":"parcel","source":"fixture","sourceVersion":"unknown"}]]}"""
  val data=ByteArrayOutputStream().also {out->GZIPOutputStream(out).use{it.write(body.toByteArray())}}.toByteArray()
  val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
  val index="""{"schemaVersion":1,"towns":[["京都府京都市左京区讃州寺町","a"],["京都府京都市上京区讃州寺町","a"]],"chunks":[{"id":"a","file":"a.gz","sha256":"$hash"}]}""".toByteArray()
  var reads=0
  val repo=ParcelSearchRepository {path->reads++;(if(path.endsWith("index.json"))index else data).inputStream()}
  for(q in listOf("左京区讃州寺町203","京都市左京区讃州寺町２０３番地","京都市左京区仁王門通新麩屋町西入ル讃州寺町203"))assertEquals("a",repo.search(q).single().id)
  assertEquals(2,reads);assertEquals(2,repo.search("讃州寺町203").size)
  for(q in listOf("左京区讃州寺町999999","下京区讃州寺町203","大阪府大阪市左京区讃州寺町203","京都府宇治市讃州寺町203"))assertTrue(q,repo.search(q).isEmpty())
  val parcel=repo.search("左京区讃州寺町203").single()
  assertEquals("a",mergeParcelCandidates(listOf(parcel.copy(id="old",precision="block")),listOf(parcel)).single().id)
  assertEquals("trusted",mergeParcelCandidates(listOf(parcel.copy(id="trusted",precision="residential")),listOf(parcel)).single().id)
  val noPrefecture=AddressCandidate("trusted","京都市左京区讃州寺町203",135.0,35.0,"residential")
  assertEquals("trusted",mergeParcelCandidates(listOf(noPrefecture),listOf(parcel)).single().id)
  val corrupt=ParcelSearchRepository {path->(if(path.endsWith("index.json"))index else byteArrayOf(1)).inputStream()}
  try {corrupt.search("左京区讃州寺町203");fail("Corrupt data must fail")}catch(_:IllegalArgumentException){}
 }

 @Test fun fullPackageLoadsOneShardAndWarmLookupIsBelow500ms() {
  val root=listOf(File("../../.implementation/kyoto-parcel-search"),File("src/main/assets/kyoto-parcel-search")).firstOrNull {File(it,"index.json").exists()}
  assumeTrue("Generate or package the Kyoto search assets first",root!=null)
  var reads=0
  val repo=ParcelSearchRepository {path->reads++;File(root!!,path.removePrefix("kyoto-parcel-search/")).inputStream()}
  val point=repo.search("左京区讃州寺町203").first()
  assertEquals("parcel-area",point.precision);assertEquals(135.77448339,point.longitude,0.000000001);assertEquals(35.01152586,point.latitude,0.000000001)
  assertEquals(2,reads)
  val start=System.nanoTime();repeat(100){assertTrue(repo.search("左京区讃州寺町２０３番地").isNotEmpty())}
  val milliseconds=(System.nanoTime()-start)/100.0/1_000_000
  println("Parcel search warm average: $milliseconds ms; files read: $reads")
  assertTrue("Warm search $milliseconds ms",milliseconds<500);assertEquals(2,reads)
  assertTrue(repo.search("左京区讃州寺町999999").isEmpty())
  assertTrue(repo.search("右京区京北下町島﨑20-1").any{it.id=="kyoto-mlit2025:701154"})
 }
}
