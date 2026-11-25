package dev.questbook.data;

public class Reward {
	public RewardType type = RewardType.ITEM;
	/** ITEM: id do item. COMMAND: comando (use %player%). */
	public String value = "minecraft:bread";
	public int amount = 1;
}
