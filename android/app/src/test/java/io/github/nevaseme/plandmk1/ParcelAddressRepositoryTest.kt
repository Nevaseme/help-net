package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.buildings.ParcelAddressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class ParcelAddressRepositoryTest {
 @Test fun candidatesRetainAmbiguityAndAreLoadedOnlyForContainingMeshes()=runBlocking {
  val data=ByteArrayOutputStream().also{out->GZIPOutputStream(out).use{it.write("""{"schemaVersion":1,"mesh":"m","buildings":{"bldg_1":{"status":"multiple-parcels","candidates":[{"parcelId":"p1","address":"京都市A町1","latitude":35.1,"longitude":135.5},{"parcelId":"p2","address":"京都市A町2","latitude":35.1,"longitude":135.5}]}}}""".toByteArray())}}.toByteArray()
  val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
  val index="""{"schemaVersion":1,"source":{"id":"land","version":"2025"},"chunks":[{"mesh":"m","bbox":[135,35,136,36],"file":"m.gz","sha256":"$hash"}]}""".toByteArray()
  var reads=0
  val repository=ParcelAddressRepository {path->reads++;(if(path.endsWith("index.json"))index else data).inputStream()}
  assertTrue(repository.choices("bldg_1",35.6,139.0).isEmpty());assertEquals(0,reads)
  assertTrue(repository.choices("bldg_1",34.8,135.5).isEmpty());assertEquals(1,reads)
  val choices=repository.choices("bldg_1",35.1,135.5)
  assertEquals(listOf("京都市A町1","京都市A町2"),choices.map{it.address});assertEquals("multiple-parcels",choices.first().status)
  assertEquals("2025",choices.first().sourceVersion);assertEquals(2,reads)
  assertEquals(choices,repository.choices("bldg_1",35.1,135.5));assertEquals(2,reads)
  val corrupt=ParcelAddressRepository {path->(if(path.endsWith("index.json"))index else byteArrayOf(1)).inputStream()}
  try {corrupt.choices("bldg_1",35.1,135.5);fail("Corrupt data must not become an empty candidate list")}catch(_:IllegalArgumentException){}
 }
}
