package com.example.simulation

import com.example.model.CircuitSchematic
import com.example.model.ComponentType
import com.example.model.GridPoint
import kotlin.math.max
import kotlin.math.min

data class CompiledNetlist(
    val nodeCount: Int,
    val pointToNode: Map<GridPoint, Int>,
    val componentNodes: Map<String, List<Int>>,
    val junctionDots: List<GridPoint>,
    val hasExplicitGround: Boolean,
    val pointIncidentCount: Map<GridPoint, Int>
)

object NetlistCompiler {

    fun compile(schematic: CircuitSchematic): CompiledNetlist {
        val allPoints = LinkedHashSet<GridPoint>()
        val pointDegree = HashMap<GridPoint, Int>()

        // Collect all terminal points
        val compTerminals = schematic.components.associate { comp ->
            val pts = comp.getWorldTerminals().map { it.second }
            pts.forEach { p ->
                allPoints.add(p)
                pointDegree[p] = (pointDegree[p] ?: 0) + 1
            }
            comp.id to pts
        }

        // Collect all wire points
        for (wire in schematic.wires) {
            for (i in wire.points.indices) {
                val p = wire.points[i]
                allPoints.add(p)
                val degAdd = when {
                    wire.points.size == 1 -> 0
                    i == 0 || i == wire.points.lastIndex -> 1
                    else -> 2
                }
                pointDegree[p] = (pointDegree[p] ?: 0) + degAdd
            }
        }

        if (allPoints.isEmpty()) {
            return CompiledNetlist(0, emptyMap(), emptyMap(), emptyList(), true, emptyMap())
        }

        val pointList = allPoints.toList()
        val pointIndex = HashMap<GridPoint, Int>(pointList.size * 2)
        pointList.forEachIndexed { idx, gp -> pointIndex[gp] = idx }

        val parent = IntArray(pointList.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var curr = x
            while (curr != r) {
                val next = parent[curr]
                parent[curr] = r
                curr = next
            }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        // Union wire segments and detect T-junctions where a point lies on an orthogonal segment
        for (wire in schematic.wires) {
            val pts = wire.points
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val ia = pointIndex[a] ?: continue
                val ib = pointIndex[b] ?: continue
                union(ia, ib)

                // Check T-junctions against all points
                val minX = min(a.x, b.x)
                val maxX = max(a.x, b.x)
                val minY = min(a.y, b.y)
                val maxY = max(a.y, b.y)
                for (p in pointList) {
                    if (p == a || p == b) continue
                    val onSegment = if (a.x == b.x && p.x == a.x && p.y in minY..maxY) {
                        true
                    } else if (a.y == b.y && p.y == a.y && p.x in minX..maxX) {
                        true
                    } else {
                        false
                    }
                    if (onSegment) {
                        val ip = pointIndex[p] ?: continue
                        union(ia, ip)
                        pointDegree[p] = (pointDegree[p] ?: 0) + 2
                    }
                }
            }
        }

        // Identify Ground root(s)
        val groundRoots = HashSet<Int>()
        for (comp in schematic.components) {
            if (comp.type == ComponentType.GROUND) {
                val pts = compTerminals[comp.id].orEmpty()
                for (p in pts) {
                    pointIndex[p]?.let { groundRoots.add(find(it)) }
                }
            }
        }

        val hasExplicitGround = groundRoots.isNotEmpty()
        if (!hasExplicitGround) {
            // Auto-select negative terminal of first voltage source, or last terminal of first component
            val fallbackComp = schematic.components.firstOrNull {
                it.type == ComponentType.DC_VOLTAGE_SOURCE || it.type == ComponentType.AC_VOLTAGE_SOURCE
            } ?: schematic.components.firstOrNull { it.type != ComponentType.SCOPE_PROBE }
            val fallbackPt = fallbackComp?.let { compTerminals[it.id]?.lastOrNull() }
            if (fallbackPt != null) {
                pointIndex[fallbackPt]?.let { groundRoots.add(find(it)) }
            }
        }

        // Map roots to 0..nodeCount
        val rootToNode = HashMap<Int, Int>()
        var nextNodeId = 1
        for (i in pointList.indices) {
            val r = find(i)
            if (r in groundRoots) {
                rootToNode[r] = 0
            } else if (!rootToNode.containsKey(r)) {
                rootToNode[r] = nextNodeId++
            }
        }

        val pointToNode = HashMap<GridPoint, Int>(pointList.size * 2)
        for (i in pointList.indices) {
            pointToNode[pointList[i]] = rootToNode[find(i)] ?: 0
        }

        val componentNodes = HashMap<String, List<Int>>(schematic.components.size * 2)
        for (comp in schematic.components) {
            val pts = compTerminals[comp.id].orEmpty()
            componentNodes[comp.id] = pts.map { pointToNode[it] ?: 0 }
        }

        val junctionDots = pointDegree.entries
            .filter { it.value >= 3 }
            .map { it.key }

        return CompiledNetlist(
            nodeCount = nextNodeId - 1,
            pointToNode = pointToNode,
            componentNodes = componentNodes,
            junctionDots = junctionDots,
            hasExplicitGround = hasExplicitGround,
            pointIncidentCount = pointDegree
        )
    }
}
