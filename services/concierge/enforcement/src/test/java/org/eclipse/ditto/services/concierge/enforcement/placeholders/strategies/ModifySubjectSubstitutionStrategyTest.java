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
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.junit.Test;

/**
 * Tests {@link ModifySubjectSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifySubjectSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {

    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifySubjectSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(POLICY_ID,
                Label.of(LABEL), Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final ModifySubject commandWithPlaceholders = ModifySubject.of(POLICY_ID,
                Label.of(LABEL), Subject.newInstance(SUBJECT_ID_PLACEHOLDER, SubjectType.GENERATED), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final ModifySubject expectedCommandReplaced = ModifySubject.of(commandWithPlaceholders.getEntityId(),
                commandWithPlaceholders.getLabel(), Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}