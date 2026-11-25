package dev.questbook;

import dev.questbook.net.Payloads;
import dev.questbook.server.Commands;
import dev.questbook.server.QuestServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestBookMod implements ModInitializer {
	public static final String ID = "questbook";
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	@Override
	public void onInitialize() {
		Payloads.register();
		Commands.register();

		ServerLifecycleEvents.SERVER_STARTING.register(QuestServer::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(QuestServer::stop);
		ServerTickEvents.END_SERVER_TICK.register(QuestServer::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> QuestServer.join(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QuestServer.leave(handler.player));

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity sp) QuestServer.onKill(sp, entity);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp) {
				QuestServer.onInteractBlock(sp, world.getBlockState(hit.getBlockPos()));
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp) {
				QuestServer.onInteractEntity(sp, entity);
			}
			return ActionResult.PASS;
		});
		LOG.info("Quest Book carregado.");
	}
}
