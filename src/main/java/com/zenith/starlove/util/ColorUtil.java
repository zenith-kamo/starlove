package com.zenith.starlove.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public class ColorUtil {
    public static Component makeRainbow(String text) {
        MutableComponent root = Component.empty();
        long time = System.currentTimeMillis() / 10;

        for (int i = 0; i < text.length(); i++) {
            float hue = ((time + ((text.length() - 1 - i) * 10)) % 360) / 360f;
            int color = Mth.hsvToRgb(hue, 0.8f, 1.0f);
            root.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(color)));
        }
        return root;
    }


}