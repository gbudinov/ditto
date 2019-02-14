/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.query.expression;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementation of {@link ThingsFieldExpressionFactory}.
 */
public final class ThingsFieldExpressionFactoryImpl implements ThingsFieldExpressionFactory {

    private static final Map<String, String> mongoSimpleFieldMappings = new HashMap<>();

    static {
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    private final Map<String, String> simpleFieldMappings;

    /**
     * Creates a ThingsFieldExpressionFactory with default field mappings (MongoDB).
     */
    public ThingsFieldExpressionFactoryImpl() {
        this(mongoSimpleFieldMappings);
    }

    /**
     * Creates a ThingsFieldExpressionFactory with custom field mappings.
     *
     * @param simpleFieldMappings the field mappings to apply
     */
    public ThingsFieldExpressionFactoryImpl(final Map<String, String> simpleFieldMappings) {
        this.simpleFieldMappings = simpleFieldMappings;
    }

    @Override
    public FilterFieldExpression filterBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        final Supplier<FilterFieldExpression> defaultSupplier = () -> (FilterFieldExpression) common(propertyName);
        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .map(f -> f.getProperty()
                        .map(property -> f.getFeatureId()
                                // we have a feature id and a property path
                                .map(id -> (FilterFieldExpression) new FeatureIdPropertyExpressionImpl(id, property))
                                // we have a property path
                                .orElseGet(() -> new FeaturePropertyExpressionImpl(property))
                        )
                        // we have a feature field but no property path, this is invalid for filter operations
                        .orElseGet(defaultSupplier)
                )
                // we have no feature at all, continue with the other possibilities
                .orElseGet(defaultSupplier);
    }

    @Override
    public ExistsFieldExpression existsBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .map(f -> f.getFeatureId()
                        .map(id -> f.getProperty()
                                // we have a feature id and a property path
                                .map(property -> (ExistsFieldExpression) new FeatureIdPropertyExpressionImpl(id,
                                        property))
                                // we have a feature id but no property path
                                .orElseGet(() -> new FeatureExpressionImpl(id))
                        )
                        // we have a feature field but no feature id, means it must be a property path
                        .orElseGet(() -> (ExistsFieldExpression) f.getProperty()
                                .map((String property) -> (ExistsFieldExpression) new FeaturePropertyExpressionImpl(
                                        property))
                                // at this point, we know that there must be a property, so the following throw should
                                // never happen and is only there to make the compiler happy:
                                .orElseThrow(() ->
                                        new IllegalStateException("Illegal state while parsing feature property path."))
                        )
                )
                // we have no feature at all, continue with the other possibilities
                .orElseGet(() -> (ExistsFieldExpression) common(propertyName));
    }

    @Override
    public SortFieldExpression sortBy(final String propertyNameWithOptionalLeadingSlash)
            throws IllegalArgumentException {

        requireNonNull(propertyNameWithOptionalLeadingSlash);
        final String propertyName = stripLeadingSlash(propertyNameWithOptionalLeadingSlash);

        return FieldExpressionUtil.parseFeatureField(requireNonNull(propertyName))
                .flatMap(f -> f.getFeatureId()
                        .flatMap(id -> f.getProperty()
                                .flatMap(property -> Optional
                                        .of((SortFieldExpression) new FeatureIdPropertyExpressionImpl(id, property))
                                )
                        )
                )
                .orElseGet(() -> (SortFieldExpression) common(propertyName));
    }

    /**
     * We have to strip the leading slash, because the propertyName may be specified as Json Pointer and our data
     * representation does not contain a leading slash.
     *
     * @param propertyName the property name which may start with a slash
     * @return the propertyName without leading slash
     */
    private static String stripLeadingSlash(final String propertyName) {
        requireNonNull(propertyName);
        if (propertyName.startsWith("/")) {
            return propertyName.substring(1);
        } else {
            return propertyName;
        }
    }

    /**
     * internal factory method for fields where the filter and the sortOptions field expression is the same.
     *
     * @throws IllegalArgumentException if the property can not be mapped. Because of this, call this method last in the
     * workflow.
     */
    private FieldExpression common(final String propertyName) throws IllegalArgumentException {
        if (FieldExpressionUtil.isAttributeFieldName(propertyName)) {
            return new AttributeExpressionImpl(FieldExpressionUtil.stripAttributesPrefix(propertyName));
        }

        final String fieldName = simpleFieldMappings.get(propertyName);
        if (fieldName != null) {
            return new SimpleFieldExpressionImpl(fieldName);
        }

        throw new IllegalArgumentException("Unknown property name: " + propertyName);
    }

    @Override
    public ExistsFieldExpression existsByFeatureId(final String featureId) {
        return new FeatureExpressionImpl(featureId);
    }

    @Override
    public FilterFieldExpression filterByFeatureProperty(final String property) {
        return new FeaturePropertyExpressionImpl(property);
    }

    @Override
    public ExistsFieldExpression existsByFeatureProperty(final String property) {
        return new FeaturePropertyExpressionImpl(property);
    }

    @Override
    public FilterFieldExpression filterByFeatureProperty(final String featureId, final String property) {
        return new FeatureIdPropertyExpressionImpl(featureId, property);
    }

    @Override
    public ExistsFieldExpression existsByFeatureProperty(final String featureId, final String property) {
        return new FeatureIdPropertyExpressionImpl(featureId, property);
    }

    @Override
    public SortFieldExpression sortByFeatureProperty(final String featureId, final String property) {
        return new FeatureIdPropertyExpressionImpl(featureId, property);
    }

    @Override
    public FilterFieldExpression filterByAttribute(final String key) {
        return new AttributeExpressionImpl(key);
    }

    @Override
    public ExistsFieldExpression existsByAttribute(final String key) {
        return new AttributeExpressionImpl(key);
    }

    @Override
    public SortFieldExpression sortByAttribute(final String key) {
        return new AttributeExpressionImpl(requireNonNull(key));
    }

    @Override
    public FilterFieldExpression filterByAcl() {
        return new ThingsAclFieldExpressionImpl();
    }

    @Override
    public FilterFieldExpression filterByGlobalRead() {
        return new ThingsGlobalReadsFieldExpressionImpl();
    }

    @Override
    public FilterFieldExpression filterByThingId() {
        return new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
    }

    @Override
    public SortFieldExpression sortByThingId() {
        return new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_ID);
    }

    @Override
    public FilterFieldExpression filterByNamespace() {
        return new SimpleFieldExpressionImpl(FieldExpressionUtil.FIELD_NAMESPACE);
    }
}
