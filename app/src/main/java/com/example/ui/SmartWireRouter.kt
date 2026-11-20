package com.example.ui

import com.example.model.CircuitComponent
import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import com.example.model.WireSegment
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SmartWireRouter {

    /**
     * Computes a clean orthogonal Manhattan path from [start] to [end],
     * avoiding component bodies while keeping bends minimal.
     */
    fun route(
        start: GridPoint,
        end: GridPoint,
        components: List<CircuitComponent>
    ): List<GridPoint> {
        if (start == end) return listOf(start)
        if (start.x == end.x || start.y == end.y) {
            return listOf(start, end)
        }

        // Build obstacle set from component interiors (leaving terminals free)
        val blocked = HashSet<GridPoint>()
        val terminalSet = HashSet<GridPoint>()
        for (c in components) {
            c.getWorldTerminals().forEach { terminalSet.add(it.second) }
            if (c.type != ComponentType.GROUND && c.type != ComponentType.SCOPE_PROBE) {
                blocked.add(GridPoint(c.x, c.y))
                if (c.type == ComponentType.OP_AMP ||
                    c.type == ComponentType.VCVS ||
                    c.type == ComponentType.VCCS ||
                    c.type == ComponentType.CCVS ||
                    c.type == ComponentType.CCCS ||
                    c.type == ComponentType.WATTMETER
                ) {
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            blocked.add(GridPoint(c.x + dx, c.y + dy))
                        }
                    }
                }
            }
        }
        blocked.remove(start)
        blocked.remove(end)
        blocked.removeAll(terminalSet)

        // Check simple L-paths first (horizontal-then-vertical or vertical-then-horizontal)
        val l1 = listOf(start, GridPoint(end.x, start.y), end)
        if (!pathHitsBlocked(l1, blocked)) return simplifyPath(l1)

        val l2 = listOf(start, GridPoint(start.x, end.y), end)
        if (!pathHitsBlocked(l2, blocked)) return simplifyPath(l2)

        // Bounded A* with bend penalty
        val minX = min(start.x, end.x) - 6
        val maxX = max(start.x, end.x) + 6
        val minY = min(start.y, end.y) - 6
        val maxY = max(start.y, end.y) + 6

        data class Node(
            val pt: GridPoint,
            val dir: Int, // -1 none, 0 horiz, 1 vert
            val g: Int,
            val f: Int
        ) : Comparable<Node> {
            override fun compareTo(other: Node) = f.compareTo(other.f)
        }

        val pq = PriorityQueue<Node>()
        val bestG = HashMap<Pair<GridPoint, Int>, Int>()
        val cameFrom = HashMap<Pair<GridPoint, Int>, Pair<GridPoint, Int>>()

        pq.add(Node(start, -1, 0, abs(start.x - end.x) + abs(start.y - end.y)))
        bestG[start to -1] = 0

        val dirs = arrayOf(
            Triple(1, 0, 0),
            Triple(-1, 0, 0),
            Triple(0, 1, 1),
            Triple(0, -1, 1)
        )

        var goalKey: Pair<GridPoint, Int>? = null
        var expansions = 0

        while (pq.isNotEmpty() && expansions < 1200) {
            val cur = pq.poll() ?: break
            expansions++
            if (cur.pt == end) {
                goalKey = cur.pt to cur.dir
                break
            }
            val curKey = cur.pt to cur.dir
            if (cur.g > (bestG[curKey] ?: Int.MAX_VALUE)) continue

            for ((dx, dy, dKind) in dirs) {
                val np = GridPoint(cur.pt.x + dx, cur.pt.y + dy)
                if (np.x !in minX..maxX || np.y !in minY..maxY) continue
                if (np in blocked) continue

                val bendCost = if (cur.dir != -1 && cur.dir != dKind) 3 else 0
                val ng = cur.g + 1 + bendCost
                val nKey = np to dKind
                if (ng < (bestG[nKey] ?: Int.MAX_VALUE)) {
                    bestG[nKey] = ng
                    cameFrom[nKey] = curKey
                    val h = abs(np.x - end.x) + abs(np.y - end.y)
                    pq.add(Node(np, dKind, ng, ng + h))
                }
            }
        }

        if (goalKey != null) {
            val path = ArrayList<GridPoint>()
            var k: Pair<GridPoint, Int>? = goalKey
            while (k != null) {
                path.add(k.first)
                k = cameFrom[k]
            }
            path.reverse()
            return simplifyPath(path)
        }

        // Fallback clean L-path
        return simplifyPath(l1)
    }

    private fun pathHitsBlocked(pts: List<GridPoint>, blocked: Set<GridPoint>): Boolean {
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val dx = Integer.signum(b.x - a.x)
            val dy = Integer.signum(b.y - a.y)
            var cx = a.x
            var cy = a.y
            while (cx != b.x || cy != b.y) {
                val p = GridPoint(cx, cy)
                if (p in blocked) return true
                cx += dx
                cy += dy
            }
        }
        return false
    }

    fun simplifyPath(points: List<GridPoint>): List<GridPoint> {
        if (points.size <= 2) return points.distinct()
        val dedup = ArrayList<GridPoint>(points.size)
        for (p in points) {
            if (dedup.isEmpty() || dedup.last() != p) {
                dedup.add(p)
            }
        }
        if (dedup.size <= 2) return dedup

        val result = ArrayList<GridPoint>(dedup.size)
        result.add(dedup.first())
        for (i in 1 until dedup.lastIndex) {
            val prev = result.last()
            val curr = dedup[i]
            val next = dedup[i + 1]
            val collinearX = prev.x == curr.x && curr.x == next.x
            val collinearY = prev.y == curr.y && curr.y == next.y
            if (!collinearX && !collinearY) {
                result.add(curr)
            }
        }
        result.add(dedup.last())
        return result
    }

    fun distancePointToSegmentSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) {
            val ex = px - ax
            val ey = py - ay
            return ex * ex + ey * ey
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        val rx = px - projX
        val ry = py - projY
        return rx * rx + ry * ry
    }

    fun findWireAt(worldX: Float, worldY: Float, wires: List<WireSegment>, maxDistGrid: Float = 0.45f): WireSegment? {
        val maxDistSq = maxDistGrid * maxDistGrid
        var best: WireSegment? = null
        var bestDist = maxDistSq
        for (w in wires) {
            for (i in 0 until w.points.size - 1) {
                val a = w.points[i]
                val b = w.points[i + 1]
                val d = distancePointToSegmentSq(
                    worldX, worldY,
                    a.x.toFloat(), a.y.toFloat(),
                    b.x.toFloat(), b.y.toFloat()
                )
                if (d < bestDist) {
                    bestDist = d
                    best = w
                }
            }
        }
        return best
    }

    fun findComponentAt(worldX: Float, worldY: Float, schematic: CircuitSchematic): CircuitComponent? {
        var best: CircuitComponent? = null
        var bestDistSq = 2.6f * 2.6f
        for (c in schematic.components.asReversed()) {
            val dx = worldX - c.x.toFloat()
            val dy = worldY - c.y.toFloat()
            val dSq = dx * dx + dy * dy
            val radiusSq = when (c.type) {
                ComponentType.GROUND, ComponentType.SCOPE_PROBE -> 1.4f * 1.4f
                else -> 2.3f * 2.3f
            }
            if (dSq <= radiusSq && dSq < bestDistSq) {
                bestDistSq = dSq
                best = c
            }
        }
        return best
    }

    fun findNearestSnapTerminalOrWire(
        worldX: Float,
        worldY: Float,
        schematic: CircuitSchematic,
        maxRadius: Float = 0.9f
    ): GridPoint? {
        var bestPt: GridPoint? = null
        var bestDistSq = maxRadius * maxRadius

        for (c in schematic.components) {
            for ((_, pt) in c.getWorldTerminals()) {
                val dx = worldX - pt.x
                val dy = worldY - pt.y
                val dSq = dx * dx + dy * dy
                if (dSq < bestDistSq) {
                    bestDistSq = dSq
                    bestPt = pt
                }
            }
        }
        if (bestPt != null) return bestPt

        for (w in schematic.wires) {
            for (pt in w.points) {
                val dx = worldX - pt.x
                val dy = worldY - pt.y
                val dSq = dx * dx + dy * dy
                if (dSq < bestDistSq) {
                    bestDistSq = dSq
                    bestPt = pt
                }
            }
        }
        return bestPt
    }
}
