package dev.questbook.client;

import dev.questbook.client.screen.QuestBookScreen;
import dev.questbook.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class QuestBookClient implements ClientModInitializer {
	/** Configurável em Opções > Controles > Quest Book. */
	public static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.questbook.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.questbook"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) client.setScreen(new QuestBookScreen());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncChapter.ID, (p, ctx) -> ClientData.onChapter(p.json()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncOrder.ID, (p, ctx) -> ClientData.onOrder(p.ids()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SyncProgress.ID,
			(p, ctx) -> ClientData.onProgress(p.json(), p.admin(), p.full()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Toast.ID,
			(p, ctx) -> Toasts.fromServer(p.kind(), p.title(), p.icon()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientData.clear();
			Toasts.clear();
		});

		HudRenderCallback.EVENT.register((ctx, tickCounter) -> Toasts.render(ctx));
	}
}
