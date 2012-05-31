/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.integration.jpa.config.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.jpa.core.JpaExecutor;
import org.springframework.integration.jpa.core.JpaOperations;
import org.springframework.integration.jpa.outbound.JpaOutboundGateway;
import org.springframework.integration.jpa.support.OutboundGatewayType;
import org.springframework.integration.jpa.support.PersistMode;
import org.springframework.integration.test.util.TestUtils;

/**
 * @author Gunnar Hillert
 * @author Amol Nayak
 * @since 2.2
 *
 */
public class JpaOutboundGatewayParserTests {

	private ConfigurableApplicationContext context;

	private EventDrivenConsumer consumer;

	@Test
	public void testRetrievingJpaOutboundGatewayParser() throws Exception {
		setUp("JpaOutboundGatewayParserTests.xml", getClass(), "retrievingJpaOutboundGateway");


		final AbstractMessageChannel inputChannel = TestUtils.getPropertyValue(this.consumer, "inputChannel", AbstractMessageChannel.class);

		assertEquals("in", inputChannel.getComponentName());

		final JpaOutboundGateway jpaOutboundGateway = TestUtils.getPropertyValue(this.consumer, "handler", JpaOutboundGateway.class);

		final OutboundGatewayType gatewayType = TestUtils.getPropertyValue(jpaOutboundGateway, "gatewayType", OutboundGatewayType.class);

		assertEquals(OutboundGatewayType.RETRIEVING, gatewayType);

		long sendTimeout = TestUtils.getPropertyValue(jpaOutboundGateway, "messagingTemplate.sendTimeout", Long.class);

		assertEquals(100, sendTimeout);


		final JpaExecutor jpaExecutor = TestUtils.getPropertyValue(this.consumer, "handler.jpaExecutor", JpaExecutor.class);

		assertNotNull(jpaExecutor);

		final Class<?> entityClass = TestUtils.getPropertyValue(jpaExecutor, "entityClass", Class.class);

		assertEquals("org.springframework.integration.jpa.test.entity.StudentDomain", entityClass.getName());

		final JpaOperations jpaOperations = TestUtils.getPropertyValue(jpaExecutor, "jpaOperations", JpaOperations.class);

		assertNotNull(jpaOperations);

		assertTrue(TestUtils.getPropertyValue(jpaExecutor, "expectSingleResult", Boolean.class));

		final Integer maxNumberOfResults = TestUtils.getPropertyValue(jpaExecutor, "maxNumberOfResults", Integer.class);

		assertEquals(Integer.valueOf(55), maxNumberOfResults);

	}

	@Test
	public void testUpdatingJpaOutboundGatewayParser() throws Exception {
		setUp("JpaOutboundGatewayParserTests.xml", getClass(), "updatingJpaOutboundGateway");


		final AbstractMessageChannel inputChannel = TestUtils.getPropertyValue(this.consumer, "inputChannel", AbstractMessageChannel.class);

		assertEquals("in", inputChannel.getComponentName());

		final JpaOutboundGateway jpaOutboundGateway = TestUtils.getPropertyValue(this.consumer, "handler", JpaOutboundGateway.class);

		final OutboundGatewayType gatewayType = TestUtils.getPropertyValue(jpaOutboundGateway, "gatewayType", OutboundGatewayType.class);

		assertEquals(OutboundGatewayType.UPDATING, gatewayType);

		long sendTimeout = TestUtils.getPropertyValue(jpaOutboundGateway, "messagingTemplate.sendTimeout", Long.class);

		assertEquals(100, sendTimeout);

		final JpaExecutor jpaExecutor = TestUtils.getPropertyValue(this.consumer, "handler.jpaExecutor", JpaExecutor.class);

		assertNotNull(jpaExecutor);

		final Class<?> entityClass = TestUtils.getPropertyValue(jpaExecutor, "entityClass", Class.class);

		assertEquals("org.springframework.integration.jpa.test.entity.StudentDomain", entityClass.getName());

		final JpaOperations jpaOperations = TestUtils.getPropertyValue(jpaExecutor, "jpaOperations", JpaOperations.class);

		assertNotNull(jpaOperations);

		final Boolean usePayloadAsParameterSource = TestUtils.getPropertyValue(jpaExecutor, "usePayloadAsParameterSource", Boolean.class);

		assertTrue(usePayloadAsParameterSource);

		final Integer order = TestUtils.getPropertyValue(jpaOutboundGateway, "order", Integer.class);

		assertEquals(Integer.valueOf(2), order);

		final PersistMode persistMode = TestUtils.getPropertyValue(jpaExecutor, "persistMode", PersistMode.class);

		assertEquals(PersistMode.PERSIST, persistMode);

	}

	@After
	public void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	public void setUp(String name, Class<?> cls, String gatewayId) {
		context    = new ClassPathXmlApplicationContext(name, cls);
		consumer   = this.context.getBean(gatewayId, EventDrivenConsumer.class);
	}

}
