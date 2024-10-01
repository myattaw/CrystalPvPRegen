package me.rages.crystalpvpregen;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lucko.helper.time.DurationFormatter;
import me.lucko.helper.time.Time;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RegenRegionPlaceholder extends PlaceholderExpansion {

    public CrystalPvPRegenPlugin plugin;

    public RegenRegionPlaceholder(CrystalPvPRegenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "regenregion";
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.contains("timer")) {
            return DurationFormatter.CONCISE.format(Time.diffToNow(plugin.getNextRegen()));
        }

        return "N/A";
    }


    @Override
    public @NotNull String getAuthor() {
        return "rages";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

}
