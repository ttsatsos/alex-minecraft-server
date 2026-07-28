# MonumentBuilder

MonumentBuilder creates reusable, block-native structures on the Paper staging
server. Builds run in batches so large structures do not freeze the game loop.

## Statue of Liberty

The initial generator uses the National Park Service dimensions as its scale
reference. The real monument is approximately 93 meters from ground to flame,
split almost evenly between its 47-meter pedestal and 46-meter statue. The test
build is 97 blocks from its submerged foundation to the flame.

The design includes:

- an eleven-point Fort Wood base and viewing pier
- a stepped stone and tuff pedestal with windows and entrance
- an oxidized-copper and prismarine robe
- a tablet, raised right arm, seven crown rays, and illuminated gold torch

The linked Sketchfab model was used only as a visual reference. Its API reports
that the model is not downloadable and does not provide a reuse license, so no
geometry or files from it are included here.

## Commands

All players can visit the configured site:

```text
/monument visit
```

Operators can build, inspect, and undo:

```text
/monument build statue-of-liberty [world x y z]
/monument status
/monument undo
```

Undo data is held in memory only. Make a consistent world backup before every
permanent build.
