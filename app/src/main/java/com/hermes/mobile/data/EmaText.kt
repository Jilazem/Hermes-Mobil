package com.hermes.mobile.data

import java.text.Normalizer
import java.util.Locale

/** Android frontend: Turkish casing, numbers, common abbreviations and bounded pieces.
 * EMA's Python normalizer-tr has additional specialised notation rules.
 */
object EmaText {
    private val ones=listOf("sıfır","bir","iki","üç","dört","beş","altı","yedi","sekiz","dokuz")
    private val tens=listOf("","on","yirmi","otuz","kırk","elli","altmış","yetmiş","seksen","doksan")
    private fun integer(raw:String):String {
        val n=raw.toLongOrNull() ?: return raw.map { ones[it.digitToInt()] }.joinToString(" ")
        if(raw.length>1 && raw.startsWith("0")) return raw.map { ones[it.digitToInt()] }.joinToString(" ")
        if(n==0L) return ones[0]
        fun group(v:Int):String = buildList {
            if(v>=100) { if(v/100!=1) add(ones[v/100]); add("yüz") }
            if(v%100>=10) add(tens[v%100/10])
            if(v%10!=0) add(ones[v%10])
        }.joinToString(" ")
        val scales=listOf("","bin","milyon","milyar","trilyon","katrilyon","kentilyon")
        var value=n; var i=0; val parts=mutableListOf<String>()
        while(value>0) {
            val g=(value%1000).toInt()
            if(g!=0) parts.add(0,if(i==1 && g==1) "bin" else listOf(group(g),scales[i]).filter { it.isNotEmpty() }.joinToString(" "))
            value/=1000; i++
        }
        return parts.joinToString(" ")
    }
    fun normalize(input:String, vocab:Set<String>):String {
        var text=input.replace(Regex("[\\p{Cc}\\p{Cf}]")," ").replace('İ','i').replace('I','ı').lowercase(Locale.forLanguageTag("tr"))
        text=text.replace(Regex("\\b(\\d{1,2}):(\\d{2})\\b")) { integer(it.groupValues[1])+" "+integer(it.groupValues[2]) }
        text=text.replace(Regex("\\d+(?:[,.]\\d+)?")) {
            val parts=it.value.split(',', '.')
            integer(parts[0])+(if(parts.size>1) " virgül "+integer(parts[1]) else "")
        }
        val abbreviations=mapOf("tl" to "türk lirası","kg" to "kilogram","km" to "kilometre","mb" to "megabayt","gb" to "gigabayt","dr" to "doktor")
        for ((short,long) in abbreviations) text=text.replace(Regex("\\b$short\\b"),long)
        text=text.replace('’','\'').replace('‘','\'').replace('–','-').replace('—','-').replace("…","...")
        val out=StringBuilder()
        for(ch in text) {
            val s=if(ch in "çğıöşü") ch.toString() else Normalizer.normalize(ch.toString(),Normalizer.Form.NFKD).replace(Regex("\\p{M}"),"")
            out.append(if(s.isNotEmpty() && s.all { it.toString() in vocab }) s else " ")
        }
        return out.toString().replace(Regex("\\s+")," ").trim()
    }
    fun pieces(text:String,max:Int=180):List<String> {
        val out=mutableListOf<String>(); var rest=text.trim()
        while(rest.isNotEmpty()) {
            if(rest.length<=max) { out+=rest; break }
            val prefix=rest.take(max)
            val punctuation=prefix.indexOfLast { it in ".!?;" }.takeIf { it>=max/3 }
            val end=punctuation?.plus(1) ?: prefix.lastIndexOf(' ').takeIf { it>0 } ?: max
            out+=rest.take(end).trim(); rest=rest.drop(end).trim()
        }
        return out
    }
}
