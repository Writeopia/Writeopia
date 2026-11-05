# Force Graph Initial Spread Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the first frame the documents force graph is spread over the viewport instead of clumped in its middle, on both Compose and native Swift.

**Architecture:** Replace the random spawn band in each platform with a deterministic golden-angle spiral scaled to 90% of the viewport. Node forces, constants and settled layout stay untouched.

**Tech Stack:** Kotlin Multiplatform + Compose; Swift 6 / SwiftUI; kotlin.test, Swift Testing.

**Spec:** `docs/superpowers/specs/2026-11-05-force-graph-spread-design.md`

## Global Constraints

- `LINK_DISTANCE = 100F`, `CHARGE_STRENGTH = 12000F`, `COLLISION_DISTANCE = 64F`
  (Swift: `linkDistance = 100`, `chargeStrength = 12000`, `collisionDistance = 64`) — do not change.
- Density scaling and every force in the simulation stay exactly as they are.
- Positions are pixels in Compose and points in Swift; use the viewport size passed
  to the graph builder (`maxWidth`/`maxHeight`, `GraphSimulation.size`).
- No new dependencies.
- Spread radius is 0.45 × width and 0.45 × height around the viewport centre.

## Review Focus

- Empty graph (0 nodes): no division by zero, no NaN. Covered by "an empty graph has no nodes".
- Single node: starts at the viewport centre, not off-centre. Covered by "a single node starts at the centre".
- Wide/elliptical viewport (e.g. 1920×600): nodes stay inside both axes. Covered by "a wide viewport still holds the spread".
- Large graph (150+ nodes): finite coordinates, everything inside the viewport, no clump in the middle. Covered by "many nodes start spread over the viewport".
- Resize/rebuild: the spread uses the size passed at build time, and Swift keeps
  already-placed nodes where they are. Covered by "a single node starts at the centre" (size) and the existing pinned test `theSimulationPullsLinkedNodesTogetherAndKeepsPlacesAcrossReloads`.

---

### Task 1: Compose spread

**Files:**
- Modify: `application/features/documents_graph/src/commonMain/kotlin/io/writeopia/documents/graph/extensions/GraphExtensions.kt`
- Test: `application/features/documents_graph/src/jvmTest/kotlin/io/writeopia/documents/graph/GraphExtensionsTest.kt`

**Interfaces:**
- Consumes: `ItemData(id, title, parentId, isFolder, selected, isTag)` and
  `Map<String, List<ItemData>>.toGraph(maxWidth: Float, maxHeight: Float): Graph`.
- Produces: `internal fun spreadOverViewport(nodes: Collection<Node>, width: Float, height: Float)`
  in package `io.writeopia.documents.graph.extensions`; called by `toGraph` after the
  nodes are collected. Not used by later tasks.

- [ ] **Step 1: Write the failing test**

Create `GraphExtensionsTest.kt` in package `io.writeopia.documents.graph`:

```kotlin
private fun documents(count: Int) = mapOf(
    ItemData(id = "root", title = "", parentId = "", isFolder = true) to
        (0 until count).map { index ->
            ItemData(id = "doc$index", title = "Doc $index", parentId = "root", isFolder = false)
        }
)

class GraphExtensionsTest {
    @Test
    fun `many nodes start spread over the viewport`() {
        val graph = documents(60).toGraph(1000f, 1000f)

        assertEquals(60, graph.nodes.size)
        graph.nodes.forEach { node ->
            assertTrue(node.x in 50f..950f, "x=${node.x} outside the viewport")
            assertTrue(node.y in 50f..950f, "y=${node.y} outside the viewport")
        }
        val xs = graph.nodes.map { it.x }
        val ys = graph.nodes.map { it.y }
        assertTrue(xs.max() - xs.min() > 800f)
        assertTrue(ys.max() - ys.min() > 800f)

        val closest = graph.nodes.indices.flatMap { i ->
            (i + 1 until graph.nodes.size).map { j ->
                val dx = graph.nodes[i].x - graph.nodes[j].x
                val dy = graph.nodes[i].y - graph.nodes[j].y
                sqrt(dx * dx + dy * dy)
            }
        }.min()
        assertTrue(closest > 70f, "closest pair $closest")
    }

    @Test
    fun `a single node starts at the centre`() {
        val node = documents(1).toGraph(1000f, 600f).nodes.single()

        assertEquals(500f, node.x, 0.01f)
        assertEquals(300f, node.y, 0.01f)
    }

    @Test
    fun `a wide viewport still holds the spread`() {
        val graph = documents(40).toGraph(1920f, 600f)

        graph.nodes.forEach { node ->
            assertTrue(node.x in 96f..1824f)
            assertTrue(node.y in 30f..570f)
        }
        assertTrue(graph.nodes.maxOf { it.y } - graph.nodes.minOf { it.y } > 480f)
    }

    @Test
    fun `an empty graph has no nodes`() {
        assertTrue(emptyMap<String, List<ItemData>>().toGraph(1000f, 1000f).nodes.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --no-daemon --no-watch-fs :application:features:documents_graph:jvmTest --tests "io.writeopia.documents.graph.GraphExtensionsTest"`

Expected: FAIL — the nodes stay in the 300–600 px band (`x=... outside the viewport` or the span assertion).

- [ ] **Step 3: Implement the spiral**

In `GraphExtensions.kt`, build nodes with `initialX = 0f, initialY = 0f`, then call
`spreadOverViewport(nodesMap.values, maxWidth, maxHeight)` before building `links`.
Add:

