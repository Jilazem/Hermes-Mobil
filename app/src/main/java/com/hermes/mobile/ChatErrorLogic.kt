package com.hermes.mobile

/** A title helper failure is diagnostic, not a failure of the completed conversation answer. */
internal fun auxiliaryTitleFailure(type: String, text: String?): Boolean =
    type == "error" && text.orEmpty().trimStart().removePrefix("⚠").removePrefix("\uFE0F")
        .trimStart().startsWith("Auxiliary title generation failed:",ignoreCase=true)
