package com.hermes.mobile.data

fun modelSwitchAccepted(output:String):Boolean =
    !listOf("credit","balance","error","failed").any { output.contains(it,ignoreCase=true) } &&
        (output.contains("switched",ignoreCase=true) || output.contains("✓"))
