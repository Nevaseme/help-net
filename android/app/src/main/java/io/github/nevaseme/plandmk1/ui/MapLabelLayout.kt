package io.github.nevaseme.plandmk1.ui

import kotlin.math.*

data class LabelRect(val x:Double,val y:Double,val w:Double,val h:Double)
fun placeMapLabel(x:Double,y:Double,screenWidth:Double,screenHeight:Double,occupied:List<LabelRect>,width:Double=148.0,height:Double=100.0):LabelRect {
 fun score(r:LabelRect):Double=occupied.sumOf {o->max(0.0,min(r.x+r.w,o.x+o.w)-max(r.x,o.x))*max(0.0,min(r.y+r.h,o.y+o.h)-max(r.y,o.y))}*10000+hypot(r.x+width/2-x,r.y+height/2-y)
 var best=LabelRect(8.0,12.0,width,height);var bestScore=Double.POSITIVE_INFINITY
 var py=12.0;while(py<=screenHeight-height-56){var px=8.0;while(px<=screenWidth-width-8){val r=LabelRect(px,py,width,height);val score=score(r);if(score<bestScore){best=r;bestScore=score};px+=12};py+=12}
 return best
}
