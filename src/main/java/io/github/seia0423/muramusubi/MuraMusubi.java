package io.github.seia0423.muramusubi;

import com.mojang.logging.LogUtils;
import io.github.seia0423.muramusubi.command.MuraMusubiCommands;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.world.RoadBuildService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MuraMusubi.MOD_ID)
public final class MuraMusubi {
    public static final String MOD_ID = "muramusubi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MuraMusubi(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, MuraMusubiConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(MuraMusubiCommands::register);
        NeoForge.EVENT_BUS.addListener(RoadBuildService::tick);
        LOGGER.info("Mura Musubi を読み込みました");
    }
}
