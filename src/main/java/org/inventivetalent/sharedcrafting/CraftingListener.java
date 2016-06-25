package org.inventivetalent.sharedcrafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CraftingListener implements Listener {

	SharedCrafting plugin;
	Map<UUID, Block> workbenches = new ConcurrentHashMap<>();

	public CraftingListener(SharedCrafting plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		final Player p = e.getPlayer();
		if (e.getClickedBlock() == null) { return; }
		final Block block = e.getClickedBlock();
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) { return; }
		if (block.getType() == Material.WORKBENCH) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {

				@Override
				public void run() {
					if (!p.isOnline()) { return; }

					if (!block.hasMetadata("shared_inventory")) {
						block.setMetadata("shared_owner", new FixedMetadataValue(plugin, p));
						block.setMetadata("shared_inventory", new FixedMetadataValue(plugin, new ArrayList<UUID>()));
					}
					final List<UUID> list = (List<UUID>) block.getMetadata("shared_inventory").get(0).value();

					final Inventory open = p.getOpenInventory().getTopInventory();
					if (open == null || open.getType() != InventoryType.WORKBENCH) {
						return;
					}

					// Workaround to get the accessed WorkBench
					final Block workbench = p.getTargetBlock((Set<Material>) null, 8);
					if (workbench == null || workbench.getType() != Material.WORKBENCH) {
						/* If the player managed to access the workbench without looking at one, we close the inventory */
						p.closeInventory();
						return;
					}

					list.add(p.getUniqueId());
					p.setMetadata("shared_inv", new FixedMetadataValue(plugin, block));

					Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {

						@Override
						public void run() {
							if (list.isEmpty()) { return; }
							Player first = Bukkit.getPlayer(list.get(0));
							Inventory pInv = first.getOpenInventory().getTopInventory();
							if (pInv == null || pInv.getType() != InventoryType.WORKBENCH) { return; }
							open.setContents(pInv.getContents());
						}
					}, 1);
				}
			});
		}
	}

	public void handle(InventoryInteractEvent e) {
		if (!(e.getWhoClicked() instanceof Player)) { return; }
		final Player p = (Player) e.getWhoClicked();
		if (e.getInventory().getType() == InventoryType.WORKBENCH) {
			if (e instanceof InventoryClickEvent) {
				if (((InventoryClickEvent) e).getSlotType() != SlotType.CRAFTING && ((InventoryClickEvent) e).getSlotType() != SlotType.RESULT) { return; }
			}
			// Workaround to get the accessed WorkBench
			final Block workbench = p.getTargetBlock((Set<Material>) null, 8);

			if (!workbench.hasMetadata("shared_inventory") || workbench.getType() != Material.WORKBENCH) {
				if (!p.hasMetadata("shared_inv")) { return; } else {
					if (p.getOpenInventory().getTopInventory() != null) {
						p.getOpenInventory().getTopInventory().clear();
					}
					p.closeInventory();
					p.removeMetadata("shared_inv", plugin);
				}
				return;
			}

			List<UUID> list = (List<UUID>) workbench.getMetadata("shared_inventory").get(0).value();
			Player owner = (Player) workbench.getMetadata("shared_owner").get(0).value();

			if (owner != null) {
				if (!owner.equals(p)) {
					e.setResult(Result.DENY);
					e.setCancelled(true);
				} else {
					final Inventory pInv = p.getOpenInventory().getTopInventory();
					if (pInv == null || pInv.getType() != InventoryType.WORKBENCH) {
						workbench.removeMetadata("shared_inventory", plugin);
						return;
					}

					Iterator<UUID> iterator = list.iterator();
					while (iterator.hasNext()) {
						UUID next = iterator.next();
						if (owner.getUniqueId().equals(next) || p.getUniqueId().equals(next)) {
							continue;
						}
						Player idPlayer = Bukkit.getPlayer(next);
						if (next == null || idPlayer == null || !idPlayer.isOnline()) {
							iterator.remove();
							continue;
						}
						final Inventory open = idPlayer.getOpenInventory().getTopInventory();
						if (open == null || open.getType() != InventoryType.WORKBENCH) {
							p.closeInventory();// Same as above, close if the workbench isn't there
							iterator.remove();
							continue;
						}

						Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {

							@Override
							public void run() {
								open.setContents(pInv.getContents());
							}
						}, 1);
					}
				}
			}
		}
	}

	@EventHandler
	public void onInventoryClick(final InventoryClickEvent e) {
		handle(e);
	}

	@EventHandler
	public void onInventoryDrag(InventoryDragEvent event) {
		handle(event);
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e) {
		if (!(e.getPlayer() instanceof Player)) { return; }
		final Player p = (Player) e.getPlayer();
		if (e.getInventory().getType() == InventoryType.WORKBENCH) {
			// Workaround to get the accessed WorkBench
			final Block workbench = p.getTargetBlock((Set<Material>) null, 8);

			if (!workbench.hasMetadata("shared_inventory") || workbench.getType() != Material.WORKBENCH) {
				if (!p.hasMetadata("shared_inv")) { return; } else {
					if (p.getOpenInventory().getTopInventory() != null) {
						p.getOpenInventory().getTopInventory().clear();
					}
					p.removeMetadata("shared_inv", plugin);
				}
				return;
			}

			List<UUID> list = (List<UUID>) workbench.getMetadata("shared_inventory").get(0).value();
			Player owner = (Player) workbench.getMetadata("shared_owner").get(0).value();
			if (owner != null) {
				if (owner.equals(p)) {
					for (UUID id : list) {
						Player idP = Bukkit.getPlayer(id);
						if (idP == null || !idP.isOnline()) {
							continue;
						}
						if (p == idP) { continue; }
						idP.closeInventory();
					}

					// for (ItemStack item : shared.getContents()) {
					// if (item == null) continue;
					// p.getWorld().dropItem(owner.getLocation().add(0, 0.5, 0), item);
					// }
					// shared.clear();

					workbench.removeMetadata("shared_inventory", plugin);
					workbench.removeMetadata("shared_owner", plugin);
				} else {
					e.getInventory().clear();
				}
			}
		}
	}
}
