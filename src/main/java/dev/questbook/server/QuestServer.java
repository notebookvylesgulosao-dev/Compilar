package dev.questbook.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.questbook.QuestBookMod;
import dev.questbook.data.*;
import dev.questbook.net.Payloads.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.structure.Structure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** Estado e lógica do lado do servidor: livro, progresso individual, rastreamento, recompensas e edição. */
public final class QuestServer {
	private static MinecraftServer server;
	private static Path dir;
	private static QuestBook book = new QuestBook();
	private static final Map<UUID, PlayerProgress> PROGRESS = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();
	private static final Map<UUID, Set<String>> CHANGED = new HashMap<>();
	private static int ticks;

	private QuestServer() {}

	public static QuestBook book() { return book; }

	// ================= ciclo de vida =================
	public static void start(MinecraftServer s) {
		server = s;
		dir = s.getSavePath(WorldSavePath.ROOT).resolve("questbook").normalize();
		PROGRESS.clear();
		DIRTY.clear();
		CHANGED.clear();
		loadBook();
	}

	public static void stop(MinecraftServer s) {
		saveAllProgress();
		PROGRESS.clear();
		DIRTY.clear();
		CHANGED.clear();
		server = null;
	}

	public static void reload() {
		if (server == null) return;
		loadBook();
		broadcastAll();
	}

	private static Path bookFile() { return dir.resolve("book.json"); }

	private static Path progressFile(UUID id) { return dir.resolve("progress").resolve(id + ".json"); }

