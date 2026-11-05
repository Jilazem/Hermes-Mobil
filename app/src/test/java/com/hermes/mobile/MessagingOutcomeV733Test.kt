package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class MessagingOutcomeV733Test {
    @Test fun emptyReplyIsAProtocolFailureAndCannotReachDelivery() {
        val result=PhoneTools(RuntimeEnvironment.getApplication()).executeOutcome("phone_reply",buildJsonObject {
            put("app","whatsapp");put("chat","__audit_missing__");put("text","")
        })
        assertFalse(result.ok)
        assertTrue(result.text.isNotBlank())
    }
    @Test fun missingNotificationPermissionIsNotReportedAsSuccessfulRead() {
        val result=MessageInbox.readOutcome(RuntimeEnvironment.getApplication(),"whatsapp")
        assertFalse(result.ok)
        assertTrue(result.text.isNotBlank())
    }
    @Test fun replyingStillRequiresTheExistingWriteAndReplyPermissions() {
        assertNotNull(PhoneBotLogic.commandBlockReason(AppSettings(),"phone_reply"))
        val allowed=AppSettings().withFullControl(true).copy(agentAutoWrite=true,agentMayReply=true,agentEmergencyStop=false)
        assertNull(PhoneBotLogic.commandBlockReason(allowed,"phone_reply"))
        assertNotNull(PhoneBotLogic.commandBlockReason(allowed.copy(agentMayReply=false),"phone_reply"))
        assertNotNull(PhoneBotLogic.commandBlockReason(allowed.copy(agentReadOnly=true),"phone_reply"))
    }
}
