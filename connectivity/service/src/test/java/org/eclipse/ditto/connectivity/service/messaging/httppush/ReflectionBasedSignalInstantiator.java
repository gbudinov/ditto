/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

/**
 * Uses Java's reflection functionality to instantiate an object for a particular class of a {@link SignalWithEntityId}.
 * The arguments that are used for the static factory method are statically provided.
 * Their only purpose is to technically enable a valid call to static factory method.
 * If an argument is {@code @Nullable}, {@code null} will be used.
 */
@Immutable
final class ReflectionBasedSignalInstantiator {

    private static final Map<Class<?>, Object> PARAMETER_VALUES_PER_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionBasedSignalInstantiator.class);

    static {
        final var feature = Feature.newBuilder().withId("myFeature").build();
        final var thingId = ThingId.generateRandom();
        final var stringValue = "myAttributeOrFeature";
        final var messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, thingId, "mySubject")
                .featureId(stringValue)
                .randomCorrelationId()
                .build();
        final var definitionIdentifier = "org.example:myDefinition:1.0.0";
        PARAMETER_VALUES_PER_TYPE = Map.ofEntries(
                Map.entry(Attributes.class, Attributes.newBuilder().set("manufacturer", "Bosch.IO").build()),
                Map.entry(CharSequence.class, stringValue),
                Map.entry(DittoHeaders.class, DittoHeaders.newBuilder().randomCorrelationId().build()),
                Map.entry(DittoRuntimeException.class, AskException.newBuilder().build()),
                Map.entry(Feature.class, feature),
                Map.entry(Features.class, Features.newBuilder().set(feature).build()),
                Map.entry(FeatureDefinition.class, FeatureDefinition.fromIdentifier(definitionIdentifier)),
                Map.entry(FeatureProperties.class, FeatureProperties.newBuilder().set("fleeb", "noob").build()),
                Map.entry(HttpStatus.class, HttpStatus.OK),
                Map.entry(JsonArray.class, JsonArray.of(JsonValue.of(definitionIdentifier))),
                Map.entry(JsonObject.class, JsonObject.newBuilder().set("foo", "bar").build()),
                Map.entry(JsonPointer.class, JsonPointer.of("foo/bar/baz")),
                Map.entry(JsonValue.class, JsonObject.newBuilder().set("bar", "baz").build()),
                Map.entry(Message.class, Message.newBuilder(messageHeaders).payload("myPayload").build()),
                Map.entry(MessageHeaders.class, messageHeaders),
                Map.entry(PolicyId.class, PolicyId.inNamespaceWithRandomName("")),
                Map.entry(String.class, stringValue),
                Map.entry(Thing.class, Thing.newBuilder().setId(thingId).build()),
                Map.entry(ThingDefinition.class, ThingsModelFactory.newDefinition(definitionIdentifier)),
                Map.entry(ThingId.class, thingId)
        );
    }

    private ReflectionBasedSignalInstantiator() {
        throw new AssertionError();
    }

    static <T extends SignalWithEntityId<?>> Try<T> tryToInstantiateSignal(final Class<T> signalImplementationClass) {
        try {
            return new Success<>(instantiateSignal(signalImplementationClass));
        } catch (final Exception e) {
            LOGGER.warn("Failed to instantiate Signal for <{}>: {}",
                    signalImplementationClass.getName(),
                    e.getMessage(),
                    e);
            return new Failure<>(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends SignalWithEntityId<?>> T instantiateSignal(final Class<T> signalImplementationClass)
            throws InvocationTargetException, IllegalAccessException {

        final var staticFactoryMethod = getStaticFactoryMethodOrThrow(signalImplementationClass);
        final var parameterValues = getParameterValues(staticFactoryMethod);
        return (T) staticFactoryMethod.invoke(null, parameterValues.toArray());
    }

    private static Method getStaticFactoryMethodOrThrow(final Class<?> clazz) {
        final var acceptedStaticFactoryMethodNames = List.of("of", "newInstance", "getInstance", "created", "modified");
        return Stream.of(clazz.getDeclaredMethods())
                .filter(method -> acceptedStaticFactoryMethodNames.contains(method.getName()))
                .filter(method -> {
                    final var modifiers = method.getModifiers();
                    return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
                })
                .findAny()
                .orElseThrow(() -> {
                    final var pattern = "Found no static factory method with any name of {0}.";
                    return new NoSuchElementException(MessageFormat.format(pattern, acceptedStaticFactoryMethodNames));
                });
    }

    private static Collection<Object> getParameterValues(final Method staticFactoryMethod) {
        final var parameterTypes = staticFactoryMethod.getParameterTypes();
        final var parameterAnnotations = staticFactoryMethod.getParameterAnnotations();
        final Collection<Object> result = new ArrayList<>(parameterTypes.length);
        for (var i = 0; i < parameterTypes.length; i++) {
            final var parameterType = parameterTypes[i];
            if (isAnnotatedNullable(parameterAnnotations[i])) {
                result.add(null);
            } else {
                result.add(getSuitableParameterValueOrNull(parameterType));
            }
        }
        return result;
    }

    private static boolean isAnnotatedNullable(final Annotation[] annotations) {
        var result = false;
        for (final var annotation : annotations) {
            if (Nullable.class.equals(annotation.annotationType())) {
                result = true;
                break;
            }
        }
        return result;
    }

    @Nullable
    private static Object getSuitableParameterValueOrNull(final Class<?> parameterType) {
        final var result = PARAMETER_VALUES_PER_TYPE.get(parameterType);
        if (null == result) {
            LOGGER.warn("Found not value for parameter type <{}>. Using null instead.", parameterType.getName());
        }
        return result;
    }

}
