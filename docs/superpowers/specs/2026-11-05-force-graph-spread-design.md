# Force graph initial spread — design

Issue: Writeopia/Writeopia#958 "Force graph starts too close".

## Intent

Opening the documents force graph should show the whole graph spread out, not a
tight clump in the middle of the screen. The graph simulation itself is fine; only
the first frame is wrong.

## What already happens

Both implementations place every node at a random point inside a narrow central
band of the viewport:

- Compose: `GraphExtensions.kt` → x ∈ [0.30, 0.60]·width, y ∈ [0.40, 0.70]·height.
- Swift: `DocumentsGraphModel.swift` → x, y ∈ [0.30, 0.70] of the viewport.

With more than a handful of nodes the random points overlap, and the collision
force untangles them over the following frames. That is the "starts too close".

## Goals

- On the first frame the nodes cover most of the viewport and no two nodes start
  on top of each other.
- Same behaviour on Compose (desktop/android/web) and on the native Swift app.
- Existing simulation forces, constants and settled layout unchanged.

## Non-goals

- Changing link distance, charge, collision distance, damping or centring.
- Animating the entrance, or the layout after it settles.
- Preserving positions across grouping changes (existing behaviour keeps them in
  Swift and rebuilds in Compose; not touched).

## Approaches

1. **Widen the random ranges** (e.g. 0.05–0.95). One line per platform, but two
   random nodes can still land on top of each other and any test would be flaky.
2. **Golden-angle spiral** — place node `i` of `n` at radius
   `sqrt((i + 0.5) / n)` and angle `i · π(3 − √5)`, scaled to 90% of the viewport
   ellipse. Deterministic, area-uniform, cheap, and testable: the first frame has
   no clump, and for small graphs a minimum pairwise distance can be asserted.

## Decision

Approach 2. It is a few lines per platform, removes chance from the bug, and lets
the tests pin the actual acceptance criterion instead of sampling randomness.

## Constraints

- Positions are in pixels (Compose) / points (Swift); the canvas may be resized
  after the view model was created, and the spread must use the current size.
- The gravity/centring force still pulls to the viewport centre, so the graph will
  contract after the first frames as before.
- Kotlin `Node.x/y` are `mutableStateOf`; assigning after construction is fine.

## Left to the builder

Exact test thresholds: for 60 nodes in a 1000×1000 viewport the spiral keeps the
closest pair ≈ 90 px apart and spans ≈ 870 px; for 12 nodes the closest pair is
≈ 200 px. Tests use conservative thresholds below those numbers.
