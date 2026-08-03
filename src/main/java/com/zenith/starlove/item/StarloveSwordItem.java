package com.zenith.starlove.item;

import com.zenith.starlove.client.EntityResetManager;
import com.zenith.starlove.util.ColorUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class StarloveSwordItem extends SwordItem {
    public StarloveSwordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (level.isClientSide) {
            if (player.isShiftKeyDown()) {
                boolean started = EntityResetManager.getInstance().startResetSequence();
                if (started) {
                    // ポテトはまずい。
                    player.sendSystemMessage(Component.literal("ポテトはおいしいですよ"));
                } else {
                    player.sendSystemMessage(Component.literal("シングルプレイ限定、または実行中"));
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {

        if (!stack.hasTag() || !stack.getTag().contains("HideFlags")) {
            stack.getOrCreateTag().putInt("HideFlags", 2);
        }

            tooltip.add(ColorUtil.makeRainbow("Eating a potato... but is that a sword?"));
            tooltip.add(Component.literal(""));
        tooltip.add(ColorUtil.makeRainbow("Left Click : SetHealth"));
            tooltip.add(ColorUtil.makeRainbow("[Shift] + Right Click : Remove All Entities"));
    }

    @Override
    public Component getName(ItemStack stack) {
        return ColorUtil.makeRainbow("Starlove Sword");
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.getEntityData().set(LivingEntity.DATA_HEALTH_ID, 0.0F);
            }
        }
        return false;
    }
}