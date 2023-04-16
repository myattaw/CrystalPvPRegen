package me.rages.crystalpvpregen;

import com.google.common.collect.ImmutableSet;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import me.lucko.helper.Schedulers;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import me.lucko.helper.text3.Text;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Plugin(
        name = "CrystalPvPRegen",
        hardDepends = {"helper", "FastAsyncWorldEdit"},
        apiVersion = "1.18",
        authors = {"Rages"}
)
public class CrystalPvPRegenPlugin extends ExtendedJavaPlugin {

    private Map<String, CrystalRegion> regionMap = new HashMap<>();

    private ZoneId ZONE_ID;

    private WorldEdit worldEdit = WorldEdit.getInstance();

    private Set<BaseBlock> replaceBlocks;

    @Override
    protected void enable() {
        saveDefaultConfig();

        this.replaceBlocks = ImmutableSet.of(
                BlockTypes.WATER.getDefaultState().toBaseBlock(),
                BlockTypes.SEAGRASS.getDefaultState().toBaseBlock(),
                BlockTypes.TALL_SEAGRASS.getDefaultState().toBaseBlock(),
                BlockTypes.KELP_PLANT.getDefaultState().toBaseBlock(),
                BlockTypes.KELP.getDefaultState().toBaseBlock()
        );

        this.ZONE_ID = ZoneId.of(getConfig().getString("settings.zone-id"));
        int timer = getConfig().getInt("settings.warning-timer");
        boolean enableWarning = getConfig().getBoolean("settings.warning-enabled");

        String path = "regions.%s.";
        for (String str : getConfig().getConfigurationSection("regions").getKeys(false)) {
            String time = getConfig().getString(String.format(path, str) + "time");
            CrystalRegion crystalRegion = CrystalRegion.create(
                    str,
                    getConfig().getString(String.format(path, str) + "world"),
                    getConfig().getInt(String.format(path, str) + "pos1.x"),
                    getConfig().getInt(String.format(path, str) + "pos1.y"),
                    getConfig().getInt(String.format(path, str) + "pos1.z"),
                    getConfig().getInt(String.format(path, str) + "pos2.x"),
                    getConfig().getInt(String.format(path, str) + "pos2.y"),
                    getConfig().getInt(String.format(path, str) + "pos2.z")
            );
            regionMap.put(time, crystalRegion);
        }

        Schedulers.sync().runRepeating(() -> {
            LocalDateTime time = LocalDateTime.now(this.ZONE_ID);
            String currTime = String.format("%02d:%02d", time.getHour(), time.getMinute());

            CrystalRegion crystalRegion = regionMap.get(currTime);

            if (crystalRegion != null) {

                if (enableWarning) {
                    Bukkit.broadcastMessage(Text.colorize(getConfig().getString("broadcast-messages.warning")
                            .replace("%name%", crystalRegion.getName())));
                }

                Schedulers.sync().runLater(() -> {

                    RegenOptions options = RegenOptions.builder()
                            .seed(crystalRegion.getWorld().getSeed())
                            .build();

                    World bukkitWorld = BukkitAdapter.adapt(crystalRegion.getWorld());
                    EditSession editSession = worldEdit.newEditSessionBuilder().world(bukkitWorld).build();

                    CuboidRegion cuboidRegion = new CuboidRegion(bukkitWorld, crystalRegion.getPos1(), crystalRegion.getPos2());

                    bukkitWorld.regenerate(
                            cuboidRegion,
                            editSession,
                            options
                    );

                    BlockStateHolder stone = BlockTypes.STONE.getDefaultState();

                    // Replace the water blocks with stone blocks

                    try {
                        editSession.replaceBlocks(cuboidRegion, replaceBlocks, stone);
                    } catch (MaxChangedBlocksException e) {
                    }

                    editSession.close();
                    Bukkit.broadcastMessage(Text.colorize(getConfig().getString("broadcast-messages.complete")
                            .replace("%name%", crystalRegion.getName())));

                }, 20L * timer);

                regionMap.remove(currTime);
            }


        }, 200L, 200L).bindWith(this);
    }
}
