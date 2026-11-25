package dev.questbook.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public final class Commands {
	private Commands() {}

	public static void register() {
		SuggestionProvider<ServerCommandSource> quests = (ctx, b) -> CommandSource.suggestMatching(QuestServer.questIds(), b);
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
			CommandManager.literal("questbook")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.literal("reload").executes(c -> {
					QuestServer.reload();
					c.getSource().sendFeedback(() -> Text.literal("Quest Book recarregado."), true);
					return 1;
				}))
				.then(CommandManager.literal("reset")
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.executes(c -> reset(c.getSource(), EntityArgumentType.getPlayers(c, "players"), null))
						.then(CommandManager.argument("quest", StringArgumentType.word()).suggests(quests)
							.executes(c -> reset(c.getSource(), EntityArgumentType.getPlayers(c, "players"),
								StringArgumentType.getString(c, "quest"))))))
				.then(CommandManager.literal("complete")
					.then(CommandManager.argument("players", EntityArgumentType.players())
						.then(CommandManager.argument("quest", StringArgumentType.word()).suggests(quests)
							.executes(c -> {
								String id = StringArgumentType.getString(c, "quest");
								int n = 0;
								for (ServerPlayerEntity p : EntityArgumentType.getPlayers(c, "players")) {
									if (QuestServer.forceComplete(p, id)) n++;
								}
								final int count = n;
								c.getSource().sendFeedback(() -> Text.literal("Missão concluída para " + count + " jogador(es)."), true);
								return n;
							}))))));
	}

	private static int reset(ServerCommandSource src, Collection<ServerPlayerEntity> players, String quest) {
		for (ServerPlayerEntity p : players) QuestServer.reset(p, quest);
		int n = players.size();
		src.sendFeedback(() -> Text.literal("Progresso reiniciado para " + n + " jogador(es)."), true);
		return n;
	}
}
