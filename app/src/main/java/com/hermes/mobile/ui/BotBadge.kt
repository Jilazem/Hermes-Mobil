package com.hermes.mobile.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ChatItem
import com.hermes.mobile.data.BotCard
import com.hermes.mobile.ui.theme.HermesColors

/**
 * K18B-B — sohbette bot kimliği rozeti (ad+emoji+model afişi).
 *
 * Her BOT tarafı mesaj grubunun (asistan turu: düşünme/araç/yanıt/onay)
 * başında çizilir; hangi bot/profil+model'in konuştuğu görünür. Kullanıcı
 * mesajlarında ve sistem (Notice) satırlarında görünmez.
 *
 * Kimlik kaynağı: K18B-G1 roster kartı ([BotCard]) — ad+emoji karttan,
 * model afişi oturumun aktif modelinden. Kart yoksa rozet yine çizilir ama
 * genel kimlikle (🤖 + profil adı / "Yönlendirici") — rozet "hangi
 * profil+model konuşuyor" bilgisini her bot grubunda taşır.
 *
 * Esin: MausBot "Pick a brain per bot" (mausbot.com) — yalnız fikir,
 * kod kopyalanmadı (CREDITS.md). Saf mantık [BotBadgeLogicTest] ile kilitli.
 */

/** Rozetin görünür üçlüsü — ham kart id'si ASLA taşınmaz (D-05). */
data class BotBadgeUi(
    val emoji: String,
    val name: String,
    /** Model afişi — boşsa rozette model satırı/etiketi çizilmez. */
    val modelLabel: String,
)

/** Rozet metni — semantik/erişilebilirlik ve testlerin tek biçimi: "emoji ad · model". */
fun botBadgeLabel(badge: BotBadgeUi): String =
    listOf("${badge.emoji} ${badge.name}".trim(), badge.modelLabel.trim())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/**
 * Rozeti çözer: roster kartı → ad+emoji; kart yoksa genel kimlik.
 *
 * Eşleşme kuralları (kararlı sıra):
 *  1. Yalnız aynı profildeki kartlar aday ([BotCard.profile] oturum profiliyle
 *     eşleşmeli; boş profil = "Yönlendirici"). Başka profilin kartı BU sohbetin
 *     rozeti olamaz.
 *  2. Adaylar içinde model birebir eşleşen kart; yoksa modeli boş (genel)
 *     kart; yoksa profil eşleşen ilk kart (model değişse de bot kimliği kalır
 *     — "model değişince afiş güncellensin": ad kalır, model etiketi yenilenir).
 *  3. Profil VE model boşsa kimlik bilinmiyor → null (rozet yok).
 *
 * Saf — JVM testi (BotBadgeLogicTest).
 */
fun resolveBotBadge(
    cards: List<BotCard>,
    profile: String?,
    model: String?,
    routerName: String = tr("Yönlendirici", "Router"),
): BotBadgeUi? {
    val p = profile?.trim().orEmpty()
    val m = model?.trim().orEmpty()
    if (p.isEmpty() && m.isEmpty()) return null
    val candidates = cards.filter { it.profile.trim().equals(p, ignoreCase = true) }
    val card = candidates.firstOrNull { m.isNotEmpty() && it.model.trim().equals(m, ignoreCase = true) }
        ?: candidates.firstOrNull { it.model.isBlank() }
        ?: candidates.firstOrNull()
    return BotBadgeUi(
        emoji = card?.emoji?.trim().orEmpty().ifEmpty { "🤖" },
        name = card?.name?.trim().orEmpty().ifEmpty { p.ifEmpty { routerName } },
        modelLabel = m.ifEmpty { card?.model?.trim().orEmpty() },
    )
}

/** Mesaj grubunun tarafı — rozet yalnız [Side.Bot] grupların BAŞINDA çizilir. */
private enum class Side { Bot, User, Neutral }

private fun sideOf(row: ChatRow): Side = when (row) {
    // Katlanmış araç/düşünme dizisi ajan günlüğü — bot tarafı.
    is ChatRow.Tools -> Side.Bot
    is ChatRow.Single -> when (row.item) {
        is ChatItem.User -> Side.User
        // Sistem satırı: ne bot ne kullanıcı — grubu bölmez, rozet almaz.
        is ChatItem.Notice -> Side.Neutral
        is ChatItem.Assistant,
        is ChatItem.Thinking,
        is ChatItem.Tool,
        is ChatItem.Approval -> Side.Bot
    }
}

/**
 * Rozet taşıyacak satırın anahtarları: her bot grubunun İLK satırı.
 *
 * Grup = kullanıcı satırıyla bölünmüş en fazla bot satırı dizisi. Nötr
 * (Notice) satırlar grubu bölmez; yalnız nötr satırdan oluşan grup rozet
 * almaz. Kararlı (stable) — aynı girdi aynı çıktı. Saf — JVM testi.
 */
internal fun botBadgeRowKeys(rows: List<ChatRow>): Set<String> {
    val keys = mutableSetOf<String>()
    var runStart: String? = null
    var runHasBot = false

    fun flush() {
        if (runHasBot) runStart?.let { keys += it }
        runStart = null
        runHasBot = false
    }

    for (row in rows) {
        when (sideOf(row)) {
            Side.User -> flush()
            Side.Neutral -> if (runStart == null) runStart = row.key
            Side.Bot -> {
                if (runStart == null) runStart = row.key
                runHasBot = true
            }
        }
    }
    flush()
    return keys
}

/**
 * Mesaj grubu başındaki bot kimliği rozeti: emoji + ad (koyu) + model afişi
 * (ince çerçeveli etiket). Yatay tek satır — grubun üstünde, gün ayırıcının
 * altında.
 */
@Composable
fun BotBadgeRow(badge: BotBadgeUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = botBadgeLabel(badge)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${badge.emoji} ${badge.name}".trim(),
            color = HermesColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (badge.modelLabel.isNotBlank()) {
            Text(
                badge.modelLabel,
                color = HermesColors.Midground,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .border(1.dp, HermesColors.BorderStrong, MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}