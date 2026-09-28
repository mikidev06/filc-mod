package me.mikidev06.filc_mod.client;

import me.mikidev06.filc_mod.client.command.FilcCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class Filc_modClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> FilcCommand.register(dispatcher));

        // Screens queued from a command or a network callback are opened here.
        // Setting one directly from a command does not survive the chat screen
        // closing itself immediately afterwards.
        ClientTickEvents.END_CLIENT_TICK.register(client -> FilcState.get().drainPendingScreen(client));

        // Warm the reference lists so tab completion has something to offer the
        // first time the player types the command. All three are cached for a
        // day, so this costs three requests per session at most.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FilcState state = FilcState.get();
            state.loadCohorts().exceptionally(error -> null);
            state.loadTeachers().exceptionally(error -> null);
            state.loadClassrooms().exceptionally(error -> null);
        });
    }
}
