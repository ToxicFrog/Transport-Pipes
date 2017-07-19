package de.robotricker.transportpipes.pipes.goldenpipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import de.robotricker.transportpipes.pipeitems.ItemData;
import de.robotricker.transportpipes.pipes.PipeDirection;
import de.robotricker.transportpipes.pipes.types.GoldenPipe;
import de.robotricker.transportpipes.pipeutils.InventoryUtils;
import de.robotricker.transportpipes.pipeutils.config.LocConf;

public class GoldenPipeInv implements Listener {

	private static HashMap<GoldenPipe, Inventory> goldenPipeInventories = new HashMap<>();

	public static void updateGoldenPipeInventory(Player p, GoldenPipe pipe) {
		Inventory inv;
		if (goldenPipeInventories.containsKey(pipe)) {
			inv = goldenPipeInventories.get(pipe);
		} else {
			inv = Bukkit.createInventory(null, 6 * 9, LocConf.load(LocConf.GOLDENPIPE_TITLE));
			goldenPipeInventories.put(pipe, inv);
		}

		Collection<PipeDirection> pipeConnections = pipe.getAllConnections();

		for (int line = 0; line < 6; line++) {
			GoldenPipeColor gpc = GoldenPipeColor.values()[line];
			PipeDirection pd = PipeDirection.fromID(line);

			String blockingModeText = pipe.isLineBlocked(line) ? LocConf.load(LocConf.GOLDENPIPE_BLOCKING_ENABLED) : LocConf.load(LocConf.GOLDENPIPE_BLOCKING_DISABLED);
			ItemStack wool = InventoryUtils.changeDisplayNameAndLore(new ItemStack(Material.WOOL, 1, gpc.getItemDamage()), blockingModeText, LocConf.load(LocConf.GOLDENPIPE_BLOCKING_CLICKTOCHANGE));
			ItemStack stainedClay = InventoryUtils.changeDisplayNameAndLore(new ItemStack(Material.STAINED_CLAY, 1, gpc.getItemDamage()), blockingModeText, LocConf.load(LocConf.GOLDENPIPE_BLOCKING_CLICKTOCHANGE));
			ItemStack glassPane = InventoryUtils.changeDisplayNameAndLore(new ItemStack(Material.STAINED_GLASS_PANE, 1, gpc.getItemDamage()), String.valueOf(ChatColor.RESET));

			inv.setItem(line * 9, pipe.isLineBlocked(line) ? stainedClay : wool);

			if (!pipeConnections.contains(pd) || pipe.isLineBlocked(line)) {
				for (int i = 1; i < 9; i++) {
					inv.setItem(line * 9 + i, glassPane);
				}
			} else {
				ItemData[] items = pipe.getOutputItems(PipeDirection.fromID(line));
				for (int i = 1; i < 9; i++) {
					if (items[i - 1] != null) {
						inv.setItem(line * 9 + i, items[i - 1].toItemStack());
					} else {
						inv.setItem(line * 9 + i, null);
					}
				}
			}
		}

		p.openInventory(inv);
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (e.getClickedInventory() != null && goldenPipeInventories.containsValue(e.getClickedInventory())) {
			GoldenPipe pipe = null;
			//get pipe with inventory
			for (GoldenPipe gp : goldenPipeInventories.keySet()) {
				if (goldenPipeInventories.get(gp).equals(e.getClickedInventory())) {
					pipe = gp;
					break;
				}
			}
			if (pipe == null) {
				return;
			}
			//clicked on glass pane
			if (isGlassItem(e.getCurrentItem())) {
				e.setCancelled(true);
				return;
			}
			//clicked on wool/stained clay block
			if (e.getRawSlot() >= 0 && e.getRawSlot() <= e.getClickedInventory().getSize() && e.getRawSlot() % 9 == 0) {
				e.setCancelled(true);

				int line = (int) (e.getRawSlot() / 9);
				pipe.setLineBlocked(line, !pipe.isLineBlocked(line));

				//drop items in line if the line gets blocked
				if (pipe.isLineBlocked(line)) {
					for (int i = 1; i < 9; i++) {
						int slot = line * 9 + i;
						ItemStack is = e.getClickedInventory().getItem(slot);
						if (is != null && is.getType() != Material.AIR && !isGlassItem(is)) {
							((Player) e.getWhoClicked()).getWorld().dropItem(((Player) e.getWhoClicked()).getEyeLocation(), is);
							e.getClickedInventory().setItem(slot, null);
						}
					}
				}

				// Update inv
				saveGoldenPipeInv((Player) e.getWhoClicked(), e.getClickedInventory());
				updateGoldenPipeInventory((Player) e.getWhoClicked(), pipe);
				return;
			}
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		saveGoldenPipeInv((Player) e.getPlayer(), e.getInventory());
	}

	private void saveGoldenPipeInv(Player p, Inventory inv) {
		if (inv != null && goldenPipeInventories.containsValue(inv)) {
			GoldenPipe pipe = null;
			//get pipe with inventory
			for (GoldenPipe gp : goldenPipeInventories.keySet()) {
				if (goldenPipeInventories.get(gp).equals(inv)) {
					pipe = gp;
					break;
				}
			}
			//cache new items in golden pipe
			linefor: for (int line = 0; line < 6; line++) {
				List<ItemData> items = new ArrayList<>();
				for (int i = 1; i < 9; i++) {
					ItemStack is = inv.getItem(line * 9 + i);
					//make sure the glass pane won't be saved
					if (is != null && !isGlassItem(is)) {
						items.add(new ItemData(is));
						if (is.getAmount() > 1) {
							//drop overflow items (only 1 item of each type is saved)
							ItemStack clone = is.clone();
							clone.setAmount(is.getAmount() - 1);
							p.getWorld().dropItem(p.getLocation(), clone);

							//cloned item which will be saved
							ItemStack clone2 = is.clone();
							clone2.setAmount(1);
							inv.setItem(line * 9, clone2);
						}
					} else if (isGlassItem(is)) {
						//skip this save-sequenz if this line is not available (not a pipe or block as neighbor)
						continue linefor;
					}
				}
				pipe.changeOutputItems(PipeDirection.fromID(line), items);
			}
		}
	}

	private boolean isGlassItem(ItemStack is) {
		return InventoryUtils.hasDisplayName(is, String.valueOf(ChatColor.RESET));
	}

}
