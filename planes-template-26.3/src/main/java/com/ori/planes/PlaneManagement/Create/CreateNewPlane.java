/**
 * @ Author: Lindsay Barton
 * @ Create Time: 2026-10-18 12:49:18
 * @ Modified by: Your name
 * @ Modified time: 2026-10-18 13:08:56
 * @ Description: Class for creating a new plane.
 */



package com.ori.planes.PlaneManagement.Create;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.ori.planes.PlaneContentScreen;
import com.ori.planes.PlaneManagement.PlaneDefinition;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public class CreateNewPlane extends PlaneContentScreen {
	public CreateNewPlane(CreateWorldScreen worldCreation, PlaneDefinition existing,
			Predicate<String> nameAvailable, Consumer<PlaneDefinition> onSave) {
		super(worldCreation, existing, nameAvailable, onSave);
	}
}
