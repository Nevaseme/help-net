package io.github.nevaseme.plandmk1
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.communityReportPdf
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class CommunityPdfTest {
 @Test fun japaneseReportProducesReadableMultipagePdfWithoutChangingAppRecords(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val rows=(1..3).map {CommunityRow("fictional-$it","確認用町内会 $it","Test neighborhood $it",total=24,safe=16,sos=2,unreported=6,materials=mutableListOf(CommunityMaterial("飲料水","リットル",80.0)))}
  val report=CommunityReport(id="test-only",issuedAt=1800000000000L,language="ja",groupId="test-only",groupName="PDF保存確認（架空データ）",incidentId="test-only",demo=true,rows=rows,people=emptyList(),sourceIds=listOf("internal-id"))
  val file=File(context.cacheDir,"helpnet-pdf-check.pdf");file.writeBytes(communityReportPdf(report));assertTrue(file.length()>1000)
  PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer->assertTrue(renderer.pageCount>=2);renderer.openPage(0).use {page->assertTrue(page.width>500);assertTrue(page.height>800)}}
 }
}
