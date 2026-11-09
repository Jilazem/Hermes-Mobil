package com.hermes.mobile.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Small original parked game. No network, purchases, microphone or stored personal data. */
internal class MolaBuddyGameView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var score=0
    private var x=.5f
    private var y=.5f
    private val positions=listOf(.22f to .36f,.72f to .62f,.40f to .72f,.76f to .34f,.28f to .64f)
    init {
        isClickable=true;isFocusable=true
        updateDescription()
        setOnClickListener {
            score++
            val point=positions[(score-1)%positions.size];x=point.first;y=point.second
            updateDescription();invalidate()
        }
    }
    private fun updateDescription() {contentDescription="Pofuduk’u yakala. $score puan. Dokunarak oyna."}
    private fun radius()=min(width,height).coerceAtLeast(1)*.10f
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(9,15,34))
        val density=resources.displayMetrics.density
        paint.color=Color.rgb(191,199,255);paint.textSize=20*density
        canvas.drawText("Puan: $score",20*density,30*density,paint)
        val r=radius();val cx=width*x;val cy=height*y
        paint.color=Color.rgb(172,164,246)
        canvas.drawOval(cx-r*.85f,cy-r*1.65f,cx-r*.25f,cy-r*.35f,paint)
        canvas.drawOval(cx+r*.25f,cy-r*1.65f,cx+r*.85f,cy-r*.35f,paint)
        canvas.drawRoundRect(cx-r,cy-r*.75f,cx+r,cy+r*.85f,r*.7f,r*.7f,paint)
        for(offset in listOf(-.35f,.35f)) {
            paint.color=Color.rgb(20,24,52)
            canvas.drawCircle(cx+r*offset,cy-r*.12f,r*.13f,paint)
            paint.color=Color.WHITE
            canvas.drawCircle(cx+r*(offset-.035f),cy-r*.16f,r*.04f,paint)
            paint.color=Color.rgb(255,178,191)
            canvas.drawOval(cx+r*(offset-.14f),cy+r*.20f,cx+r*(offset+.14f),cy+r*.34f,paint)
        }
        paint.color=Color.rgb(20,24,52);paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.05f
        canvas.drawArc(cx-r*.18f,cy+r*.16f,cx+r*.18f,cy+r*.45f,0f,180f,false,paint)
        paint.style=Paint.Style.FILL
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action==MotionEvent.ACTION_UP && hypot(event.x-width*x,event.y-height*y)<radius()*1.6f) performClick()
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
