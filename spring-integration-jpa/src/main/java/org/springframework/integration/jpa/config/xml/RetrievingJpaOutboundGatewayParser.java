/*
 * Copyright 2002-2012 the original author or authors.
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
package org.springframework.integration.jpa.config.xml;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;
import org.springframework.integration.jpa.support.OutboundGatewayType;
import org.w3c.dom.Element;

/**
 * The Parser for the Retrieving Jpa Outbound Gateway.
 *
 * @author Amol Nayak
 * @author Gunnar Hillert
 *
 * @since 2.2
 *
 */
public class RetrievingJpaOutboundGatewayParser extends AbstractJpaOutboundGatewayParser {

	@Override
	protected BeanDefinitionBuilder parseHandler(Element gatewayElement, ParserContext parserContext) {

		final BeanDefinitionBuilder jpaOutboundGatewayBuilder = super.parseHandler(gatewayElement, parserContext);

		final BeanDefinitionBuilder jpaExecutorBuilder = JpaParserUtils.getOutboundGatewayJpaExecutorBuilder(gatewayElement, parserContext);

		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "max-number-of-results");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "delete-after-poll");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "delete-per-row");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(jpaExecutorBuilder, gatewayElement, "expect-single-result");

		final BeanDefinition jpaExecutorBuilderBeanDefinition = jpaExecutorBuilder.getBeanDefinition();
		final String jpaExecutorBeanName = BeanDefinitionReaderUtils.generateBeanName(jpaExecutorBuilderBeanDefinition, parserContext.getRegistry());

		parserContext.registerBeanComponent(new BeanComponentDefinition(jpaExecutorBuilderBeanDefinition, jpaExecutorBeanName));

		jpaOutboundGatewayBuilder.addConstructorArgReference(jpaExecutorBeanName);
		jpaOutboundGatewayBuilder.addPropertyValue("gatewayType", OutboundGatewayType.RETRIEVING);
		return jpaOutboundGatewayBuilder;

	}

}
