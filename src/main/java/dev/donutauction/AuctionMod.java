package dev.donutauction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuctionMod implements ClientModInitializer {
    public static final String MOD_ID = "donutauction";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openKey;
    private static volatile boolean openMenuRequested = false;

    @Override
    public void onInitializeClient() {
        AuctionConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutauction.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) openMenuRequested = true;

            // Opened from a tick (not straight from a command) so the chat screen has closed first.
            if (openMenuRequested && client.currentScreen == null && client.player != null) {
                openMenuRequested = false;
                client.setScreen(new AuctionScreen());
            }
            AuctionManager.tick(client);
        });

        AuctionHud.register();
        registerCommands();
        LOGGER.info("Donut Auction Manager loaded.");
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("aucmenu").executes(ctx -> {
                openMenuRequested = true;
                return 1;
            }));

            dispatcher.register(ClientCommandManager.literal("auc")
                    .executes(ctx -> {
                        openMenuRequested = true;
                        return 1;
                    })
                    // /auc start  -> uses the last settings from the menu
                    .then(ClientCommandManager.literal("start").executes(ctx ->
                            report(ctx.getSource(), AuctionManager.start(
                                    MinecraftClient.getInstance(), AuctionConfig.get().last.copy(), -1))))
                    .then(ClientCommandManager.literal("cancel").executes(ctx -> {
                        AuctionManager.cancel(MinecraftClient.getInstance());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal("[Auction] " + AuctionManager.statusLine()));
                        return 1;
                    }))
                    // /auc preset <name>  -> start a saved preset
                    .then(ClientCommandManager.literal("preset")
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) ->
                                            CommandSource.suggestMatching(AuctionConfig.get().presetNames(), builder))
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        AuctionConfig.Preset p = AuctionConfig.get().findPreset(name);
                                        if (p == null) {
                                            ctx.getSource().sendError(Text.literal("No preset named '" + name + "'."));
                                            return 0;
                                        }
                                        return report(ctx.getSource(), AuctionManager.start(
                                                MinecraftClient.getInstance(), p.copy(), -1));
                                    })))
            );
        });
    }

    private static int report(FabricClientCommandSource source, String error) {
        if (error == null) return 1;
        source.sendError(Text.literal(error));
        return 0;
    }
}
