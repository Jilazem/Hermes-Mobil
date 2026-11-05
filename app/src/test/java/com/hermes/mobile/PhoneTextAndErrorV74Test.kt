package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PhoneTextAndErrorV74Test {
    @Test fun explicitEmptyTextClearsWhileMissingOrNonTextArgumentsAreRejected() {
        assertEquals("",FullControl.inputText(buildJsonObject {put("text","")}))
        assertEquals("  Türkçe\n",FullControl.inputText(buildJsonObject {put("text","  Türkçe\n")}))
        assertNull(FullControl.inputText(buildJsonObject {}))
        assertNull(FullControl.inputText(buildJsonObject {put("text",JsonNull)}))
        assertNull(FullControl.inputText(buildJsonObject {put("text",false)}))
    }
    @Test fun titleDiagnosticIsNotConfusedWithMainModelFailureOrQuotedMessageText() {
        assertTrue(auxiliaryTitleFailure("error","⚠ Auxiliary title generation failed: timed out"))
        assertTrue(auxiliaryTitleFailure("error","Auxiliary title generation failed: connection closed"))
        assertFalse(auxiliaryTitleFailure("error","Request timed out waiting for the provider"))
        assertFalse(auxiliaryTitleFailure("message.delta","Auxiliary title generation failed: quoted text"))
        assertFalse(auxiliaryTitleFailure("error",null))
    }
}
