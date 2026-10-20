"""V5-5 — persona enjeksiyonu: bot tanımından sistem istemi üretir.

Hermes gateway protokolünde oturum başına sistem istemi alanı YOKTUR
(PLAN.md K18B-G1 R3). Bu yüzden persona, sohbet isteğinin `messages`
dizisinin başında `role=system` olarak taşınır ve oturum kaydında saklanır —
böylece persona hem modele gider hem UI'da görünür (kabul: persona görünür).
"""
from __future__ import annotations

from typing import Dict, List

from .models import TOOL_CATALOG, Bot

PERSONA_HEADER = "[BOT PERSONA]"


def build_system_prompt(bot: Bot) -> str:
    """Bot tanımından deterministik sistem istemi üretir."""
    lines: List[str] = []
    lines.append("%s Sen «%s» %s adlı botsun." % (PERSONA_HEADER, bot.name.strip(), bot.emoji))
    lines.append("")
    lines.append("## Persona")
    lines.append(bot.persona.strip())

    if bot.tools:
        lines.append("")
        lines.append("## Yetkili araçlar (yalnız bunlar)")
        for tool in bot.tools:
            lines.append("- %s: %s" % (tool, TOOL_CATALOG[tool]))
    else:
        lines.append("")
        lines.append("## Yetkili araçlar")
        lines.append("- Yok. Araç çağırmadan yanıtla.")

    lines.append("")
    lines.append("## Çalışma ayarları")
    lines.append("- Varsayılan model: %s" % (bot.model or "oturumun mevcut modeli"))
    lines.append("- Think modu: %s" % bot.think_mode)
    if bot.profile:
        lines.append("- Hermes profili: %s" % bot.profile)

    lines.append("")
    lines.append("## Sınırlar")
    lines.append("- Token, şifre, API anahtarı istemez ve paylaşmazsın.")
    lines.append("- Yetkili araç listesi dışında araç çağırmazsın.")
    lines.append("- Bilmediğin bilgiyi uydurmaz, 'doğrulanamadı' diye işaretlersin.")
    return "\n".join(lines)


def system_message(bot: Bot) -> Dict[str, str]:
    return {"role": "system", "content": build_system_prompt(bot)}
