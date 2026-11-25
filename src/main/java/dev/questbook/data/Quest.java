package dev.questbook.data;

import java.util.ArrayList;
import java.util.List;

public class Quest {
	public String id = "";
	public String title = "Nova missão";
	public String description = "";
	public String icon = "minecraft:book";
	public double x, y;
	/** Oculta até estar disponível. */
	public boolean hidden;
	public List<String> dependencies = new ArrayList<>();
	public List<Objective> objectives = new ArrayList<>();
	public List<Reward> rewards = new ArrayList<>();
}
