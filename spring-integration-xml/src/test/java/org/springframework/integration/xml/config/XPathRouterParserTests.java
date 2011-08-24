/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.xml.config;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.router.AbstractMessageRouter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.xml.util.XmlTestUtil;
import org.springframework.test.context.ContextConfiguration;
import org.w3c.dom.Document;

/**
 * @author Jonas Partner
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
@ContextConfiguration
public class XPathRouterParserTests {

	String channelConfig = "<si:channel id='test-input'/> <si:channel id='outputOne'><si:queue capacity='10'/></si:channel>" +
		"<si:channel id='defaultOutput'><si:queue capacity='10'/></si:channel>";
	
	@Autowired @Qualifier("test-input")
	MessageChannel inputChannel;
	
	@Autowired @Qualifier("outputOne")
	QueueChannel outputChannel;
	
	@Autowired @Qualifier("defaultOutput")
	QueueChannel defaultOutput;
	
	
	ConfigurableApplicationContext appContext;
	
	public EventDrivenConsumer buildContext(String routerDef){
		appContext = TestXmlApplicationContextHelper.getTestAppContext( channelConfig + routerDef);
		appContext.getAutowireCapableBeanFactory().autowireBeanProperties(this, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
		EventDrivenConsumer consumer = (EventDrivenConsumer) appContext.getBean("router");
		consumer.start();
		return consumer;
	}
	
	
	@After
	public void tearDown(){
		if(appContext != null){
			appContext.close();
		}
	}
	
	@Test
	public void testSimpleStringExpression() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<name>outputOne</name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		buildContext("<si-xml:xpath-router id='router' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		inputChannel.send(docMessage);
		assertEquals("Wrong number of messages", 1, outputChannel.getQueueSize());
	}

	@Test
	public void testNamespacedStringExpression() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<ns1:name xmlns:ns1='www.example.org'>outputOne</ns1:name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		buildContext("<si-xml:xpath-router id='router' input-channel='test-input'><si-xml:xpath-expression expression='/ns2:name' ns-prefix='ns2' ns-uri='www.example.org' /></si-xml:xpath-router>");
		inputChannel.send(docMessage);
		assertEquals("Wrong number of messages", 1, outputChannel.getQueueSize());
	}

	@Test
	public void testStringExpressionWithNestedNamespaceMap() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString(
				"<ns1:name xmlns:ns1='www.example.org' xmlns:ns2='www.example.org2'><ns2:type>outputOne</ns2:type></ns1:name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		StringBuffer buffer = new StringBuffer(
				"<si-xml:xpath-router id='router' input-channel='test-input'><si-xml:xpath-expression expression='/ns1:name/ns2:type'> ");
		buffer.append("<map><entry key='ns1' value='www.example.org' /> <entry key='ns2' value='www.example.org2'/></map>");
		buffer.append("</si-xml:xpath-expression></si-xml:xpath-router>");
		buildContext(buffer.toString());
		inputChannel.send(docMessage);
		assertEquals("Wrong number of messages", 1, outputChannel.getQueueSize());
	}

	@Test
	public void testStringExpressionWithReferenceToNamespaceMap() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString(
				"<ns1:name xmlns:ns1='www.example.org' xmlns:ns2='www.example.org2'><ns2:type>outputOne</ns2:type></ns1:name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		StringBuffer buffer = new StringBuffer(
				"<si-xml:xpath-router id='router' input-channel='test-input'><si-xml:xpath-expression expression='/ns1:name/ns2:type' namespace-map='nsMap'/>");
		buffer.append("</si-xml:xpath-router>");
		buffer.append("<util:map id='nsMap'><entry key='ns1' value='www.example.org' /><entry key='ns2' value='www.example.org2' /></util:map>");
		
		buildContext(buffer.toString());
		inputChannel.send(docMessage);
		assertEquals("Wrong number of messages", 1, outputChannel.getQueueSize());
	}

	@Test
	public void testSetChannelResolver() throws Exception {
		StringBuffer contextBuffer = new StringBuffer("<si-xml:xpath-router id='router' channel-resolver='stubResolver' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		contextBuffer.append("<bean id='stubResolver' class='").append(StubChannelResolver.class.getName()).append("'/>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object resolver = accessor.getPropertyValue("channelResolver");
		assertEquals("Wrong channel resolver ",StubChannelResolver.class, resolver.getClass());
	}
	
	@Test
	public void testSetResolutionRequiredFalse() throws Exception {
		StringBuffer contextBuffer = new StringBuffer("<si-xml:xpath-router id='router' resolution-required='false' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object resolutionRequired = accessor.getPropertyValue("resolutionRequired");
		assertEquals("Resolution required not set to false ", false, resolutionRequired);
	}
	
	@Test
	public void testSetResolutionRequiredTrue() throws Exception {
		StringBuffer contextBuffer = new StringBuffer("<si-xml:xpath-router id='router' resolution-required='true' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object resolutionRequired = accessor.getPropertyValue("resolutionRequired");
		assertEquals("Resolution required not set to true ", true, resolutionRequired);
	}
	
	@Test
	public void testIgnoreChannelNameResolutionFailuresFalse() throws Exception {
		StringBuffer contextBuffer = new StringBuffer(
				"<si-xml:xpath-router id='router' ignore-channel-name-resolution-failures='false' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object ignoreChannelNameResolutionFailures = accessor.getPropertyValue("ignoreChannelNameResolutionFailures");
		assertEquals("ignoreChannelNameResolutionFailures not set to false", false, ignoreChannelNameResolutionFailures);
	}
	
	@Test
	public void testIgnoreChannelNameResolutionFailuresTrue() throws Exception {
		StringBuffer contextBuffer = new StringBuffer(
				"<si-xml:xpath-router id='router' ignore-channel-name-resolution-failures='true' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object ignoreChannelNameResolutionFailures = accessor.getPropertyValue("ignoreChannelNameResolutionFailures");
		assertEquals("ignoreChannelNameResolutionFailures not set to true ", true, ignoreChannelNameResolutionFailures);
	}

	@Test
	public void testSetDefaultOutputChannel() throws Exception {
		StringBuffer contextBuffer = new StringBuffer("<si-xml:xpath-router id='router' default-output-channel='defaultOutput' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object defaultOutputChannelValue = accessor.getPropertyValue("defaultOutputChannel");
		assertEquals("Default output channel not correctly set ", defaultOutput, defaultOutputChannelValue);
		inputChannel.send(MessageBuilder.withPayload("<unrelated/>").build());
		assertEquals("Wrong count of messages on default output channel",1, defaultOutput.getQueueSize());
	}
	@Test
	public void testWithDynamicChanges() throws Exception {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("XPathRouterTests-context.xml", this.getClass());
		
		MessageChannel inputChannel = ac.getBean("xpathRouterEmptyChannel", MessageChannel.class);
		PollableChannel channelA = ac.getBean("channelA", PollableChannel.class);
		PollableChannel channelB = ac.getBean("channelB", PollableChannel.class);
		Document doc = XmlTestUtil.getDocumentForString("<name>channelA</name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		inputChannel.send(docMessage);
		assertNotNull(channelA.receive(10));
		assertNull(channelB.receive(10));
		
		EventDrivenConsumer routerEndpoint = ac.getBean("xpathRouterEmpty", EventDrivenConsumer.class);
		AbstractMessageRouter xpathRouter = (AbstractMessageRouter) TestUtils.getPropertyValue(routerEndpoint, "handler");
		xpathRouter.setChannelMapping("channelA", "channelB");
		inputChannel.send(docMessage);
		assertNotNull(channelB.receive(10));
		assertNull(channelA.receive(10));
	}
	@Test
	public void testWithDynamicChangesWithExistingMappings() throws Exception {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("XPathRouterTests-context.xml", this.getClass());
		
		MessageChannel inputChannel = ac.getBean("xpathRouterWithMappingChannel", MessageChannel.class);
		PollableChannel channelA = ac.getBean("channelA", PollableChannel.class);
		PollableChannel channelB = ac.getBean("channelB", PollableChannel.class);
		Document doc = XmlTestUtil.getDocumentForString("<name>channelA</name>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		inputChannel.send(docMessage);
		assertNull(channelA.receive(10));
		assertNotNull(channelB.receive(10));
		
		EventDrivenConsumer routerEndpoint = ac.getBean("xpathRouterWithMapping", EventDrivenConsumer.class);
		AbstractMessageRouter xpathRouter = (AbstractMessageRouter) TestUtils.getPropertyValue(routerEndpoint, "handler");
		xpathRouter.removeChannelMapping("channelA");
		inputChannel.send(docMessage);
		assertNotNull(channelA.receive(10));
		assertNull(channelB.receive(10));
	}
	
	@Test
	public void testWithDynamicChangesWithExistingMappingsAndMultiChannel() throws Exception {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("XPathRouterTests-context.xml", this.getClass());
		
		MessageChannel inputChannel = ac.getBean("multiChannelRouterChannel", MessageChannel.class);
		PollableChannel channelA = ac.getBean("channelA", PollableChannel.class);
		PollableChannel channelB = ac.getBean("channelB", PollableChannel.class);
		Document doc = XmlTestUtil.getDocumentForString("<root><name>channelA</name><name>channelB</name></root>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		inputChannel.send(docMessage);
		assertNotNull(channelA.receive(10));
		assertNotNull(channelA.receive(10));
		assertNull(channelB.receive(10));	
		
		EventDrivenConsumer routerEndpoint = ac.getBean("xpathRouterWithMappingMultiChannel", EventDrivenConsumer.class);
		AbstractMessageRouter xpathRouter = (AbstractMessageRouter) TestUtils.getPropertyValue(routerEndpoint, "handler");
		xpathRouter.removeChannelMapping("channelA");
		xpathRouter.removeChannelMapping("channelB");
		inputChannel.send(docMessage);
		assertNotNull(channelA.receive(10));
		assertNotNull(channelB.receive(10));
	}
	@Test
	public void testWithStringEvaluationType() throws Exception {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("XPathRouterTests-context.xml", this.getClass());	
		MessageChannel inputChannel = ac.getBean("xpathStringChannel", MessageChannel.class);
		PollableChannel channelA = ac.getBean("channelA", PollableChannel.class);
		Document doc = XmlTestUtil.getDocumentForString("<channelA/>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		inputChannel.send(docMessage);
		assertNotNull(channelA.receive(10));
		
	}

	@Test
	public void testApplySequenceIsSet() throws Exception {
		StringBuffer contextBuffer = new StringBuffer(
				"<si-xml:xpath-router id='router' apply-sequence='true' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		Object applySequence = accessor.getPropertyValue("applySequence");
		assertEquals("applySequence not set to true ", true, applySequence);
	}
	
	@Test
	public void testDefaultOutputChannelIsSet() throws Exception {
		StringBuffer contextBuffer = new StringBuffer(
				"<si-xml:xpath-router id='router' default-output-channel='outputOne' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		QueueChannel defaultOutputChannel = (QueueChannel) accessor.getPropertyValue("defaultOutputChannel");
		assertEquals("Channel name is not 'outputOne' ", "outputOne", defaultOutputChannel.getComponentName());
	}

	@Test
	public void testTimeOutIsSet() throws Exception {
		StringBuffer contextBuffer = new StringBuffer(
				"<si-xml:xpath-router id='router' timeout='100' input-channel='test-input'><si-xml:xpath-expression expression='/name'/></si-xml:xpath-router>");
		EventDrivenConsumer consumer = buildContext(contextBuffer.toString());
		
		DirectFieldAccessor accessor = new DirectFieldAccessor(consumer);
		Object handler = accessor.getPropertyValue("handler");
		accessor = new DirectFieldAccessor(handler);
		handler =  accessor.getPropertyValue("messagingTemplate");
		accessor = new DirectFieldAccessor(handler);
		Long timeout = (Long) accessor.getPropertyValue("sendTimeout");
		assertEquals("Send Timeout is not 100 ", Long.valueOf(100), timeout);
	}
	
}
