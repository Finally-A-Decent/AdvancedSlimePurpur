package com.grinderwolf.swm.nms.v1_16_R1;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.api.world.properties.SlimePropertyMap;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import lombok.Getter;
import lombok.SneakyThrows;
import net.md_5.bungee.api.ChatColor;
import net.minecraft.server.v1_16_R1.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.IOException;
import java.util.Arrays;

@Getter
public class v1_16_R1SlimeNMS implements SlimeNMS {

    private static final Logger LOGGER = LogManager.getLogger("SWM");

    private final byte worldVersion = 0x05;

    public v1_16_R1SlimeNMS() {
        try {
            CraftCLSMBridge.initialize(this);
        } catch (NoClassDefFoundError ex) {
            LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
            System.exit(1); // No ClassModifier, no party
        }
    }

    @SneakyThrows
    public static Convertable.ConversionSession getConversionSession(String worldName, MinecraftServer mcServer, ResourceKey<WorldDimension> dimensionKey) {
        return Convertable.a(mcServer.server.getWorldContainer().toPath()).c(worldName, dimensionKey);
    }

    @SneakyThrows
    @Override
    public void generateWorld(SlimeWorld world) {
        CustomWorldServer server = createNMSWorld(world);

        MinecraftServer mcServer = MinecraftServer.getServer();

        LOGGER.info("Loading world " + server.getWorld().getName());
        long startTime = System.currentTimeMillis();

        server.setReady(true);
        mcServer.initWorld(server, server.worldDataServer, null, server.worldDataServer.getGeneratorSettings());

        mcServer.server.addWorld(server.getWorld());
        mcServer.worldServer.put(server.getDimensionKey(), server);

        Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));

        if (server.getWorld().getKeepSpawnInMemory()) {
            LOGGER.debug("Preparing start region for dimension '{}'", server.getWorld().getName());
            BlockPosition spawn = server.getSpawn();
            ChunkProviderServer provider = server.getChunkProvider();
            provider.addTicket(TicketType.START, new ChunkCoordIntPair(spawn), 11, Unit.INSTANCE);
        }

        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));

        LOGGER.info("World " + server.getWorld().getName() + " loaded in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;

        if (!(craftWorld.getHandle() instanceof CustomWorldServer)) {
            return null;
        }

        CustomWorldServer worldServer = (CustomWorldServer) craftWorld.getHandle();
        return worldServer.getSlimeWorld();
    }

    @Override
    public CompoundTag convertChunk(CompoundTag tag) {
        NBTTagCompound nmsTag = (NBTTagCompound) Converter.convertTag(tag);
        int version = nmsTag.getInt("DataVersion");

        NBTTagCompound newNmsTag = GameProfileSerializer.a(DataConverterRegistry.a(), DataFixTypes.CHUNK, nmsTag, version);

        return (CompoundTag) Converter.convertTag("", newNmsTag);
    }

    @Override
    public CustomWorldServer createNMSWorld(SlimeWorld world) {
        String worldName = world.getName();

        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalArgumentException("World " + worldName + " already exists! Maybe it's an outdated SlimeWorld object?");
        }

        ResourceKey<net.minecraft.server.v1_16_R1.World> worldKey = ResourceKey.a(IRegistry.ae, new MinecraftKey(worldName));

        MinecraftServer mcServer = MinecraftServer.getServer();

        Convertable.ConversionSession conversionSession = getConversionSession(worldName, mcServer, WorldDimension.OVERWORLD);

        CustomNBTStorage dataManager = new CustomNBTStorage(world, conversionSession);


        World.Environment env = World.Environment.valueOf(world.getPropertyMap().getString(SlimeProperties.ENVIRONMENT).toUpperCase());

        DimensionManager dimensionManager = mcServer.f.a().fromId(env.getId());
        WorldDataServer worldData = (WorldDataServer)dataManager.getWorldData();

        CustomWorldServer server = null;

        try {
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-pre: " + server);
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-world: " + world.getName());
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-DM: " + dimensionManager);
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-env: " + env.getId());
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-CG: " + worldData.getGeneratorSettings());
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-WS: " + worldData);
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-Dir: " + conversionSession.folder.toString());
            server = new CustomWorldServer((CraftSlimeWorld) world, dataManager, conversionSession, dimensionManager, env, worldData, worldKey, DimensionManager.OVERWORLD, Arrays.asList(new MobSpawnerCat()), false, false);
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "SLIME-WORLD-NAME: " + server.getSlimeWorld().getName());
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "SERVER-WORLD-NAME: " + server.getWorld().getName());
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "SLIMEWORLD-NAME: " + worldName);
        } catch(IOException e) {
            Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-error: " + server);
            e.printStackTrace();
            return null;
        }
        Bukkit.broadcastMessage(ChatColor.of("#590c0c") + "Server-post: " + server);
        return server;
    }

    @Override
    public void addWorldToServerList(Object worldObject) {
        if (!(worldObject instanceof WorldServer)) {
            throw new IllegalArgumentException("World object must be an instance of WorldServer!");
        }

        CustomWorldServer server = (CustomWorldServer) worldObject;
        String worldName = server.getWorld().getName();

        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalArgumentException("World " + worldName + " already exists! Maybe it's an outdated SlimeWorld object?");
        }

        LOGGER.info("Async Loading world " + worldName);
        long startTime = System.currentTimeMillis();

        server.setReady(true);
        MinecraftServer mcServer = MinecraftServer.getServer();

        mcServer.server.addWorld(server.getWorld());
        MinecraftServer.getServer().worldServer.put(server.getDimensionKey(), server);

        Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));

        SlimePropertyMap worldProperties = server.getSlimeWorld().getPropertyMap();

        server.getWorld().setSpawnLocation(
            worldProperties.getInt(SlimeProperties.SPAWN_X),
            worldProperties.getInt(SlimeProperties.SPAWN_Y),
            worldProperties.getInt(SlimeProperties.SPAWN_Z)
        );

        LOGGER.info("Async World " + worldName + " loaded in " + (System.currentTimeMillis() - startTime) + "ms.");
    }
}
