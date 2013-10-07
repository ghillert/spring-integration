/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.integration.twitter.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.Message;
import org.springframework.integration.redis.rules.RedisAvailable;
import org.springframework.integration.redis.store.metadata.RedisMetadataStore;
import org.springframework.integration.store.metadata.MetadataStore;
import org.springframework.integration.store.metadata.SimpleMetadataStore;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.social.twitter.api.SearchMetadata;
import org.springframework.social.twitter.api.SearchOperations;
import org.springframework.social.twitter.api.SearchResults;
import org.springframework.social.twitter.api.Tweet;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.social.twitter.api.impl.SearchParameters;
import org.springframework.social.twitter.api.impl.TwitterTemplate;


/**
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 */
public class SearchReceivingMessageSourceTests {

	private static final String SEARCH_QUERY = "#springsource";

	@SuppressWarnings("unchecked")
	@Test @Ignore
	public void demoReceiveSearchResults() throws Exception{
		PropertiesFactoryBean pf = new PropertiesFactoryBean();
		pf.setLocation(new ClassPathResource("sample.properties"));
		pf.afterPropertiesSet();
		Properties prop =  pf.getObject();
		System.out.println(prop);
		TwitterTemplate template = new TwitterTemplate(prop.getProperty("z_oleg.oauth.consumerKey"),
										               prop.getProperty("z_oleg.oauth.consumerSecret"),
										               prop.getProperty("z_oleg.oauth.accessToken"),
										               prop.getProperty("z_oleg.oauth.accessTokenSecret"));
		SearchReceivingMessageSource tSource = new SearchReceivingMessageSource(template);
		tSource.setQuery(SEARCH_QUERY);
		tSource.afterPropertiesSet();
		for (int i = 0; i < 50; i++) {
			Message<Tweet> message = (Message<Tweet>) tSource.receive();
			if (message != null){
				Tweet tweet = message.getPayload();
				System.out.println(tweet.getFromUser() + " - " + tweet.getText() + " - " + tweet.getCreatedAt());
			}
		}
	}

	/**
	 * Unit Test ensuring some basic initialization properties being set.
	 */
	@Test
	public void testSearchReceivingMessageSourceInit() {

		final SearchReceivingMessageSource messageSource = new SearchReceivingMessageSource(new TwitterTemplate("test"));
		messageSource.setComponentName("twitterSearchMessageSource");

		final Object metadataStore = TestUtils.getPropertyValue(messageSource, "metadataStore");
		final Object metadataKey = TestUtils.getPropertyValue(messageSource, "metadataKey");

		assertNull(metadataStore);
		assertNull(metadataKey);

		messageSource.afterPropertiesSet();

		final Object metadataStoreInitialized = TestUtils.getPropertyValue(messageSource, "metadataStore");
		final Object metadataKeyInitialized = TestUtils.getPropertyValue(messageSource, "metadataKey");

		assertNotNull(metadataStoreInitialized);
		assertTrue(metadataStoreInitialized instanceof SimpleMetadataStore);
		assertNotNull(metadataKeyInitialized);
		assertEquals("twitter:search-inbound-channel-adapter.twitterSearchMessageSource", metadataKeyInitialized);

		final Twitter twitter = TestUtils.getPropertyValue(messageSource, "twitter", Twitter.class);

		assertFalse(twitter.isAuthorized());
		assertNotNull(twitter.userOperations());

	}

