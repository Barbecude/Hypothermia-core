# Graph Report - Hypothermia core  (2026-09-16)

## Corpus Check
- 51 files · ~18,829 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: (none) 7, .properties 2, .bat 2)

## Summary
- 225 nodes · 374 edges · 28 communities (18 shown, 7 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3a7b91a8`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- What You Must Do When Invoked
- org.spongepowered.asm.mixin.Mixin
- HypothermiaMixinPlugin
- graphify reference: extra exports and benchmark
- ExampleMod
- graphify reference: query, path, explain
- ExampleModClient
- gradlew
- graphify reference: add a URL and watch a folder
- graphify reference: commit hook and native CLAUDE.md integration
- graphify reference: incremental update and cluster-only
- Example Mod
- opencode.json
- graphify.js
- graphify reference: GitHub clone and cross-repo merge
- graphify reference: transcribe video and audio
- AGENTS.md
- extraction-spec.md
- ItemRendererFrozenFoodMixin.java
- SpectreLib
- FallingSnowOptimizationMixin.java
- HypothermiaMixinCanceller
- App.tsx
- ColdSweatCompatibilityMixin.java
- SpectreConfigTrackerFixMixin.java

## God Nodes (most connected - your core abstractions)
1. `What You Must Do When Invoked` - 12 edges
2. `/graphify` - 10 edges
3. `HypothermiaMixinPlugin` - 9 edges
4. `FallingSnowOptimizationMixin` - 8 edges
5. `graphify reference: extra exports and benchmark` - 8 edges
6. `FoodFreezingHelper` - 7 edges
7. `SpectreConfigTrackerFixMixin` - 6 edges
8. `ExampleMod` - 5 edges
9. `ColdSweatCompatibilityMixin` - 5 edges
10. `SpartanWeaponryFixMixin` - 5 edges

## Surprising Connections (you probably didn't know these)
- `FoodFreezingHelper` --references--> `java.lang.reflect.Method`  [EXTRACTED]
  src/client/java/com/example/client/FoodFreezingHelper.java →   _Bridges community 20 → community 26_
- `ItemRendererFrozenFoodMixin` --references--> `org.spongepowered.asm.mixin.Mixin`  [EXTRACTED]
  src/client/java/com/example/client/mixin/ItemRendererFrozenFoodMixin.java →   _Bridges community 20 → community 1_
- `ColdSweatCompatibilityMixin` --references--> `org.spongepowered.asm.mixin.Mixin`  [EXTRACTED]
  src/main/java/com/example/mixin/ColdSweatCompatibilityMixin.java →   _Bridges community 26 → community 1_
- `FallingSnowOptimizationMixin` --references--> `org.spongepowered.asm.mixin.Mixin`  [EXTRACTED]
  src/main/java/com/example/mixin/FallingSnowOptimizationMixin.java →   _Bridges community 22 → community 1_
- `SpectreConfigTrackerFixMixin` --references--> `org.spongepowered.asm.mixin.Mixin`  [EXTRACTED]
  src/main/java/com/example/mixin/SpectreConfigTrackerFixMixin.java →   _Bridges community 27 → community 1_

## Import Cycles
- None detected.

## Communities (28 total, 7 thin omitted)

### Community 0 - "What You Must Do When Invoked"
Cohesion: 0.08
Nodes (24): For /graphify add and --watch, For /graphify query, For the commit hook and native CLAUDE.md integration, For --update and --cluster-only, /graphify, Honesty Rules, Interpreter guard for subcommands, Part A - Structural extraction for code files (+16 more)

### Community 1 - "org.spongepowered.asm.mixin.Mixin"
Cohesion: 0.11
Nodes (23): com.illusivesoulworks.spectrelib.EntrypointUtils, com.mojang.datafixers.util.Either, net.minecraft.client.Minecraft, net.minecraft.server.MinecraftServer, net.minecraft.world.entity.monster.AbstractSkeleton, net.minecraft.world.item.ProjectileWeaponItem, org.spongepowered.asm.mixin.injection.callback.CallbackInfo, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable (+15 more)

### Community 2 - "HypothermiaMixinPlugin"
Cohesion: 0.29
Nodes (5): org.objectweb.asm.tree.ClassNode, org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin, org.spongepowered.asm.mixin.extensibility.IMixinInfo, HypothermiaMixinPlugin, Override

### Community 3 - "graphify reference: extra exports and benchmark"
Cohesion: 0.22
Nodes (8): graphify reference: extra exports and benchmark, Step 6b - Wiki (only if --wiki flag), Step 7 - Neo4j export (only if --neo4j or --neo4j-push flag), Step 7a - FalkorDB export (only if --falkordb or --falkordb-push flag), Step 7b - SVG export (only if --svg flag), Step 7c - GraphML export (only if --graphml flag), Step 7d - MCP server (only if --mcp flag), Step 8 - Token reduction benchmark (only if total_words > 5000)

### Community 4 - "ExampleMod"
Cohesion: 0.36
Nodes (5): net.fabricmc.api.ModInitializer, net.minecraft.resources.ResourceLocation, org.slf4j.Logger, ExampleMod, Override

### Community 5 - "graphify reference: query, path, explain"
Cohesion: 0.33
Nodes (5): For /graphify explain, For /graphify path, graphify reference: query, path, explain, Step 0 — Constrained query expansion (REQUIRED before traversal), Step 1 — Traversal

### Community 6 - "ExampleModClient"
Cohesion: 0.50
Nodes (3): net.fabricmc.api.ClientModInitializer, ExampleModClient, Override

### Community 7 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 8 - "graphify reference: add a URL and watch a folder"
Cohesion: 0.50
Nodes (3): For /graphify add, For --watch, graphify reference: add a URL and watch a folder

### Community 9 - "graphify reference: commit hook and native CLAUDE.md integration"
Cohesion: 0.50
Nodes (3): For git commit hook, For native CLAUDE.md integration, graphify reference: commit hook and native CLAUDE.md integration

### Community 10 - "graphify reference: incremental update and cluster-only"
Cohesion: 0.50
Nodes (3): For --cluster-only, For --update (incremental re-extraction), graphify reference: incremental update and cluster-only

### Community 11 - "Example Mod"
Cohesion: 0.50
Nodes (3): Example Mod, License, Setup

### Community 20 - "ItemRendererFrozenFoodMixin.java"
Cohesion: 0.20
Nodes (13): com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.entity.ItemRenderer, net.minecraft.client.renderer.MultiBufferSource, net.minecraft.client.resources.model.BakedModel, net.minecraft.network.chat.Component, net.minecraft.world.entity.player.Player, net.minecraft.world.item.ItemDisplayContext, net.minecraft.world.item.ItemStack (+5 more)

### Community 22 - "FallingSnowOptimizationMixin.java"
Cohesion: 0.19
Nodes (12): FunctionalInterface, net.minecraft.core.BlockPos, net.minecraft.core.Direction, net.minecraft.world.entity.Entity, net.minecraft.world.entity.EntityType, net.minecraft.world.entity.item.FallingBlockEntity, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.Level (+4 more)

### Community 23 - "HypothermiaMixinCanceller"
Cohesion: 0.40
Nodes (3): com.bawnorton.mixinsquared.api.MixinCanceller, HypothermiaMixinCanceller, Override

### Community 24 - "App.tsx"
Cohesion: 0.38
Nodes (4): App(), TaskItem, TASKS, JAR_BASE64

### Community 26 - "ColdSweatCompatibilityMixin.java"
Cohesion: 0.53
Nodes (4): java.lang.reflect.Method, net.minecraft.core.dispenser.ShearsDispenseItemBehavior, net.minecraft.server.level.ServerLevel, ColdSweatCompatibilityMixin

### Community 27 - "SpectreConfigTrackerFixMixin.java"
Cohesion: 0.25
Nodes (8): com.electronwill.nightconfig.core.file.CommentedFileConfig, com.illusivesoulworks.spectrelib.config.SpectreConfig, com.illusivesoulworks.spectrelib.config.SpectreConfigSpec, com.illusivesoulworks.spectrelib.config.SpectreConfigTracker, org.spongepowered.asm.mixin.Shadow, SpectreConfigTrackerFixMixin, SuppressWarnings, SpectreConfigValueFixMixin

## Knowledge Gaps
- **50 isolated node(s):** `$schema`, `plugin`, `TaskItem`, `TASKS`, `Usage` (+45 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 76 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FallingSnowOptimizationMixin` connect `FallingSnowOptimizationMixin.java` to `org.spongepowered.asm.mixin.Mixin`?**
  _High betweenness centrality (0.009) - this node is a cross-community bridge._
- **What connects `$schema`, `plugin`, `TaskItem` to the rest of the system?**
  _50 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `What You Must Do When Invoked` be split into smaller, more focused modules?**
  _Cohesion score 0.08 - nodes in this community are weakly interconnected._
- **Should `org.spongepowered.asm.mixin.Mixin` be split into smaller, more focused modules?**
  _Cohesion score 0.11149825783972125 - nodes in this community are weakly interconnected._