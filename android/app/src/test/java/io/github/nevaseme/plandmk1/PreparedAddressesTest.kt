package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.buildings.*
import java.io.File
import org.junit.Test
import org.junit.Assert.*

class PreparedAddressesTest {
 @Test fun packagedNormalizationAndSearchAreGeneral(){
  for(code in addressCityCodes)for(kind in listOf("addresses","places")){
   val rows=readPreparedAddresses(File("src/main/assets/cities/$code-$kind.prepared.gzip").inputStream(),code)
   assertTrue(rows.isNotEmpty())
   // Spread over each complete package; no special-address allowlist.
   for(i in 0 until rows.size step maxOf(1,rows.size/80)){
    val r=rows[i];assertEquals(normalizeAddress(r.address),r.search)
    if(r.search.length<2)continue // Existing UI requires at least two search characters.
    val hits=searchAddresses(rows,r.address,code)
    assertTrue("Missing ${r.address}",hits.any{it.address==r.address})
   }
  }
 }
}
