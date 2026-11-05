package io.github.nevaseme.plandmk1.core

fun usDateTime(value:Long):String=java.text.SimpleDateFormat("MM/dd/yyyy h:mm a",java.util.Locale.US).format(java.util.Date(value))
fun usTime(value:Long):String=java.text.SimpleDateFormat("h:mm a",java.util.Locale.US).format(java.util.Date(value))
