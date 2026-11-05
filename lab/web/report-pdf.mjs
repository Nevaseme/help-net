import {usDateTime,usTime} from './date-time.mjs';
import {communityLabels} from './community-overview.mjs';

// A self-contained PDF: page images retain the browser's Japanese font rendering.
export function imagePagesPdf(jpegs,width=1240,height=1754){
 const encoder=new TextEncoder(),parts=[],offsets=[0];let length=0;
 const put=value=>{const bytes=typeof value==='string'?encoder.encode(value):value;parts.push(bytes);length+=bytes.length;};
 const object=(id,body)=>{offsets[id]=length;put(`${id} 0 obj\n`);put(body);put('\nendobj\n');};
 const pages=jpegs.map((_,i)=>3+i*3),pageHeight=841.89,pageWidth=595.28;
 put('%PDF-1.4\n');object(1,'<< /Type /Catalog /Pages 2 0 R >>');object(2,`<< /Type /Pages /Count ${pages.length} /Kids [${pages.map(id=>`${id} 0 R`).join(' ')}] >>`);
 jpegs.forEach((jpeg,i)=>{const id=pages[i],commands=`q ${pageWidth} 0 0 ${pageHeight} 0 0 cm /Im0 Do Q\n`;
  object(id,`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /XObject << /Im0 ${id+2} 0 R >> >> /Contents ${id+1} 0 R >>`);
  object(id+1,`<< /Length ${encoder.encode(commands).length} >>\nstream\n${commands}endstream`);
  offsets[id+2]=length;put(`${id+2} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${width} /Height ${height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`);put(jpeg);put('\nendstream\nendobj\n');
 });
 const xref=length;put(`xref\n0 ${offsets.length}\n0000000000 65535 f \n`);for(const offset of offsets.slice(1))put(`${String(offset).padStart(10,'0')} 00000 n \n`);
 put(`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
 const result=new Uint8Array(length);let at=0;for(const part of parts){result.set(part,at);at+=part.length;}return result;
}

export async function communityReportPdf(report){
 await document.fonts?.ready;
 const en=report.language==='en',labels=communityLabels(en),pages=[],w=1240,h=1754,margin=76;
 let canvas,ctx,y;
 const finish=()=>{if(!canvas)return;ctx.font='24px sans-serif';ctx.fillStyle='#52616a';ctx.fillText(String(pages.length+1),w-margin-20,h-40);const base64=canvas.toDataURL('image/jpeg',.94).split(',')[1];pages.push(Uint8Array.from(atob(base64),c=>c.charCodeAt(0)));};
 const page=()=>{finish();canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;ctx=canvas.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,w,h);y=margin;};
 const line=(value,size=28,bold=false)=>{ctx.font=`${bold?'600 ':''}${size}px system-ui,sans-serif`;const rows=[];let row='';for(const char of String(value)){if(char==='\n'||ctx.measureText(row+char).width>w-2*margin){rows.push(row);row=char==='\n'?'':char;}else row+=char;}rows.push(row);for(const text of rows){if(y+size+22>h-margin){page();ctx.font=`${bold?'600 ':''}${size}px system-ui,sans-serif`;}ctx.fillStyle='#172b35';ctx.fillText(text,margin,y+size);y+=size+18;}};
 page();line('Help Net',28,true);line(labels.title,40,true);line(report.groupName,30);line(usDateTime(report.issuedAt),24);
 for(const row of report.rows){if(y+570>h-margin)page();y+=20;line(en?row.nameEn:row.nameJa,34,true);
  for(const key of ['total','safe','sos','rescued','unreported','injured','injuryUnknown','reports']){if(y+52>h-margin)page();ctx.fillStyle='#172b35';ctx.font='28px system-ui,sans-serif';ctx.fillText(labels[key],margin,y+30);ctx.textAlign='right';ctx.fillText(String(row[key]),w-margin,y+30);ctx.textAlign='left';y+=50;ctx.strokeStyle='#c6d1d6';ctx.beginPath();ctx.moveTo(margin,y);ctx.lineTo(w-margin,y);ctx.stroke();}
  y+=18;line(labels.materials,28,true);if(!row.materials.length)line(labels.none,26);for(const material of row.materials)line(`${material.name}: ${material.shortfall} ${material.unit}${material.conflict?' ('+labels.conflict+')':''}`,26);
 }
 finish();return new Blob([imagePagesPdf(pages,w,h)],{type:'application/pdf'});
}
