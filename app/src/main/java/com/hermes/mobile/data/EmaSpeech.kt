package com.hermes.mobile.data

/** Same voice contract for downloaded EMA and the Hermes EMA server. */
interface EmaSpeech {
    suspend fun health(): Boolean
    suspend fun speak(text: String): ByteArray
    suspend fun stream(text: String, onPcm: (ByteArray, Int) -> Unit)
}
