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
            	ServerPlayer target = findRandomAlivePlayer(server, player);
                if (target != null) {
                    player.setCamera(target);
                    player.setInvulnerable(true);
                    player.setNoGravity(true);
                }
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
