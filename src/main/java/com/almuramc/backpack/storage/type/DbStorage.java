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
package com.almuramc.backpack.storage.type;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.storage.model.Backpack;
import com.almuramc.backpack.storage.model.BackpackSlot;
import com.almuramc.backpack.util.PermissionHelper;
import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.TxRunnable;
import com.comphenix.protocol.utility.StreamSerializer;

public class DbStorage extends Storage
{
    private EbeanServer database;

    public DbStorage(EbeanServer database)
    {
        this.database = database;
    }

    @Override
    public void initWorld(World world)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public BackpackInventory load(Player player, World world)
    {
        BackpackInventory inventory;

        if (has(player, world)) {
            inventory = fetch(player, world);
        }
        else {
            List<ItemStack> items = new ArrayList<ItemStack>();
            Backpack backpack = database.find(Backpack.class).where().eq("player_name", player.getName()).eq("world_name", world.getName()).findUnique();

            if (backpack != null) {
                for (BackpackSlot slot : backpack.getSlots()) {
                    try {
                        ItemStack stack = StreamSerializer.getDefault().deserializeItemStack(slot.getItemStackString());
                        items.add(stack);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
    
                int psize = PermissionHelper.getMaxSizeFor(player, world);
                int size = backpack.getContentAmount() > psize ? psize : backpack.getContentAmount();
    
                inventory = new BackpackInventory(Bukkit.createInventory(player, size, "Backpack"));
                inventory.setContents(items.toArray(new ItemStack[items.size()]));
            }
            else {
                inventory = new BackpackInventory(Bukkit.createInventory(player, BackpackPlugin.getInstance().getCached().getDefaultSize(), "Backpack"));
            }
        }

        return inventory;
    }

    @Override
    public void save(Player player, World world, BackpackInventory inventory)
    {
        // Store this backpack to memory
        store(player, world, inventory);
        // Save to db
        if (!has(player, world)) {
            return;
        }
        final String playerName = player.getName();
        final String worldName = world.getName();
        
        saveToDb(playerName, worldName, inventory);
    }

    public void saveToDb(final String playerName, final String worldName, final BackpackInventory inventory)
    {
        database.execute(new TxRunnable() {

            @Override
            public void run()
            {
                Backpack backpack = database.find(Backpack.class).where().eq("player_name", playerName).eq("world_name", worldName).findUnique();
                if (backpack == null) {
                    backpack = database.createEntityBean(Backpack.class);
                    backpack.setPlayerName(playerName);
                    backpack.setWorldName(worldName);
                }
                else {
                    database.delete(backpack.getSlots());
                }
                backpack.setContentAmount(inventory.getSize());

                List<BackpackSlot> slots = new ArrayList<BackpackSlot>();
                ItemStack[] contents = inventory.getContents();
                for (int i = 0; i < 54 && i < contents.length; i++) {
                    final ItemStack stack = contents[i];
                    if (stack == null) {
                        continue;
                    }
                    try {
                        BackpackSlot slot = database.createEntityBean(BackpackSlot.class);
                        slot.setSlotNumber(i);
                        slot.setItemStackString(StreamSerializer.getDefault().serializeItemStack(stack));
                        slots.add(slot);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                backpack.setSlots(slots);
                database.save(backpack);
            }
        });
    }

    @Override
    public void updateSize(Player player, World world, int size)
    {
        Backpack backpack = database.find(Backpack.class).where().eq("player_name", player.getName()).eq("world_name", world.getName()).findUnique();
        backpack.setContentAmount(size);
        database.save(backpack);
    }

}
