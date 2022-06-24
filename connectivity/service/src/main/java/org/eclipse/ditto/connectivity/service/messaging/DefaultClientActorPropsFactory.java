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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.HonoConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpPushClientActor;
import org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.HiveMqtt3ClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.HiveMqtt5ClientActor;
import org.eclipse.ditto.connectivity.service.messaging.rabbitmq.RabbitMQClientActor;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}. Singleton which is created just once
 * and otherwise returns the already created instance.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    @Nullable private static DefaultClientActorPropsFactory instance;

    private DefaultClientActorPropsFactory() {}

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}. Creates a new one if not already done.
     *
     * @return the factory instance.
     */
    public static DefaultClientActorPropsFactory getInstance() {
        if (null == instance) {
            instance = new DefaultClientActorPropsFactory();
        }
        return instance;
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorSystem actorSystem,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        final ConnectionType connectionType = connection.getConnectionType();
        final Props result;
        switch (connectionType) {
            case AMQP_091:
                result = RabbitMQClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case AMQP_10:
                result = AmqpClientActor.props(connection, proxyActor, connectionActor, connectivityConfigOverwrites,
                        actorSystem, dittoHeaders);
                break;
            case MQTT:
                result = HiveMqtt3ClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case MQTT_5:
                result = HiveMqtt5ClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case KAFKA:
                result = KafkaClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            case HONO:
                result = KafkaClientActor.props(getEnrichedConnection(actorSystem, connection),
                        proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
                break;
            case HTTP_PUSH:
                result = HttpPushClientActor.props(connection, proxyActor, connectionActor, dittoHeaders,
                        connectivityConfigOverwrites);
                break;
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
        return result;
    }

    private Connection getEnrichedConnection(final ActorSystem actorSystem, final Connection connection) {
        var honoConfig = HonoConfig.get(actorSystem);
        return ConnectivityModelFactory.newConnectionBuilder(
                        connection.getId(),
                        connection.getConnectionType(),
                        connection.getConnectionStatus(),
                        honoConfig.getBaseUri())
                .validateCertificate(honoConfig.getValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().getValue(),
                        "bootstrapServers", honoConfig.getBootstrapServers()))
                .credentials(honoConfig.getCredentials(connection.getId()))
                .sources(connection.getSources()
                        .stream()
                        .map(source -> ConnectivityModelFactory.sourceFromJson(
                                resolveSourceAliases(source, honoConfig.getTenantId(connection.getId())), 1))
                        .toList())
                .targets(connection.getTargets()
                        .stream()
                        .map(target -> ConnectivityModelFactory.targetFromJson(
                                resolveTargetAlias(target, honoConfig.getTenantId(connection.getId()))))
                        .toList())
                .build();
    }

    private JsonObject resolveSourceAliases(final Source source, String tenantId) {
        return JsonFactory.newObjectBuilder(source.toJson())
                .set(Source.JsonFields.REPLY_TARGET, )
                .set(Source.JsonFields.ADDRESSES, JsonArray.of(source.getAddresses().stream()
                        .map(a -> HonoAddressAlias.fromName(a) + "/" + tenantId)
                        .map(JsonValue::of)
                        .toList()))
                .build();
    }

    private JsonObject resolveTargetAlias(final Target target, String tenantId) {
        return JsonFactory.newObjectBuilder(target.toJson())
                .set(Target.JsonFields.ADDRESS, Optional.of(target.getAddress())
                        .map(a -> HonoAddressAlias.fromName(a) + "/" + tenantId)
                        .orElse("")
                )
                .build();
    }

}
