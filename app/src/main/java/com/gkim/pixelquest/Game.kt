package com.gkim.pixelquest

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
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
        const val CHUNK = 32
        const val REGION = 64
        const val MOB_CAP = 14
        const val BANDIT_CAP = 3
        const val PLAYER_SPEED = 70f
        const val SWING_TIME = 0.22f
        const val SWING_COOLDOWN = 0.38f

        const val MAX_HP = 10
        const val HOT_SLOTS = 6
        const val INV_SLOTS = 40
        const val STACK = 100
        const val HOT_X0 = 106f
        const val HOT_Y0 = 160f
        const val HOT_CELL = 18f

        const val PX0 = 40f
        const val PY0 = 16f
        const val PW = 240f
        const val PH = 148f
        const val GRID_X0 = 88f
        const val GRID_Y0 = 40f
        const val GRID_COLS = 8
        const val GRID_ROWS = 5
    }

    enum class State { TITLE, PLAY, DEAD }

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
        var hp = MAX_HP
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
            hp = MAX_HP
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
        var type = 0
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
        var homeX = 0f
        var homeY = 0f
    }

    private val B_FOREST = 0
    private val B_DESERT = 1
    private val B_SNOW = 2
    private val B_VOLCANIC = 3
    private val B_MOUNTAIN = 4
    private val B_LAKE = 5

    private val T_GRASS = 0
    private val T_TALL = 1
    private val T_FLOWER = 2
    private val T_TREE = 3
    private val T_ROCK = 4
    private val T_PATH = 6
    private val T_SAND = 7
    private val T_CACTUS = 8
    private val T_SNOW = 9
    private val T_STREE = 10
    private val T_VROCK = 11
    private val T_MFLOOR = 13
    private val T_ORE_IRON = 14
    private val T_ORE_GOLD = 15
    private val T_DEAD_TREE = 16
    private val T_MWALL = 17
    private val T_RUIN_WALL = 18
    private val T_RUIN_FLOOR = 19
    private val T_WORKBENCH = 20
    private val T_WWOOD = 21
    private val T_WSTONE = 22
    private val T_DOOR = 23
    private val T_HOUSE_WALL = 24
    private val T_HOUSE_FLOOR = 25
    private val T_FENCE = 26
    private val T_WINDOW = 27
    private val T_FLOOR_WOOD = 28
    private val T_FURNACE = 29

    private val I_SWORD = 0
    private val I_AXE = 1
    private val I_PICK = 2
    private val I_WOOD = 3
    private val I_STONE = 4
    private val I_IRON = 5
    private val I_GOLD = 6
    private val I_WORKBENCH = 7
    private val I_WWOOD = 8
    private val I_WSTONE = 9
    private val I_DOOR = 10
    private val I_IRON_SWORD = 11
    private val I_IRON_PICK = 12
    private val I_FLOOR = 13
    private val I_WINDOW = 14
    private val I_FENCE = 15
    private val I_FURNACE = 16

    private val itemNames = arrayOf(
        "Sword", "Axe", "Pickaxe", "Wood", "Stone", "Iron", "Gold",
        "Workbench", "Wood wall", "Stone wall", "Door", "Iron sword", "Iron pick",
        "Wood floor", "Window", "Fence", "Furnace"
    )

    var state = State.TITLE
    private val chunks = HashMap<Long, IntArray>()
    private val nodeHp = HashMap<Long, Int>()
    private var worldSeed = 0L
    private val player = Player()
    private val slimes = ArrayList<Slime>()
    private var spawnX = 0f
    private var spawnY = 0f
    private var camX = 0f
    private var camY = 0f
    private var swingTimer = 0f
    private var swingCd = 0f
    private var swingId = 0
    private var gatherX = -1
    private var gatherY = -1
    private var gatherFlash = 0f
    private var hurtFlash = 0f
    private var time = 0f
    private var random = Random(42)
    private val invItem = IntArray(INV_SLOTS) { -1 }
    private val invCount = IntArray(INV_SLOTS)
    private var selected = 0
    var invOpen = false
    private set
    private var invPage = 0
    private var invSel = -1
    private var msg = ""
    private var msgTime = 0f
    private var curBiome = -1
    private var tapAttack = false
    private var btnFlash = 0f
    private var startQueued = false
    private var slimeTimer = 0f
    private var banditTimer = 25f
    private var villageTimer = 0f

    private val paint = Paint()
    private val textPaint = Paint()
    private val oval = RectF()

    private val slimeBody = intArrayOf(0xFF58c14e.toInt(), 0xFFd8a03c.toInt(), 0xFF7ad0e8.toInt(), 0xFFd84a3a.toInt(), 0xFF9a9aa8.toInt(), 0xFF4a5568.toInt(), 0xFF8a6a4a.toInt())
    private val slimeDark = intArrayOf(0xFF2f7a2a.toInt(), 0xFF96681e.toInt(), 0xFF3a92ac.toInt(), 0xFF8f2418.toInt(), 0xFF5c6068.toInt(), 0xFF2c3440.toInt(), 0xFF6a5238.toInt())
    private val slimeMaxHp = intArrayOf(2, 2, 3, 4, 3, 6, 5)
    private val slimeWander = floatArrayOf(22f, 26f, 20f, 24f, 18f, 30f, 12f)
    private val slimeChase = floatArrayOf(40f, 46f, 36f, 44f, 34f, 58f, 20f)

    private val villagerLines = arrayOf(
        "Welcome, traveler!",
        "Beware of bandits out there!",
        "Fine weather today.",
        "We built this village ourselves.",
        "Come back anytime!"
    )
    private val biomeNames = arrayOf("Forest", "Desert", "Snowfields", "Volcanic lands", "Mountains", "Meadow")

    init {
        textPaint.isAntiAlias = false
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.isFakeBoldText = true
    }

    private fun fdiv(a: Int, b: Int): Int = if (a >= 0) a / b else (a - b + 1) / b

    private fun tf(v: Float): Int = floor(v / TILE).toInt()

    private fun fmod(a: Int, b: Int): Int = a - fdiv(a, b) * b

    private fun chunkKey(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)

    private fun tkey(tx: Int, ty: Int): Long =
        (tx.toLong() shl 32) or (ty.toLong() and 0xffffffffL)

    private fun chunkAt(cx: Int, cy: Int): IntArray =
        chunks.getOrPut(chunkKey(cx, cy)) { generateChunk(cx, cy) }

    private fun setTile(tx: Int, ty: Int, t: Int) {
        chunkAt(fdiv(tx, CHUNK), fdiv(ty, CHUNK))[fmod(ty, CHUNK) * CHUNK + fmod(tx, CHUNK)] = t
    }

    private fun tileAt(tx: Int, ty: Int): Int =
        chunkAt(fdiv(tx, CHUNK), fdiv(ty, CHUNK))[fmod(ty, CHUNK) * CHUNK + fmod(tx, CHUNK)]

    private fun solidTile(t: Int) =
        t == T_TREE || t == T_ROCK || t == T_CACTUS || t == T_STREE ||
            t == T_ORE_IRON || t == T_ORE_GOLD || t == T_DEAD_TREE ||
            t == T_MWALL || t == T_RUIN_WALL ||
            t == T_WORKBENCH || t == T_WWOOD || t == T_WSTONE ||
            t == T_HOUSE_WALL || t == T_FENCE || t == T_WINDOW || t == T_FURNACE

    private fun solid(tx: Int, ty: Int) = solidTile(tileAt(tx, ty))

    private fun h2(x: Int, y: Int, s: Int): Float {
        var n = x.toLong() * 374761393L + y.toLong() * 668265263L + s.toLong() * 1013904223L
        n = n xor (n ushr 13)
        n *= 1274126177L
        n = n xor (n ushr 16)
        return (n and 0x7fffffffL) / 2147483647f
    }

    private fun vnoise(x: Float, y: Float, scale: Float, s: Int): Float {
        val fx = x / scale
        val fy = y / scale
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val tx = fx - x0
        val ty = fy - y0
        val sx = tx * tx * (3 - 2 * tx)
        val sy = ty * ty * (3 - 2 * ty)
        val a = h2(x0, y0, s)
        val b = h2(x0 + 1, y0, s)
        val c = h2(x0, y0 + 1, s)
        val d = h2(x0 + 1, y0 + 1, s)
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
    }

    private fun biomeAt(tx: Int, ty: Int): Int {
        val e = vnoise(tx.toFloat(), ty.toFloat(), 30f, 11) * 0.65f +
            vnoise(tx.toFloat(), ty.toFloat(), 9f, 12) * 0.35f
        if (e > 0.64f) return B_MOUNTAIN
        if (e < 0.23f) return B_LAKE
        val t = vnoise(tx.toFloat(), ty.toFloat(), 36f, 21)
        if (vnoise(tx.toFloat(), ty.toFloat(), 44f, 31) > 0.76f) return B_VOLCANIC
        return when {
            t > 0.62f -> B_DESERT
            t < 0.38f -> B_SNOW
            else -> B_FOREST
        }
    }

    private fun bareTile(tx: Int, ty: Int): Int = when (biomeAt(tx, ty)) {
        B_DESERT -> T_SAND
        B_SNOW -> T_SNOW
        B_VOLCANIC -> T_VROCK
        B_MOUNTAIN -> T_MFLOOR
        else -> T_GRASS
    }

    fun start(seed: Long) {
        random = Random(seed)
        worldSeed = seed
        chunks.clear()
        nodeHp.clear()
        findSpawn()
        player.reset(spawnX, spawnY)
        slimes.clear()
        slimeTimer = 0f
        banditTimer = 25f
        villageTimer = 0f
        repeat(8) { spawnMob(-1) }
        invItem.fill(-1)
        invCount.fill(0)
        invOpen = false
        invSel = -1
        invItem[0] = I_SWORD; invCount[0] = 1
        invItem[1] = I_AXE; invCount[1] = 1
        invItem[2] = I_PICK; invCount[2] = 1
        selected = 0
        curBiome = -1
        msg = ""
        msgTime = 0f
        swingTimer = 0f
        swingCd = 0f
        hurtFlash = 0f
        startQueued = false
        state = State.PLAY
    }

    private fun respawn() {
        player.reset(spawnX, spawnY)
        hurtFlash = 0f
        invOpen = false
        tapAttack = false
        startQueued = false
        state = State.PLAY
    }

    private fun findSpawn() {
        for (r in 0 until 64) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (max(abs(dx), abs(dy)) != r) continue
                    val tx = dx
                    val ty = dy
                    if (solid(tx, ty)) continue
                    if (biomeAt(tx, ty) != B_FOREST) continue
                    spawnX = tx * TILE + 8f
                    spawnY = ty * TILE + 8f
                    return
                }
            }
        }
        spawnX = 8f
        spawnY = 8f
    }

    private fun randomWalkable(minDist: Float, maxDist: Float = 1e9f): Pair<Float, Float>? {
        val pcx = player.x + player.w / 2
        val pcy = player.y + player.h / 2
        repeat(400) {
            val ang = random.nextFloat() * 2f * PI.toFloat()
            val dist = minDist + random.nextFloat() * (maxDist - minDist)
            val tx = tf(pcx + cos(ang) * dist)
            val ty = tf(pcy + sin(ang) * dist)
            if (solid(tx, ty)) return@repeat
            return Pair(tx * TILE + 8f, ty * TILE + 8f)
        }
        return null
    }

    private fun randomWalkableNear(cx: Float, cy: Float, radius: Float): Pair<Float, Float>? {
        val r = (radius / TILE).toInt() + 1
        val ctx = tf(cx)
        val cty = tf(cy)
        repeat(60) {
            val tx = ctx - r + random.nextInt(r * 2 + 1)
            val ty = cty - r + random.nextInt(r * 2 + 1)
            if (!solid(tx, ty)) return Pair(tx * TILE + 8f, ty * TILE + 8f)
        }
        return null
    }

    private fun spawnMob(kind: Int): Boolean {
        val p = randomWalkable(90f, 160f) ?: return false
        val m = Slime()
        m.x = p.first - m.w / 2
        m.y = p.second - m.h / 2
        m.phase = random.nextFloat() * 6f
        m.type = if (kind == -1) {
            when (biomeAt(tf(p.first), tf(p.second))) {
                B_DESERT -> 1
                B_SNOW -> 2
                B_VOLCANIC -> 3
                B_MOUNTAIN -> 4
                else -> 0
            }
        } else kind
        m.hp = slimeMaxHp[m.type]
        slimes.add(m)
        if (m.type == 5) showMsg("A bandit attacks!")
        return true
    }

    private fun trySpawnVillagers() {
        val pcx = player.x + player.w / 2
        val pcy = player.y + player.h / 2
        if (slimes.count { it.type == 6 } >= 3) return
        val prx = fdiv(tf(pcx), REGION)
        val pry = fdiv(tf(pcy), REGION)
        for (ry in pry - 1..pry + 1) {
            for (rx in prx - 1..prx + 1) {
                if (!villageSite(rx, ry)) continue
                val o = siteOrigin(rx, ry, 73)
                val cx = (o.first + 9) * TILE + 8f
                val cy = (o.second + 7) * TILE + 8f
                if (hypot(cx - pcx, cy - pcy) > 260f) continue
                if (slimes.any { it.type == 6 && hypot(it.x - cx, it.y - cy) < 100f }) continue
                val p = randomWalkableNear(cx, cy, 60f) ?: continue
                val m = Slime()
                m.x = p.first - m.w / 2
                m.y = p.second - m.h / 2
                m.type = 6
                m.hp = slimeMaxHp[6]
                m.phase = random.nextFloat() * 6f
                m.homeX = cx
                m.homeY = cy
                slimes.add(m)
            }
        }
    }

    private fun villageSite(rx: Int, ry: Int): Boolean =
        h2(rx, ry, 71) < 0.16f && biomeAt(rx * REGION + 32, ry * REGION + 32) == B_FOREST

    private fun ruinSite(rx: Int, ry: Int): Boolean =
        !villageSite(rx, ry) && h2(rx, ry, 72) < 0.20f &&
            biomeAt(rx * REGION + 32, ry * REGION + 32) != B_LAKE

    private fun siteOrigin(rx: Int, ry: Int, salt: Int): Pair<Int, Int> =
        Pair(
            rx * REGION + 8 + (h2(rx, ry, salt) * (REGION - 34)).toInt(),
            ry * REGION + 8 + (h2(rx, ry, salt + 1) * (REGION - 26)).toInt()
        )

    private fun villageTile(x: Int, y: Int, ox: Int, oy: Int): Int {
        val vx = x - ox
        val vy = y - oy
        if (vx < 0 || vy < 0 || vx >= 18 || vy >= 14) return -1
        if (vx < 10 && vy < 7) {
            val edge = vx == 0 || vx == 9 || vy == 0 || vy == 6
            return when {
                vx == 5 && vy == 6 -> T_HOUSE_FLOOR
                edge -> if (vx == 3 && vy == 0) T_WINDOW else T_HOUSE_WALL
                else -> T_HOUSE_FLOOR
            }
        }
        if (vx >= 11 && vy >= 8 && vx < 17 && vy < 13) {
            val lx = vx - 11
            val ly = vy - 8
            val edge = lx == 0 || lx == 5 || ly == 0 || ly == 4
            return when {
                lx == 2 && ly == 4 -> T_HOUSE_FLOOR
                edge -> if (lx == 4 && ly == 0) T_WINDOW else T_HOUSE_WALL
                else -> T_HOUSE_FLOOR
            }
        }
        if (vy == 7 || (vx == 10 && vy in 7..8)) return T_PATH
        return -1
    }

    private fun ruinTile(x: Int, y: Int, ox: Int, oy: Int, rng: Random): Int {
        val rw = 12
        val rh = 8
        val vx = x - ox
        val vy = y - oy
        if (vx < 0 || vy < 0 || vx >= rw || vy >= rh) return -1
        val edge = vx == 0 || vx == rw - 1 || vy == 0 || vy == rh - 1
        return when {
            edge -> {
                val gap = (vx == rw / 2 && (vy == 0 || vy == rh - 1)) ||
                    (vy == rh / 2 && (vx == 0 || vx == rw - 1))
                if (gap) T_RUIN_FLOOR else T_RUIN_WALL
            }
            rng.nextFloat() < 0.08f -> T_ORE_GOLD
            else -> T_RUIN_FLOOR
        }
    }

    private fun generateChunk(cx: Int, cy: Int): IntArray {
        val ch = IntArray(CHUNK * CHUNK)
        val bx = cx * CHUNK
        val by = cy * CHUNK
        val rng = Random(chunkKey(cx, cy) xor worldSeed)
        for (y in 0 until CHUNK) {
            for (x in 0 until CHUNK) {
                ch[y * CHUNK + x] = baseTerrain(bx + x, by + y, rng.nextFloat())
            }
        }
        for (y in 0 until CHUNK) {
            for (x in 0 until CHUNK) {
                val wx = bx + x
                val wy = by + y
                val rx = fdiv(wx, REGION)
                val ry = fdiv(wy, REGION)
                if (villageSite(rx, ry)) {
                    val o = siteOrigin(rx, ry, 73)
                    val v = villageTile(wx, wy, o.first, o.second)
                    if (v >= 0) ch[y * CHUNK + x] = v
                } else if (ruinSite(rx, ry)) {
                    val o = siteOrigin(rx, ry, 75)
                    val v = ruinTile(wx, wy, o.first, o.second, rng)
                    if (v >= 0) ch[y * CHUNK + x] = v
                }
            }
        }
        return ch
    }

    private fun baseTerrain(x: Int, y: Int, r: Float): Int {
        val b = biomeAt(x, y)
        var t = when (b) {
            B_MOUNTAIN -> T_MFLOOR
            B_DESERT -> T_SAND
            B_SNOW -> T_SNOW
            B_VOLCANIC -> T_VROCK
            else -> T_GRASS
        }
        when (b) {
            B_FOREST -> {
                if (r < 0.07f) t = T_TREE
                else if (r < 0.11f) t = T_ROCK
                else if (r < 0.25f) t = T_TALL
                else if (r < 0.29f) t = T_FLOWER
            }
            B_DESERT -> {
                if (r < 0.035f) t = T_CACTUS
                else if (r < 0.06f) t = T_ROCK
            }
            B_SNOW -> {
                if (r < 0.06f) t = T_STREE
                else if (r < 0.09f) t = T_ROCK
            }
            B_MOUNTAIN -> {
                if (r < 0.10f) t = T_ROCK
                else if (r < 0.15f) t = T_ORE_IRON
                else if (r < 0.165f) t = T_ORE_GOLD
            }
            B_LAKE -> {
                if (r < 0.20f) t = T_FLOWER
                else if (r < 0.45f) t = T_TALL
            }
            B_VOLCANIC -> {
                if (r < 0.05f) t = T_DEAD_TREE
            }
            else -> {}
        }
        return t
    }


    private fun collides(px: Float, py: Float, w: Float, h: Float): Boolean {
        val x0 = tf(px)
        val x1 = tf(px + w - 0.01f)
        val y0 = tf(py)
        val y1 = tf(py + h - 0.01f)
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                if (solid(tx, ty)) return true
            }
        }
        return false
    }

    private fun moveBox(e: Box, dx: Float, dy: Float): Boolean {
        var moved = false
        val ox = e.x
        val oy = e.y
        val steps = max(1, ceil(max(abs(dx), abs(dy)) / (TILE / 2f)).toInt())
        val sx = dx / steps
        val sy = dy / steps
        repeat(steps) {
            if (sx != 0f) {
                val nx = e.x + sx
                if (!collides(nx, e.y, e.w, e.h)) {
                    e.x = nx
                    moved = true
                }
            }
            if (sy != 0f) {
                val ny = e.y + sy
                if (!collides(e.x, ny, e.w, e.h)) {
                    e.y = ny
                    moved = true
                }
            }
        }
        if (collides(e.x, e.y, e.w, e.h)) {
            e.x = ox
            e.y = oy
            moved = false
        }
        return moved
    }

    private fun overlaps(a: Box, b: Box): Boolean =
        a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y

    fun onTap(vx: Float, vy: Float) {
        when (state) {
            State.PLAY -> {
                if (invOpen) {
                    invTap(vx, vy)
                    return
                }
                if (vx >= VW - 18f && vy <= 18f) {
                    toggleInv()
                    return
                }
                val wx = camX + vx
                val wy = camY + vy
                for (s in slimes) {
                    if (!s.alive || s.type != 6) continue
                    if (abs(s.x + s.w / 2 - wx) < 14f && abs(s.y + s.h / 2 - wy) < 14f) {
                        showMsg(villagerLines[random.nextInt(villagerLines.size)])
                        return
                    }
                }
                if (vx >= VW - 36f && vy >= VH - 36f) {
                    btnFlash = 0.18f
                    tapAttack = true
                    return
                }
                if (vy >= HOT_Y0 - 4f && vx >= HOT_X0 - 2f && vx <= HOT_X0 + HOT_SLOTS * HOT_CELL) {
                    val slot = ((vx - HOT_X0) / HOT_CELL).toInt().coerceIn(0, HOT_SLOTS - 1)
                    selected = slot
                    val it = invItem[slot]
                    showMsg(if (it >= 0) itemNames[it] else "empty")
                } else {
                    tapAttack = true
                }
            }
            else -> startQueued = true
        }
    }

    fun toggleInv() {
        invOpen = !invOpen
        invSel = -1
        tapAttack = false
    }

    private fun invTap(vx: Float, vy: Float) {
        if (vx < PX0 || vx > PX0 + PW || vy < PY0 || vy > PY0 + PH) {
            toggleInv()
            return
        }
        if (vx >= PX0 + PW - 16f && vy <= PY0 + 16f) {
            toggleInv()
            return
        }
        if (vy >= PY0 + 4f && vy <= PY0 + 18f) {
            if (vx >= PX0 + 8f && vx <= PX0 + 68f) {
                invPage = 0
                invSel = -1
                return
            }
            if (vx >= PX0 + 72f && vx <= PX0 + 132f) {
                invPage = 1
                invSel = -1
                return
            }
        }
        if (invPage == 0) {
            if (vx >= GRID_X0 && vy >= GRID_Y0) {
                val fx = (vx - GRID_X0) / 18f
                val fy = (vy - GRID_Y0) / 18f
                val col = fx.toInt()
                val row = fy.toInt()
                if (col < GRID_COLS && row < GRID_ROWS && fx - col < 16f / 18f && fy - row < 16f / 18f) {
                    val idx = row * GRID_COLS + col
                    if (invSel >= 0 && invSel != idx) {
                        val a = invSel
                        if (invItem[a] >= 0 && invItem[a] == invItem[idx]) {
                            val move = min(invCount[a], STACK - invCount[idx])
                            invCount[idx] += move
                            invCount[a] -= move
                            if (invCount[a] == 0) invItem[a] = -1
                        } else {
                            val ti = invItem[a]; val tc = invCount[a]
                            invItem[a] = invItem[idx]; invCount[a] = invCount[idx]
                            invItem[idx] = ti; invCount[idx] = tc
                        }
                        invSel = -1
                    } else if (invItem[idx] >= 0) {
                        invSel = idx
                    } else {
                        invSel = -1
                    }
                }
            }
        } else {
            if (vx >= PX0 + 8f && vx <= PX0 + PW - 8f) {
                val fy = vy - (PY0 + 26f)
                val row = (fy / 13f).toInt()
                if (row in recipes.indices && fy - row * 13f < 12f) tryCraft(row)
            }
        }
    }

    private fun showMsg(m: String) {
        msg = m
        msgTime = 1.6f
    }

    fun update(dt: Float, input: Input) {
        time += dt
        if (hurtFlash > 0f) hurtFlash -= dt
        if (btnFlash > 0f) btnFlash -= dt
        if (msgTime > 0f) msgTime -= dt
        if (gatherFlash > 0f) gatherFlash -= dt
        when (state) {
            State.PLAY -> {
                if (invOpen) {
                    tapAttack = false
                } else {
                    updatePlay(dt, input)
                }
            }
            else -> if (startQueued || input.attack) {
                if (state == State.TITLE) start(System.nanoTime()) else respawn()
            }
        }
    }

    private fun hurtPlayer(fromX: Float, fromY: Float) {
        if (player.invuln > 0f) return
        player.hp--
        player.invuln = 1.2f
        hurtFlash = 0.35f
        val dx = player.x + player.w / 2 - fromX
        val dy = player.y + player.h / 2 - fromY
        val d = max(hypot(dx, dy), 0.001f)
        player.kbx = dx / d * 150f
        player.kby = dy / d * 150f
        if (player.hp <= 0) state = State.DEAD
    }

    private fun countItem(item: Int): Int {
        var n = 0
        for (i in 0 until INV_SLOTS) if (invItem[i] == item) n += invCount[i]
        return n
    }

    private fun addItem(item: Int, n: Int): Boolean {
        var left = n
        for (i in 0 until INV_SLOTS) {
            if (invItem[i] == item && invCount[i] < STACK) {
                val add = min(left, STACK - invCount[i])
                invCount[i] += add
                left -= add
                if (left == 0) break
            }
        }
        if (left > 0) {
            for (i in 0 until INV_SLOTS) {
                if (invItem[i] == -1) {
                    val add = min(left, STACK)
                    invItem[i] = item
                    invCount[i] = add
                    left -= add
                    if (left == 0) break
                }
            }
        }
        if (left < n) showMsg("+${n - left} ${itemNames[item]}")
        if (left > 0) {
            showMsg("Inventory full!")
            return false
        }
        return true
    }

    private fun consumeItem(item: Int, n: Int): Boolean {
        if (countItem(item) < n) return false
        var left = n
        for (i in INV_SLOTS - 1 downTo 0) {
            if (invItem[i] == item) {
                val take = min(left, invCount[i])
                invCount[i] -= take
                left -= take
                if (invCount[i] == 0) invItem[i] = -1
                if (left == 0) break
            }
        }
        return true
    }

    private fun heldItem(): Int = if (selected in 0 until HOT_SLOTS) invItem[selected] else -1

    private fun heldTool(): Int = when (heldItem()) {
        I_AXE -> I_AXE
        I_PICK, I_IRON_PICK -> I_PICK
        else -> -1
    }

    private fun swordDamage(): Int = if (heldItem() == I_IRON_SWORD) 2 else 1

    private fun pickPower(): Int = if (heldItem() == I_IRON_PICK) 2 else 1

    private fun benchNearby(): Boolean {
        val ptx = tf(player.x + player.w / 2)
        val pty = tf(player.y + player.h / 2)
        for (dy in -2..2) {
            for (dx in -2..2) {
                if (tileAt(ptx + dx, pty + dy) == T_WORKBENCH) return true
            }
        }
        return false
    }

    private val recipes = arrayOf(
        intArrayOf(I_WORKBENCH, 1, I_WOOD, 8, 0),
        intArrayOf(I_WWOOD, 4, I_WOOD, 2, 0),
        intArrayOf(I_WSTONE, 4, I_STONE, 2, 1),
        intArrayOf(I_DOOR, 1, I_WOOD, 4, 1),
        intArrayOf(I_IRON_SWORD, 1, I_IRON, 3, 1),
        intArrayOf(I_IRON_PICK, 1, I_IRON, 3, 1),
        intArrayOf(I_FLOOR, 4, I_WOOD, 2, 0),
        intArrayOf(I_FENCE, 4, I_WOOD, 2, 1),
        intArrayOf(I_WINDOW, 1, I_WOOD, 2, 1),
        intArrayOf(I_FURNACE, 1, I_STONE, 8, 1)
    )

    private val recipeExtra = arrayOf(
        intArrayOf(0, 0),
        intArrayOf(0, 0),
        intArrayOf(0, 0),
        intArrayOf(I_WOOD, 1),
        intArrayOf(I_WOOD, 2),
        intArrayOf(I_WOOD, 2),
        intArrayOf(0, 0),
        intArrayOf(0, 0),
        intArrayOf(0, 0),
        intArrayOf(0, 0)
    )

    private fun recipeLabel(r: Int): String {
        val rec = recipes[r]
        val extra = recipeExtra[r]
        var s = "${itemNames[rec[0]]} x${rec[1]} < ${itemNames[rec[2]]} x${rec[3]}"
        for (e in extra.indices step 2) {
            if (extra[e] > 0) s += " + ${itemNames[extra[e]]} x${extra[e + 1]}"
        }
        if (rec[4] == 1) s += " [bench]"
        return s
    }

    private fun tryCraft(r: Int) {
        val rec = recipes[r]
        if (rec[4] == 1 && !benchNearby()) {
            showMsg("Need a workbench nearby")
            return
        }
        if (countItem(rec[2]) < rec[3]) {
            showMsg("Not enough ${itemNames[rec[2]]}")
            return
        }
        val extra = recipeExtra[r]
        for (e in extra.indices step 2) {
            if (extra[e] > 0 && countItem(extra[e]) < extra[e + 1]) {
                showMsg("Not enough ${itemNames[extra[e]]}")
                return
            }
        }
        consumeItem(rec[2], rec[3])
        for (e in extra.indices step 2) {
            if (extra[e] > 0) consumeItem(extra[e], extra[e + 1])
        }
        if (addItem(rec[0], rec[1])) showMsg("Crafted ${itemNames[rec[0]]}")
    }

    private fun buildableTile(t: Int): Boolean = when (t) {
        T_MWALL -> false
        else -> true
    }

    private fun tryPlace() {
        val item = heldItem()
        val tile = when (item) {
            I_WORKBENCH -> T_WORKBENCH
            I_WWOOD -> T_WWOOD
            I_WSTONE -> T_WSTONE
            I_DOOR -> T_DOOR
            I_FLOOR -> T_FLOOR_WOOD
            I_WINDOW -> T_WINDOW
            I_FENCE -> T_FENCE
            I_FURNACE -> T_FURNACE
            else -> return
        }
        val ttx = tf(player.x + player.w / 2 + player.dirX * 14f)
        val tty = tf(player.y + player.h / 2 + player.dirY * 14f)
        val cur = tileAt(ttx, tty)
        if (solidTile(cur) || !buildableTile(cur)) {
            showMsg("Can't build here")
            return
        }
        val tl = ttx * TILE
        val tt = tty * TILE
        if (player.x < tl + TILE && player.x + player.w > tl && player.y < tt + TILE && player.y + player.h > tt) {
            showMsg("No room")
            return
        }
        setTile(ttx, tty, tile)
        consumeItem(item, 1)
        gatherFlash = 0.15f
    }

    private fun maxNodeHp(tile: Int): Int = when (tile) {
        T_TREE, T_STREE, T_CACTUS, T_DEAD_TREE -> 3
        T_ROCK -> 3
        T_ORE_IRON -> 4
        T_ORE_GOLD -> 5
        T_WORKBENCH, T_WWOOD, T_WSTONE, T_DOOR -> 2
        T_HOUSE_WALL, T_FENCE, T_WINDOW, T_FLOOR_WOOD -> 2
        T_FURNACE -> 3
        else -> 0
    }

    private fun toolFor(tile: Int): Int = when (tile) {
        T_TREE, T_STREE, T_CACTUS, T_DEAD_TREE -> I_AXE
        T_ROCK, T_ORE_IRON, T_ORE_GOLD -> I_PICK
        T_WORKBENCH, T_WWOOD, T_DOOR -> I_AXE
        T_WSTONE, T_FURNACE -> I_PICK
        T_HOUSE_WALL, T_FENCE, T_WINDOW, T_FLOOR_WOOD -> I_AXE
        else -> -1
    }

    private fun updatePlay(dt: Float, input: Input) {
        if (player.invuln > 0f) player.invuln -= dt
        if (swingCd > 0f) swingCd -= dt

        slimeTimer -= dt
        if (slimeTimer <= 0f) {
            slimeTimer = 2.5f
            if (slimes.count { it.alive && it.type < 5 } < MOB_CAP) spawnMob(-1)
        }
        banditTimer -= dt
        if (banditTimer <= 0f) {
            banditTimer = 18f + random.nextFloat() * 15f
            if (slimes.count { it.alive && it.type == 5 } < BANDIT_CAP) spawnMob(5)
        }
        villageTimer -= dt
        if (villageTimer <= 0f) {
            villageTimer = 4f
            trySpawnVillagers()
        }

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

        val attack = input.attack || tapAttack
        tapAttack = false
        if (attack && swingCd <= 0f) {
            val heldRaw = heldItem()
            if (heldRaw in I_WORKBENCH..I_FURNACE) {
                swingCd = 0.25f
                tryPlace()
            } else {
                swingTimer = SWING_TIME
                swingCd = SWING_COOLDOWN
                swingId++
                val ttx = tf(player.x + player.w / 2 + player.dirX * 14f)
                val tty = tf(player.y + player.h / 2 + player.dirY * 14f)
                gatherX = ttx
                gatherY = tty
            }
        }
        if (swingTimer > 0f) {
            swingTimer -= dt
            val heldRaw = heldItem()
            val held = heldTool()
            val tile = if (gatherX >= 0) tileAt(gatherX, gatherY) else -1
            val needTool = if (tile >= 0) toolFor(tile) else -1
            if (needTool >= 0) {
                if (held != needTool) {
                    if (msgTime <= 0f) showMsg("need ${itemNames[needTool]}")
                } else {
                    val gk = tkey(gatherX, gatherY)
                    val cur = nodeHp[gk] ?: maxNodeHp(tile)
                    val left = cur - pickPower()
                    if (left <= 0) {
                        nodeHp.remove(gk)
                        val tx = gatherX
                        val ty = gatherY
                        setTile(tx, ty, bareTile(tx, ty))
                        when (tile) {
                            T_TREE, T_STREE, T_CACTUS, T_DEAD_TREE -> addItem(I_WOOD, 2 + random.nextInt(2))
                            T_ROCK -> addItem(I_STONE, 2 + random.nextInt(2))
                            T_ORE_IRON -> addItem(I_IRON, 1 + random.nextInt(2))
                            T_ORE_GOLD -> addItem(I_GOLD, 1 + random.nextInt(2))
                            T_WORKBENCH -> addItem(I_WORKBENCH, 1)
                            T_WWOOD -> addItem(I_WWOOD, 1)
                            T_WSTONE -> addItem(I_WSTONE, 1)
                            T_DOOR -> addItem(I_DOOR, 1)
                            T_HOUSE_WALL -> addItem(I_WOOD, 1)
                            T_FLOOR_WOOD, T_HOUSE_FLOOR -> addItem(I_FLOOR, 1)
                            T_FENCE -> addItem(I_FENCE, 1)
                            T_WINDOW -> addItem(I_WINDOW, 1)
                            T_FURNACE -> addItem(I_FURNACE, 1)
                        }
                    } else {
                        nodeHp[gk] = left
                    }
                    gatherFlash = 0.15f
                }
            }
            if (heldRaw == I_SWORD || heldRaw == I_IRON_SWORD) {
                val sx = player.x + player.w / 2 + player.dirX * 13f
                val sy = player.y + player.h / 2 + player.dirY * 13f
                for (s in slimes) {
                    if (!s.alive || s.hitSwing == swingId) continue
                    if (abs(s.x + s.w / 2 - sx) < 13f && abs(s.y + s.h / 2 - sy) < 13f) {
                        s.hitSwing = swingId
                        s.hp -= swordDamage()
                        s.flash = 0.15f
                        s.kbx = player.dirX * 130f
                        s.kby = player.dirY * 130f
                        if (s.hp <= 0) {
                            s.alive = false
                            if (s.type == 5) {
                                addItem(I_GOLD, 1 + random.nextInt(2))
                                if (random.nextFloat() < 0.3f) addItem(I_IRON, 1)
                            }
                        }
                    }
                }
            }
        }

        val pcx = player.x + player.w / 2
        val pcy = player.y + player.h / 2
        val mit = slimes.iterator()
        while (mit.hasNext()) {
            val s = mit.next()
            if (!s.alive || hypot(s.x - pcx, s.y - pcy) > 340f) mit.remove()
        }
        for (s in slimes) {
            if (!s.alive) continue
            if (s.flash > 0f) s.flash -= dt
            s.kbx *= decay
            s.kby *= decay
            val scx = s.x + s.w / 2
            val scy = s.y + s.h / 2
            val dist = hypot(pcx - scx, pcy - scy)
            s.think -= dt
            if (s.type == 6) {
                if (s.think <= 0f) {
                    s.think = 1.0f + random.nextFloat() * 1.5f
                    val a = random.nextFloat() * 2f * PI.toFloat()
                    s.vx = cos(a) * slimeWander[6]
                    s.vy = sin(a) * slimeWander[6]
                    val hd = hypot(s.homeX - scx, s.homeY - scy)
                    if (hd > 70f) {
                        s.vx = (s.homeX - scx) / hd * 24f
                        s.vy = (s.homeY - scy) / hd * 24f
                    }
                }
            } else if (dist < (if (s.type == 5) 150f else 90f)) {
                val inv = 1f / max(dist, 0.001f)
                s.vx = (pcx - scx) * inv * slimeChase[s.type]
                s.vy = (pcy - scy) * inv * slimeChase[s.type]
            } else if (s.think <= 0f) {
                s.think = 0.8f + random.nextFloat() * 1.4f
                val a = random.nextFloat() * 2f * PI.toFloat()
                s.vx = cos(a) * slimeWander[s.type]
                s.vy = sin(a) * slimeWander[s.type]
            }
            if (!moveBox(s, (s.vx + s.kbx) * dt, (s.vy + s.kby) * dt)) s.think = 0f
            if (overlaps(player, s) && s.type != 6) {
                hurtPlayer(scx, scy)
                if (state != State.PLAY) return
            }
        }

        val pb = biomeAt(tf(pcx), tf(pcy))
        if (pb != curBiome) {
            curBiome = pb
            showMsg(biomeNames[pb])
        }

        camX = pcx - VW / 2f
        camY = pcy - VH / 2f
    }

    private fun hash(a: Int, b: Int): Int = ((a * 73856093) xor (b * 19349663)) and 0x7fffffff

    fun render(c: Canvas) {
        val tx0 = tf(camX)
        val ty0 = tf(camY)
        val tx1 = tf(camX + VW) + 1
        val ty1 = tf(camY + VH) + 1
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                drawTile(c, tx, ty, tx * TILE - camX, ty * TILE - camY)
            }
        }

        for (s in slimes) {
            if (s.alive) drawSlime(c, s)
        }

        drawPlayer(c)
        drawHeldItem(c)

        if (gatherFlash > 0f && gatherX >= 0) {
            paint.color = ((gatherFlash / 0.15f * 160).toInt() shl 24) or 0x00ffffff
            val gx = gatherX * TILE - camX
            val gy = gatherY * TILE - camY
            c.drawRect(gx, gy, gx + TILE, gy + TILE, paint)
        }

        if (swingTimer > 0f && (heldItem() == I_SWORD || heldItem() == I_IRON_SWORD)) drawSlash(c)

        drawHud(c)

        if (hurtFlash > 0f) {
            paint.color = ((hurtFlash / 0.35f * 90).toInt() shl 24) or 0x00ff0000
            c.drawRect(0f, 0f, VW.toFloat(), VH.toFloat(), paint)
        }

        when (state) {
            State.TITLE -> {
                overlay(c)
                drawCentered(c, "PIXEL QUEST", VH / 2f - 22f, 20f, 0xFF4dd0e8.toInt())
                drawCentered(c, "survival sandbox", VH / 2f + 2f, 10f, 0xFFffffff.toInt())
            }
            State.DEAD -> {
                overlay(c)
                drawCentered(c, "YOU DIED", VH / 2f - 14f, 20f, 0xFFe23b3b.toInt())
            }
            else -> {}
        }
        if (state != State.PLAY) {
            if ((time * 2f).toInt() % 2 == 0) {
                drawCentered(c, "tap to play", VH / 2f + 22f, 10f, 0xFFffffff.toInt())
            }
            drawCentered(c, "left: move   right: use item   tap hotbar to switch", VH - 8f, 8f, 0xFFc8c8c8.toInt())
        }
    }

    private fun overlay(c: Canvas) {
        paint.color = 0x96000000.toInt()
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
        if (t == T_WORKBENCH || t == T_WWOOD || t == T_WSTONE || t == T_DOOR ||
            t == T_FENCE || t == T_WINDOW || t == T_FURNACE) {
            paint.color = when (biomeAt(tx, ty)) {
                B_DESERT -> if ((tx + ty) % 2 == 0) 0xFFd8c07a.toInt() else 0xFFd2b972.toInt()
                B_SNOW -> if ((tx + ty) % 2 == 0) 0xFFe8f0f4.toInt() else 0xFFdfe9ef.toInt()
                B_VOLCANIC -> if ((tx + ty) % 2 == 0) 0xFF4a4048.toInt() else 0xFF453b43.toInt()
                B_MOUNTAIN -> if ((tx + ty) % 2 == 0) 0xFF8a8f98.toInt() else 0xFF848992.toInt()
                else -> if ((tx + ty) % 2 == 0) 0xFF4a8f3c.toInt() else 0xFF468838.toInt()
            }
            c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
        }
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
            }
            T_SAND -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFFd8c07a.toInt() else 0xFFd2b972.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFFc4a860.toInt()
                c.drawRect(sx + (h % 13), sy + (h % 11), sx + (h % 13) + 1f, sy + (h % 11) + 1f, paint)
            }
            T_SNOW -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFFe8f0f4.toInt() else 0xFFdfe9ef.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFFffffff.toInt()
                c.drawRect(sx + (h % 12), sy + (h % 10), sx + (h % 12) + 2f, sy + (h % 10) + 1f, paint)
            }
            T_VROCK, T_DEAD_TREE -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFF4a4048.toInt() else 0xFF453b43.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF332c34.toInt()
                c.drawRect(sx + (h % 10), sy + (h % 12), sx + (h % 10) + 4f, sy + (h % 12) + 1f, paint)
            }
            T_MFLOOR, T_MWALL, T_ORE_IRON, T_ORE_GOLD -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFF8a8f98.toInt() else 0xFF848992.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF767b84.toInt()
                c.drawRect(sx + (h % 12), sy + (h % 10), sx + (h % 12) + 2f, sy + (h % 10) + 1f, paint)
            }
            T_RUIN_FLOOR, T_RUIN_WALL -> {
                paint.color = 0xFFb0a890.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF968e78.toInt()
                c.drawRect(sx + (h % 11), sy + (h % 9), sx + (h % 11) + 3f, sy + (h % 9) + 1f, paint)
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
            T_STREE -> {
                paint.color = 0xFF6b4a2b.toInt()
                c.drawRect(sx + 6f, sy + 10f, sx + 10f, sy + 16f, paint)
                paint.color = 0xFF2a5c46.toInt()
                c.drawRect(sx + 2f, sy + 3f, sx + 14f, sy + 12f, paint)
                c.drawRect(sx + 4f, sy + 1f, sx + 12f, sy + 3f, paint)
                paint.color = 0xFFeef4f8.toInt()
                c.drawRect(sx + 4f, sy, sx + 12f, sy + 3f, paint)
                c.drawRect(sx + 3f, sy + 3f, sx + 7f, sy + 5f, paint)
            }
            T_CACTUS -> {
                paint.color = 0xFF3f8f4f.toInt()
                c.drawRect(sx + 6f, sy + 2f, sx + 10f, sy + 15f, paint)
                c.drawRect(sx + 2f, sy + 6f, sx + 6f, sy + 9f, paint)
                c.drawRect(sx + 10f, sy + 8f, sx + 14f, sy + 11f, paint)
                paint.color = 0xFF2e6b3a.toInt()
                c.drawRect(sx + 9f, sy + 2f, sx + 10f, sy + 15f, paint)
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
            T_ORE_IRON, T_ORE_GOLD -> {
                paint.color = 0xFF7d828b.toInt()
                c.drawRect(sx + 2f, sy + 4f, sx + 14f, sy + 14f, paint)
                paint.color = if (t == T_ORE_IRON) 0xFFc8ccd4.toInt() else 0xFFf0c040.toInt()
                c.drawRect(sx + 4f, sy + 6f, sx + 7f, sy + 9f, paint)
                c.drawRect(sx + 9f, sy + 9f, sx + 12f, sy + 12f, paint)
                paint.color = 0xFF5f646d.toInt()
                c.drawRect(sx + 2f, sy + 12f, sx + 14f, sy + 14f, paint)
            }
            T_DEAD_TREE -> {
                paint.color = 0xFF5a4a44.toInt()
                c.drawRect(sx + 6f, sy + 6f, sx + 10f, sy + 16f, paint)
                c.drawRect(sx + 2f, sy + 2f, sx + 4f, sy + 7f, paint)
                c.drawRect(sx + 12f, sy + 3f, sx + 14f, sy + 8f, paint)
                paint.color = 0xFF463a35.toInt()
                c.drawRect(sx + 9f, sy + 6f, sx + 10f, sy + 16f, paint)
            }
            T_MWALL -> {
                paint.color = 0xFF5a5f68.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF474c54.toInt()
                c.drawRect(sx, sy + 7f, sx + TILE, sy + 8f, paint)
                c.drawRect(sx + 7f, sy, sx + 8f, sy + 7f, paint)
                c.drawRect(sx + 3f, sy + 8f, sx + 4f, sy + TILE, paint)
            }
            T_RUIN_WALL -> {
                paint.color = 0xFF9a9284.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF7e7666.toInt()
                c.drawRect(sx, sy + 5f, sx + TILE, sy + 6f, paint)
                c.drawRect(sx, sy + 11f, sx + TILE, sy + 12f, paint)
                c.drawRect(sx + 8f, sy, sx + 9f, sy + 5f, paint)
                c.drawRect(sx + 4f, sy + 6f, sx + 5f, sy + 11f, paint)
                if (h % 4 == 0) {
                    c.drawRect(sx + 10f, sy + 12f, sx + 14f, sy + 13f, paint)
                }
            }
            T_WORKBENCH -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(sx + 1f, sy + 5f, sx + 15f, sy + 9f, paint)
                c.drawRect(sx + 3f, sy + 9f, sx + 5f, sy + 14f, paint)
                c.drawRect(sx + 11f, sy + 9f, sx + 13f, sy + 14f, paint)
                paint.color = 0xFF9a7440.toInt()
                c.drawRect(sx + 1f, sy + 5f, sx + 15f, sy + 7f, paint)
                paint.color = 0xFFc8ccd4.toInt()
                c.drawRect(sx + 4f, sy + 3f, sx + 8f, sy + 5f, paint)
            }
            T_WWOOD -> {
                paint.color = 0xFFb58a4a.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF9a7440.toInt()
                c.drawRect(sx, sy + 7f, sx + TILE, sy + 8f, paint)
                c.drawRect(sx + 7f, sy, sx + 8f, sy + 7f, paint)
                c.drawRect(sx + 3f, sy + 8f, sx + 4f, sy + TILE, paint)
                c.drawRect(sx + 11f, sy + 8f, sx + 12f, sy + TILE, paint)
            }
            T_WSTONE -> {
                paint.color = 0xFF9aa0aa.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF767c86.toInt()
                c.drawRect(sx, sy + 5f, sx + TILE, sy + 6f, paint)
                c.drawRect(sx, sy + 11f, sx + TILE, sy + 12f, paint)
                c.drawRect(sx + 8f, sy, sx + 9f, sy + 5f, paint)
                c.drawRect(sx + 4f, sy + 6f, sx + 5f, sy + 11f, paint)
                c.drawRect(sx + 10f, sy + 12f, sx + 11f, sy + TILE, paint)
            }
            T_DOOR -> {
                paint.color = 0xFF8a5a30.toInt()
                c.drawRect(sx + 2f, sy, sx + 14f, sy + 16f, paint)
                paint.color = 0xFFa5713c.toInt()
                c.drawRect(sx + 4f, sy + 1f, sx + 12f, sy + 15f, paint)
                paint.color = 0xFFf0c040.toInt()
                c.drawRect(sx + 10f, sy + 8f, sx + 12f, sy + 9f, paint)
            }
            T_HOUSE_WALL -> {
                paint.color = 0xFFa88050.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF8a6238.toInt()
                c.drawRect(sx, sy + 7f, sx + TILE, sy + 8f, paint)
                c.drawRect(sx + 7f, sy, sx + 8f, sy + 7f, paint)
                c.drawRect(sx + 3f, sy + 8f, sx + 4f, sy + TILE, paint)
                c.drawRect(sx + 11f, sy + 8f, sx + 12f, sy + TILE, paint)
            }
            T_HOUSE_FLOOR, T_FLOOR_WOOD -> {
                paint.color = if ((tx + ty) % 2 == 0) 0xFFb8894e.toInt() else 0xFFb08246.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFF966a38.toInt()
                c.drawRect(sx, sy + 7f, sx + TILE, sy + 8f, paint)
                c.drawRect(sx + (h % 2) * 8f, sy, sx + (h % 2) * 8f + 1f, sy + 7f, paint)
                c.drawRect(sx + ((h + 1) % 2) * 8f, sy + 8f, sx + ((h + 1) % 2) * 8f + 1f, sy + TILE, paint)
            }
            T_FENCE -> {
                paint.color = 0xFF8a6a3a.toInt()
                c.drawRect(sx + 2f, sy + 4f, sx + 4f, sy + 14f, paint)
                c.drawRect(sx + 12f, sy + 4f, sx + 14f, sy + 14f, paint)
                c.drawRect(sx, sy + 6f, sx + TILE, sy + 8f, paint)
                c.drawRect(sx, sy + 11f, sx + TILE, sy + 13f, paint)
            }
            T_WINDOW -> {
                paint.color = 0xFFa88050.toInt()
                c.drawRect(sx, sy, sx + TILE, sy + TILE, paint)
                paint.color = 0xFFa8d8e8.toInt()
                c.drawRect(sx + 3f, sy + 3f, sx + 13f, sy + 13f, paint)
                paint.color = 0xFFc8ecf8.toInt()
                c.drawRect(sx + 4f, sy + 4f, sx + 8f, sy + 8f, paint)
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(sx + 7f, sy + 3f, sx + 9f, sy + 13f, paint)
                c.drawRect(sx + 3f, sy + 7f, sx + 13f, sy + 9f, paint)
            }
            T_FURNACE -> {
                paint.color = 0xFF6a6f78.toInt()
                c.drawRect(sx + 1f, sy + 2f, sx + 15f, sy + 15f, paint)
                paint.color = 0xFF54585f.toInt()
                c.drawRect(sx + 1f, sy + 2f, sx + 15f, sy + 4f, paint)
                paint.color = 0xFF1a1a1a.toInt()
                c.drawRect(sx + 5f, sy + 7f, sx + 11f, sy + 13f, paint)
                paint.color = if (h % 2 == 0) 0xFFf08030.toInt() else 0xFFd8481c.toInt()
                c.drawRect(sx + 6f, sy + 10f, sx + 10f, sy + 13f, paint)
            }
        }
    }

    private fun drawSlime(c: Canvas, s: Slime) {
        if (s.type >= 5) {
            drawHumanoid(c, s)
            return
        }
        val sx = s.x - 2f - camX
        val sy = s.y - 3f - camY
        val sq = sin(time * 6f + s.phase)
        val flash = s.flash > 0f
        paint.color = if (flash) 0xFFffffff.toInt() else slimeBody[s.type]
        c.drawRect(sx + 1f, sy + 3f + sq, sx + 15f, sy + 12f, paint)
        c.drawRect(sx + 3f, sy + 1f + sq, sx + 13f, sy + 3f + sq, paint)
        paint.color = if (flash) 0xFFdddddd.toInt() else slimeDark[s.type]
        c.drawRect(sx + 1f, sy + 10f, sx + 15f, sy + 12f, paint)
        paint.color = 0xFFffffff.toInt()
        c.drawRect(sx + 4f, sy + 5f + sq, sx + 6f, sy + 7f + sq, paint)
        c.drawRect(sx + 10f, sy + 5f + sq, sx + 12f, sy + 7f + sq, paint)
        paint.color = 0xFF1a1a1a.toInt()
        c.drawRect(sx + 5f, sy + 6f + sq, sx + 6f, sy + 7f + sq, paint)
        c.drawRect(sx + 10f, sy + 6f + sq, sx + 11f, sy + 7f + sq, paint)
    }

    private fun drawHumanoid(c: Canvas, s: Slime) {
        val px = s.x - 2f - camX
        val py = s.y - 3f - camY
        val bandit = s.type == 5
        val walk = sin(time * 8f + s.phase) * 1.5f
        val flash = s.flash > 0f
        paint.color = if (flash) 0xFFffffff.toInt() else if (bandit) 0xFF2c3440.toInt() else 0xFF5a4632.toInt()
        c.drawRect(px + 4f, py + 10f + walk, px + 7f, py + 14f, paint)
        c.drawRect(px + 9f, py + 10f - walk, px + 12f, py + 14f, paint)
        paint.color = if (flash) 0xFFffffff.toInt() else if (bandit) 0xFF4a5568.toInt() else 0xFF8a6a4a.toInt()
        c.drawRect(px + 3f, py + 4f, px + 13f, py + 10f, paint)
        paint.color = if (flash) 0xFFffffff.toInt() else 0xFFe8b890.toInt()
        c.drawRect(px + 4f, py - 1f, px + 12f, py + 4f, paint)
        paint.color = if (bandit) 0xFFa03030.toInt() else 0xFF6a5238.toInt()
        c.drawRect(px + 4f, py - 1f, px + 12f, py + 1f, paint)
        paint.color = 0xFF1a1a1a.toInt()
        c.drawRect(px + 6f, py + 1f, px + 7f, py + 3f, paint)
        c.drawRect(px + 9f, py + 1f, px + 10f, py + 3f, paint)
        if (bandit) {
            paint.color = 0xFF3a3f4a.toInt()
            c.drawRect(px + 14f, py + 5f, px + 16f, py + 11f, paint)
        }
    }

    private fun drawPlayer(c: Canvas) {
        if (player.invuln > 0f && (time * 12f).toInt() % 2 == 0) return
        val step = if (player.moving) ((player.walkT * 7f).toInt()) % 4 else -1
        val legL = if (step == 1) -2f else 0f
        val legR = if (step == 3) -2f else 0f
        val bob = when {
            player.moving -> if (step == 1 || step == 3) 1f else 0f
            sin(time * 2f) > 0.5f -> 1f
            else -> 0f
        }
        val px = player.x - 1f - camX
        val py = player.y - 2f - camY + bob
        val facing = if (abs(player.dirX) > abs(player.dirY)) {
            if (player.dirX < 0) 1 else 2
        } else {
            if (player.dirY < 0) 3 else 0
        }
        paint.color = 0xFF35455e.toInt()
        c.drawRect(px + 2f, py + 11f, px + 5f, py + 14f + legL, paint)
        c.drawRect(px + 7f, py + 11f, px + 10f, py + 14f + legR, paint)
        paint.color = 0xFFc0392b.toInt()
        c.drawRect(px + 1f, py + 6f, px + 11f, py + 11f, paint)
        paint.color = 0xFFf2c79a.toInt()
        c.drawRect(px, py + 7f + legR, px + 2f, py + 10f + legR, paint)
        c.drawRect(px + 10f, py + 7f + legL, px + 12f, py + 10f + legL, paint)
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

    private fun drawHeldItem(c: Canvas) {
        val held = heldItem()
        if (held < 0) return
        val swinging = swingTimer > 0f
        val p = if (swinging) 1f - swingTimer / SWING_TIME else 0f
        val extend = if (swinging) 8f + sin(p * PI.toFloat()) * 5f else 8f
        val sway = if (swinging) sin(p * PI.toFloat()) * 3f else 0f
        val dx = player.dirX
        val dy = player.dirY
        val perpX = -dy
        val perpY = dx
        val cx = player.x + player.w / 2 - camX + dx * extend + perpX * sway
        val cy = player.y + player.h / 2 - camY + dy * extend + perpY * sway

        fun seg(i: Int, size: Float, color: Int) {
            paint.color = color
            val ex = cx + dx * i
            val ey = cy + dy * i
            c.drawRect(ex - size / 2, ey - size / 2, ex + size / 2, ey + size / 2, paint)
        }

        when (held) {
            I_SWORD, I_IRON_SWORD -> {
                val blade = if (held == I_IRON_SWORD) 0xFFe8ecf2.toInt() else 0xFFc8ccd4.toInt()
                val tip = if (held == I_IRON_SWORD) 0xFFf6f8fb.toInt() else 0xFFeef2f6.toInt()
                seg(-2, 2f, 0xFF5a3a1e.toInt())
                seg(0, 3f, 0xFF8f6a30.toInt())
                for (i in 1..5) seg(i, 2f, blade)
                seg(6, 2f, tip)
            }
            I_WORKBENCH, I_WWOOD, I_WSTONE, I_DOOR,
            I_FLOOR, I_WINDOW, I_FENCE, I_FURNACE -> {
                val col = when (held) {
                    I_WORKBENCH -> 0xFF9a7440.toInt()
                    I_WWOOD -> 0xFFb58a4a.toInt()
                    I_WSTONE -> 0xFF9aa0aa.toInt()
                    I_FLOOR -> 0xFFb8894e.toInt()
                    I_WINDOW -> 0xFFa8d8e8.toInt()
                    I_FENCE -> 0xFF8a6a3a.toInt()
                    I_FURNACE -> 0xFF6a6f78.toInt()
                    else -> 0xFF8a5a30.toInt()
                }
                paint.color = col
                val ex = cx + dx * 4f
                val ey = cy + dy * 4f
                c.drawRect(ex - 3f, ey - 3f, ex + 3f, ey + 3f, paint)
                paint.color = 0xFF3a2a18.toInt()
                c.drawRect(ex - 3f, ey - 3f, ex + 3f, ey - 2f, paint)
            }
            I_PICK, I_IRON_PICK -> {
                val head = if (held == I_IRON_PICK) 0xFFe8ecf2.toInt() else 0xFFb0b5bd.toInt()
                for (i in -1..4) seg(i, 2f, 0xFF7a5a30.toInt())
                val hx = cx + dx * 5
                val hy = cy + dy * 5
                paint.color = head
                c.drawRect(hx - perpX * 4f - 1f, hy - perpY * 4f - 1f, hx + perpX * 4f + 1f, hy + perpY * 4f + 1f, paint)
                paint.color = 0xFF84898f.toInt()
                c.drawRect(hx - perpX * 4f - 1f, hy - perpY * 4f - 1f, hx - perpX * 2f - 1f, hy - perpY * 2f - 1f, paint)
                c.drawRect(hx + perpX * 2f - 1f, hy + perpY * 2f - 1f, hx + perpX * 4f + 1f, hy + perpY * 4f + 1f, paint)
            }
            I_AXE -> {
                for (i in -1..4) seg(i, 2f, 0xFF7a5a30.toInt())
                val hx = cx + dx * 5
                val hy = cy + dy * 5
                paint.color = 0xFFb0b5bd.toInt()
                c.drawRect(hx - 3f, hy - 3f, hx + 3f, hy + 3f, paint)
                paint.color = 0xFF84898f.toInt()
                c.drawRect(hx - 3f + dx, hy - 3f + dy, hx + 3f + dx, hy + 3f + dy, paint)
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

    private fun drawItemIcon(c: Canvas, item: Int, x: Float, y: Float) {
        when (item) {
            I_SWORD -> {
                paint.color = 0xFF5a3a1e.toInt()
                c.drawRect(x + 4f, y + 8f, x + 6f, y + 11f, paint)
                paint.color = 0xFF8f6a30.toInt()
                c.drawRect(x + 3f, y + 7f, x + 7f, y + 8f, paint)
                paint.color = 0xFFc8ccd4.toInt()
                c.drawRect(x + 4f, y, x + 6f, y + 7f, paint)
                paint.color = 0xFFeef2f6.toInt()
                c.drawRect(x + 4f, y, x + 5f, y + 2f, paint)
            }
            I_AXE -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(x + 4f, y + 2f, x + 6f, y + 11f, paint)
                paint.color = 0xFFb0b5bd.toInt()
                c.drawRect(x + 1f, y, x + 6f, y + 4f, paint)
                paint.color = 0xFF84898f.toInt()
                c.drawRect(x + 1f, y + 3f, x + 6f, y + 4f, paint)
            }
            I_PICK -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(x + 4f, y + 2f, x + 6f, y + 11f, paint)
                paint.color = 0xFFb0b5bd.toInt()
                c.drawRect(x + 1f, y, x + 9f, y + 2f, paint)
                c.drawRect(x, y + 2f, x + 2f, y + 3f, paint)
                c.drawRect(x + 8f, y + 2f, x + 10f, y + 3f, paint)
            }
            I_WOOD -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(x + 1f, y + 3f, x + 10f, y + 8f, paint)
                paint.color = 0xFF9a7440.toInt()
                c.drawRect(x + 1f, y + 3f, x + 10f, y + 5f, paint)
                paint.color = 0xFF5f4423.toInt()
                c.drawRect(x + 9f, y + 3f, x + 10f, y + 8f, paint)
            }
            I_STONE -> {
                paint.color = 0xFF9a9fa8.toInt()
                c.drawRect(x + 1f, y + 3f, x + 10f, y + 9f, paint)
                c.drawRect(x + 3f, y + 1f, x + 8f, y + 3f, paint)
                paint.color = 0xFFc4c9d1.toInt()
                c.drawRect(x + 3f, y + 2f, x + 6f, y + 4f, paint)
            }
            I_IRON, I_GOLD -> {
                paint.color = if (item == I_IRON) 0xFFc8ccd4.toInt() else 0xFFf0c040.toInt()
                c.drawRect(x + 1f, y + 4f, x + 10f, y + 9f, paint)
                c.drawRect(x + 2f, y + 2f, x + 9f, y + 4f, paint)
                paint.color = if (item == I_IRON) 0xFFe8ecf2.toInt() else 0xFFffe080.toInt()
                c.drawRect(x + 2f, y + 3f, x + 5f, y + 5f, paint)
            }
            I_WORKBENCH -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(x + 1f, y + 3f, x + 10f, y + 6f, paint)
                c.drawRect(x + 2f, y + 6f, x + 4f, y + 11f, paint)
                c.drawRect(x + 7f, y + 6f, x + 9f, y + 11f, paint)
                paint.color = 0xFF9a7440.toInt()
                c.drawRect(x + 1f, y + 3f, x + 10f, y + 5f, paint)
                paint.color = 0xFFc8ccd4.toInt()
                c.drawRect(x + 3f, y + 2f, x + 6f, y + 3f, paint)
            }
            I_WWOOD -> {
                paint.color = 0xFFb58a4a.toInt()
                c.drawRect(x + 1f, y + 2f, x + 10f, y + 9f, paint)
                paint.color = 0xFF9a7440.toInt()
                c.drawRect(x + 1f, y + 5f, x + 10f, y + 6f, paint)
                c.drawRect(x + 5f, y + 2f, x + 6f, y + 9f, paint)
            }
            I_WSTONE -> {
                paint.color = 0xFF9aa0aa.toInt()
                c.drawRect(x + 1f, y + 2f, x + 10f, y + 6f, paint)
                c.drawRect(x + 1f, y + 7f, x + 10f, y + 11f, paint)
                paint.color = 0xFF767c86.toInt()
                c.drawRect(x + 5f, y + 2f, x + 6f, y + 6f, paint)
                c.drawRect(x + 2f, y + 7f, x + 3f, y + 11f, paint)
                c.drawRect(x + 8f, y + 7f, x + 9f, y + 11f, paint)
            }
            I_DOOR -> {
                paint.color = 0xFF8a5a30.toInt()
                c.drawRect(x + 2f, y + 1f, x + 9f, y + 12f, paint)
                paint.color = 0xFFa5713c.toInt()
                c.drawRect(x + 3f, y + 2f, x + 8f, y + 11f, paint)
                paint.color = 0xFFf0c040.toInt()
                c.drawRect(x + 7f, y + 6f, x + 8f, y + 7f, paint)
            }
            I_IRON_SWORD -> {
                paint.color = 0xFF5a3a1e.toInt()
                c.drawRect(x + 4f, y + 8f, x + 6f, y + 11f, paint)
                paint.color = 0xFF8f6a30.toInt()
                c.drawRect(x + 3f, y + 7f, x + 7f, y + 8f, paint)
                paint.color = 0xFFe8ecf2.toInt()
                c.drawRect(x + 4f, y, x + 6f, y + 7f, paint)
                paint.color = 0xFFf6f8fb.toInt()
                c.drawRect(x + 4f, y, x + 5f, y + 2f, paint)
            }
            I_IRON_PICK -> {
                paint.color = 0xFF7a5a30.toInt()
                c.drawRect(x + 4f, y + 2f, x + 6f, y + 11f, paint)
                paint.color = 0xFFe8ecf2.toInt()
                c.drawRect(x + 1f, y, x + 9f, y + 2f, paint)
                c.drawRect(x, y + 2f, x + 2f, y + 3f, paint)
                c.drawRect(x + 8f, y + 2f, x + 10f, y + 3f, paint)
            }
            I_FLOOR -> {
                paint.color = 0xFFb8894e.toInt()
                c.drawRect(x + 1f, y + 2f, x + 10f, y + 11f, paint)
                paint.color = 0xFF966a38.toInt()
                c.drawRect(x + 1f, y + 6f, x + 10f, y + 7f, paint)
            }
            I_WINDOW -> {
                paint.color = 0xFFa88050.toInt()
                c.drawRect(x + 1f, y + 1f, x + 10f, y + 12f, paint)
                paint.color = 0xFFa8d8e8.toInt()
                c.drawRect(x + 3f, y + 3f, x + 8f, y + 10f, paint)
            }
            I_FENCE -> {
                paint.color = 0xFF8a6a3a.toInt()
                c.drawRect(x + 2f, y + 2f, x + 4f, y + 11f, paint)
                c.drawRect(x + 7f, y + 2f, x + 9f, y + 11f, paint)
                c.drawRect(x, y + 4f, x + 11f, y + 5f, paint)
            }
            I_FURNACE -> {
                paint.color = 0xFF6a6f78.toInt()
                c.drawRect(x + 1f, y + 2f, x + 10f, y + 11f, paint)
                paint.color = 0xFF1a1a1a.toInt()
                c.drawRect(x + 3f, y + 5f, x + 8f, y + 9f, paint)
                paint.color = 0xFFf08030.toInt()
                c.drawRect(x + 4f, y + 7f, x + 7f, y + 9f, paint)
            }
        }
    }

    private fun drawInv(c: Canvas) {
        paint.color = 0xd0181824.toInt()
        c.drawRect(PX0, PY0, PX0 + PW, PY0 + PH, paint)
        paint.color = 0xFF4dd0e8.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRect(PX0 + 0.5f, PY0 + 0.5f, PX0 + PW - 0.5f, PY0 + PH - 0.5f, paint)
        paint.style = Paint.Style.FILL

        textPaint.textSize = 9f
        paint.color = if (invPage == 0) 0xFF3a5a6a.toInt() else 0xaa2a2a34.toInt()
        c.drawRect(PX0 + 8f, PY0 + 4f, PX0 + 68f, PY0 + 18f, paint)
        paint.color = if (invPage == 1) 0xFF3a5a6a.toInt() else 0xaa2a2a34.toInt()
        c.drawRect(PX0 + 72f, PY0 + 4f, PX0 + 132f, PY0 + 18f, paint)
        textPaint.color = 0xFFffffff.toInt()
        c.drawText("ITEMS", PX0 + 18f, PY0 + 14f, textPaint)
        c.drawText("CRAFT", PX0 + 84f, PY0 + 14f, textPaint)
        paint.color = 0xFFa03030.toInt()
        c.drawRect(PX0 + PW - 16f, PY0 + 4f, PX0 + PW - 4f, PY0 + 16f, paint)
        paint.color = 0xFFffffff.toInt()
        c.drawRect(PX0 + PW - 13f, PY0 + 7f, PX0 + PW - 11f, PY0 + 13f, paint)
        c.drawRect(PX0 + PW - 9f, PY0 + 7f, PX0 + PW - 7f, PY0 + 13f, paint)

        if (invPage == 0) {
            for (i in 0 until INV_SLOTS) {
                val col = i % GRID_COLS
                val row = i / GRID_COLS
                val x = GRID_X0 + col * 18f
                val y = GRID_Y0 + row * 18f
                paint.color = 0xaa2a2a34.toInt()
                c.drawRect(x, y, x + 16f, y + 16f, paint)
                if (i == invSel) {
                    paint.color = 0xFFffd94a.toInt()
                    paint.style = Paint.Style.STROKE
                    c.drawRect(x + 0.5f, y + 0.5f, x + 15.5f, y + 15.5f, paint)
                    paint.style = Paint.Style.FILL
                }
                if (i < HOT_SLOTS) {
                    paint.color = 0x504dd0e8
                    c.drawRect(x, y, x + 16f, y + 1f, paint)
                }
                val it = invItem[i]
                if (it >= 0) {
                    drawItemIcon(c, it, x + 3f, y + 3f)
                    if (invCount[i] > 1) {
                        textPaint.color = 0xFF000000.toInt()
                        textPaint.textSize = 7f
                        c.drawText(invCount[i].toString(), x + 8f, y + 15f, textPaint)
                        textPaint.color = 0xFFffffff.toInt()
                        c.drawText(invCount[i].toString(), x + 7.5f, y + 14.5f, textPaint)
                    }
                }
            }
            textPaint.color = 0xFF9ab0c0.toInt()
            textPaint.textSize = 8f
            c.drawText("top row = hotbar", GRID_X0, PY0 + 30f, textPaint)
        } else {
            for (r in recipes.indices) {
                val y = PY0 + 26f + r * 13f
                paint.color = 0xaa2a2a34.toInt()
                c.drawRect(PX0 + 8f, y, PX0 + PW - 8f, y + 12f, paint)
                val rec = recipes[r]
                var can = countItem(rec[2]) >= rec[3]
                val extra = recipeExtra[r]
                for (e in extra.indices step 2) {
                    if (extra[e] > 0 && countItem(extra[e]) < extra[e + 1]) can = false
                }
                if (rec[4] == 1 && !benchNearby()) can = false
                textPaint.color = if (can) 0xFF7ce87c.toInt() else 0xFFe86a6a.toInt()
                textPaint.textSize = 7f
                c.drawText(recipeLabel(r), PX0 + 12f, y + 9f, textPaint)
            }
        }
    }

    private fun drawHud(c: Canvas) {
        for (i in 0 until MAX_HP) {
            drawHeart(c, 4f + i * 8f, 4f, i < player.hp)
        }
        if (curBiome >= 0) {
            drawCentered(c, biomeNames[curBiome], 12f, 8f, 0xFFffffff.toInt())
        }
        paint.color = 0xaa2a2a34.toInt()
        c.drawRect(VW - 18f, 2f, VW - 2f, 18f, paint)
        paint.color = 0xFFc8a050.toInt()
        c.drawRect(VW - 13f, 7f, VW - 7f, 14f, paint)
        paint.color = 0xFF8a6a30.toInt()
        c.drawRect(VW - 12f, 6f, VW - 8f, 8f, paint)
        val pressed = btnFlash > 0f
        paint.color = if (pressed) 0xFF3a6a7a.toInt() else 0xaa2a2a34.toInt()
        c.drawCircle(VW - 18f, VH - 18f, 15f, paint)
        paint.color = if (pressed) 0xFFe8ffff.toInt() else 0xFF4dd0e8.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawCircle(VW - 18f, VH - 18f, 15f, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = 0xFFffffff.toInt()
        textPaint.textSize = 9f
        c.drawText("USE", VW - 18f - textPaint.measureText("USE") / 2f, VH - 15f, textPaint)
        for (i in 0 until HOT_SLOTS) {
            val x = HOT_X0 + i * HOT_CELL
            paint.color = 0xaa2a2a34.toInt()
            c.drawRect(x, HOT_Y0, x + 16f, HOT_Y0 + 16f, paint)
            if (i == selected) {
                paint.color = 0xFF4dd0e8.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                c.drawRect(x + 0.5f, HOT_Y0 + 0.5f, x + 15.5f, HOT_Y0 + 15.5f, paint)
                paint.style = Paint.Style.FILL
            }
            val it = invItem[i]
            if (it >= 0) {
                drawItemIcon(c, it, x + 3f, HOT_Y0 + 3f)
                if (invCount[i] > 1) {
                    textPaint.color = 0xFF000000.toInt()
                    textPaint.textSize = 7f
                    c.drawText(invCount[i].toString(), x + 8f, HOT_Y0 + 15f, textPaint)
                    textPaint.color = 0xFFffffff.toInt()
                    c.drawText(invCount[i].toString(), x + 7.5f, HOT_Y0 + 14.5f, textPaint)
                }
            }
        }
        if (msgTime > 0f) {
            drawCentered(c, msg, HOT_Y0 - 6f, 9f, 0xFFffd94a.toInt())
        }
        if (invOpen) drawInv(c)
    }
}