	/**
	 * This test ensures that when polling for a list of Tweets null is never returned.
	 * In case of no polling results, an empty list is returned instead.
	 */
	@Test
	public void testPollForTweetsNullResults() {

		final TwitterTemplate twitterTemplate = mock(TwitterTemplate.class);
		final SearchOperations so = mock(SearchOperations.class);

		when(twitterTemplate.searchOperations()).thenReturn(so);
		when(twitterTemplate.searchOperations().search(SEARCH_QUERY, 20, 0, 0)).thenReturn(null);

		final SearchReceivingMessageSource messageSource = new SearchReceivingMessageSource(twitterTemplate);
		messageSource.setQuery(SEARCH_QUERY);

		final String setQuery = TestUtils.getPropertyValue(messageSource, "query", String.class);

		assertEquals(SEARCH_QUERY, setQuery);
		assertEquals("twitter:search-inbound-channel-adapter", messageSource.getComponentType());

		final List<Tweet> tweets = messageSource.pollForTweets(0);

		assertNotNull(tweets);
		assertTrue(tweets.isEmpty());
	}

	/**
	 * Verify that a polling operation returns in fact 3 results.
	 */
	@Test
	public void testPollForTweetsThreeResults() {

		final TwitterTemplate twitterTemplate = getMockedTwitterTemplate();

		final SearchReceivingMessageSource messageSource = new SearchReceivingMessageSource(twitterTemplate);

		messageSource.setQuery(SEARCH_QUERY);

		final List<Tweet> tweetSearchResults = messageSource.pollForTweets(0);

		assertNotNull(tweetSearchResults);
		assertEquals(3, tweetSearchResults.size());
	}

	@Test
	public void testPollForTweetsThreeResultsAndResetMetadataStore() throws Exception {

		final TwitterTemplate twitterTemplate = getMockedTwitterTemplate();
		final SearchReceivingMessageSource source = new SearchReceivingMessageSource(twitterTemplate);
		source.setMinimumWaitBetweenPolls(0);
		source.afterPropertiesSet();

		final MetadataStore metadataStore = TestUtils.getPropertyValue(source, "metadataStore", MetadataStore.class);
		assertTrue("Exptected metadataStore to be an instance of SimpleMetadataStore", metadataStore instanceof SimpleMetadataStore);

		final Message<?> message1 = source.receive();
		final Message<?> message2 = source.receive();
		final Message<?> message3 = source.receive();

		/* We received 3 messages so far. When invoking receive() again the search
		 * will return again the 3 test Tweets but as we already processed them
		 * no message (null) is returned. */
		final Message<?> message4 = source.receive();

		source.resetMetadataStore();

		/* After the reset of the metadataStore we will get the 3 tweets again. */
		final Message<?> message5 = source.receive();
		final Message<?> message6 = source.receive();
		final Message<?> message7 = source.receive();

		assertNotNull(message1);
		assertNotNull(message2);
		assertNotNull(message3);

		assertNull(message4);

		assertNotNull(message5);
		assertNotNull(message6);
		assertNotNull(message7);

		assertEquals(3L, source.getLastProcessedId());

		source.resetMetadataStore();
	}

	private TwitterTemplate getMockedTwitterTemplate() {
		final TwitterTemplate twitterTemplate = mock(TwitterTemplate.class);

		final SearchOperations so = mock(SearchOperations.class);

		final Tweet tweet3 = new Tweet(3L, "first", new GregorianCalendar(2013, 2, 20).getTime(), "fromUser", "profileImageUrl", 888L, 999L, "languageCode", "source");
		final Tweet tweet1 = new Tweet(1L, "first", new GregorianCalendar(2013, 0, 20).getTime(), "fromUser", "profileImageUrl", 888L, 999L, "languageCode", "source");
		final Tweet tweet2 = new Tweet(2L, "first", new GregorianCalendar(2013, 1, 20).getTime(), "fromUser", "profileImageUrl", 888L, 999L, "languageCode", "source");

		final List<Tweet> tweets = new ArrayList<Tweet>();

		tweets.add(tweet3);
		tweets.add(tweet1);
		tweets.add(tweet2);

		final SearchResults results = new SearchResults(tweets, new SearchMetadata(111, 111));

		when(twitterTemplate.searchOperations()).thenReturn(so);
		when(twitterTemplate.searchOperations().search(any(SearchParameters.class))).thenReturn(results);

		return twitterTemplate;
	}

}
