package dev.questbook.data;

import java.util.HashMap;
import java.util.Map;

public class PlayerProgress {
	public Map<String, QuestState> quests = new HashMap<>();

	public QuestState peek(String questId) { return quests.get(questId); }

	public QuestState state(String questId) { return quests.computeIfAbsent(questId, k -> new QuestState()); }

	public boolean done(String questId) { QuestState s = quests.get(questId); return s != null && s.done; }

	public boolean claimed(String questId) { QuestState s = quests.get(questId); return s != null && s.claimed; }

	public int get(String questId, String objId) {
		QuestState s = quests.get(questId);
		if (s == null) return 0;
		Integer v = s.obj.get(objId);
		return v == null ? 0 : v;
	}

	public void set(String questId, String objId, int value) { state(questId).obj.put(objId, value); }
}
