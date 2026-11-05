package io.github.nevaseme.plandmk1.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import io.github.nevaseme.plandmk1.core.*
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

fun communityReportPdf(report:CommunityReport):ByteArray {
 val document=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG);val en=report.language=="en"
 var page:PdfDocument.Page?=null;var y=40f;var pageNumber=0
 fun finish(){page?.let {p->paint.textSize=10f;paint.color=Color.DKGRAY;p.canvas.drawText(pageNumber.toString(),550f,815f,paint);document.finishPage(p)}}
 fun next(){finish();pageNumber++;page=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create());y=40f}
 fun text(value:String,size:Float=14f,bold:Boolean=false){
  paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
  val lines=mutableListOf<String>();var line=""
  value.forEach {c->if(c=='\n'||paint.measureText(line+c)>515f){lines.add(line);line=if(c=='\n')"" else c.toString()}else line+=c};lines.add(line)
  lines.forEach {v->if(y+size+10>795f){next();paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)};paint.color=Color.rgb(23,43,53);page!!.canvas.drawText(v,40f,y+size,paint);y+=size+10}
 }
 try{
  next();text("Help Net",14f,true);text(communityLabel("title",en),22f,true);text(report.groupName,16f);text(io.github.nevaseme.plandmk1.core.usDateTime(report.issuedAt),12f)
  report.rows.forEach {row->if(y+320>795f)next();y+=12;text(if(en)row.nameEn else row.nameJa,18f,true)
   communityCounts(row).forEach {(key,count)->if(y+27>795f)next();paint.textSize=14f;paint.typeface=Typeface.DEFAULT;paint.color=Color.rgb(23,43,53);page!!.canvas.drawText(communityLabel(key,en),40f,y+16,paint);paint.textAlign=Paint.Align.RIGHT;page!!.canvas.drawText(count.toString(),555f,y+16,paint);paint.textAlign=Paint.Align.LEFT;y+=27;paint.color=Color.LTGRAY;page!!.canvas.drawLine(40f,y,555f,y,paint)}
   y+=8;text(communityLabel("materials",en),14f,true);if(row.materials.isEmpty())text(communityLabel("none",en))
   row.materials.forEach {text("${it.name}: ${it.shortfall} ${it.unit}${if(it.conflict)" (${communityLabel("conflict",en)})" else ""}")}
  };finish();page=null;return ByteArrayOutputStream().use {document.writeTo(it);it.toByteArray()}
 }finally{document.close()}
}

class CommunityPdfPrintAdapter(private val bytes:ByteArray):PrintDocumentAdapter(){
 override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes?,cancellationSignal:CancellationSignal?,callback:LayoutResultCallback,extras:android.os.Bundle?){
  if(cancellationSignal?.isCanceled==true){callback.onLayoutCancelled();return}
  callback.onLayoutFinished(PrintDocumentInfo.Builder("Help-Net-report.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true)
 }
 override fun onWrite(pages:Array<out PageRange>?,destination:ParcelFileDescriptor,cancellationSignal:CancellationSignal?,callback:WriteResultCallback){
  if(cancellationSignal?.isCanceled==true){callback.onWriteCancelled();return}
  runCatching{FileOutputStream(destination.fileDescriptor).use {it.write(bytes)}}.onSuccess{callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))}.onFailure{callback.onWriteFailed(it.message)}
 }
}
