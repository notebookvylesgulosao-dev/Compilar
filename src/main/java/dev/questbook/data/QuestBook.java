package dev.questbook.data;

import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class QuestBook {
	public int version = 1;
	public List<Chapter> chapters = new ArrayList<>();

	private transient Map<String, Quest> index = new HashMap<>();
	private transient Map<String, Chapter> owner = new HashMap<>();

	// ---------- lookup ----------
	public void reindex() {
		index = new HashMap<>();
		owner = new HashMap<>();
		if (chapters == null) chapters = new ArrayList<>();
		for (Chapter c : chapters) {
			if (c.quests == null) c.quests = new ArrayList<>();
			for (Quest q : c.quests) {
				index.put(q.id, q);
				owner.put(q.id, c);
			}
		}
	}

	public Quest quest(String id) { return index.get(id); }

	public Chapter chapterOf(String questId) { return owner.get(questId); }

	public Chapter chapter(String id) {
		for (Chapter c : chapters) if (c.id.equals(id)) return c;
		return null;
	}

	public int totalQuests() { return index.size(); }

	/** Uma missão está disponível quando todas as dependências existentes foram concluídas. */
	public boolean available(PlayerProgress p, Quest q) {
		for (String d : q.dependencies) {
			if (index.containsKey(d) && !p.done(d)) return false;
		}
		return true;
	}

	/** true se a missão {@code questId} depende (direta ou indiretamente) de {@code target}. */
	public boolean dependsOn(String questId, String target) {
		return dependsOn(questId, target, new HashSet<>());
	}

	private boolean dependsOn(String qid, String target, Set<String> seen) {
		if (!seen.add(qid)) return false;
		Quest q = index.get(qid);
		if (q == null) return false;
		for (String d : q.dependencies) {
			if (d.equals(target) || dependsOn(d, target, seen)) return true;
		}
		return false;
	}

	// ---------- ids ----------
	public static String newId() { return String.format("%08x", ThreadLocalRandom.current().nextInt()); }

	public static String shortId() { return String.format("%04x", ThreadLocalRandom.current().nextInt(0x10000)); }

	public static String norm(String s) {
		if (s == null) return "";
		s = s.trim().toLowerCase(Locale.ROOT);
		boolean tag = s.startsWith("#");
		if (tag) s = s.substring(1);
		if (s.isEmpty()) return "";
		if (!s.contains(":")) s = "minecraft:" + s;
		if (Identifier.tryParse(s) == null) return "";
		return tag ? "#" + s : s;
	}

	private static String clip(String s, int max) {
		if (s == null) return "";
		s = s.strip();
		return s.length() > max ? s.substring(0, max) : s;
	}

	private static String slug(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_"); }

	// ---------- sanitização ----------
	public void sanitize() {
		if (chapters == null) chapters = new ArrayList<>();
		Set<String> chIds = new HashSet<>(), qIds = new HashSet<>();
		for (Chapter c : chapters) {
			c.id = slug(c.id);
			while (c.id.isEmpty() || !chIds.add(c.id)) c.id = "c" + newId();
			cleanChapter(c);
			for (Quest q : c.quests) {
				while (q.id == null || q.id.isBlank() || !qIds.add(q.id)) q.id = newId();
			}
		}
		reindex();
		for (Chapter c : chapters) for (Quest q : c.quests) cleanQuest(q);
	}

	public void cleanChapter(Chapter c) {
		c.title = clip(c.title, 60);
		if (c.title.isEmpty()) c.title = "Capítulo";
		c.description = clip(c.description, 600);
		c.icon = norm(c.icon);
		if (c.icon.isEmpty() || c.icon.startsWith("#")) c.icon = "minecraft:book";
		if (c.quests == null) c.quests = new ArrayList<>();
	}

	/** Requer que a missão já esteja no índice (reindex) para validar dependências. */
	public void cleanQuest(Quest q) {
		q.title = clip(q.title, 80);
		if (q.title.isEmpty()) q.title = "Sem título";
		q.description = clip(q.description, 1500);
		q.icon = norm(q.icon);
		if (q.icon.isEmpty() || q.icon.startsWith("#")) q.icon = "minecraft:book";
		if (!Double.isFinite(q.x)) q.x = 0;
		if (!Double.isFinite(q.y)) q.y = 0;
		q.x = Math.max(-5000, Math.min(5000, q.x));
		q.y = Math.max(-5000, Math.min(5000, q.y));
		// dependências
		List<String> deps = new ArrayList<>();
		if (q.dependencies != null) {
			for (String d : q.dependencies) {
				if (d == null || d.equals(q.id) || deps.contains(d) || !index.containsKey(d)) continue;
				if (dependsOn(d, q.id)) continue; // evitaria ciclo
				deps.add(d);
			}
		}
		q.dependencies = deps;
		// objetivos
		List<Objective> objs = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		if (q.objectives != null) {
			for (Objective o : q.objectives) {
				if (objs.size() >= 12 || o == null) break;
				if (o.type == null) o.type = ObjectiveType.COLLECT;
				if (o.type == ObjectiveType.LOCATION) {
					o.target = clip(o.target, 64).replace(" ", "");
					if (!o.target.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) continue;
					o.extra = norm(o.extra);
					o.count = Math.max(1, Math.min(1000, o.count));
				} else {
					o.target = norm(o.target);
					if (o.target.isEmpty()) continue;
					o.extra = "";
					o.count = Math.max(1, Math.min(1_000_000, o.count));
				}
				if (o.id == null || o.id.isBlank() || !ids.add(o.id)) {
					do { o.id = shortId(); } while (!ids.add(o.id));
				}
				objs.add(o);
			}
		}
		q.objectives = objs;
		// recompensas
		List<Reward> rws = new ArrayList<>();
		if (q.rewards != null) {
			for (Reward r : q.rewards) {
				if (rws.size() >= 12 || r == null) break;
				if (r.type == null) r.type = RewardType.ITEM;
				if (r.type == RewardType.COMMAND) {
					r.value = clip(r.value, 200);
					if (r.value.isEmpty()) continue;
					r.amount = 1;
				} else {
					r.amount = Math.max(1, Math.min(r.type == RewardType.ITEM ? 6400 : 100000, r.amount));
					if (r.type == RewardType.ITEM) {
						r.value = norm(r.value);
						if (r.value.isEmpty() || r.value.startsWith("#")) continue;
					} else {
						r.value = "";
					}
				}
				rws.add(r);
			}
		}
		q.rewards = rws;
	}
}
