/**
 * @ Author: OriginalAnon (Lindsay Barton)
 * @ Create Time: 2026-10-18 06:19:19
 * @ Modified by: Your name
 * @ Modified time: 2026-10-18 12:45:40
 * @ Description: Screen for browsing plane content within the mod; Shows up before world generation. PoC.
 */

package com.ori.planes;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class PlaneContentScreen extends Screen {
    private static final List<String> CATEGORIES = List.of("all", "blocks", "items", "entities", "biomes",
            "configured_features", "placed_features", "structures", "structure_sets", "dimension_types", "dimensions");
    private final List<PlaneContentCatalog.ModContent> mods;
    private PlaneContentCatalog.ModContent selectedMod;
    private String category = "all";
    private String query = "";
    private BrowserList contentList;
    private int matchingCount;

    PlaneContentScreen(CreateWorldScreen parent) {
        super(Component.translatable("planes.menu.title"));
        this.mods = PlaneContentCatalog.scan(parent.getUiState().getSettings());
        this.selectedMod = mods.stream().filter(mod -> mod.id().equals(Planes.MODID))
                .findFirst().orElse(mods.getFirst());
    }

    @Override
    protected void init() {
        //Init

        int modWidth = Math.min(180, width / 3);
        int detailX = modWidth + 16;
        int detailWidth = width - detailX - 8;
        var modList = new BrowserList(8, 60, modWidth, height - 100);
        contentList = new BrowserList(detailX, 60, detailWidth, height - 100);
        for (var mod : mods) {
            var row = new BrowserRow(mod.name(), Component.translatable("planes.menu.mod_summary",
                    mod.id(), mod.entries().size()), () -> {
                        selectedMod = mod;
                        refreshContent();
                    });
            modList.addRow(row);
            if (mod.id().equals(selectedMod.id())) {
                modList.setSelected(row);
            }
        }
        addRenderableWidget(modList);
        addRenderableWidget(contentList);
        addRenderableWidget(CycleButton.<String>builder(value -> Component.translatable("planes.category." + value), category)
            .withValues(CATEGORIES)
                .create(8, 32, modWidth, 20, Component.translatable("planes.menu.category"), (button, value) -> {
                    category = value;
                    refreshContent();
                }));
        var search = new EditBox(font, detailX, 32, detailWidth, 20, Component.translatable("planes.menu.search"));
        search.setMaxLength(256);
        search.setHint(Component.translatable("planes.menu.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refreshContent();
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
        refreshContent();
    }

    private void refreshContent() {
        contentList.clearRows();
        String filter = query.toLowerCase(Locale.ROOT).strip();
        matchingCount = 0;
        for (var entry : selectedMod.entries()) {
            if ((category.equals("all") || category.equals(entry.category()))
                    && entry.id().toLowerCase(Locale.ROOT).contains(filter)) {
                contentList.addRow(new BrowserRow(entry.id(),
                        Component.translatable("planes.category." + entry.category()), () -> {}));
                matchingCount++;
            }
        }
        if (matchingCount == 0) {
            contentList.addRow(new BrowserRow(Component.translatable("planes.menu.empty").getString(),
                    Component.empty(), () -> {}));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
       graphics.fill(0, 0, width, height, 0xD9000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("planes.menu.summary", mods.size(), matchingCount),
                width / 2, 20, 0xFFAAAAAA);
    }

    @Override
    public void repositionElements() {
        rebuildWidgets();
    }

    private final class BrowserList extends ObjectSelectionList<BrowserRow> {
        BrowserList(int left, int top, int listWidth, int listHeight) {
            super(PlaneContentScreen.this.minecraft, listWidth, listHeight, top, 30);
            setX(left);
        }

        void addRow(BrowserRow row) {
            addEntry(row);
        }

        void clearRows() {
            clearEntries();
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return Math.max(20, getWidth() - 16);
        }

        @Override
        public void setSelected(BrowserRow row) {
            super.setSelected(row);
            if (row != null) {
                row.onSelect.run();
            }
        }
    }

    private final class BrowserRow extends ObjectSelectionList.Entry<BrowserRow> {
        private final String label;
        private final Component detail;
        private final Runnable onSelect;

        BrowserRow(String label, Component detail, Runnable onSelect) {
            this.label = label;
            this.detail = detail;
            this.onSelect = onSelect;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label).append(" ").append(detail);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.text(font, font.plainSubstrByWidth(label, getContentWidth()), getContentX(), getContentY() + 2, 0xFFFFFFFF);
            graphics.text(font, font.plainSubstrByWidth(detail.getString(), getContentWidth()), getContentX(), getContentY() + 14, 0xFFAAAAAA);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, getNarration(), mouseX, mouseY);
            }
        }
    }
}