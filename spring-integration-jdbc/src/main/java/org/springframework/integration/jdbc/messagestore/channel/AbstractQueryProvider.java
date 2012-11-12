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
package org.springframework.integration.jdbc.messagestore.channel;

/**
 * @author Gunnar Hillert
 * @since 2.2
 */
public abstract class AbstractQueryProvider implements QueryProvider {

	public String countAllMessagesInGroup() {
		return "SELECT COUNT(MESSAGE_ID) from %PREFIX%CHANNEL_MESSAGE where GROUP_KEY=? and REGION=?";
	}

	public abstract String pollFromGroupExcludeIds();

	public abstract String pollFromGroup();

	public String getMessage() {
		return "SELECT MESSAGE_ID, CREATED_DATE, MESSAGE_BYTES from %PREFIX%CHANNEL_MESSAGE where MESSAGE_ID=? and REGION=?";
	}

	public String getMessageCountForRegion() {
		return "SELECT COUNT(MESSAGE_ID) from %PREFIX%CHANNEL_MESSAGE where REGION=?";
	}

	public String deleteMessage() {
		return "DELETE from %PREFIX%CHANNEL_MESSAGE where MESSAGE_ID=? and GROUP_KEY = ? and REGION=?";
	}

	public String createMessage() {
		return "INSERT into %PREFIX%CHANNEL_MESSAGE(MESSAGE_ID, GROUP_KEY, REGION, CREATED_DATE, MESSAGE_BYTES)"
				+ " values (?, ?, ?, ?, ?)";
	}

	public String deleteMessageGroup() {
		return "DELETE from %PREFIX%CHANNEL_MESSAGE where GROUP_KEY=? and REGION=?";
	}

}
