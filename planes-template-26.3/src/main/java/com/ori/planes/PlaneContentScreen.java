/**
 * @ Author: OriginalAnon (Lindsay Barton)
 * @ Create Time: 2026-10-18 06:19:19
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 11:31:09
 * @ Description: Screen for browsing plane content within the mod; Shows up before world generation. PoC.
 */

package com.ori.planes;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.ori.planes.PlaneManagement.PlaneDefinition;
import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;
import com.ori.planes.PlaneManagement.PlaneType;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class PlaneContentScreen extends Screen {
    private static final List<String> CATEGORIES = List.of("all", "blocks", "items", "entities", "biomes",
            "configured_features", "placed_features", "structures", "structure_sets", "dimension_types", "dimensions");
    private final List<PlaneContentCatalog.ModContent> mods;
    private PlaneContentCatalog.ModContent selectedMod;
    private String category = "all";
    private String query = "";
    private BrowserList contentList;
    private int matchingCount;
    private final UUID planeId;
    private final Set<ContentKey> selectedContent;
    private final Predicate<String> nameAvailable;
        private final Predicate<PlaneDefinition> onSave;
    private String planeName;
        private PlaneType planeType;
    private Button saveButton;
        private boolean saveFailed;

    protected PlaneContentScreen(CreateWorldScreen parent, PlaneDefinition existing,
            Predicate<String> nameAvailable, Predicate<PlaneDefinition> onSave) {
        super(Component.translatable(existing == null ? "planes.editor.create" : "planes.editor.edit"));
        this.mods = PlaneContentCatalog.scan(parent.getUiState().getSettings());
        this.selectedMod = mods.stream().filter(mod -> mod.id().equals(Planes.MODID)).findFirst()
                .orElseGet(() -> mods.isEmpty() ? null : mods.getFirst());
        this.planeId = existing == null ? UUID.randomUUID() : existing.id();
        this.planeName = existing == null ? "" : existing.name();
        this.planeType = existing == null ? PlaneType.OVERWORLD : existing.type();
        this.selectedContent = new HashSet<>(existing == null ? Set.of() : existing.content());
        this.nameAvailable = nameAvailable;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int modWidth = Math.min(180, width / 3);
        int detailX = modWidth + 16;
        int detailWidth = width - detailX - 8;
        var modList = new BrowserList(8, 84, modWidth, height - 124);
        contentList = new BrowserList(detailX, 84, detailWidth, height - 124);

        
        for (var mod : mods) {
            var row = new BrowserRow(mod.name(), Component.translatable("planes.menu.mod_summary",
                    mod.id(), mod.entries().size()), () -> {
                        selectedMod = mod;
                        refreshContent();
                    });
            modList.addRow(row);
            if (selectedMod != null && mod.id().equals(selectedMod.id())) {
                modList.setSelected(row);
            }
        }
        addRenderableWidget(modList);
        addRenderableWidget(contentList);

        addRenderableWidget(Button.builder(Component.translatable("planes.menu.select_all"), button -> {
            setVisibleContentSelected(true);
        }).bounds(8, height - 56, modWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("planes.menu.deselect_all"), button -> {
            setVisibleContentSelected(false);
        }).bounds(8, height - 28, modWidth, 20).build());

        addRenderableWidget(CycleButton.<String>builder(value -> Component.translatable("planes.category." + value), category)
            .withValues(CATEGORIES)
                .create(8, 56, modWidth, 20, Component.translatable("planes.menu.category"), (button, value) -> {
                    category = value;
                    refreshContent();
                }));

        
        var search = new EditBox(font, detailX, 56, detailWidth, 20, Component.translatable("planes.menu.search"));
        search.setMaxLength(256);
        search.setHint(Component.translatable("planes.menu.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refreshContent();
        });
        addRenderableWidget(search);


        int nameWidth = Math.max(120, (width - 24) * 2 / 3);
        var name = new EditBox(font, 8, 32, nameWidth, 20, Component.translatable("planes.editor.name"));
        name.setMaxLength(64);
        name.setHint(Component.translatable("planes.editor.name"));
        name.setValue(planeName);
        name.setResponder(value -> {
            planeName = value;
            updateSaveButton();
        });
        addRenderableWidget(name);

        int typeX = 16 + nameWidth;
        int typeWidth = Math.max(100, width - typeX - 8);
        addRenderableWidget(CycleButton.<PlaneType>builder(
            value -> Component.translatable("planes.editor.type." + value.name().toLowerCase(Locale.ROOT)), planeType)
            .withValues(PlaneType.values())
            .create(typeX, 32, typeWidth, 20, Component.translatable("planes.editor.type"),
                (button, value) -> planeType = value));


        int buttonWidth = Math.min(150, (width - 24) / 2);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("planes.editor.save"), button -> {
            if (validName()) {
            saveFailed = !onSave.test(new PlaneDefinition(planeId, planeName, selectedContent, planeType));
            if (!saveFailed) {
                onClose();
            }
            }
        }).bounds(width / 2 - buttonWidth - 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 28, buttonWidth, 20).build());

        
        updateSaveButton();
        refreshContent();
    }

    private boolean validName() {
        return !planeName.isBlank() && nameAvailable.test(planeName.strip());
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = validName();
            saveButton.setTooltip(validName() ? null : net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("planes.editor.invalid_name")));
        }
    }

    private void refreshContent() {
        contentList.clearRows();
        var entries = matchingEntries();
        matchingCount = entries.size();
        for (var entry : entries) {
            var key = new ContentKey(entry.category(), entry.id());
            contentList.addRow(new BrowserRow(entry.id(),
                Component.translatable("planes.category." + entry.category()), key));
        }
        if (matchingCount == 0) {
            contentList.addRow(new BrowserRow(Component.translatable("planes.menu.empty"),
                    Component.empty(), () -> {}));
        }
    }

    private List<PlaneContentCatalog.Content> matchingEntries() {
        if (selectedMod == null) {
            return List.of();
        }
        String filter = query.toLowerCase(Locale.ROOT).strip();
        return selectedMod.entries().stream()
                .filter(entry -> category.equals("all") || category.equals(entry.category()))
                .filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
    }

    private void setVisibleContentSelected(boolean selected) {
        for (var entry : matchingEntries()) {
            var key = new ContentKey(entry.category(), entry.id());
            if (selected) {
                selectedContent.add(key);
            } else {
                selectedContent.remove(key);
            }
        }
        refreshContent();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
       graphics.fill(0, 0, width, height, 0xD9000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("planes.editor.summary", matchingCount, selectedContent.size()),
                width / 2, 20, 0xFFAAAAAA);
        if (saveFailed) {
            graphics.centeredText(font, Component.translatable("planes.editor.save_failed"), width / 2,
                height - 43, 0xFFFF5555);
        }
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
        private final Component localizedLabel;
        private final Component detail;
        private final Runnable onSelect;
        private Checkbox checkbox;

        BrowserRow(String label, Component detail, Runnable onSelect) {
            this.label = label;
            this.localizedLabel = null;
            this.detail = detail;
            this.onSelect = onSelect;
        }

        BrowserRow(Component label, Component detail, Runnable onSelect) {
            this.label = label.getString();
            this.localizedLabel = label;
            this.detail = detail;
            this.onSelect = onSelect;
        }

        BrowserRow(String label, Component detail, ContentKey key) {
            this(label, detail, () -> {});
            this.checkbox = Checkbox.builder(Component.empty(), font)
                    .selected(selectedContent.contains(key)).onValueChange((button, selected) -> {
                        if (selected) {
                            selectedContent.add(key);
                        } else {
                            selectedContent.remove(key);
                        }
                    }).build();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (checkbox != null && checkbox.mouseClicked(event, doubleClick)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (checkbox != null) {
                checkbox.setFocused(isFocused());
                return checkbox.keyPressed(event);
            }
            return super.keyPressed(event);
        }

        @Override
        public Component getNarration() {
            var narration = (localizedLabel == null ? Component.literal(label) : localizedLabel).copy()
                .append(" ").append(detail);
                return checkbox == null ? narration : narration.append(" ").append(Component.translatable(
                    checkbox.selected() ? "planes.editor.included" : "planes.editor.excluded"));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int textX = getContentX();
            if (checkbox != null) {
                checkbox.setX(getContentX());
                checkbox.setY(getContentY() + 4);
                checkbox.extractRenderState(graphics, mouseX, mouseY, partialTick);
                textX += checkbox.getWidth() + 4;
            }
            int textWidth = Math.max(0, getContentRight() - textX);
            if (localizedLabel == null) {
                graphics.text(font, font.plainSubstrByWidth(label, textWidth), textX, getContentY() + 2, 0xFFFFFFFF);
            } else {
                graphics.text(font, localizedLabel, textX, getContentY() + 2, 0xFFFFFFFF);
            }
            graphics.text(font, detail, textX, getContentY() + 14, 0xFFAAAAAA);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, getNarration(), mouseX, mouseY);
            }
        }
    }
}