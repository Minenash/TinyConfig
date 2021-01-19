package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {

	KeyBinding keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding("test", GLFW.GLFW_KEY_G, "cat"));

	@Override
	public void onInitialize() {

		ExampleConfig.init("example_modid", ExampleConfig.class);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (keyBinding.wasPressed())
				client.openScreen(new ExampleConfig().getScreen(null));
		});

	}
}