```kotlin
/**
 * Spreads the nodes over 90% of the viewport on a golden-angle spiral: the first frame shows
 * the whole graph instead of a clump in the middle, and close nodes don't start on top of each
 * other. The simulation then pulls the graph together as before.
 */
internal fun spreadOverViewport(nodes: Collection<Node>, width: Float, height: Float) {
    val count = nodes.size
    if (count == 0) return

    val centerX = width / 2
    val centerY = height / 2
    if (count == 1) {
        nodes.first().x = centerX
        nodes.first().y = centerY
        return
    }

    nodes.forEachIndexed { index, node ->
        val angle = index * GOLDEN_ANGLE
        val radius = sqrt((index + 0.5f) / count)
        node.x = centerX + cos(angle) * radius * width * 0.45f
        node.y = centerY + sin(angle) * radius * height * 0.45f
    }
}

/** π(3 − √5): the angle between neighbours on a sunflower spiral. */
private const val GOLDEN_ANGLE = 2.3999632f
```

Imports: `kotlin.math.cos`, `kotlin.math.sin`, `kotlin.math.sqrt`.

- [ ] **Step 4: Run the test to verify it passes**

Run the Step 2 command. Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add application/features/documents_graph/src
git commit -m "fix(#958): spread force-graph nodes over the viewport on start"
```

---

### Task 2: Swift spread

**Files:**
- Modify: `iosAppNat/Packages/Features/Sources/DocumentsFeature/DocumentsGraphModel.swift`
- Test: `iosAppNat/Packages/Features/Tests/DocumentsFeatureTests/GraphBuilderTests.swift`

**Interfaces:**
- Consumes: `GraphSimulation(size: CGSize)`, `load(nodes: [GraphNodeData], links: [GraphLinkData])`,
  `GraphNode.x/y`, `collisionDistance = 64`.
- Produces: `GraphSimulation.spread(_ nodes: [GraphNode])` (private) used by `load`.
  Not used by later tasks.

- [ ] **Step 1: Write the failing test**

Append to `GraphBuilderTests`:

```swift
@Test func manyNodesStartSpreadOverTheViewport() {
    let simulation = GraphSimulation(size: CGSize(width: 1000, height: 1000))
    let documents = (0..<60).map { GraphNodeData(id: "d\($0)", label: "d\($0)", kind: .document) }
    simulation.load(
        nodes: documents,
        links: (0..<59).map { GraphLinkData(source: "d\($0)", target: "d\($0 + 1)") }
    )

    let nodes = simulation.nodes
    for node in nodes {
        #expect((50...950).contains(node.x) && (50...950).contains(node.y))
    }
    let xs = nodes.map(\.x)
    let ys = nodes.map(\.y)
    #expect((xs.max() ?? 0) - (xs.min() ?? 0) > 800)
    #expect((ys.max() ?? 0) - (ys.min() ?? 0) > 800)

    let closest = nodes.indices.flatMap { i in
        (i + 1..<nodes.count).map { j in hypot(nodes[i].x - nodes[j].x, nodes[i].y - nodes[j].y) }
    }.min() ?? 0
    #expect(closest > 70)
}

@Test func aSingleNodeStartsAtTheCentre() {
    let simulation = GraphSimulation(size: CGSize(width: 1000, height: 600))
    simulation.load(nodes: [GraphNodeData(id: "x", label: "x", kind: .folder)], links: [])

    #expect(abs(simulation.nodes[0].x - 500) < 0.01)
    #expect(abs(simulation.nodes[0].y - 300) < 0.01)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --package-path iosAppNat/Packages/Features --filter "GraphBuilderTests"`

Expected: FAIL — `manyNodesStartSpreadOverTheViewport` (nodes outside `50...950`
or span too small); `aSingleNodeStartsAtTheCentre` fails on the random position.

- [ ] **Step 3: Implement the spiral**

In `GraphSimulation`, replace the random spawn in `load` with:

```swift
public func load(nodes data: [GraphNodeData], links linkData: [GraphLinkData]) {
    let previous = Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    nodes = data.map { item in
        if let old = previous[item.id] {
            return GraphNode(data: item, x: old.x, y: old.y)
        }
        return GraphNode(data: item, x: 0, y: 0)
    }
    spread(nodes)
    let byId = Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    links = linkData.compactMap { link in
        guard let source = byId[link.source], let target = byId[link.target] else { return nil }
        return (source, target)
    }
}

/// Spreads the nodes over 90% of the viewport on a golden-angle spiral, so the first frame
/// shows the whole graph instead of a clump, and close nodes don't start on top of each other.
private func spread(_ nodes: [GraphNode]) {
    let count = nodes.count
    guard count > 0 else { return }
    for (index, node) in nodes.enumerated() {
        let angle = CGFloat(index) * Self.goldenAngle
        let radius = sqrt((CGFloat(index) + 0.5) / CGFloat(count))
        node.x = size.width / 2 + cos(angle) * radius * size.width * 0.45
        node.y = size.height / 2 + sin(angle) * radius * size.height * 0.45
    }
}

/// π(3 − √5): the angle between neighbours on a sunflower spiral.
private static let goldenAngle = CGFloat.pi * (3 - sqrt(5))
```

`static let goldenAngle` must not be inside a generic context; put it next to
`linkDistance`/`chargeStrength`.

- [ ] **Step 4: Run the test to verify it passes**

Run the Step 2 command. Expected: PASS, all tests in `GraphBuilderTests`.

- [ ] **Step 5: Commit**

```bash
git add iosAppNat/Packages/Features
git commit -m "fix(#958): spread the native force graph on start"
```
