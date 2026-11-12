package com.gkim.pixelquest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.hypot

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val game = Game()
    private var thread: Thread? = null
    @Volatile private var running = false

    private val frame = Bitmap.createBitmap(Game.VW, Game.VH, Bitmap.Config.ARGB_8888)
    private val frameCanvas = Canvas(frame)
    private val screenPaint = Paint()
    private val dest = RectF()

    private var joyPointer = -1
    private var joyBaseX = 0f
    private var joyBaseY = 0f
    private var joyX = 0f
    private var joyY = 0f
    private var attackQueued = false
    private val keys = HashSet<Int>()

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun resumeGame() {
        if (running) return
        running = true
        thread = Thread(this).also { it.start() }
    }

    fun pauseGame() {
        running = false
        while (thread?.isAlive == true) {
            try {
                thread?.join()
            } catch (_: InterruptedException) {
            }
        }
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.05f) dt = 0.05f

            var mx = joyX
            var my = joyY
            if (keys.contains(KeyEvent.KEYCODE_DPAD_LEFT) || keys.contains(KeyEvent.KEYCODE_A)) mx = -1f
            if (keys.contains(KeyEvent.KEYCODE_DPAD_RIGHT) || keys.contains(KeyEvent.KEYCODE_D)) mx = 1f
            if (keys.contains(KeyEvent.KEYCODE_DPAD_UP) || keys.contains(KeyEvent.KEYCODE_W)) my = -1f
            if (keys.contains(KeyEvent.KEYCODE_DPAD_DOWN) || keys.contains(KeyEvent.KEYCODE_S)) my = 1f
            val attack = attackQueued || keys.contains(KeyEvent.KEYCODE_SPACE)
            attackQueued = false

            game.update(dt, Game.Input(mx, my, attack))

            val canvas = holder.lockCanvas() ?: continue
            try {
                game.render(frameCanvas)
                canvas.drawBitmap(frame, null, dest, screenPaint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            try {
                Thread.sleep(8)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun layoutDest() {
        if (width == 0 || height == 0) return
        val scale = minOf(width / Game.VW.toFloat(), height / Game.VH.toFloat())
        val w = Game.VW * scale
        val h = Game.VH * scale
        dest.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        screenPaint.isFilterBitmap = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val leftSide = event.getX(i) < width / 2f
                if (leftSide && joyPointer == -1 && game.state == Game.State.PLAY) {
                    joyPointer = id
                    joyBaseX = event.getX(i)
                    joyBaseY = event.getY(i)
                    joyX = 0f
                    joyY = 0f
                } else {
                    attackQueued = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) != joyPointer) continue
                    val dx = event.getX(i) - joyBaseX
                    val dy = event.getY(i) - joyBaseY
                    val len = hypot(dx, dy)
                    if (len > 24f) {
                        joyX = dx / len
                        joyY = dy / len
                    } else {
                        joyX = 0f
                        joyY = 0f
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                if (event.getPointerId(i) == joyPointer) {
                    joyPointer = -1
                    joyX = 0f
                    joyY = 0f
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                joyPointer = -1
                joyX = 0f
                joyY = 0f
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        keys.add(keyCode)
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) attackQueued = true
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        keys.remove(keyCode)
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        layoutDest()
        resumeGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        layoutDest()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pauseGame()
    }
}
