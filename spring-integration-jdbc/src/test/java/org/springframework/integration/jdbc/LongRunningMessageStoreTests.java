/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.integration.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageGroupStoreReaper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import sun.print.resources.serviceui;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class LongRunningMessageStoreTests {

	private static final Log log = LogFactory.getLog(LongRunningMessageStoreTests.class);

	private final StopWatch stopWatch = new StopWatch("LongRunningMessageStoreTests");

	@Autowired
	private QueueChannel relay;

	@Autowired
	@Qualifier("lock")
	private Object storeLock;

	@Autowired
	private JdbcMessageStore messageStore;

	@Autowired
	private MessageGroupStoreReaper messageGroupStoreReaper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Before
	public void clear() {
		this.messageGroupStoreReaper.run();
//		for (MessageGroup group : messageStore) {
//			messageStore.removeMessageGroup(group.getGroupId());
//		}
	}

	@Test
	public void clear2() {
//		for (MessageGroup group : messageStore) {
//			messageStore.removeMessageGroup(group.getGroupId());
//		}
		this.messageGroupStoreReaper.run();
	}

	@After
	public void after() {
		log.info(stopWatch.prettyPrint());

		log.info("SERVICE: " + Service.getStopWatch().prettyPrint());
	}

	@Test
	// @Repeat(50)
	public void testSameTransactionDifferentChannelSendAndReceive() throws Exception {

		Service.reset(1);
		assertNull(relay.receive(100L));

		stopWatch.start("Send 10000 messages");
		for (int i = 1; i<=3000; i++) {

						relay.send(new GenericMessage<String>("foo " + i), 500L);

			if (i % 10 == 0) {
				System.out.println(i + " sent.");
			}
		}
		stopWatch.stop();
		// If the poll blocks in the RDBMS there is no way for the queue to respect the timeout

		Service.await(3000);
		// Eventual activation
		Thread.sleep(4000);
		assertEquals(3000, Service.messages.size());

	}

	public static class Service {
		private static boolean fail = false;

		private static List<String> messages = new CopyOnWriteArrayList<String>();

		private static CountDownLatch latch = new CountDownLatch(3000);

		private static final StopWatch stopWatch = new StopWatch("Service Watch");

		private static final AtomicInteger counter = new AtomicInteger();

		public static void reset(int count) {
			fail = false;
			messages.clear();
			latch = new CountDownLatch(count);
		}

		public static void await(long timeout) throws InterruptedException {
			if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
				throw new IllegalStateException("Timed out waiting for message");
			}
		}

		public void echo(String input) {


			int i = counter.addAndGet(1);

			if (i % 500 == 0) {
				stopWatch.start("Service " + i);
				System.out.println("Received message " + i);
			}

			try {
				Thread.sleep(2000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			messages.add(input);
			latch.countDown();
			if (fail) {
				throw new RuntimeException("Planned failure");
			}
			if (i % 500 == 0) {
				stopWatch.stop();
				System.out.println("Received message " + i);
			}


		}

		public static List<String> getMessages() {
			return messages;
		}

		public static StopWatch getStopWatch() {
			return stopWatch;
		}

	}

}
