package com.zenith.starlove.init;

import com.zenith.starlove.Starlove;
import com.zenith.starlove.item.StarloveSwordItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Starlove.MODID);

    public static final RegistryObject<Item> STARLOVE_SWORD = ITEMS.register("starlove_sword",
            () -> new StarloveSwordItem(Tiers.NETHERITE, 3, -2.4F, new Item.Properties().fireResistant()));
}