/** Keep neighborhood cards away from safety markers, without moving their coordinates. */
export function placeMapLabel(point,size,occupied,width=136,height=112,previous=null){
 const overlap=r=>occupied.reduce((n,o)=>n+Math.max(0,Math.min(r.x+r.w,o.x+o.w)-Math.max(r.x,o.x))*Math.max(0,Math.min(r.y+r.h,o.y+o.h)-Math.max(r.y,o.y)),0);
 if(previous&&previous.x>=8&&previous.y>=12&&previous.x+width<=size.x-8&&previous.y+height<=size.y-56&&overlap(previous)===0)return previous;
 const candidates=[];
 for(let y=12;y<=size.y-height-56;y+=12)for(let x=8;x<=size.x-width-8;x+=12)candidates.push({x,y,w:width,h:height});
 const score=r=>overlap(r)*10000+Math.hypot(r.x+width/2-point.x,r.y+height/2-point.y);
 return candidates.reduce((best,r)=>!best||score(r)<score(best)?r:best,null)||{x:8,y:12,w:width,h:height};
}
