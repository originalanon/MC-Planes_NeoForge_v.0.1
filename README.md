# MC-Planes_NeoForge_v.0.1
A NeoForge mod for creating different planes of reality. Choose which mobs, structures, items, ores, etc. should be active in a given plane.

## Current Proof of Concept

In Minecraft 26.3 with NeoForge 26.3.0.90, open **Singleplayer > Create New World > Planes**.

- The plane list starts empty for each new world-creation screen.
- **Create Plane** opens the content browser. Enter a unique name and use checkboxes to select registered content, then **Save Plane**.
- Select a saved draft and choose **Manage Plane** to rename it or change its content. **Cancel** or Escape discards editor changes.
- Filters and search do not clear selections. Blocks and their items are tracked separately, even when they share a registry ID.
- Back returns to world creation. Reopening Planes retains that screen's drafts, including after resizing. Leaving world creation and starting a different world starts a separate list.

These are **session-only drafts**, not generated Minecraft dimensions. They are not written to disk or loaded from existing world saves. Content selections do not yet restrict spawning, crafting, loot, or world generation. Existing-world management and persistence are later milestones.

`PlanesClient` associates draft lists with world-creation screens using weak keys, keeping worlds isolated without retaining abandoned screens. `ManagePlanes` owns the list workflow, `CreateNewPlane` opens the shared content editor, and immutable `PlaneDefinition` records keep saved drafts separate from unsaved editor changes. The catalog retains its filter for namespaces with no supported content.

## Build and Test

From `planes-template-26.3`, run `bash gradlew build` with Java 25. The packaged mod is `build/libs/planes-1.0.0.jar`. JUnit tests cover draft identity, name validation, immutable content snapshots, category-aware IDs, and editing copies; they do not exercise Minecraft rendering.

In Prism, verify an empty list, create and save a named plane, reopen it for management, cancel an edit, and reopen it to confirm the saved version is unchanged. Also test blank/duplicate names, filtered selections, Back/Escape, resizing, and isolation between two new world-creation screens. Keep the Ice and Fire port enabled to check the layered backgrounds still avoid the double-blur error.
