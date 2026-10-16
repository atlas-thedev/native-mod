# Native client (Minecraft mod) – PRD notes

## Shulker Tooltip (module id `shulker-preview`, label "Shulker Tooltip") – Jun 2026
- Replaces the old held-only Shulker Preview. File: src/main/java/xyz/nativelaunch/ui/mod/ShulkerTooltip.java
- Hover a shulker box in any vanilla inventory/chest -> 9x3 panel next to cursor (above vanilla tooltip, left of cursor if no room), fade-in.
- Settings: Show on hover (Always / Hold Shift), Compact (merge same items, sorted, totals like 76 / 1.7K / 12K), Item counts,
  Durability bars, Tint to box colour, Box name + total header, Tooltip size, Also show box in hand (HUD card, main or off hand).
- Bridge: McBridge.hoveredStack(), shulkerContents(stack, out), shulkerInHand(out) now returns box id (String).
  Intermediary: HandledScreen.field_2787 / Slot.method_7677; 1.16-1.20.4 uses ItemStack.fromNbt (method_7915) for damage/tints.
  26.x: AbstractContainerScreen.hoveredSlot; ItemContainerContents.copyInto keeps slot order.
- Drawn from UiRuntime.frame (shulkerTip flag) over vanilla screens.
- NOT built / NOT tested (user asked not to build/run/test).

## Backlog
- Optionally hide vanilla shulker tooltip lines while panel shows
- Ender chest contents preview, bundle preview

## Hide vanilla tooltip – Jun 2026
- Setting "Hide the game's own tooltip" (default on): ShulkerTooltipMixin (class_465.method_2380) / ShulkerTooltipMixin26
  (AbstractContainerScreen.extractTooltip|renderTooltip) cancel at HEAD while ShulkerTooltip.hidesVanilla().
- Panel then sits where vanilla tooltip would (cursor +12, -12), header shows the stack's real/custom name (McBridge.stackName).
- NOT built / NOT tested.
