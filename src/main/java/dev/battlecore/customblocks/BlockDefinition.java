package dev.battlecore.customblocks;

import org.bukkit.Material;

public record BlockDefinition(String id, String name, Material icon, int note) {
}
