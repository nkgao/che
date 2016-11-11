/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.environment.server;

import com.google.common.collect.ImmutableMap;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.ExtendedMachine;
import org.eclipse.che.api.environment.server.model.CheServiceImpl;
import org.eclipse.che.api.environment.server.model.CheServicesEnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentRecipeImpl;
import org.eclipse.che.api.workspace.server.model.impl.ExtendedMachineImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexander Garagatyi
 */
@Listeners(MockitoTestNGListener.class)
public class EnvironmentParserTest {

    private static final String TEXT                 = "to be or not to be";
    private static final String DEFAULT_MACHINE_NAME = "dev-machine";
    private static final String DEFAULT_DOCKERFILE   = "FROM codenvy/ubuntu_jdk8\n";
    private static final String DEFAULT_DOCKER_IMAGE = "codenvy/ubuntu_jdk8";

    @Mock
    private EnvironmentImpl               environment;
    @Mock
    private EnvironmentRecipeImpl         recipe;
    @Mock
    private ExtendedMachineImpl           machine;
    @Mock
    private TypeSpecificEnvironmentParser envParser;
    @Mock
    private Map<String, TypeSpecificEnvironmentParser> parsers;
    @Mock
    private CheServicesEnvironmentImpl cheEnv;
    @Mock
    private CheServiceImpl cheService1;
    @Mock
    private CheServiceImpl cheService2;
    @Mock
    private ExtendedMachineImpl extendedMachine1;
    @Mock
    private ExtendedMachineImpl extendedMachine2;

    private EnvironmentParser parser;

    @BeforeMethod
    public void setUp() throws ServerException {
        when(environment.getRecipe()).thenReturn(recipe);
        when(recipe.getType()).thenReturn(TEXT);
        when(recipe.getContent()).thenReturn(TEXT);
        when(envParser.parse(environment)).thenReturn(cheEnv);

        parser = spy(new EnvironmentParser(singletonMap(TEXT, envParser)));
    }

    @Test
    public void shouldReturnEnvTypesCoveredByTests() throws Exception {
        // when
        Set<String> environmentTypes = parser.getEnvironmentTypes();

        // then
        assertEquals(environmentTypes, singletonList(TEXT));
        assertEquals(environmentTypes.size(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "Environment type '.*' is not supported. " +
                                            "Supported environment types: " + TEXT)
    public void shouldThrowExceptionOnParsingUnknownEnvironmentType() throws Exception {
        parser.parse(new EnvironmentImpl(new EnvironmentRecipeImpl("unknownType",
                                                                   "text/x-dockerfile",
                                                                   "content", null),
                                         null));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "Environment should not be null")
    public void environmentShouldNotBeNull() throws ServerException {
        parser.parse(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "Environment recipe should not be null")
    public void environmentRecipeShouldNotBeNull() throws ServerException {
        when(environment.getRecipe()).thenReturn(null);

        parser.parse(environment);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "Environment recipe type should not be null")
    public void recipeTypeShouldNotBeNull() throws ServerException {
        when(recipe.getType()).thenReturn(null);

        parser.parse(environment);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = "Recipe of environment must contain location or content")
    public void recipeShouldContainsContentOrLocationNotBeNull() throws ServerException {
        when(recipe.getContent()).thenReturn(null);

        parser.parse(environment);
    }

    @Test
    public void environmentShouldBeParsed() throws ServerException {
        //given
        doNothing().when(parser).normalizeMachine(any(String.class),
                                                  any(CheServiceImpl.class),
                                                  any(ExtendedMachine.class));

        //when
        parser.parse(environment);

        //then
        verify(envParser).parse(environment);
    }

    @Test
    public void allServicesInTheCheServicesEnvironmentShouldBeNormalized() throws ServerException {
        //given
        doNothing().when(parser).normalizeMachine(any(String.class),
                                                  any(CheServiceImpl.class),
                                                  any(ExtendedMachine.class));
        when(cheEnv.getServices()).thenReturn(ImmutableMap.of(TEXT, cheService1, TEXT + 2, cheService2));
        when(environment.getMachines()).thenReturn(ImmutableMap.of(TEXT, extendedMachine1, TEXT + 2, extendedMachine2));

        //when
        parser.parse(environment);

        //then
        verify(envParser).parse(environment);
        verify(parser, times(2)).normalizeMachine(any(String.class),
                                                  any(CheServiceImpl.class),
                                                  any(ExtendedMachine.class));
    }
}
