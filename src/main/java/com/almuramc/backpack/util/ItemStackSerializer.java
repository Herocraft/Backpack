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
package com.almuramc.backpack.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class ItemStackSerializer implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final YamlConfiguration SERIALIZER = new YamlConfiguration();
    public transient ItemStack ITEM_STACK;

    public ItemStackSerializer() {
    }

    public ItemStackSerializer(ItemStack itemStack) {
        this.ITEM_STACK = itemStack;
    }

    public ItemStackSerializer(byte[] serialized) throws InvalidConfigurationException, IOException, ClassNotFoundException {
        deserialize(serialized);
    }

    public byte[] serialize() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos);
        writeObject(out);
        return baos.toByteArray();
    }

    public String serializeAsString() throws IOException {
        if (ITEM_STACK == null) {
            throw new NullPointerException("Cannot serialize a null ItemStack");
        }
        SERIALIZER.set("item", ITEM_STACK);
        return SERIALIZER.saveToString();
    }

    public ItemStackSerializer deserialize(byte[] serialized) throws InvalidConfigurationException, IOException, ClassNotFoundException {
        final BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(serialized));
        readObject(in);
        return this;
    }

    public ItemStack deserializeFromString(String serialized) throws InvalidConfigurationException, IOException, ClassNotFoundException {
        SERIALIZER.loadFromString(serialized);
        ITEM_STACK = SERIALIZER.getItemStack("item");
        return ITEM_STACK;
    }

    private void writeObject(BukkitObjectOutputStream out) throws IOException {
        try {
            out.writeUTF(serializeAsString());
        } finally {
            out.close();
        }
    }

    private void readObject(BukkitObjectInputStream in) throws IOException, ClassNotFoundException, InvalidConfigurationException {
        try {
            SERIALIZER.loadFromString(in.readUTF());
            ITEM_STACK = SERIALIZER.getItemStack("item");
        } finally {
            in.close();
        }
    }
}
