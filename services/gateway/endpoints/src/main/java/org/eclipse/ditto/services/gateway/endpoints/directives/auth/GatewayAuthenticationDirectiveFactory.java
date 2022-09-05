/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

/**
 * Factory for authentication directives.
 */
public interface GatewayAuthenticationDirectiveFactory {

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for HTTP API.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildHttpAuthentication();

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for WebSocket API.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildWsAuthentication();
}