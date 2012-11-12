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

package org.springframework.integration.jdbc;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.UUID;

import javax.sql.DataSource;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.Message;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Dave Syer
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Artem Bilan
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class JdbcChannelMessageStoreTests {

	@Autowired
	private DataSource dataSource;

	private JdbcChannelMessageStore messageStore;

	@Before
	public void init() {
		messageStore = new JdbcChannelMessageStore(dataSource);
		messageStore.setRegion("JdbcChannelMessageStoreTests");
		messageStore.removeMessageGroup("my-channel");
		messageStore.removeMessageGroup("channel1000");
	}

	@Test
	@Transactional
	public void testGetNonExistent() throws Exception {
		Message<?> result = messageStore.getMessage(UUID.randomUUID());
		assertNull(result);
	}

	@Test
	@Transactional
	@Rollback(false)
	public void testAddAndGet() throws Exception {
		final Message<String> message = MessageBuilder.withPayload("Cartman and Kenny")
				.setHeader("homeTown", "Southpark")
				.build();

		final MessageGroup saved = messageStore.addMessageToGroup("my-channel", message);
		assertNotNull(messageStore.getMessage(message.getHeaders().getId()));

		final Message<?> result = messageStore.pollMessageFromGroup(saved.getGroupId());
		assertNotNull(result);
		assertNotNull(result.getHeaders().get(JdbcChannelMessageStore.SAVED_KEY));
		assertNotNull(result.getHeaders().get(JdbcChannelMessageStore.CREATED_DATE_KEY));
	}

	@Test
	@Transactional
	@Rollback(true)
	public void testAdd1000() throws Exception {

		for (int i = 1; i<=1000; i++) {
			Message<String> message = MessageBuilder.withPayload("Cartman").build();
			messageStore.addMessageToGroup("channel1000", message);
			assertNotNull(messageStore.getMessage(message.getHeaders().getId()));
		}

		Assert.assertEquals(1000, messageStore.messageGroupSize("channel1000"));

	}

//	@Test
//	@Transactional
//	public void testSerializer() throws Exception {
//		// N.B. these serializers are not realistic (just for test purposes)
//		messageStore.setSerializer(new Serializer<Message<?>>() {
//			public void serialize(Message<?> object, OutputStream outputStream) throws IOException {
//				outputStream.write(((Message<?>) object).getPayload().toString().getBytes());
//				outputStream.flush();
//			}
//		});
//		messageStore.setDeserializer(new Deserializer<GenericMessage<String>>() {
//			public GenericMessage<String> deserialize(InputStream inputStream) throws IOException {
//				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//				return new GenericMessage<String>(reader.readLine());
//			}
//		});
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		Message<String> saved = messageStore.addMessage(message);
//		assertNotNull(messageStore.getMessage(message.getHeaders().getId()));
//		Message<?> result = messageStore.getMessage(saved.getHeaders().getId());
//		assertNotNull(result);
//		assertEquals("foo", result.getPayload());
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndGetWithDifferentRegion() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		Message<String> saved = messageStore.addMessage(message);
//		messageStore.setRegion("FOO");
//		Message<?> result = messageStore.getMessage(saved.getHeaders().getId());
//		assertNull(result);
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndUpdate() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId("X").build();
//		message = messageStore.addMessage(message);
//		message = MessageBuilder.fromMessage(message).setCorrelationId("Y").build();
//		message = messageStore.addMessage(message);
//		assertEquals("Y", messageStore.getMessage(message.getHeaders().getId()).getHeaders().getCorrelationId());
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndUpdateAlreadySaved() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		message = messageStore.addMessage(message);
//		Message<String> result = messageStore.addMessage(message);
//		assertSame(message, result);
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndUpdateAlreadySavedAndCopied() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		Message<String> saved = messageStore.addMessage(message);
//		Message<String> copy = MessageBuilder.fromMessage(saved).build();
//		Message<String> result = messageStore.addMessage(copy);
//		assertEquals(copy, result);
//		assertEquals(saved, result);
//		assertNotNull(messageStore.getMessage(saved.getHeaders().getId()));
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndUpdateWithChange() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		Message<String> saved = messageStore.addMessage(message);
//		Message<String> copy = MessageBuilder.fromMessage(saved).setHeader("newHeader", 1).build();
//		Message<String> result = messageStore.addMessage(copy);
//		assertNotSame(saved, result);
//		assertThat(saved, sameExceptIgnorableHeaders(result, JdbcMessageStore.CREATED_DATE_KEY, "newHeader"));
//		assertNotNull(messageStore.getMessage(saved.getHeaders().getId()));
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndRemoveMessageGroup() throws Exception {
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		message = messageStore.addMessage(message);
//		assertNotNull(messageStore.removeMessage(message.getHeaders().getId()));
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndGetMessageGroup() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		long now = System.currentTimeMillis();
//		messageStore.addMessageToGroup(groupId, message);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(1, group.size());
//		assertTrue("Timestamp too early: " + group.getTimestamp() + "<" + now, group.getTimestamp() >= now);
//	}
//
//	@Test
//	@Transactional
//	public void testAddAndRemoveMessageFromMessageGroup() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.removeMessageFromGroup(groupId, message);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(0, group.size());
//	}
//
//	@Test
//	@Transactional
//	public void testRemoveMessageGroup() throws Exception {
//		JdbcTemplate template = new JdbcTemplate(dataSource);
//		template.afterPropertiesSet();
//		String groupId = "X";
//
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.removeMessageGroup(groupId);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(0, group.size());
//
//		String uuidGroupId = UUIDConverter.getUUID(groupId).toString();
//		assertTrue(template.queryForList(
//				"SELECT * from INT_GROUP_TO_MESSAGE where GROUP_KEY = '"  + uuidGroupId + "'").size() == 0);
//	}
//
//	@Test
//	@Transactional
//	public void testCompleteMessageGroup() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.completeGroup(groupId);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertTrue(group.isComplete());
//		assertEquals(1, group.size());
//	}
//
//	@Test
//	@Transactional
//	public void testUpdateLastReleasedSequence() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.setLastReleasedSequenceNumberForGroup(groupId, 5);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(5, group.getLastReleasedMessageSequenceNumber());
//	}
//
//	@Test
//	@Transactional
//	public void testMessageGroupCount() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		messageStore.addMessageToGroup(groupId, message);
//		assertEquals(1, messageStore.getMessageGroupCount());
//	}
//
//	@Test
//	@Transactional
//	public void testMessageGroupSizes() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").build();
//		messageStore.addMessageToGroup(groupId, message);
//		assertEquals(1, messageStore.getMessageCountForAllMessageGroups());
//	}
//
//	@Test
//	@Transactional
//	public void testOrderInMessageGroup() throws Exception {
//		String groupId = "X";
//
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("foo").setCorrelationId(groupId).build());
//		Thread.sleep(1);
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("bar").setCorrelationId(groupId).build());
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(2, group.size());
//		assertEquals("foo", messageStore.pollMessageFromGroup(groupId).getPayload());
//		assertEquals("bar", messageStore.pollMessageFromGroup(groupId).getPayload());
//	}
//
//	@Test
//	@Transactional
//	public void testExpireMessageGroupOnCreateOnly() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.registerMessageGroupExpiryCallback(new MessageGroupCallback() {
//			public void execute(MessageGroupStore messageGroupStore, MessageGroup group) {
//				messageGroupStore.removeMessageGroup(group.getGroupId());
//			}
//		});
//		Thread.sleep(1000);
//		messageStore.expireMessageGroups(2000);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(1, group.size());
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("bar").setCorrelationId(groupId).build());
//		Thread.sleep(2001);
//		messageStore.expireMessageGroups(2000);
//		group = messageStore.getMessageGroup(groupId);
//		assertEquals(0, group.size());
//	}
//
//	@Test
//	@Transactional
//	public void testExpireMessageGroupOnIdleOnly() throws Exception {
//		String groupId = "X";
//		Message<String> message = MessageBuilder.withPayload("foo").setCorrelationId(groupId).build();
//		messageStore.setTimeoutOnIdle(true);
//		messageStore.addMessageToGroup(groupId, message);
//		messageStore.registerMessageGroupExpiryCallback(new MessageGroupCallback() {
//			public void execute(MessageGroupStore messageGroupStore, MessageGroup group) {
//				messageGroupStore.removeMessageGroup(group.getGroupId());
//			}
//		});
//		Thread.sleep(1000);
//		messageStore.expireMessageGroups(2000);
//		MessageGroup group = messageStore.getMessageGroup(groupId);
//		assertEquals(1, group.size());
//		Thread.sleep(2000);
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("bar").setCorrelationId(groupId).build());
//		group = messageStore.getMessageGroup(groupId);
//		assertEquals(2, group.size());
//		Thread.sleep(2000);
//		messageStore.expireMessageGroups(2000);
//		group = messageStore.getMessageGroup(groupId);
//		assertEquals(0, group.size());
//	}
//
//	@Test
//	@Transactional
//	public void testMessagePollingFromTheGroup() throws Exception {
//		String groupId = "X";
//
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("foo").setCorrelationId(groupId).build());
//		Thread.sleep(1);
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("bar").setCorrelationId(groupId).build());
//		Thread.sleep(1);
//		messageStore.addMessageToGroup(groupId, MessageBuilder.withPayload("baz").setCorrelationId(groupId).build());
//
//		messageStore.addMessageToGroup("Y", MessageBuilder.withPayload("barA").setCorrelationId(groupId).build());
//		Thread.sleep(1);
//		messageStore.addMessageToGroup("Y", MessageBuilder.withPayload("bazA").setCorrelationId(groupId).build());
//
//		MessageGroup group = messageStore.getMessageGroup("X");
//		assertEquals(3, group.size());
//
//		Message<?> message1 = messageStore.pollMessageFromGroup("X");
//		assertNotNull(message1);
//		assertEquals("foo", message1.getPayload());
//
//		group = messageStore.getMessageGroup("X");
//		assertEquals(2, group.size());
//
//		Message<?> message2 = messageStore.pollMessageFromGroup("X");
//		assertNotNull(message2);
//		assertEquals("bar", message2.getPayload());
//
//		group = messageStore.getMessageGroup("X");
//		assertEquals(1, group.size());
//	}

}
