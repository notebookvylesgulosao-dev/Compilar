package dev.questbook.client;

import com.google.gson.JsonObject;
import dev.questbook.data.*;
import dev.questbook.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cópia local (cliente) do livro e do progresso do jogador, sincronizados pelo servidor. */
public final class ClientData {
	public enum Status { LOCKED, AVAILABLE, STARTED, DONE, CLAIMED }

	public static QuestBook book = new QuestBook();
	public static PlayerProgress progress = new PlayerProgress();
	public static boolean admin;
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private ClientData() {}

	public static void clear() {
		book = new QuestBook();
		progress = new PlayerProgress();
		admin = false;
	}

	// ---------- recebimento ----------
	public static void onChapter(String json) {
		Chapter in = Json.parse(json, Chapter.class);
		if (in == null || in.id == null) return;
		if (in.quests == null) in.quests = new ArrayList<>();
		for (Quest q : in.quests) {
			if (q.dependencies == null) q.dependencies = new ArrayList<>();
			if (q.objectives == null) q.objectives = new ArrayList<>();
			if (q.rewards == null) q.rewards = new ArrayList<>();
		}
		for (int i = 0; i < book.chapters.size(); i++) {
			if (book.chapters.get(i).id.equals(in.id)) {
				book.chapters.set(i, in);
				book.reindex();
				return;
			}
		}
		book.chapters.add(in);
		book.reindex();
	}

	public static void onOrder(String ids) {
		List<String> order = ids.isEmpty() ? List.of() : Arrays.asList(ids.split(","));
		List<Chapter> sorted = new ArrayList<>();
		for (String id : order) {
			Chapter c = book.chapter(id);
			if (c != null) sorted.add(c);
		}
		book.chapters = sorted;
		book.reindex();
	}

	public static void onProgress(String json, boolean isAdmin, boolean full) {
		admin = isAdmin;
		Map<String, QuestState> m = Json.GSON.fromJson(json, Json.STATE_MAP);
		if (full) progress.quests.clear();
		if (m != null) progress.quests.putAll(m);
	}

	// ---------- consultas ----------
	public static List<Chapter> chapters() { return book.chapters; }

	public static Quest quest(String id) { return book.quest(id); }

	public static boolean available(Quest q) { return book.available(progress, q); }

	public static Status status(Quest q) {
		QuestState s = progress.peek(q.id);
		if (s != null && s.done) return s.claimed || q.rewards.isEmpty() ? Status.CLAIMED : Status.DONE;
		if (!available(q)) return Status.LOCKED;
		for (Objective o : q.objectives) if (progress.get(q.id, o.id) > 0) return Status.STARTED;
		return Status.AVAILABLE;
	}

	public static boolean claimable(Quest q) {
		QuestState s = progress.peek(q.id);
		return s != null && s.done && !s.claimed && !q.rewards.isEmpty();
	}

	public static int claimableCount() {
		int n = 0;
		for (Chapter c : book.chapters) for (Quest q : c.quests) if (claimable(q)) n++;
		return n;
	}

	public static int[] chapterCounts(Chapter c) {
		int done = 0;
		for (Quest q : c.quests) if (progress.done(q.id)) done++;
		return new int[]{done, c.quests.size()};
	}

	public static float questFraction(Quest q) {
		if (progress.done(q.id)) return 1f;
		if (q.objectives.isEmpty()) return 0f;
		float sum = 0;
		for (Objective o : q.objectives) sum += Math.min(1f, progress.get(q.id, o.id) / (float) o.required());
		return sum / q.objectives.size();
	}

	public static ItemStack stack(String id) {
		return STACKS.computeIfAbsent(id == null ? "" : id, k -> {
			Identifier i = Identifier.tryParse(k.startsWith("#") ? k.substring(1) : k);
			if (i == null || k.startsWith("#")) return new ItemStack(Items.BARRIER);
			return Registries.ITEM.getOptionalValue(i).map(ItemStack::new).orElse(new ItemStack(Items.BARRIER));
		});
	}

	// ---------- envio ----------
	public static void send(net.minecraft.network.packet.CustomPayload p) {
		if (ClientPlayNetworking.canSend(p.getId())) ClientPlayNetworking.send(p);
	}

	public static void claim(Quest q) { send(new Payloads.Claim(q.id)); }

	public static void claimAll() { send(new Payloads.ClaimAll()); }

	public static void submit(Quest q, Objective o) { send(new Payloads.Submit(q.id, o.id)); }

	public static boolean edit(String action, String json) {
		if (json.getBytes(StandardCharsets.UTF_8).length > Payloads.MAX_EDIT_BYTES - 200) {
			Toasts.push(Lang.t("Dados grandes demais", "Data too large"), Lang.t("Reduza descrição/objetivos.", "Reduce description/objectives."), "minecraft:barrier", Ui0.RED);
			return false;
		}
		send(new Payloads.Edit(action, json));
		return true;
	}

	public static void saveQuest(String chapterId, Quest q) {
		JsonObject o = new JsonObject();
		o.addProperty("c", chapterId);
		o.add("q", Json.GSON.toJsonTree(q));
		edit("QUEST_SAVE", o.toString());
	}

	public static void moveQuest(Quest q, double x, double y) {
		JsonObject o = new JsonObject();
		o.addProperty("id", q.id);
		o.addProperty("x", x);
		o.addProperty("y", y);
		edit("QUEST_MOVE", o.toString());
	}

	public static void deleteQuest(Quest q) {
		JsonObject o = new JsonObject();
		o.addProperty("id", q.id);
		edit("QUEST_DELETE", o.toString());
	}

	public static void saveChapter(Chapter c) {
		Chapter copy = new Chapter();
		copy.id = c.id;
		copy.title = c.title;
		copy.icon = c.icon;
		copy.description = c.description;
		edit("CHAPTER_SAVE", Json.GSON.toJson(copy));
	}

	public static void deleteChapter(Chapter c) {
		JsonObject o = new JsonObject();
		o.addProperty("id", c.id);
		edit("CHAPTER_DELETE", o.toString());
	}

	public static void moveChapter(Chapter c, int dir) {
		JsonObject o = new JsonObject();
		o.addProperty("id", c.id);
		o.addProperty("dir", dir);
		edit("CHAPTER_MOVE", o.toString());
	}

	/** Cores usadas em toasts (evita dependência cíclica com a UI). */
	static final class Ui0 {
		static final int RED = 0xFFFF6464;
	}
}
