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
import me.lucko.helper.Commands;
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
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Plugin(
        name = "CrystalPvPRegen",
        hardDepends = {
                "helper", "FastAsyncWorldEdit"
        },
        softDepends = {
                "Factions"
        },
        apiVersion = "1.18",
        authors = {"Rages"}
)
public class CrystalPvPRegenPlugin extends ExtendedJavaPlugin {

    private Map<String, CrystalRegion> regionMap = new HashMap<>();


    private WorldEdit worldEdit = WorldEdit.getInstance();
    private Instant nextRegen;
    private RegenRegionPlaceholder regionPlaceholder;


    @Override
    protected void enable() {
        saveDefaultConfig();


        int timer = getConfig().getInt("settings.warning-timer");
        boolean enableWarning = getConfig().getBoolean("settings.warning-enabled");


        // Get the list of schematics (strings like "north", "south", "west", "east")
        List<String> schematics = getConfig().getStringList("schematics");

        // Use a builder to create the ImmutableSet dynamically
        ImmutableSet<File> crystalZones = schematics.stream()
                .map(schem -> new File(getDataFolder(), "/" + schem + ".schem"))  // Map each string to a File object
                .collect(ImmutableSet.toImmutableSet());

        org.bukkit.@Nullable World world = Bukkit.getWorld(getConfig().getString("paste-location.world"));
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(getConfig().getString("paste-location.world")));
        }

        List<String> broadcast = getConfig().getStringList("settings.broadcast");
        BlockVector3 vector3 = BlockVector3.at(
                getConfig().getInt("paste-location.x"),
                getConfig().getInt("paste-location.y"),
                getConfig().getInt("paste-location.z")
        );

        org.bukkit.@Nullable World finalWorld = world;

        int resetTimer = getConfig().getInt("settings.reset-timer");
        this.nextRegen = Instant.now().plus(resetTimer, ChronoUnit.MINUTES);
        Schedulers.async().runRepeating(() ->
                        fixWarzone(enableWarning, broadcast, finalWorld, crystalZones, vector3),
                        6, TimeUnit.MINUTES, resetTimer, TimeUnit.MINUTES
                )
                .bindWith(this);

        Commands.create()
                .assertPermission("crystalregen.admin")
                .handler(cmd -> Schedulers.async().run(() ->
                        fixWarzone(enableWarning, broadcast, finalWorld, crystalZones, vector3)))
                .registerAndBind(this, "crystalregen");

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.regionPlaceholder = new RegenRegionPlaceholder(this);
            this.regionPlaceholder.register();
        }
    }

    @Override
    protected void disable() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.regionPlaceholder.unregister();
        }
    }

    public void fixWarzone(
            boolean enableWarning,
            List<String> broadcast,
            org.bukkit.World finalWorld,
            ImmutableSet<File> crystalZones,
            BlockVector3 vector3
    ) {
        this.nextRegen = Instant.now().plus(getConfig().getInt("settings.reset-timer"), ChronoUnit.MINUTES);

        if (enableWarning) {
            broadcast.stream().map(Text::colorize).forEach(Bukkit::broadcastMessage);
        }

        World bukkitWorld = BukkitAdapter.adapt(finalWorld);
        for (File file : crystalZones) {
            if (!file.exists()) {
                getLogger().log(Level.SEVERE, "Could not find file " + file.getAbsolutePath());
            }

            EditSession editSession = WorldEdit.getInstance().newEditSession(bukkitWorld);
            editSession.setFastMode(false);
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            ClipboardReader reader;
            try {
                reader = format.getReader(Files.newInputStream(file.toPath()));
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
                    .to(vector3)
//                                .ignoreAirBlocks(true)
                    .build();

            try {
                // This simply completes our paste and then cleans up.
                Operations.complete(operation);
                editSession.close();
            } catch (WorldEditException e) {
                e.printStackTrace();
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Factions")) {
            Players.all().forEach(player -> {
                FLocation fLocation = new FLocation(player.getLocation());
                // If player is in crystal zone faction then teleport them to surface
                if (Board.getInstance().getFactionAt(fLocation).getId().equals("-3") && !player.getLocation().getBlock().getType().isAir()) {
                    for (int y = 64; y < 150; y++) {
                        Block block = player.getWorld().getBlockAt(player.getLocation().getBlockX(), y, player.getLocation().getBlockZ());
                        if (block.getType().isAir() && block.getRelative(BlockFace.UP).getType().isAir() && block.getRelative(BlockFace.DOWN).isSolid()) {
                            Schedulers.sync().run(() -> player.teleport(block.getLocation()));
                        }
                    }
                }
            });
        }
    }

    public Instant getNextRegen() {
        return nextRegen;
    }
}
