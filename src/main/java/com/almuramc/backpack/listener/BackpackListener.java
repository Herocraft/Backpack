/*
 * This file is part of Backpack.
 *
 * Copyright (c) 2012, AlmuraDev <http://www.almuramc.com/>
 * Backpack is licensed under the Almura Development License version 1.
 *
 * Backpack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * As an exception, all classes which do not reference GPL licensed code
 * are hereby licensed under the GNU Lesser Public License, as described
 * in Almura Development License version 1.
 *
 * Backpack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * the GNU Lesser Public License (for classes that fulfill the exception)
 * and the Almura Development License version 1 along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU General Public License and
 * the GNU Lesser Public License.
 */
package com.almuramc.backpack.listener;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.util.*;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackpackListener implements Listener {
	private static final BackpackPlugin plugin = BackpackPlugin.getInstance();
	private static final Storage STORE = BackpackPlugin.getInstance().getStore();
	private static final CachedConfiguration CONFIG = BackpackPlugin.getInstance().getCached();
	private static final Permission PERM = BackpackPlugin.getInstance().getHooks().getPermissions();
	private static boolean debug = false;
	private static Pattern playerNamePattern = Pattern.compile("Backpack \\((.+)\\)");

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			String worldName = PermissionHelper.getWorldToOpen(player, player.getWorld().getName());
			if (PERM.playerHas(player.getWorld().getName(), player, "backpack.keepitems")) {
				return;
			}
			BackpackInventory backpack = STORE.load(player, worldName);
			ItemStack[] contents = backpack.getVisibleContents();
			if (contents == null) {
				return;
			}
			for (ItemStack toDrop : contents) {
				player.getWorld().dropItemNaturally(player.getLocation(), toDrop);
			}
			backpack.clear();
			STORE.save(player, worldName, backpack);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClose(InventoryCloseEvent event) {
		onBackpackClose(event.getView(), event.getPlayer());
		if (debug) {
			Bukkit.getLogger().warning("[Backpack Debug] - BackpackListener.java - onInventoryClose method called.");
		}
	}
	
	@EventHandler()
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.isCancelled()) {
			return;
		}
		final Inventory myInventory = event.getInventory();
		final InventoryView myView = event.getView();
		final Player player = (Player) event.getWhoClicked();
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(BackpackPlugin.getInstance(), new Runnable() {
			@Override
			public void run() {
				if (myInventory.getTitle().equals("Backpack")) {
					if (debug) {
						Bukkit.getLogger().warning("[Backpack Debug] - BackpackListener.java - onInventoryClick method called.");
					}
					onBackpackClose(myView, ((Player) player));
				}
			}
		}, 20L);
		
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (event.isCancelled()) {
			event.setCancelled(true);
			return;
		}
		Player player = (Player) event.getPlayer();
		if (event.getInventory().getTitle().equals("Backpack") && !PERM.playerHas(player.getWorld().getName(), player, "backpack.use")) {
			event.setCancelled(true);
			return;
		}
	}

	@EventHandler
	public void onPlayerKick(PlayerKickEvent event) {
		if (event.isCancelled()) {
			event.setCancelled(true);
			return;
		}
		//onBackpackClose(event.getPlayer().getOpenInventory(), event.getPlayer());
		forceSave(event.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (debug) {
			Bukkit.getLogger().warning("[Backpack Debug] - onPlayerQuit method called.");
		}
		//onBackpackClose(event.getPlayer().getOpenInventory(), event.getPlayer());
		forceSave(event.getPlayer());
	}
	
	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		plugin.getStore().initWorld(event.getWorld().getName());
	}

	@EventHandler
	public void onItemPickup(PlayerPickupItemEvent event) {
		if (!PERM.playerHas(event.getPlayer().getWorld().getName(), event.getPlayer(), "backpack.overflow")) {
			return;
		}
		Player player = event.getPlayer();
		if (!InventoryUtil.hasOnlyOneFreeSlot(player.getInventory())) {
			return;
		}
		ItemStack item = event.getItem().getItemStack();
		String world = PermissionHelper.getWorldToOpen(player, player.getWorld().getName());
		BackpackInventory inventory = STORE.load(player, world);
		//backpack is full, we are done here.
		if (inventory.firstEmpty() == -1) {
			return;
		}
		//Check if the inventory has any items matching the pickup in it, see if we can add it
		HashMap<Integer, ? extends ItemStack> withinInventory = event.getPlayer().getInventory().all(item);
		for (ItemStack stack : withinInventory.values()) {
			if (stack.getAmount() < event.getPlayer().getInventory().getMaxStackSize()) {
				return;
			}
		}
		List<ItemStack> blacklistedItems = inventory.getIllegalItems(CONFIG.getBlacklistedItems());
		if (blacklistedItems.contains(item)) {
			if (CONFIG.useSpout() && BackpackPlugin.getInstance().getHooks().isSpoutPluginEnabled()) {
				SafeSpout.sendMessage(player, "Picking up illegal item(s)!", "Backpack", Material.LAVA);
			} else {
				MessageHelper.sendMessage(player, "Trying to pickup illegal item(s)! Stopping the pickup...");
			}
			return;
		}
		inventory.addItem(item);
		final Map<Integer, ItemStack> nonMergable = inventory.addItem(item);
		STORE.save(player, world, inventory);
		event.getItem().remove();
		if (nonMergable.isEmpty()) {
		    event.getItem().remove();
		}
		Random random = new Random();
		player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
		event.setCancelled(true);
	}

	private void onBackpackClose(InventoryView viewer, HumanEntity entity) {
		Player player = (Player) entity;
		Inventory inventory = viewer.getTopInventory();
		InventoryHolder holder = inventory.getHolder();
		String title = inventory.getTitle();
		if (holder == null) {
			if (debug) {
				Bukkit.getLogger().warning("[Backpack Debug] - BackpackListener.java - onBackpackClose method >> holder is null.");
			}
			return;
		}
		if (holder.equals(player) && title.startsWith("Backpack")) {
			BackpackInventory backpack = new BackpackInventory(inventory);
			List<ItemStack> blacklistedItems = backpack.getIllegalItems(CONFIG.getBlacklistedItems());
			String world = PermissionHelper.getWorldToOpen(player, player.getWorld().getName());
			boolean hadIllegalItems = false;
			for (ItemStack item : blacklistedItems) {
				if (!hadIllegalItems) {
					hadIllegalItems = true;
				}
				player.getWorld().dropItemNaturally(player.getLocation(), item);
			}
			backpack.filterIllegalItems();
			if (hadIllegalItems) {
				if (CONFIG.useSpout() && BackpackPlugin.getInstance().getHooks().isSpoutPluginEnabled()) {
					SafeSpout.sendMessage(player, "Dropping illegal items!", "Backpack", Material.LAVA);
				} else {
					MessageHelper.sendMessage(player, "Found illegal items in your Backpack! Dropping them around you...");
				}
			}
			if (debug) {
				Bukkit.getLogger().warning("[Backpack Debug] - BackpackListener.java - STORE.save method called.");
			}
			Matcher playerNameMatcher = playerNamePattern.matcher(title);
			OfflinePlayer offlinePlayer = playerNameMatcher.matches() ? Bukkit.getOfflinePlayer(playerNameMatcher.group(1)) : player;
		    STORE.save(offlinePlayer, world, new BackpackInventory(backpack.getInventory()));
		}
	}
	
	private void forceSave(Player player) {
		String worldName = PermissionHelper.getWorldToOpen(player, player.getWorld().getName());
		BackpackInventory inventory = STORE.load(player, worldName);
		STORE.save(player, worldName, inventory);
		STORE.purge(player, worldName);
		if (debug) {
			Bukkit.getLogger().warning("[Backpack Debug] - BackpackListener.java - forceSave method called.");
		}
	}
}
