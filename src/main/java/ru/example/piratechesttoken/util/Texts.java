package ru.example.piratechesttoken.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Разбор &-кодов цвета в компоненты Adventure. */
public final class Texts {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Texts() {
    }

    public static Component text(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(raw);
    }

    public static List<Component> lines(List<String> raw) {
        List<Component> result = new ArrayList<>();
        if (raw != null) {
            for (String line : raw) {
                result.add(text(line));
            }
        }
        return result;
    }
}
