package com.gkim.pixelquest

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Game {

    companion object {
        const val VW = 320
        const val VH = 180
        const val TILE = 16
        const val MAP_W = 64
        const val MAP_H = 64
        const val GEMS_TO_WIN = 10
        const val SLIME_COUNT = 12
        const val PLAYER_SPEED = 70f
        const val SWING_TIME = 0.22f
        const val SWING_COOLDOWN = 0.38f
    }

    enum class State { TITLE, PLAY, DEAD, WIN }

    data class Input(val moveX: Float, val moveY: Float, val attack: Boolean)

    private interface Box {
        var x: Float
        var y: Float
        val w: Float
        val h: Float
    }

    private class Player : Box {
        override var x = 0f
        override var y = 0f
        override val w = 10f
        override val h = 12f
        var hp = 3
        var dirX = 0f
        var dirY = 1f
        var walkT = 0f
        var moving = false
        var invuln = 0f
        var kbx = 0f
        var kby = 0f

        fun reset(cx: Float, cy: Float) {
            x = cx - w / 2
            y = cy - h / 2
            hp = 3
            dirX = 0f
            dirY = 1f
            walkT = 0f
            moving = false
            invuln = 0f
            kbx = 0f
            kby = 0f
        }
    }

    private class Slime : Box {
        override var x = 0f
        override var y = 0f
        override val w = 12f
        override val h = 10f
        var hp = 2
        var alive = true
        var vx = 0f
        var vy = 0f
        var kbx = 0f
        var kby = 0f
        var think = 0f
        var flash = 0f
        var hitSwing = -1
        var phase = 0f
    }

    private class Gem {
        var x = 0f
        var y = 0f
        var taken = false
        var phase = 0f
    }

    private val T_GRASS = 0
    private val T_TALL = 1
    private val T_TREE = 2
    private val T_ROCK = 3
    private val T_WATER = 4
    private val T_PATH = 5
    private val T_FLOWER = 6

    var state = State.TITLE
    private val tiles = IntArray(MAP_W * MAP_H)
    private val player = Player()
    private val slimes = ArrayList<Slime>()
    private val gems = ArrayList<Gem>()
    private var gemsTotal = 0
    private var gemsGot = 0
    private var camX = 0f
    private var camY = 0f
    private var swingTimer = 0f
    private var swingCd = 0f
    private var swingId = 0
    private var hurtFlash = 0f
    private var time = 0f
    private var random = Random(42)

    private val paint = Paint()
    private val textPaint = Paint()
    private val oval = RectF()

    init {
        textPaint.isAntiAlias = false
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.isFakeBoldText = true
    }

    private fun setTile(tx: Int, ty: Int, t: Int) {
        if (tx in 0 until MAP_W && ty in 0 until MAP_H) tiles[ty * MAP_W + tx] = t
    }

    private fun tileAt(tx: Int, ty: Int): Int {
        if (tx !in 0 until MAP_W || ty !in 0 until MAP_H) return T_TREE
        return tiles[ty * MAP_W + tx]
    }

    private fun solidTile(t: Int) = t == T_TREE || t == T_ROCK || t == T_WATER

    private fun solid(tx: Int, ty: Int) = solidTile(tileAt(tx, ty))

    fun start(seed: Long) {
        random = Random(seed)
        generateMap()
        player.reset(32f * TILE + 16f, 32f * TILE + 16f)
        slimes.clear()
        gems.clear()
        gemsGot = 0
        repeat(SLIME_COUNT) {
            val p = randomWalkable(110f) ?: return@repeat
            val s = Slime()
            s.x = p.first - s.w / 2
            s.y = p.second - s.h / 2
            s.phase = random.nextFloat() * 6f
            slimes.add(s)
        }
        gemsTotal = GEMS_TO_WIN
        repeat(gemsTotal) {
            val p = randomWalkable(150f) ?: return@repeat
            val g = Gem()
            g.x = p.first
            g.y = p.second
            g.phase = random.nextFloat() * 6f
            gems.add(g)
        }
        swingTimer = 0f
        swingCd = 0f
        hurtFlash = 0f
        state = State.PLAY
    }

    private fun randomWalkable(minDist: Float): Pair<Float, Float>? {
        val pcx = player.x + player.w / 2
        val pcy = player.y + player.h / 2
        repeat(300) {
            val tx = 3 + random.nextInt(MAP_W - 6)
            val ty = 3 + random.nextInt(MAP_H - 6)
            if (solidTile(tileAt(tx, ty))) return@repeat
            val cx = tx * TILE + 8f
            val cy = ty * TILE + 8f
            if (hypot(cx - pcx, cy - pcy) < minDist) return@repeat
            return Pair(cx, cy)
        }
        return null
    }

    private fun generateMap() {
        for (y in 0 until MAP_H) {
            for (x in 0 until MAP_W) {
                val r = random.nextFloat()
                tiles[y * MAP_W + x] = when {
                    r < 0.05f -> T_TREE
                    r < 0.09f -> T_ROCK
                    r < 0.24f -> T_TALL
                    r < 0.28f -> T_FLOWER
                    else -> T_GRASS
                }
            }
        }
        for (y in 26..37) {
            for (x in 26..37) {
                setTile(x, y, if ((x + y) % 7 == 0) T_FLOWER else T_GRASS)
            }
        }
        for (x in 2 until MAP_W - 2) {
            setTile(x, 32, T_PATH)
            setTile(x, 33, T_PATH)
        }
        for (y in 2 until MAP_H - 2) {
            setTile(32, y, T_PATH)
            setTile(33, y, T_PATH)
        }
        for (dy in -4..4) {
            for (dx in -4..4) {
                val d = dx * dx + dy * dy
                if (d <= 16) setTile(48 + dx, 12 + dy, T_WATER)
                else if (d <= 22 && random.nextFloat() < 0.5f) setTile(48 + dx, 12 + dy, T_WATER)
            }
        }
        for (y in 0 until MAP_H) {
            for (x in 0 until MAP_W) {
                if (x < 2 || y < 2 || x >= MAP_W - 2 || y >= MAP_H - 2) setTile(x, y, T_TREE)
            }
        }
    }

    private fun collides(px: Float, py: Float, w: Float, h: Float): Boolean {
        val x0 = (px / TILE).toInt()
        val x1 = ((px + w - 0.01f) / TILE).toInt()
        val y0 = (py / TILE).toInt()
        val y1 = ((py + h - 0.01f) / TILE).toInt()
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                if (solid(tx, ty)) return true
            }
        }
        return false
    }

    private fun moveBox(e: Box, dx: Float, dy: Float): Boolean {
        var moved = false
        if (dx != 0f) {
            val nx = e.x + dx
            if (!collides(nx, e.y, e.w, e.h)) {
                e.x = nx
                moved = true
            }
        }
        if (dy != 0f) {
            val ny = e.y + dy
            if (!collides(e.x, ny, e.w, e.h)) {
                e.y = ny
                moved = true
            }
        }
        return moved
    }

    private fun overlaps(a: Box, b: Box): Boolean =
        a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y

    fun update(dt: Float, input: Input) {
        time += dt
        if (hurtFlash > 0f) hurtFlash -= dt
        when (state) {
            State.PLAY -> updatePlay(dt, input)
            else -> if (input.attack) start(System.nanoTime())
        }
    }

    private fun updatePlay(dt: Float, input: Input) {
        if (player.invuln > 0f) player.invuln -= dt
        if (swingCd > 0f) swingCd -= dt

        val len = hypot(input.moveX, input.moveY)
        var vx = 0f
        var vy = 0f
        if (len > 0.25f) {
            val nx = input.moveX / max(len, 1f)
            val ny = input.moveY / max(len, 1f)
            vx = nx * PLAYER_SPEED
            vy = ny * PLAYER_SPEED
            player.dirX = nx
            player.dirY = ny
            player.moving = true
            player.walkT += dt
        } else {
            player.moving = false
        }
        val decay = max(0f, 1f - 8f * dt)
        player.kbx *= decay
        player.kby *= decay
        moveBox(player, (vx + player.kbx) * dt, (vy + player.kby) * dt)

        if (input.attack && swingCd <= 0f) {
            swingTimer = SWING_TIME
            swingCd = SWING_COOLDOWN
            swingId++
        }
        if (swingTimer > 0f) {
            swingTimer -= dt
            val sx = player.x + player.w / 2 + player.dirX * 13f
            val sy = player.y + player.h / 2 + player.dirY * 13f
            for (s in slimes) {
                if (!s.alive || s.hitSwing == swingId) continue
                if (abs(s.x + s.w / 2 - sx) < 13f && abs(s.y + s.h / 2 - sy) < 13f) {
                    s.hitSwing = swingId
                    s.hp--
                    s.flash = 0.15f
                    s.kbx = player.dirX * 130f
                    s.kby = player.dirY * 130f
                    if (s.hp <= 0) s.alive = false
                }
            }
        }

        val pcx = player.x + player.w / 2
        val pcy = player.y + player.h / 2
        for (s in slimes) {
            if (!s.alive) continue
            if (s.flash > 0f) s.flash -= dt
            s.kbx *= decay
            s.kby *= decay
            val scx = s.x + s.w / 2
            val scy = s.y + s.h / 2
            val dist = hypot(pcx - scx, pcy - scy)
            s.think -= dt
            if (dist < 90f) {
                val inv = 1f / max(dist, 0.001f)
                s.vx = (pcx - scx) * inv * 40f
                s.vy = (pcy - scy) * inv * 40f
            } else if (s.think <= 0f) {
                s.think = 0.8f + random.nextFloat() * 1.4f
                val a = random.nextFloat() * 2f * PI.toFloat()
                s.vx = cos(a) * 22f
                s.vy = sin(a) * 22f
            }
            if (!moveBox(s, (s.vx + s.kbx) * dt, (s.vy + s.kby) * dt)) s.think = 0f
            if (player.invuln <= 0f && overlaps(player, s)) {
                player.hp--
                player.invuln = 1.2f
                hurtFlash = 0.35f
                val inv = 1f / max(dist, 0.001f)
                player.kbx = (pcx - scx) * inv * 150f
                player.kby = (pcy - scy) * inv * 150f
                if (player.hp <= 0) {
                    state = State.DEAD
                    return
                }
            }
        }

        for (g in gems) {
            if (g.taken) continue
            if (abs(g.x - pcx) < 10f && abs(g.y - pcy) < 12f) {
                g.taken = true
                gemsGot++
                if (gemsGot >= gemsTotal) {
                    state = State.WIN
                    return
                }
            }
        }

        camX = (player.x + player.w / 2 - VW / 2f).coerceIn(0f, (MAP_W * TILE - VW).toFloat())
        camY = (player.y + player.h / 2 - VH / 2f).coerceIn(0f, (MAP_H * TILE - VH).toFloat())
    }

    private fun hash(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

    fun render(c: Canvas) {
        val tx0 = (camX / TILE).toInt()
        val ty0 = (camY / TILE).toInt()
        val tx1 = min(MAP_W - 1, ((camX + VW) / TILE).toInt() + 1)
        val ty1 = min(MAP_H - 1, ((camY + VH) / TILE).toInt() + 1)
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                drawTile(c, tx, ty, tx * TILE - camX, ty * TILE - camY)
            }
        }

        for (g in gems) {
            if (g.taken) continue
            drawGem(c, g.x - camX, g.y - camY + sin(time * 3f + g.phase) * 1.5f)
        }

        for (s in slimes) {
            if (s.alive) drawSlime(c, s)
        }

        drawPlayer(c)

        if (swingTimer > 0f) drawSlash(c)

        drawHud(c)

        if (hurtFlash > 0f) {
            paint.color = ((hurtFlash / 0.35f * 90).toInt() shl 24) or 0x00ff0000
            c.drawRect(0f, 0f, VW.toFloat(), VH.toFloat(), paint)
        }

        when (state) {
            State.TITLE -> drawCentered(c, "PIXEL QUEST", VH / 2f - 18f, 20f, 0xFF4dd0e8.toInt())
            State.DEAD -> {
                overlay(c)
                drawCentered(c, "YOU DIED", VH / 2f - 14f, 20f, 0xFFe23b3b.toInt())
            }
            State.WIN -> {
                overlay(c)
                drawCentered(c, "YOU WIN!", VH / 2f - 20f, 20f, 0xFFffd94a.toInt())
                drawCentered(c, "All $gemsTotal gems collected", VH / 2f + 2f, 10f, 0xFFffffff.toInt())
            }
            else -> {}
        }
        if (state != State.PLAY) {
            if ((time * 2f).toInt() % 2 == 0) {
                drawCentered(c, "tap to play", VH / 2f + 22f, 10f, 0xFFffffff.toInt())
            }
            drawCentered(c, "left: move   right: attack", VH - 14f, 8f, 0xFFc8c8c8.toInt())
        }
    }

    private fun overlay(c: Canvas) {
        paint.color = 0x96000000
        c.drawRect(0f, 0f, VW.toFloat(), VH.toFloat(), paint)
    }

    private fun drawCentered(c: Canvas, text: String, y: Float, size: Float, color: Int) {
        textPaint.color = 0xFF000000.toInt()
        textPaint.textSize = size
        val x = VW / 2f - textPaint.measureText(text) / 2f
        c.drawText(text, x + 1f, y + 1f, textPaint)
        textPaint.color = color
        c.drawText(text, x, y, textPaint)
    }

    private fun drawTile(c: Canvas, tx: Int, ty: Int, sx: Float, sy: Float) {
        val t = tileAt(tx, ty)
        val h = hash(tx, ty)
        when (t) {
            T_GRASS, T_TALL, T_FLOWER, T_TREE, T_ROCK -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFF4a8f3c.toInt() else 0xFF468838.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                if (h % 5 == 0) {
                    paint.color = 0xFF3f7d33.toInt()
                    val ox = (h % 11) + 2f
                    val oy = (h % 9) + 3f
                    c.drawRect(sx + ox, sy + oy, sx + ox + 1f, sy + oy + 2f, paint)
                }
            }
            T_PATH -> {
                paint.color = 0xFFc9a86a.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFFb5945a.toInt()
                c.drawRect(sx + (h % 12), sy + (h % 10), sx + (h % 12) + 2f, sy + (h % 10) + 2f, paint)
                c.drawRect(sx + ((h / 3) % 12), sy + ((h / 5) % 11), sx + ((h / 3) % 12) + 1f, sy + ((h / 5) % 11) + 1f, paint)
            }
            T_WATER -> {
                paint.color = 0xFF3a6ea8.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF5b8fd4.toInt()
                val wave = ((time * 8f).toInt() + tx * 3 + ty * 7) % 12
                c.drawRect(sx + 2f, sy + wave, sx + 7f, sy + wave + 1f, paint)
                c.drawRect(sx + 9f, sy + (wave + 6) % 14, sx + 14f, sy + (wave + 6) % 14 + 1f, paint)
            }
        }
        when (t) {
            T_TALL -> {
                paint.color = 0xFF2e6b2a.toInt()
                val bx = sx + (h % 8) + 1f
                c.drawRect(bx, sy + 6f, bx + 1f, sy + 13f, paint)
                c.drawRect(bx + 3f, sy + 4f, bx + 4f, sy + 12f, paint)
                c.drawRect(bx + 6f, sy + 7f, bx + 7f, sy + 13f, paint)
            }
            T_FLOWER -> {
                paint.color = if (h % 2 == 0) 0xFFf2f2f2.toInt() else 0xFFe86a8a.toInt()
                val fx = sx + (h % 10) + 2f
                val fy = sy + (h % 8) + 3f
                c.drawRect(fx, fy, fx + 2f, fy + 2f, paint)
                paint.color = 0xFFffd94a.toInt()
                c.drawRect(fx + 0.5f, fy + 0.5f, fx + 1.5f, fy + 1.5f, paint)
            }
            T_TREE -> {
                paint.color = 0xFF6b4a2b.toInt()
                c.drawRect(sx + 6f, sy + 9f, sx + 10f, sy + 16f, paint)
                paint.color = 0xFF2f6b2f.toInt()
                c.drawRect(sx + 2f, sy + 2f, sx + 14f, sy + 11f, paint)
                c.drawRect(sx + 4f, sy, sx + 12f, sy + 2f, paint)
                paint.color = 0xFF3f8f3f.toInt()
                c.drawRect(sx + 4f, sy + 1f, sx + 10f, sy + 4f, paint)
                c.drawRect(sx + 3f, sy + 3f, sx + 7f, sy + 6f, paint)
            }
            T_ROCK -> {
                paint.color = 0xFF8a8f98.toInt()
                c.drawRect(sx + 2f, sy + 5f, sx + 14f, sy + 14f, paint)
                c.drawRect(sx + 4f, sy + 3f, sx + 12f, sy + 5f, paint)
                paint.color = 0xFFb0b5bd.toInt()
                c.drawRect(sx + 4f, sy + 4f, sx + 9f, sy + 7f, paint)
                paint.color = 0xFF6a6f78.toInt()
                c.drawRect(sx + 2f, sy + 12f, sx + 14f, sy + 14f, paint)
            }
        }
    }

    private fun drawGem(c: Canvas, cx: Float, cy: Float) {
        val l = cx - 3.5f
        val r = cx + 3.5f
        paint.color = 0xFF35d0e8.toInt()
        c.drawRect(cx - 1f, cy - 4.5f, cx + 1f, cy - 3.5f, paint)
        c.drawRect(l + 1f, cy - 3.5f, r - 1f, cy - 2.5f, paint)
        c.drawRect(l, cy - 2.5f, r, cy + 0.5f, paint)
        c.drawRect(l + 1f, cy + 0.5f, r - 1f, cy + 2.5f, paint)
        c.drawRect(cx - 1f, cy + 2.5f, cx + 1f, cy + 3.5f, paint)
        paint.color = 0xFF1a8fa8.toInt()
        c.drawRect(cx + 1f, cy - 2.5f, r, cy + 0.5f, paint)
        paint.color = 0xFFa8f0f8.toInt()
        c.drawRect(l + 1f, cy - 2.5f, l + 2f, cy - 0.5f, paint)
    }

    private fun drawSlime(c: Canvas, s: Slime) {
        val sx = s.x - 2f - camX
        val sy = s.y - 3f - camY
        val sq = sin(time * 6f + s.phase)
        paint.color = if (s.flash > 0f) 0xFFffffff.toInt() else 0xFF58c14e.toInt()
        c.drawRect(sx + 1f, sy + 3f + sq, sx + 15f, sy + 12f, paint)
        c.drawRect(sx + 3f, sy + 1f + sq, sx + 13f, sy + 3f + sq, paint)
        paint.color = if (s.flash > 0f) 0xFFdddddd.toInt() else 0xFF2f7a2a.toInt()
        c.drawRect(sx + 1f, sy + 10f, sx + 15f, sy + 12f, paint)
        paint.color = 0xFFffffff.toInt()
        c.drawRect(sx + 4f, sy + 5f + sq, sx + 6f, sy + 7f + sq, paint)
        c.drawRect(sx + 10f, sy + 5f + sq, sx + 12f, sy + 7f + sq, paint)
        paint.color = 0xFF1a1a1a.toInt()
        c.drawRect(sx + 5f, sy + 6f + sq, sx + 6f, sy + 7f + sq, paint)
        c.drawRect(sx + 10f, sy + 6f + sq, sx + 11f, sy + 7f + sq, paint)
    }

    private fun drawPlayer(c: Canvas) {
        if (player.invuln > 0f && (time * 12f).toInt() % 2 == 0) return
        val bob = if (player.moving) (((player.walkT * 8f).toInt()) % 2).toFloat() else 0f
        val px = player.x - 1f - camX
        val py = player.y - 2f - camY + bob
        val facing = if (abs(player.dirX) > abs(player.dirY)) {
            if (player.dirX < 0) 1 else 2
        } else {
            if (player.dirY < 0) 3 else 0
        }
        paint.color = 0xFF35455e.toInt()
        c.drawRect(px + 2f, py + 11f, px + 5f, py + 14f, paint)
        c.drawRect(px + 7f, py + 11f, px + 10f, py + 14f, paint)
        paint.color = 0xFFc0392b.toInt()
        c.drawRect(px + 1f, py + 6f, px + 11f, py + 11f, paint)
        paint.color = 0xFFf2c79a.toInt()
        c.drawRect(px, py + 7f, px + 2f, py + 10f, paint)
        c.drawRect(px + 10f, py + 7f, px + 12f, py + 10f, paint)
        c.drawRect(px + 2f, py, px + 10f, py + 6f, paint)
        paint.color = 0xFF5a3a1e.toInt()
        if (facing == 3) {
            c.drawRect(px + 2f, py, px + 10f, py + 5f, paint)
        } else {
            c.drawRect(px + 2f, py, px + 10f, py + 2f, paint)
            if (facing == 1) c.drawRect(px + 2f, py + 2f, px + 4f, py + 4f, paint)
            if (facing == 2) c.drawRect(px + 8f, py + 2f, px + 10f, py + 4f, paint)
        }
        if (facing != 3) {
            paint.color = 0xFF1a1a1a.toInt()
            when (facing) {
                0 -> {
                    c.drawRect(px + 4f, py + 3f, px + 5f, py + 5f, paint)
                    c.drawRect(px + 7f, py + 3f, px + 8f, py + 5f, paint)
                }
                1 -> c.drawRect(px + 3f, py + 3f, px + 4f, py + 5f, paint)
                2 -> c.drawRect(px + 8f, py + 3f, px + 9f, py + 5f, paint)
            }
        }
    }

    private fun drawSlash(c: Canvas) {
        val cx = player.x + player.w / 2 - camX
        val cy = player.y + player.h / 2 - camY
        val ang = atan2(player.dirY, player.dirX) * 180f / PI.toFloat()
        val alpha = (min(1f, swingTimer / SWING_TIME) * 220).toInt()
        paint.color = (alpha shl 24) or 0x00ffffff
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        oval.set(cx - 14f, cy - 14f, cx + 14f, cy + 14f)
        c.drawArc(oval, ang - 55f, 110f, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeart(c: Canvas, x: Float, y: Float, full: Boolean) {
        paint.color = if (full) 0xFFe23b3b.toInt() else 0xFF3a2020.toInt()
        c.drawRect(x + 1f, y, x + 3f, y + 2f, paint)
        c.drawRect(x + 4f, y, x + 6f, y + 2f, paint)
        c.drawRect(x, y + 1f, x + 7f, y + 3f, paint)
        c.drawRect(x + 1f, y + 3f, x + 6f, y + 4f, paint)
        c.drawRect(x + 2f, y + 4f, x + 5f, y + 5f, paint)
        c.drawRect(x + 3f, y + 5f, x + 4f, y + 6f, paint)
        if (full) {
            paint.color = 0xFFff8a8a.toInt()
            c.drawRect(x + 1f, y + 1f, x + 2f, y + 2f, paint)
        }
    }

    private fun drawHud(c: Canvas) {
        for (i in 0 until 3) {
            drawHeart(c, 4f + i * 9f, 4f, i < player.hp)
        }
        drawGem(c, VW - 34f, 9f)
        textPaint.color = 0xFF000000.toInt()
        textPaint.textSize = 10f
        val txt = "$gemsGot/$gemsTotal"
        c.drawText(txt, VW - 24f + 1f, 14f, textPaint)
        textPaint.color = 0xFFffffff.toInt()
        c.drawText(txt, VW - 24f, 13f, textPaint)
    }
}
