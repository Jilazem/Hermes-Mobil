package com.hermes.mobile.ui

import com.hermes.mobile.data.HermesProfile

/**
 * Prompt sırasındaki bot (profil) ataması — Composer üstündeki yatay çipler.
 *
 * Çip kümesi: varsayılan "Yönlendirici" (profil = null, sunucunun aktif
 * yönlendirmesi) + `GET /api/profiles`'ten gelen her profil (isim alanı `name`).
 * Uç yanıt alamazsa liste boş kalır → yalnız varsayılan çip gösterilir.
 *
 * Kilit mantığı: mevcut oturumda (`currentProfile != null`) çipler salt-okunur
 * — mevcut profil tek çip olarak gösterilir; profil bilinmiyorsa "—".
 * YENİ sohbette (oturum yok) çipler tıklanabilir; seçilen profil
 * `createSession(profile)` argümanı olur (varsayılan çip → null).
 */

/** Varsayılan çipin etiketi — profil gönderilmez, sunucu yönlendirir. */
const val ROUTER_CHIP = "router"

/** Varsayılanın etiketi — dil'e göre (UI tabanında gösterilir). */
val ROUTER_LABEL_TR = "Yönlendirici"
val ROUTER_LABEL_EN = "Router"

/**
 * Çip tıklanabilirliği: mevcut oturumda çipler kilitli (salt-okunur).
 * Varsayılan "Yönlendirici" çipi her zaman kullanılabilir görünür —
 * kilitli oturumda zaten tıklanamaz, yeni sohbette varsayılanı seçmek
 * profili temizler.
 */
fun chipsLocked(currentProfile: String?): Boolean = currentProfile != null

/**
 * Kilitli durumda gösterilecek tek çip: mevcut profil adı; bilinmiyorsa "—".
 * Yeni sohbette null döner (çip kümesi serbest kullanılır).
 */
fun lockedChipLabel(currentProfile: String?): String? =
    if (currentProfile == null) null else currentProfile.ifBlank { "—" }

/**
 * YENİ sohbette seçilen çipi `createSession(profile)` argümanına çevirir:
 * varsayılan çip → null (profil gönderilmez, sunucunun varsayılanı işler);
 * profil çipi → profil adı.
 */
fun createSessionProfileArg(selectedChip: String?): String? =
    selectedChip?.takeIf { it.isNotBlank() && it != ROUTER_CHIP }

/** Çip sırası: varsayılan ilk, sonra profiller; yüklenmemişse yalnız varsayılan. */
fun orderedChips(profiles: List<HermesProfile>): List<HermesProfile> = profiles
