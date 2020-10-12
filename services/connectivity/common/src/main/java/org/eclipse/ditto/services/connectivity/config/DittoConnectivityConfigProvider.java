/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.config;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Implements {@link ConnectivityConfigProvider} by providing a {@link ScopedConfig} from the given
 * {@link com.typesafe.config.Config}.
 */
public class DittoConnectivityConfigProvider implements ConnectivityConfigProvider {

    private final DittoConnectivityConfig connectivityConfig;

    public DittoConnectivityConfigProvider(final ActorSystem actorSystem) {
        this.connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
    }

    @Override
    public ConnectivityConfig getConnectivityConfig(final EntityId connectionId) {
        return connectivityConfig;
    }

    @Override
    public ConnectivityConfig getConnectivityConfig(final DittoHeaders dittoHeaders) {
        return connectivityConfig;
    }

    @Override
    public CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(final EntityId connectionId) {
        return CompletableFuture.completedFuture(connectivityConfig);
    }

    @Override
    public CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(final DittoHeaders dittoHeaders) {
        return CompletableFuture.completedFuture(connectivityConfig);
    }

    @Override
    public void registerForChanges(final EntityId connectionId, final ActorRef sender) {
        // noop
    }
}