	private static void loadBook() {
		Path f = bookFile();
		QuestBook b = null;
		if (Files.exists(f)) {
			try {
				b = Json.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), QuestBook.class);
			} catch (Exception e) {
				QuestBookMod.LOG.error("book.json inválido, usando backup .corrupt e livro padrão", e);
				try { Files.copy(f, f.resolveSibling("book.json.corrupt"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
			}
		}
		if (b == null) {
			b = DefaultBook.create();
			book = b;
			saveBook();
		} else {
			b.sanitize();
			book = b;
		}
	}

	private static void saveBook() {
		write(bookFile(), Json.PRETTY.toJson(book));
	}

	private static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			QuestBookMod.LOG.error("Falha ao salvar " + file, e);
		}
	}

	private static PlayerProgress prog(UUID id) {
		return PROGRESS.computeIfAbsent(id, u -> {
			Path f = progressFile(u);
			if (Files.exists(f)) {
				try {
					PlayerProgress p = Json.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PlayerProgress.class);
					if (p != null && p.quests != null) return p;
				} catch (Exception e) {
					QuestBookMod.LOG.error("Progresso inválido de " + u, e);
				}
			}
			return new PlayerProgress();
		});
	}

	private static void saveProgress(UUID id) {
		PlayerProgress p = PROGRESS.get(id);
		if (p != null) write(progressFile(id), Json.GSON.toJson(p));
		DIRTY.remove(id);
	}

	private static void saveAllProgress() {
		for (UUID id : new ArrayList<>(DIRTY)) saveProgress(id);
	}

	private static void touch(UUID id, String questId) {
		DIRTY.add(id);
		CHANGED.computeIfAbsent(id, k -> new HashSet<>()).add(questId);
	}

	public static boolean isAdmin(ServerPlayerEntity p) {
		return p.hasPermissionLevel(2) || (server != null && server.isHost(p.getGameProfile()));
	}

	// ================= sincronização =================
	public static void join(ServerPlayerEntity p) {
		if (server == null) return;
		sendBook(p);
		sendFullProgress(p);
	}

	public static void leave(ServerPlayerEntity p) {
		saveProgress(p.getUuid());
		CHANGED.remove(p.getUuid());
	}

	private static void sendBook(ServerPlayerEntity p) {
		for (Chapter c : book.chapters) ServerPlayNetworking.send(p, new SyncChapter(Json.GSON.toJson(c)));
		ServerPlayNetworking.send(p, new SyncOrder(orderString()));
	}

	private static String orderString() {
		StringBuilder sb = new StringBuilder();
		for (Chapter c : book.chapters) {
			if (sb.length() > 0) sb.append(',');
			sb.append(c.id);
		}
		return sb.toString();
	}

	public static void sendFullProgress(ServerPlayerEntity p) {
		if (server == null) return;
		ServerPlayNetworking.send(p, new SyncProgress(Json.GSON.toJson(prog(p.getUuid()).quests), isAdmin(p), true));
	}

	private static void broadcastChapter(Chapter c) {
		SyncChapter pkt = new SyncChapter(Json.GSON.toJson(c));
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(p, pkt);
	}

	private static void broadcastOrder() {
		SyncOrder pkt = new SyncOrder(orderString());
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(p, pkt);
	}

	private static void broadcastAll() {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendBook(p);
			sendFullProgress(p);
		}
	}

	private static void flush() {
		for (Map.Entry<UUID, Set<String>> e : CHANGED.entrySet()) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
			if (p == null) continue;
			PlayerProgress pp = prog(e.getKey());
			JsonObject o = new JsonObject();
			for (String id : e.getValue()) {
				QuestState s = pp.peek(id);
				if (s != null) o.add(id, Json.GSON.toJsonTree(s));
			}
			ServerPlayNetworking.send(p, new SyncProgress(o.toString(), isAdmin(p), false));
		}
		CHANGED.clear();
	}

	private static void toast(ServerPlayerEntity p, int kind, Quest q) {
		ServerPlayNetworking.send(p, new Toast(kind, q.title, q.icon));
	}

	// ================= tick / rastreamento =================
	public static void tick(MinecraftServer s) {
		if (server == null) return;
		ticks++;
		if (ticks % 20 == 0) {
			for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
				try { scan(p); } catch (Exception e) { QuestBookMod.LOG.error("Erro no scan de missões", e); }
			}
		}
		if (ticks % 10 == 0 && !CHANGED.isEmpty()) flush();
		if (ticks % 1200 == 0) saveAllProgress();
	}

	private static void scan(ServerPlayerEntity p) {
		PlayerProgress pp = prog(p.getUuid());
		ServerWorld w = p.getServerWorld();
		BlockPos pos = p.getBlockPos();
		for (Chapter c : new ArrayList<>(book.chapters)) {
			for (Quest q : c.quests) {
				QuestState st = pp.peek(q.id);
				if (st != null && st.done) continue;
				if (!book.available(pp, q)) continue;
				if (q.objectives.isEmpty()) { after(p, pp, q); continue; }
				boolean touched = false;
				for (Objective o : q.objectives) {
					int req = o.required(), cur = pp.get(q.id, o.id);
					if (cur >= req) continue;
					int v = switch (o.type) {
						case COLLECT -> countItems(p, o.target);
						case BIOME -> inBiome(w, pos, o.target) ? 1 : 0;
						case STRUCTURE -> inStructure(w, pos, o.target) ? 1 : 0;
						case DIMENSION -> w.getRegistryKey().getValue().toString().equals(o.target) ? 1 : 0;
						case LOCATION -> atLocation(w, p, o) ? 1 : 0;
						default -> 0;
					};
					if (v > cur) {
						pp.set(q.id, o.id, Math.min(req, v));
						touched = true;
					}
				}
				if (touched) after(p, pp, q);
			}
		}
	}

	private static int countItems(ServerPlayerEntity p, String target) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (Matchers.item(s, target)) n += s.getCount();
		}
		return n;
	}

	private static boolean inBiome(ServerWorld w, BlockPos pos, String t) {
		RegistryEntry<Biome> e = w.getBiome(pos);
		if (t.startsWith("#")) {
			Identifier id = Identifier.tryParse(t.substring(1));
			return id != null && e.isIn(TagKey.of(RegistryKeys.BIOME, id));
		}
		Identifier id = Identifier.tryParse(t);
		return id != null && e.matchesId(id);
	}

	private static boolean inStructure(ServerWorld w, BlockPos pos, String t) {
		try {
			StructureAccessor sa = w.getStructureAccessor();
			StructureStart start;
			if (t.startsWith("#")) {
				Identifier id = Identifier.tryParse(t.substring(1));
				if (id == null) return false;
				start = sa.getStructureContaining(pos, TagKey.of(RegistryKeys.STRUCTURE, id));
			} else {
				Identifier id = Identifier.tryParse(t);
				Structure st = id == null ? null : w.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).get(id);
				if (st == null) return false;
				start = sa.getStructureContaining(pos, st);
			}
			return start != null && start.hasChildren();
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean atLocation(ServerWorld w, ServerPlayerEntity p, Objective o) {
		if (!o.extra.isEmpty() && !w.getRegistryKey().getValue().toString().equals(o.extra)) return false;
		try {
			String[] a = o.target.split(",");
			Vec3d t = new Vec3d(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
			double r = Math.max(1, o.count);
			return p.getPos().squaredDistanceTo(t) <= r * r;
		} catch (Exception e) {
			return false;
		}
	}

	// ================= eventos =================
	public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
		if (server == null) return;
		bump(p, ObjectiveType.KILL, o -> Matchers.entity(victim.getType(), o.target), 1);
	}

	public static void onCraft(ServerPlayerEntity p, ItemStack stack, int amount) {
		if (server == null) return;
		bump(p, ObjectiveType.CRAFT, o -> Matchers.item(stack, o.target), amount);
	}

	public static void onInteractBlock(ServerPlayerEntity p, BlockState state) {
		if (server == null) return;
		bump(p, ObjectiveType.INTERACT_BLOCK, o -> Matchers.block(state, o.target), 1);
	}

	public static void onInteractEntity(ServerPlayerEntity p, Entity e) {
		if (server == null) return;
		bump(p, ObjectiveType.INTERACT_ENTITY, o -> Matchers.entity(e.getType(), o.target), 1);
	}

	private static void bump(ServerPlayerEntity p, ObjectiveType type, Predicate<Objective> match, int amount) {
		PlayerProgress pp = prog(p.getUuid());
		for (Chapter c : book.chapters) {
			for (Quest q : c.quests) {
				QuestState st = pp.peek(q.id);
				if (st != null && st.done) continue;
				Boolean avail = null;
				boolean touched = false;
				for (Objective o : q.objectives) {
					if (o.type != type || !match.test(o)) continue;
					if (avail == null) avail = book.available(pp, q);
					if (!avail) break;
					int cur = pp.get(q.id, o.id), req = o.required();
					if (cur >= req) continue;
					pp.set(q.id, o.id, Math.min(req, cur + amount));
					touched = true;
				}
				if (touched) after(p, pp, q);
			}
		}
	}

	private static boolean allDone(PlayerProgress pp, Quest q) {
		for (Objective o : q.objectives) if (pp.get(q.id, o.id) < o.required()) return false;
		return true;
	}

	private static void after(ServerPlayerEntity p, PlayerProgress pp, Quest q) {
		QuestState st = pp.state(q.id);
		touch(p.getUuid(), q.id);
		if (!st.done && allDone(pp, q)) {
			st.done = true;
			if (q.rewards.isEmpty()) st.claimed = true;
			toast(p, 0, q);
			int shown = 0;
			for (Chapter c : book.chapters) {
				for (Quest d : c.quests) {
					if (shown < 2 && d.dependencies.contains(q.id) && !pp.done(d.id) && book.available(pp, d)) {
						toast(p, 2, d);
						shown++;
					}
				}
			}
		}
	}

	// ================= entrega / recompensas =================
	public static void submit(ServerPlayerEntity p, String qid, String oid) {
		if (server == null) return;
		Quest q = book.quest(qid);
		if (q == null) return;
		PlayerProgress pp = prog(p.getUuid());
		QuestState st = pp.peek(qid);
		if ((st != null && st.done) || !book.available(pp, q)) return;
		for (Objective o : q.objectives) {
			if (!o.id.equals(oid) || o.type != ObjectiveType.SUBMIT) continue;
			int need = o.required() - pp.get(qid, oid);
			int taken = 0;
			var inv = p.getInventory();
			for (int i = 0; i < 36 && need > 0; i++) {
				ItemStack s = inv.getStack(i);
				if (!Matchers.item(s, o.target)) continue;
				int n = Math.min(need, s.getCount());
				s.decrement(n);
				need -= n;
				taken += n;
			}
			if (taken > 0) {
				pp.set(qid, oid, pp.get(qid, oid) + taken);
				inv.markDirty();
				after(p, pp, q);
			}
			return;
		}
	}

	public static void claim(ServerPlayerEntity p, String qid) {
		if (server == null) return;
		Quest q = book.quest(qid);
		if (q == null) return;
		if (claimQuest(p, prog(p.getUuid()), q)) toast(p, 1, q);
	}

	public static void claimAll(ServerPlayerEntity p) {
		if (server == null) return;
		PlayerProgress pp = prog(p.getUuid());
		int n = 0;
		Quest last = null;
		for (Chapter c : book.chapters) {
			for (Quest q : c.quests) {
				if (claimQuest(p, pp, q)) { n++; last = q; }
			}
		}
		if (n == 1) toast(p, 1, last);
		else if (n > 1) ServerPlayNetworking.send(p, new Toast(1, n + "", "minecraft:chest"));
	}

	private static boolean claimQuest(ServerPlayerEntity p, PlayerProgress pp, Quest q) {
		QuestState st = pp.peek(q.id);
		if (st == null || !st.done || st.claimed || q.rewards.isEmpty()) return false;
		st.claimed = true;
		touch(p.getUuid(), q.id);
		for (Reward r : q.rewards) give(p, r);
		return true;
	}

	private static void give(ServerPlayerEntity p, Reward r) {
		try {
			switch (r.type) {
				case ITEM -> {
					Identifier id = Identifier.tryParse(r.value);
					Optional<Item> item = id == null ? Optional.empty() : Registries.ITEM.getOptionalValue(id);
					if (item.isEmpty()) return;
					int left = r.amount;
					while (left > 0) {
						int n = Math.min(left, Math.max(1, item.get().getMaxCount()));
						p.getInventory().offerOrDrop(new ItemStack(item.get(), n));
						left -= n;
					}
				}
				case XP_POINTS -> p.addExperience(r.amount);
				case XP_LEVELS -> p.addExperienceLevels(r.amount);
				case COMMAND -> {
					ServerCommandSource src = p.getCommandSource().withLevel(4).withSilent();
					server.getCommandManager().executeWithPrefix(src, r.value.replace("%player%", p.getGameProfile().getName()));
				}
			}
		} catch (Exception e) {
			QuestBookMod.LOG.error("Falha ao entregar recompensa " + r.type + " " + r.value, e);
		}
	}

	// ================= administração (comandos) =================
	public static void reset(ServerPlayerEntity p, String questId) {
		PlayerProgress pp = prog(p.getUuid());
		if (questId == null) pp.quests.clear();
		else pp.quests.remove(questId);
		DIRTY.add(p.getUuid());
		sendFullProgress(p);
	}

	public static boolean forceComplete(ServerPlayerEntity p, String questId) {
		Quest q = book.quest(questId);
		if (q == null) return false;
		PlayerProgress pp = prog(p.getUuid());
		for (Objective o : q.objectives) pp.set(q.id, o.id, o.required());
		after(p, pp, q);
		return true;
	}

	public static List<String> questIds() {
		List<String> l = new ArrayList<>();
		for (Chapter c : book.chapters) for (Quest q : c.quests) l.add(q.id);
		return l;
	}

	// ================= editor (somente administradores) =================
	public static void edit(ServerPlayerEntity p, String action, String json) {
		if (server == null) return;
		if (!isAdmin(p)) {
			p.sendMessage(Text.literal("§cVocê não tem permissão para editar o Quest Book."), false);
			sendFullProgress(p);
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(json.isEmpty() ? "{}" : json);
			JsonObject o = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
			switch (action) {
				case "CHAPTER_SAVE" -> chapterSave(Json.GSON.fromJson(o, Chapter.class));
				case "CHAPTER_DELETE" -> chapterDelete(str(o, "id"));
				case "CHAPTER_MOVE" -> chapterMove(str(o, "id"), o.has("dir") ? o.get("dir").getAsInt() : 0);
				case "QUEST_SAVE" -> questSave(str(o, "c"), Json.GSON.fromJson(o.get("q"), Quest.class));
				case "QUEST_DELETE" -> questDelete(str(o, "id"));
				case "QUEST_MOVE" -> questMove(str(o, "id"), o.get("x").getAsDouble(), o.get("y").getAsDouble());
				default -> { return; }
			}
			book.reindex();
			saveBook();
		} catch (Exception e) {
			QuestBookMod.LOG.warn("Edição inválida de {}: {}", p.getGameProfile().getName(), e.toString());
		}
	}

	private static String str(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

	private static void chapterSave(Chapter in) {
		if (in == null) return;
		Chapter ex = in.id == null ? null : book.chapter(in.id);
		if (ex == null) {
			in.id = "c" + QuestBook.newId();
			in.quests = new ArrayList<>();
			book.cleanChapter(in);
			book.chapters.add(in);
			ex = in;
		} else {
			ex.title = in.title;
			ex.icon = in.icon;
			ex.description = in.description;
			book.cleanChapter(ex);
		}
		broadcastChapter(ex);
		broadcastOrder();
	}

	private static void chapterDelete(String id) {
		Chapter c = book.chapter(id);
		if (c == null) return;
		book.chapters.remove(c);
		book.reindex();
		for (Chapter ch : book.chapters) for (Quest q : ch.quests) book.cleanQuest(q);
		broadcastAll();
	}

	private static void chapterMove(String id, int dir) {
		Chapter c = book.chapter(id);
		if (c == null) return;
		int i = book.chapters.indexOf(c), j = i + (dir < 0 ? -1 : 1);
		if (j < 0 || j >= book.chapters.size()) return;
		Collections.swap(book.chapters, i, j);
		broadcastOrder();
	}

	private static void questSave(String chapterId, Quest in) {
		Chapter target = book.chapter(chapterId);
		if (target == null || in == null) return;
		Quest ex = in.id == null || in.id.isBlank() ? null : book.quest(in.id);
		Chapter oldCh = null;
		if (ex == null) {
			do { in.id = QuestBook.newId(); } while (book.quest(in.id) != null);
			target.quests.add(in);
		} else {
			oldCh = book.chapterOf(ex.id);
			if (oldCh == target) {
				target.quests.set(target.quests.indexOf(ex), in);
			} else {
				oldCh.quests.remove(ex);
				target.quests.add(in);
			}
		}
		book.reindex();
		book.cleanQuest(in);
		broadcastChapter(target);
		if (oldCh != null && oldCh != target) broadcastChapter(oldCh);
	}

	private static void questDelete(String id) {
		Chapter c = book.chapterOf(id);
		Quest q = book.quest(id);
		if (c == null || q == null) return;
		c.quests.remove(q);
		book.reindex();
		for (Chapter ch : book.chapters) for (Quest x : ch.quests) book.cleanQuest(x);
		broadcastAll();
	}

	private static void questMove(String id, double x, double y) {
		Quest q = book.quest(id);
		if (q == null) return;
		q.x = x;
		q.y = y;
		book.cleanQuest(q);
		broadcastChapter(book.chapterOf(id));
	}
}
