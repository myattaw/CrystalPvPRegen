package me.rages.crystalpvpregen;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class CrystalRegion {

    private String name;
    private World world;
    private BlockVector3 pos1;
    private BlockVector3 pos2;

    private CrystalRegion(String name, String world, int x, int y, int z, int x2, int y2, int z2) {
        this.name = name;
        this.world = Bukkit.getWorld(world);
        this.pos1 = BlockVector3.at(x, y, z);
        this.pos2 = BlockVector3.at(x2, y2, z2);
    }

    public static CrystalRegion create(String time, String world, int x, int y, int z, int x2, int y2, int z2) {
        return new CrystalRegion(time, world, x, y, z, x2, y2, z2);
    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        return world;
    }

    public BlockVector3 getPos1() {
        return pos1;
    }

    public BlockVector3 getPos2() {
        return pos2;
    }
}
