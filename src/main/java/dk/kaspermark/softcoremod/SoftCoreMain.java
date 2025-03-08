package dk.kaspermark.softcoremod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.UUID;


@Mod(SoftCoreMain.MODID)
public class SoftCoreMain {
    public static final String MODID = "softcore";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SPECTATOR_TIME = 20 * 20; // 10 sekunder i ticks (20 ticks = 1 sekund)
    private final HashMap<UUID, Integer> deadPlayers = new HashMap<>();

    public SoftCoreMain() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    	MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    	ServerPlayer player = (ServerPlayer) event.getEntity();
        
        if (!player.getPersistentData().getBoolean("hasJoinedBefore")) {
            player.getPersistentData().putBoolean("hasJoinedBefore", true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Velkommen til serveren! Lorem ipsum dolor sit amet..."));
        }
        
        if ((player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) && !deadPlayers.containsKey(player.getUUID())) {
            respawnPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
    	MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getEntity() instanceof ServerPlayer player) {
        	player.drop(true);
            deadPlayers.put(player.getUUID(), SPECTATOR_TIME);
            if (server.getPlayerList().getPlayers().size() == 1)
            {            	
                player.setGameMode(GameType.ADVENTURE);
            }else
            {
            	player.setGameMode(GameType.SPECTATOR);
            }
        }
    }
    
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    	MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getEntity() instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
        	ServerPlayer target = findRandomAlivePlayer(server, player);
            if (target != null) {
                player.setCamera(target);
                player.setInvulnerable(true);
                player.setNoGravity(true);
            }
        }
    }    
    
    private ServerPlayer findRandomAlivePlayer(MinecraftServer server, ServerPlayer deadPlayer) {
        List<ServerPlayer> alivePlayers = server.getPlayerList().getPlayers().stream()
            .filter(p -> !p.isSpectator() && !p.getUUID().equals(deadPlayer.getUUID()))
            .toList();

        if (!alivePlayers.isEmpty()) {
            return alivePlayers.get(new Random().nextInt(alivePlayers.size()));
        }
        deadPlayer.setGameMode(GameType.ADVENTURE);
        return null; 
    }

    @SubscribeEvent
    public void onPlayerDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            deadPlayers.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                int ticksLeft = entry.getValue() - 1;

                if (ticksLeft <= 0) {
                    ServerPlayer player = playerByUUID(playerId);
                    if (player != null) {
                        respawnPlayer(player);
                    }
                    return true;
                } else {
                    entry.setValue(ticksLeft);
                    return false;
                }
            });
        }
    }

    private ServerPlayer playerByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getPlayerList().getPlayer(uuid);
        }
        return null;
    }

    private void respawnPlayer(ServerPlayer player) {
        // Find spillerens respawn-position
        ServerLevel world = player.serverLevel();
        BlockPos respawnPos = player.getRespawnPosition();
        
        if (respawnPos == null) {
            // Hvis spilleren ikke har et specifikt spawnpoint, brug verdens default spawn
            respawnPos = world.getSharedSpawnPos();
        }

        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20); 
        player.getFoodData().setSaturation(5.0f);
        
        // Sæt spilleren tilbage til survival mode og teleportér til spawn
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(respawnPos.getX() + 0.5, respawnPos.getY(), respawnPos.getZ() + 0.5);
    }
}