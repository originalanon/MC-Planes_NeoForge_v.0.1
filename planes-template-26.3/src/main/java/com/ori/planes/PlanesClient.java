/**
 * @ Author: OriginalAnon (Lindsay Barton)
 * @ Create Time: 2026-10-18 06:19:19
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 09:43:48
 * @ Description: Main client class for the Planes mod.
 */




package com.ori.planes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.ori.planes.PlaneManagement.Manage.ManagePlanes;
import com.ori.planes.PlaneManagement.PlaneDefinition;
import com.ori.planes.PlaneManagement.PlaneDatapackGenerator;
import com.ori.planes.PlaneManagement.PlaneDefinitionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Planes.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Planes.MODID, value = Dist.CLIENT)
public class PlanesClient {
    private static final Map<CreateWorldScreen, List<PlaneDefinition>> PLANE_DRAFTS = new WeakHashMap<>();

    public PlanesClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) {
            return;
        }
        Button create = null;
        Button cancel = null;
        for (var listener : event.getListenersList()) {
            if (listener instanceof Button button) {
                if (button.getMessage().equals(Component.translatable("selectWorld.create"))) {
                    create = button;
                } else if (button.getMessage().equals(Component.translatable("gui.cancel"))) {
                    cancel = button;
                }
            }
        }
        if (create == null || cancel == null) {
            return;
        }
        int left = screen.width / 2 - 154;
        create.setX(left);
        create.setWidth(98);
        cancel.setX(left + 212);
        cancel.setWidth(98);
        event.addListener(Button.builder(Component.translatable("planes.menu.open"), button ->
            Minecraft.getInstance().gui.pushScreenLayer(new ManagePlanes(screen,
                PLANE_DRAFTS.computeIfAbsent(screen, key -> loadPlaneDefinitions(screen)))))
                .bounds(left + 106, create.getY(), 98, 20).build());
        arrangePlaneButton(screen);
    }

    private static List<PlaneDefinition> loadPlaneDefinitions(CreateWorldScreen screen) {
        try {
            List<PlaneDefinition> definitions = new ArrayList<>(
                    PlaneDefinitionStore.loadAll(Minecraft.getInstance().gameDirectory.toPath()));
            for (PlaneDefinition definition : definitions) {
                try {
                    PlaneDatapackGenerator.generate(screen, definition);
                } catch (IOException exception) {
                    Planes.LOGGER.error("Could not stage datapack for saved plane {}", definition.id(), exception);
                }
            }
            return definitions;
        } catch (IOException exception) {
            Planes.LOGGER.error("Could not load saved plane definitions", exception);
            return new ArrayList<>();
        }
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreateWorldScreen screen) {
            arrangePlaneButton(screen);
        }
    }

    private static void arrangePlaneButton(CreateWorldScreen screen) {
        Button create = null;
        Button cancel = null;
        Button planes = null;
        for (var listener : screen.children()) {
            if (listener instanceof Button button) {
                if (button.getMessage().equals(Component.translatable("selectWorld.create"))) {
                    create = button;
                } else if (button.getMessage().equals(Component.translatable("gui.cancel"))) {
                    cancel = button;
                } else if (button.getMessage().equals(Component.translatable("planes.menu.open"))) {
                    planes = button;
                }
            }
        }
        if (create != null && cancel != null && planes != null) {
            int totalWidth = Math.min(310, screen.width - 16);
            int buttonWidth = (totalWidth - 16) / 3;
            int left = (screen.width - totalWidth) / 2;
            create.setX(left);
            create.setWidth(buttonWidth);
            planes.setX(left + buttonWidth + 8);
            planes.setY(create.getY());
            planes.setWidth(buttonWidth);
            cancel.setX(left + (buttonWidth + 8) * 2);
            cancel.setWidth(buttonWidth);
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Planes.LOGGER.info("HELLO FROM CLIENT SETUP");
        Planes.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
