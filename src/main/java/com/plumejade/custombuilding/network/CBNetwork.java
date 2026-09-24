package com.plumejade.custombuilding.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network setup. */
public final class CBNetwork {
    private static final String VERSION = "1";

    private CBNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(BlueprintSyncPayload.TYPE, BlueprintSyncPayload.STREAM_CODEC, BlueprintSyncPayload::handle);
        registrar.playToClient(SchematicResponsePayload.TYPE, SchematicResponsePayload.STREAM_CODEC, SchematicResponsePayload::handle);
        registrar.playToServer(BlueprintBuildPayload.TYPE, BlueprintBuildPayload.STREAM_CODEC, BlueprintBuildPayload::handle);
        registrar.playToServer(SchematicRequestPayload.TYPE, SchematicRequestPayload.STREAM_CODEC, SchematicRequestPayload::handle);
    }
}
