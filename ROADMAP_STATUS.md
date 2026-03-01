# Framed Block System — Roadmap Status

## Original 9-item roadmap:
1. ~~Oddstone pull immunity~~ DONE
2. ~~Frameability whitelist flip~~ DONE
3. ~~Behavioral delegation~~ DONE (stepOn, fallOn, updateEntityAfterFallOn, entityInside, getSpeedFactor, getJumpFactor, getPistonPushReaction)
4. Waterloggable empty frames + ice melt
5. ~~INNER_STICKY=4 (Oddstone)~~ DONE
6. ~~Framed Oddstone stickiness rules~~ DONE
7. ~~Framed obsidian immovable~~ DONE (via PistonBaseBlockMixin Feature 4 + Resolver Feature 5. Also anchors sticky groups.)
8. Copper/iron framing restrictions (recipe level)
9. Copper/iron blocks as frame family members (FrameActivation expansion)

## Items that came up during sessions:
1. Cross-frame mediation (copper-framed iron bridges families)
2. ~~Framed block transit rendering~~ DONE (BlockMoveHelper step 2.6 populates MovingFrameData on client side)
3. Framed blocks not grabbed by slime during Repulser/Attractor moves
4. Frame inside-face rendering -- inverted cuboid in JSON models
5. Portal activation -- ender pearl
6. Copper oxidation (deferred)
7. ~~Sympathetic framed oddstone transit rendering~~ DONE (PistonBaseBlockClientMixin sympathetic scan added to retraction capture)
8. ~~Sympathetic retraction with machine-activated framed blocks~~ DONE (PistonBaseBlockMixin.scanSympathetic() now calls scanStickyGroup() on each found oddstone)
9. Honey block wall-sliding (known limitation -- requires inset collision shape, deferred)

Written by Claude, directed and guided by The Ideomancer.
