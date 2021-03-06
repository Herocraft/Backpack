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
package com.almuramc.backpack.command;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.storage.type.DbStorage;
import com.almuramc.backpack.util.CachedConfiguration;
import com.almuramc.backpack.util.Dependency;
import com.almuramc.backpack.util.MessageHelper;
import com.almuramc.backpack.util.PermissionHelper;
import com.almuramc.backpack.util.SafeSpout;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.getspout.spoutapi.player.SpoutPlayer;

public class BackpackCommands implements CommandExecutor {
	private static final Storage STORE = BackpackPlugin.getInstance().getStore();
	private static final CachedConfiguration CONFIG = BackpackPlugin.getInstance().getCached();
	private static final Dependency HOOKS = BackpackPlugin.getInstance().getHooks();
	private static final Economy ECON = BackpackPlugin.getInstance().getHooks().getEconomy();
	private static final Permission PERM = BackpackPlugin.getInstance().getHooks().getPermissions();

	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
		if (command.getName().equalsIgnoreCase("backpack")) {
			Player player = commandSender instanceof Player ? (Player) commandSender : null;

			boolean useSpoutInterface = false;
			if (strings.length == 0 && player != null) {
				if (PERM.playerHas(player.getWorld().getName(), player, "backpack.use")) {
					player.openInventory(STORE.load(player, PermissionHelper.getWorldToOpen(player, player.getWorld().getName())).getInventory());
					return true;
				} else {
					MessageHelper.sendMessage(commandSender, "Insufficient permissions to use backpack!");
				}
				return true;
			} else if (strings.length > 1 && strings[0].equalsIgnoreCase("edit")) {
			    if (player != null && !PERM.playerHas(player.getWorld().getName(), player, "backpack.admin")) {
                    MessageHelper.sendMessage(commandSender, "Insufficient permissions to edit backpacks!");
                    return true;
			    }
			    final OfflinePlayer target = Bukkit.getOfflinePlayer(strings[1]);
			    if (!target.hasPlayedBefore()) {
                    MessageHelper.sendMessage(commandSender, strings[1] + " has not played before!");
                    return true;
                }
                player.openInventory(STORE.edit(player, target, PermissionHelper.getWorldToOpen(target, player.getWorld().getName())).getInventory());
                return true;
			} else if (strings.length > 1 && strings[0].equalsIgnoreCase("clear")) {
				if (player != null && !PERM.playerHas(player.getWorld().getName(), player, "backpack.admin")) {
					MessageHelper.sendMessage(commandSender, "Insufficient permissions to clear backpacks!");
					return true;
				}
				final Player target = Bukkit.getPlayerExact(strings[1]);
				if (target == null) {
					MessageHelper.sendMessage(commandSender, strings[1] + " is not online!");
					return true;
				}
				final BackpackInventory backpack = STORE.load(target, target.getWorld().getName());
				backpack.clear();
				if (player != null && CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
					SafeSpout.sendMessage(player, target.getName() + "'s backpack was cleared.", "Backpack", Material.GOLDEN_APPLE);
				} else {
					MessageHelper.sendMessage(commandSender, target.getName() + "'s backpack was cleared.");
				}
				return true;
			} else if (strings.length > 0 && strings[0].equalsIgnoreCase("reload")) {
				if (player != null && !PERM.playerHas(player.getWorld().getName(), player, "backpack.admin")) {
					MessageHelper.sendMessage(commandSender, "Insufficient permissions to reload backpack!");
					return true;
				}
				CONFIG.reload();
				if (CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
					SafeSpout.sendMessage(player, "Configuration reloaded", "Backpack", Material.CAKE);
				} else {
					MessageHelper.sendMessage(commandSender, "Configuration reloaded");
				}
				return true;
			} else if (strings.length > 0 && strings[0].equalsIgnoreCase("upgrade") && player != null) {
				String target = PermissionHelper.getWorldToOpen(player, player.getWorld().getName());
				if (!PERM.playerHas(player.getWorld().getName(), player, "backpack.upgrade")) {
					MessageHelper.sendMessage(commandSender, "Insufficient permissions to upgrade your backpack!");
					return true;
				}
				if (CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
					SpoutPlayer sPlayer = (SpoutPlayer) player;
					if (sPlayer.isSpoutCraftEnabled()) {  // Check if player is a Spoutcraft User
						useSpoutInterface = true;
					}
				}
				if (useSpoutInterface) {
					SafeSpout.openUpgradePanel(player);
				} else {
					BackpackInventory backpack = STORE.load(player, target);
					int newSize = backpack.getSize() + 9;
					if (backpack.getSize() >= 54 || newSize > PermissionHelper.getMaxSizeFor(player, target)) {
						if (CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
							SafeSpout.sendMessage(player, "Max size reached!", "Backpack", Material.LAVA_BUCKET);
						} else {
							MessageHelper.sendMessage(commandSender, "You already have the maximum size for a backpack allowed!");
						}
						return true;
					}
					if (ECON != null && CONFIG.useEconomy() && !PERM.playerHas(player.getWorld().getName(), player, "backpack.noupgradecost")) {
						double cost = CONFIG.getUpgradeCosts().get("slot" + newSize);
						if (!ECON.has(player, cost)) {
							if (CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
								SafeSpout.sendMessage(player, "Not enough money!", "Backpack", Material.BONE);
							} else {
								MessageHelper.sendMessage(commandSender, "You do not have enough money!");
							}
							return true;
						}
						ECON.withdrawPlayer(player, cost);
						MessageHelper.sendMessage(commandSender, "Your account has been deducted by: " + cost);
					}
					backpack.setSize(player, newSize);
					STORE.store(player, target, backpack);
					STORE.updateSize(player, target, newSize);
					if (CONFIG.useSpout() && HOOKS.isSpoutPluginEnabled()) {
						SafeSpout.sendMessage(player, "Upgraded to " + newSize + " slots", "Backpack", Material.CHEST);
					} else {
						MessageHelper.sendMessage(commandSender, "Your backpack has been upgraded to " + newSize + " slots!");
					}
				}
				return true;
			} else if (strings.length > 1 && strings[0].equalsIgnoreCase("migrate")) {
			    if (player != null && !PERM.playerHas(player.getWorld().getName(), player, "backpack.admin")) {
                    MessageHelper.sendMessage(commandSender, "Insufficient permissions to migrate data!");
                    return true;
                }
			    if ("yaml".equalsIgnoreCase(strings[1]) && STORE instanceof DbStorage) {
			        DbStorage dbStorage = (DbStorage) STORE;
			        dbStorage.migrate();
			    } else {
			        MessageHelper.sendMessage(commandSender, "Unsupported migration type!");
                    return true;
			    }
			}
		}
		return true;
	}
}
