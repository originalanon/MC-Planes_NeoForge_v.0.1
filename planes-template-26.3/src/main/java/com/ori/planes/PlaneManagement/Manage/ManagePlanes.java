/**
 * @ Author: Lindsay Barton
 * @ Create Time: 2026-10-18 12:52:42
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 11:22:00
 * @ Description: Class for managing planes within the mod.
 */




package com.ori.planes.PlaneManagement.Manage;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.ori.planes.Planes;
import com.ori.planes.PlaneManagement.Create.CreateNewPlane;
import com.ori.planes.PlaneManagement.PlaneDefinition;
import com.ori.planes.PlaneManagement.PlaneDatapackGenerator;
import com.ori.planes.PlaneManagement.PlaneDefinitionStore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ManagePlanes extends Screen {
	private final CreateWorldScreen worldCreation;
	private final List<PlaneDefinition> planes;
	private UUID selectedId;
	private Button manageButton;
	private Button deleteButton;

	public ManagePlanes(CreateWorldScreen worldCreation, List<PlaneDefinition> planes) {
		super(Component.translatable("planes.manager.title"));
		this.worldCreation = worldCreation;
		this.planes = planes;
	}

	@Override
	protected void init() {
		int buttonWidth = Math.min(150, (width - 32) / 3);
		int left = (width - buttonWidth * 3 - 16) / 2;
		addRenderableWidget(Button.builder(Component.translatable("planes.menu.create_plane"), button -> openEditor(null))
				.bounds(left, height - 52, buttonWidth, 20).build());
		manageButton = addRenderableWidget(Button.builder(Component.translatable("planes.menu.manage_planes"), button -> {
			planes.stream().filter(plane -> plane.id().equals(selectedId)).findFirst().ifPresent(this::openEditor);
		}).bounds(left + buttonWidth + 8, height - 52, buttonWidth, 20).build());
		
		deleteButton = addRenderableWidget(Button.builder(Component.translatable("planes.menu.delete_plane"), button -> {
			if (selectedId != null) {
				confirmDeletePlane(selectedId);
			}
		}).bounds(left + (buttonWidth + 8) * 2, height - 52, buttonWidth, 20).build());

		boolean hasSelection = planes.stream().anyMatch(plane -> plane.id().equals(selectedId));
		manageButton.active = hasSelection;
		deleteButton.active = hasSelection;
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
				.bounds(width / 2 - buttonWidth / 2, height - 28, buttonWidth, 20).build());
		var list = addRenderableWidget(new PlaneList());

		for (var plane : planes) {
			var row = new PlaneRow(plane);
			list.addPlane(row);
			if (plane.id().equals(selectedId)) {
				list.setSelected(row);
			}
		}
	}

	//Opens the editor for the currently selected plane
	private void openEditor(PlaneDefinition existing) {
		minecraft.gui.pushScreenLayer(new CreateNewPlane(worldCreation, existing,
				name -> planes.stream().noneMatch(plane -> plane.name().equalsIgnoreCase(name)
						&& (existing == null || !plane.id().equals(existing.id()))), saved -> {
					try {
						PlaneDatapackGenerator.generate(worldCreation, saved);
					} catch (IOException exception) {
						Planes.LOGGER.error("Could not generate datapack for plane {}", saved.id(), exception);
						return false;
					}
					try {
						PlaneDefinitionStore.save(minecraft.gameDirectory.toPath(), saved);
					} catch (IOException exception) {
						try {
							if (existing == null) {
								PlaneDatapackGenerator.remove(worldCreation, saved);
							} else {
								PlaneDatapackGenerator.generate(worldCreation, existing);
							}
						} catch (IOException rollbackException) {
							exception.addSuppressed(rollbackException);
						}
						Planes.LOGGER.error("Could not save plane definition {}", saved.id(), exception);
						return false;
					}
					int index = -1;
					for (int position = 0; position < planes.size(); position++) {
						if (planes.get(position).id().equals(saved.id())) {
							index = position;
							break;
						}
					}
					if (index < 0) {
						planes.add(saved);
					} else {
						planes.set(index, saved);
					}
					selectedId = saved.id();

					rebuildWidgets();
					return true;
				}));
	}

	private void confirmDeletePlane(UUID planeId) {
		PlaneDefinition plane = planes.stream().filter(entry -> entry.id().equals(planeId)).findFirst().orElse(null);
		if (plane == null) {
			return;
		}

		//Confirm delete plane
		minecraft.gui.pushScreenLayer(new ConfirmScreen(confirmed -> {

			minecraft.gui.popScreenLayer();
			if (confirmed) {
				deletePlane(planeId);
			}
		}, Component.translatable("planes.menu.delete_plane.confirm"),
				Component.translatable("planes.menu.delete_plane.confirm_message", plane.name())) {
			@Override
			public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
				graphics.fill(0, 0, width, height, 0x99000000);
			}
		});
	}

	private void deletePlane(UUID planeId) {
		PlaneDefinition plane = planes.stream().filter(entry -> entry.id().equals(planeId)).findFirst().orElse(null);
		if (plane == null) {
			return;
		}
		try {
			PlaneDatapackGenerator.remove(worldCreation, plane);
		} catch (IOException exception) {
			Planes.LOGGER.error("Could not remove staged datapack for plane {}", planeId, exception);
			return;
		}
		try {
			if (!PlaneDefinitionStore.delete(minecraft.gameDirectory.toPath(), planeId)) {
				PlaneDatapackGenerator.generate(worldCreation, plane);
				Planes.LOGGER.warn("Plane definition {} was not found on disk; keeping it in the manager", planeId);
				return;
			}
		} catch (IOException exception) {
			try {
				PlaneDatapackGenerator.generate(worldCreation, plane);
			} catch (IOException rollbackException) {
				exception.addSuppressed(rollbackException);
			}
			Planes.LOGGER.error("Could not delete plane definition {}", planeId, exception);
			return;
		}

		planes.removeIf(definition -> definition.id().equals(planeId));
		if (selectedId != null && selectedId.equals(planeId)) {
			selectedId = null;
		}
		rebuildWidgets();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xD9000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("planes.manager.count", planes.size()), width / 2, 24, 0xFFAAAAAA);
		if (planes.isEmpty()) {
			graphics.centeredText(font, Component.translatable("planes.manager.empty"), width / 2,
					Math.max(48, (height - 64) / 2), 0xFFAAAAAA);
		}
	}

	private final class PlaneList extends ObjectSelectionList<PlaneRow> {
		PlaneList() {
			super(ManagePlanes.this.minecraft, ManagePlanes.this.width - 16, ManagePlanes.this.height - 108, 40, 32);
			setX(8);
		}

		void addPlane(PlaneRow row) {
			addEntry(row);
		}

		@Override
		public int getRowWidth() {
			return Math.max(20, Math.min(400, getWidth() - 16));
		}

		@Override
		public void setSelected(PlaneRow row) {
			super.setSelected(row);
			selectedId = row == null ? null : row.plane.id();
			manageButton.active = row != null;
			deleteButton.active = row != null;
		}
	}

	private final class PlaneRow extends ObjectSelectionList.Entry<PlaneRow> {
		private final PlaneDefinition plane;

		PlaneRow(PlaneDefinition plane) {
			this.plane = plane;
		}

		@Override
		public Component getNarration() {
			return Component.literal(plane.name()).append(" ")
					.append(Component.translatable("planes.manager.content_count", plane.content().size()));
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.text(font, font.plainSubstrByWidth(plane.name(), getContentWidth()),
					getContentX(), getContentY() + 2, 0xFFFFFFFF);
			graphics.text(font, Component.translatable("planes.manager.content_count", plane.content().size()),
					getContentX(), getContentY() + 15, 0xFFAAAAAA);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, getNarration(), mouseX, mouseY);
			}
		}

		
	}

}
