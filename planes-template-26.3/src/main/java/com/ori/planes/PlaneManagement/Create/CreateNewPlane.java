/**
 * @ Author: Lindsay Barton
 * @ Create Time: 2026-10-18 12:49:18
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 09:43:43
 * @ Description: Class for creating a new plane.
 */



package com.ori.planes.PlaneManagement.Create;

import java.util.function.Predicate;

import com.ori.planes.PlaneContentScreen;
import com.ori.planes.PlaneManagement.PlaneDefinition;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

public class CreateNewPlane extends PlaneContentScreen {
	public CreateNewPlane(CreateWorldScreen worldCreation, PlaneDefinition existing,
			Predicate<String> nameAvailable, Predicate<PlaneDefinition> onSave) {
		super(worldCreation, existing, nameAvailable, onSave);
	}

	//call Plane creation logic - output as a JSON datapack
	public void createPlane(PlaneDefinition plane) {
		// Implement the logic to create a new plane and output it as a JSON datapack
		System.out.println("Hi there! Creating a new plane...");
	}


}
