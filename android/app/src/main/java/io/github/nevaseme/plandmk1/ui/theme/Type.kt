package io.github.nevaseme.plandmk1.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val ApprovedFont = FontFamily((listOf(400,500,600,700,750,800,850,900)).map { weight ->
    androidx.compose.ui.text.font.Font(io.github.nevaseme.plandmk1.R.font.noto_sans_jp,weight=FontWeight(weight),variationSettings=androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(weight)))
})

private fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(
    fontFamily=ApprovedFont,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,letterSpacing=0.sp)

val Typography=Typography(
    headlineMedium=type(28,36,FontWeight.SemiBold),headlineSmall=type(24,32,FontWeight.SemiBold),
    titleLarge=type(22,30,FontWeight.SemiBold),titleMedium=type(18,26,FontWeight.Medium),titleSmall=type(16,24,FontWeight.Medium),
    bodyLarge=type(16,26),bodyMedium=type(15,24),bodySmall=type(13,21),
    labelLarge=type(15,22,FontWeight.Medium),labelMedium=type(13,20,FontWeight.Medium),labelSmall=type(12,18,FontWeight.Medium))
