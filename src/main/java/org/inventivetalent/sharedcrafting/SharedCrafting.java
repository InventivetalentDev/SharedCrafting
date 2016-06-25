package org.inventivetalent.sharedcrafting;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SharedCrafting extends JavaPlugin {

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(new CraftingListener(this), this);
	}

}
