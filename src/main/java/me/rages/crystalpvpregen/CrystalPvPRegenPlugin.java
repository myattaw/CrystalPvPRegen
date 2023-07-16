package me.rages.crystalpvpregen;

import com.google.common.collect.ImmutableSet;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import me.lucko.helper.Schedulers;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import me.lucko.helper.text3.Text;
import me.lucko.helper.utils.Players;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Plugin(
        name = "CrystalPvPRegen",
        hardDepends = {"helper", "FastAsyncWorldEdit", "Factions"},
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


        ImmutableSet<File> crystalZones = ImmutableSet.of(
                new File(getDataFolder().getAbsolutePath() + "/north.schem"),
                new File(getDataFolder().getAbsolutePath() + "/south.schem"),
                new File(getDataFolder().getAbsolutePath() + "/west.schem"),
                new File(getDataFolder().getAbsolutePath() + "/east.schem")
        );

        Schedulers.async().runRepeating(() -> {

            if (enableWarning) {
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(Text.colorize("&7&lCrystalZone is being regenerated outside &9&l/spawn&7!"));
                Bukkit.broadcastMessage("");
            }

            org.bukkit.@Nullable World world = Bukkit.getWorld("spawn");
            if (world == null) {
                world = Bukkit.createWorld(new WorldCreator("spawn"));
            }

            World bukkitWorld = BukkitAdapter.adapt(world);
            for (File file : crystalZones) {
                EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(bukkitWorld, -1);

                ClipboardFormat format = ClipboardFormats.findByFile(file);
                ClipboardReader reader;
                try {
                    reader = format.getReader(new FileInputStream(file));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Clipboard clipboard;
                try {
                    clipboard = reader.read();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                //-23 85 -7

                // Saves our operation and builds the paste - ready to be completed.
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(-24, 85, -8))
                        .ignoreAirBlocks(true)
                        .build();

                try {
                    // This simply completes our paste and then cleans up.
                    Operations.complete(operation);
                    editSession.flushSession();
                } catch (WorldEditException e) {
                    e.printStackTrace();
                }
            }

            Players.all().forEach(player -> {
                FLocation fLocation = new FLocation(player.getLocation());
                if (Board.getInstance().getFactionAt(fLocation).getId().equals("-3") && !player.getLocation().getBlock().getType().isAir()) {
                    for (int y = 64; y < 150; y++) {
                        Block block = player.getWorld().getBlockAt(player.getLocation().getBlockX(), y, player.getLocation().getBlockZ());
                        if (block.getType().isAir() && block.getRelative(BlockFace.UP).getType().isAir() && block.getRelative(BlockFace.DOWN).isSolid()) {
                            Schedulers.sync().run(() -> player.teleport(block.getLocation()));
                        }
                    }
                }
            });


        }, 1L, TimeUnit.MINUTES, 1L, TimeUnit.MINUTES).bindWith(this);

    }
}
