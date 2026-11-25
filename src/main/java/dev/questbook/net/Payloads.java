package dev.questbook.net;

import dev.questbook.server.QuestServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Payloads {
	public static final String MOD = "questbook";
	private static final int S2C_MAX = 900_000;
	private static final int C2S_MAX = 30_000;

	private Payloads() {}

	private static <T extends CustomPayload> CustomPayload.Id<T> id(String name) {
		return new CustomPayload.Id<>(Identifier.of(MOD, name));
	}

	// ---------------- S2C ----------------
	public record SyncChapter(String json) implements CustomPayload {
		public static final Id<SyncChapter> ID = id("sync_chapter");
		public static final PacketCodec<RegistryByteBuf, SyncChapter> CODEC =
			PacketCodec.tuple(PacketCodecs.string(S2C_MAX), SyncChapter::json, SyncChapter::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** Ids dos capítulos, separados por vírgula, na ordem correta. */
	public record SyncOrder(String ids) implements CustomPayload {
		public static final Id<SyncOrder> ID = id("sync_order");
		public static final PacketCodec<RegistryByteBuf, SyncOrder> CODEC =
			PacketCodec.tuple(PacketCodecs.string(S2C_MAX), SyncOrder::ids, SyncOrder::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record SyncProgress(String json, boolean admin, boolean full) implements CustomPayload {
		public static final Id<SyncProgress> ID = id("sync_progress");
		public static final PacketCodec<RegistryByteBuf, SyncProgress> CODEC = PacketCodec.tuple(
			PacketCodecs.string(S2C_MAX), SyncProgress::json,
			PacketCodecs.BOOLEAN, SyncProgress::admin,
			PacketCodecs.BOOLEAN, SyncProgress::full,
			SyncProgress::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	/** kind: 0 concluída, 1 recompensa, 2 desbloqueada. */
	public record Toast(int kind, String title, String icon) implements CustomPayload {
		public static final Id<Toast> ID = id("toast");
		public static final PacketCodec<RegistryByteBuf, Toast> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, Toast::kind,
			PacketCodecs.string(200), Toast::title,
			PacketCodecs.string(200), Toast::icon,
			Toast::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	// ---------------- C2S ----------------
	public record Claim(String questId) implements CustomPayload {
		public static final Id<Claim> ID = id("claim");
		public static final PacketCodec<RegistryByteBuf, Claim> CODEC =
			PacketCodec.tuple(PacketCodecs.string(64), Claim::questId, Claim::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record ClaimAll() implements CustomPayload {
		public static final Id<ClaimAll> ID = id("claim_all");
		public static final PacketCodec<RegistryByteBuf, ClaimAll> CODEC = PacketCodec.unit(new ClaimAll());
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record Refresh() implements CustomPayload {
		public static final Id<Refresh> ID = id("refresh");
		public static final PacketCodec<RegistryByteBuf, Refresh> CODEC = PacketCodec.unit(new Refresh());
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record Submit(String questId, String objectiveId) implements CustomPayload {
		public static final Id<Submit> ID = id("submit");
		public static final PacketCodec<RegistryByteBuf, Submit> CODEC = PacketCodec.tuple(
			PacketCodecs.string(64), Submit::questId,
			PacketCodecs.string(64), Submit::objectiveId,
			Submit::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public record Edit(String action, String json) implements CustomPayload {
		public static final Id<Edit> ID = id("edit");
		public static final PacketCodec<RegistryByteBuf, Edit> CODEC = PacketCodec.tuple(
			PacketCodecs.string(32), Edit::action,
			PacketCodecs.string(C2S_MAX), Edit::json,
			Edit::new);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	public static final int MAX_EDIT_BYTES = C2S_MAX;

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SyncChapter.ID, SyncChapter.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncOrder.ID, SyncOrder.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncProgress.ID, SyncProgress.CODEC);
		PayloadTypeRegistry.playS2C().register(Toast.ID, Toast.CODEC);
		PayloadTypeRegistry.playC2S().register(Claim.ID, Claim.CODEC);
		PayloadTypeRegistry.playC2S().register(ClaimAll.ID, ClaimAll.CODEC);
		PayloadTypeRegistry.playC2S().register(Refresh.ID, Refresh.CODEC);
		PayloadTypeRegistry.playC2S().register(Submit.ID, Submit.CODEC);
		PayloadTypeRegistry.playC2S().register(Edit.ID, Edit.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Claim.ID, (p, ctx) -> QuestServer.claim(ctx.player(), p.questId()));
		ServerPlayNetworking.registerGlobalReceiver(ClaimAll.ID, (p, ctx) -> QuestServer.claimAll(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Refresh.ID, (p, ctx) -> QuestServer.sendFullProgress(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Submit.ID, (p, ctx) -> QuestServer.submit(ctx.player(), p.questId(), p.objectiveId()));
		ServerPlayNetworking.registerGlobalReceiver(Edit.ID, (p, ctx) -> QuestServer.edit(ctx.player(), p.action(), p.json()));
	}
}
