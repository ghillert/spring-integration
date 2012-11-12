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

package org.springframework.integration.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.integration.Message;
import org.springframework.integration.MessageHeaders;
import org.springframework.integration.jdbc.messagestore.channel.PostgresQueryProvider;
import org.springframework.integration.jdbc.messagestore.channel.QueryProvider;
import org.springframework.integration.store.AbstractMessageGroupStore;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.MessageStore;
import org.springframework.integration.store.SimpleMessageGroup;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.util.UUIDConverter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link MessageGroupStore} using a relational database via JDBC.
 * This message store is specifically for message channels only.
 *
 * SQL scripts to create the necessary tables are packaged as
 * <code>org/springframework/integration/jdbc/messagestore/channel/schema-*.sql</code>,
 * where <code>*</code> is the target database type.
 *
 * @author Gunnar Hillert
 * @since 2.2
 */
@ManagedResource
public class JdbcChannelMessageStore extends AbstractMessageGroupStore {

	private static final Log logger = LogFactory.getLog(JdbcChannelMessageStore.class);
	private static final Set<String> ids = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());

	/**
	 * Default value for the table prefix property.
	 */
	public static final String DEFAULT_TABLE_PREFIX = "INT_";

	public static void removeId(String id) {
		logger.error("Removing Id:" + id);
	}

	private QueryProvider queryProvider = new PostgresQueryProvider();

	public static final int DEFAULT_LONG_STRING_LENGTH = 2500;

	/**
	 * The name of the message header that stores a flag to indicate that the message has been saved. This is an
	 * optimization for the put method.
	 */
	public static final String SAVED_KEY = JdbcChannelMessageStore.class.getSimpleName() + ".SAVED";

	/**
	 * The name of the message header that stores a timestamp for the time the message was inserted.
	 */
	public static final String CREATED_DATE_KEY = JdbcChannelMessageStore.class.getSimpleName() + ".CREATED_DATE";

	private volatile String region = "DEFAULT";

	private volatile String tablePrefix = DEFAULT_TABLE_PREFIX;

	private volatile JdbcTemplate jdbcTemplate;

	private volatile DeserializingConverter deserializer;

	private volatile SerializingConverter serializer;

	private volatile LobHandler lobHandler = new DefaultLobHandler();

	private volatile MessageMapper mapper = new MessageMapper();

	private volatile Map<String, String> queryCache = new HashMap<String, String>();

	/**
	 * Convenient constructor for configuration use.
	 */
	public JdbcChannelMessageStore() {
		deserializer = new DeserializingConverter();
		serializer = new SerializingConverter();
	}

	/**
	 * Create a {@link MessageStore} with all mandatory properties.
	 *
	 * @param dataSource a {@link DataSource}
	 */
	public JdbcChannelMessageStore(DataSource dataSource) {
		this();
		jdbcTemplate = new JdbcTemplate(dataSource);
		this.jdbcTemplate.setFetchSize(1);
	}

	/**
	 * The JDBC {@link DataSource} to use when interacting with the database. Either this property can be set or the
	 * {@link #setJdbcTemplate(JdbcOperations) jdbcTemplate}.
	 *
	 * @param dataSource a {@link DataSource}
	 */
	public void setDataSource(DataSource dataSource) {
		jdbcTemplate = new JdbcTemplate(dataSource);
		this.jdbcTemplate.setFetchSize(1);
	}

	/**
	 * A converter for deserializing byte arrays to messages.
	 *
	 * @param deserializer the deserializer to set
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setDeserializer(Deserializer<? extends Message<?>> deserializer) {
		this.deserializer = new DeserializingConverter((Deserializer) deserializer);
	}

	/**
	 * The {@link JdbcOperations} to use when interacting with the database. Either this property can be set or the
	 * {@link #setDataSource(DataSource) dataSource}.
	 *
	 * @param jdbcTemplate a {@link JdbcOperations}
	 */
	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcTemplate.setFetchSize(1);
	}

	public void setLastReleasedSequenceNumberForGroup(Object groupId, final int sequenceNumber) {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Override the {@link LobHandler} that is used to create and unpack large objects in SQL queries. The default is
	 * fine for almost all platforms, but some Oracle drivers require a native implementation.
	 *
	 * @param lobHandler a {@link LobHandler}
	 */
	public void setLobHandler(LobHandler lobHandler) {
		this.lobHandler = lobHandler;
	}

	public void setQueryProvider(QueryProvider queryProvider) {
		this.queryProvider = queryProvider;
	}

	/**
	 * A unique grouping identifier for all messages persisted with this store. Using multiple regions allows the store
	 * to be partitioned (if necessary) for different purposes. Defaults to <code>DEFAULT</code>.
	 *
	 * @param region the region name to set
	 */
	public void setRegion(String region) {
		this.region = region;
	}

	/**
	 * A converter for serializing messages to byte arrays for storage.
	 *
	 * @param serializer the serializer to set
	 */
	@SuppressWarnings("unchecked")
	public void setSerializer(Serializer<? super Message<?>> serializer) {
		this.serializer = new SerializingConverter((Serializer<Object>) serializer);
	}

	/**
	 * Public setter for the table prefix property. This will be prefixed to all the table names before queries are
	 * executed. Defaults to {@link #DEFAULT_TABLE_PREFIX}.
	 *
	 * @param tablePrefix the tablePrefix to set
	 */
	public void setTablePrefix(String tablePrefix) {
		this.tablePrefix = tablePrefix;
	}

	/**
	 * Check mandatory properties (data source and incrementer).
	 *
	 * @throws Exception
	 */
	public void afterPropertiesSet() throws Exception {
		Assert.state(jdbcTemplate != null, "A DataSource or JdbcTemplate must be provided");
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {

		final String groupKey = getKey(groupId);

		final long createdDate = System.currentTimeMillis();
		Message<?> result = MessageBuilder.fromMessage(message).setHeader(SAVED_KEY, Boolean.TRUE)
				.setHeader(CREATED_DATE_KEY, new Long(createdDate)).build();

		Map innerMap = (Map) new DirectFieldAccessor(result.getHeaders()).getPropertyValue("headers");
		// using reflection to set ID since it is immutable through MessageHeaders
		innerMap.put(MessageHeaders.ID, message.getHeaders().get(MessageHeaders.ID));

		final String messageId = getKey(result.getHeaders().getId());
		final byte[] messageBytes = serializer.convert(result);

		jdbcTemplate.update(getQuery(queryProvider.createMessage()), new PreparedStatementSetter() {
			public void setValues(PreparedStatement ps) throws SQLException {
				if (logger.isDebugEnabled()){
					logger.debug("Inserting message with id key=" + messageId);
				}
				ps.setString(1, messageId);
				ps.setString(2, groupKey);
				ps.setString(3, region);
				ps.setTimestamp(4, new Timestamp(createdDate));
				lobHandler.getLobCreator().setBlobAsBytes(ps, 5, messageBytes);
			}
		});

		return getMessageGroup(groupId);
	}

	public void completeGroup(Object groupId) {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * This method executes a call to the DB to get the oldest Message in the MessageGroup
	 * Override this method if need to. For example if you DB supports advanced function such as FIRST etc.
	 *
	 * @param groupIdKey String representation of message group ID
	 * @return a message; could be null if query produced no Messages
	 */
	protected Message<?> doPollForMessage(String groupIdKey) {

		final NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
		final MapSqlParameterSource parameters = new MapSqlParameterSource();

		parameters.addValue("region", region);
		parameters.addValue("group_key", groupIdKey);

		final String query;

		if (ids.isEmpty()) {
			query = getQuery(queryProvider.pollFromGroup());
		} else {
			query = getQuery(queryProvider.pollFromGroupExcludeIds());
			parameters.addValue("message_ids", ids);
		}

		final List<Message<?>> messages = namedParameterJdbcTemplate.query(query, parameters, mapper
		);

		Assert.isTrue(messages.size() == 0 || messages.size() == 1);
		if (messages.size() > 0){
			Message<?>message = messages.get(0);
			ids.add(message.getHeaders().getId().toString());
			return message;
		}
		return null;
	}

	/**
	 * To be used to get a reference to JdbcOperations
	 * in case this class is subclassed
	 *
	 * @return the JdbcOperations implementation
	 */
	protected JdbcOperations getJdbcOperations(){
		return this.jdbcTemplate;
	}

	private String getKey(Object input) {
		return input == null ? null : UUIDConverter.getUUID(input).toString();
	}

	public Message<?> getMessage(UUID id) {
		List<Message<?>> list = jdbcTemplate.query(getQuery(queryProvider.getMessage()), new Object[] { getKey(id), region }, mapper);
		if (list.isEmpty()) {
			return null;
		}
		return list.get(0);
	}

	@ManagedAttribute
	public long getMessageCount() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	@ManagedAttribute
	public int getMessageCountForAllMessageGroups() {
		throw new UnsupportedOperationException("Not implemented");
	}

	public MessageGroup getMessageGroup(Object groupId) {
		return new SimpleMessageGroup(groupId);
	}

	@Override
	@ManagedAttribute
	public int getMessageGroupCount() {
		throw new UnsupportedOperationException("Not implemented");
	}

	/**
	 * Replace patterns in the input to produce a valid SQL query. This implementation lazily initializes a
	 * simple map-based cache, only replacing the table prefix on the first access to a named query. Further
	 * accesses will be resolved from the cache.
	 *
	 * @param sqlQuery the SQL query to be transformed
	 * @return a transformed query with replacements
	 */
	protected String getQuery(String sqlQuery) {
		String query = queryCache.get(sqlQuery);

		if (query == null) {
			query = StringUtils.replace(sqlQuery, "%PREFIX%", tablePrefix);
			queryCache.put(sqlQuery, query);
		}

		return query;
	}

	public Iterator<MessageGroup> iterator() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@ManagedAttribute
	public int messageGroupSize(Object groupId) {
		String key = getKey(groupId);
		return jdbcTemplate.queryForInt(getQuery(queryProvider.countAllMessagesInGroup()), key, this.region);
	}

	public Message<?> pollMessageFromGroup(Object groupId) {
		String key = getKey(groupId);

		Message<?> polledMessage = this.doPollForMessage(key);
		if (polledMessage != null){
			ids.add(polledMessage.getHeaders().getId().toString());
			this.removeMessageFromGroup(groupId, polledMessage);
		}
		return polledMessage;
	}

	public MessageGroup removeMessageFromGroup(Object groupId, Message<?> messageToRemove) {

		final UUID id = messageToRemove.getHeaders().getId();

		int updated = jdbcTemplate.update(getQuery(queryProvider.deleteMessage()), new Object[] { getKey(id), getKey(groupId), region }, new int[] {
					Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });

		if (updated != 0) {
			logger.debug(String.format("Message with id '%s' was deleted.", id));
		} else {
			logger.warn(String.format("Message with id '%s' was not deleted.", id));
		}

		return getMessageGroup(groupId);
	}

	public void removeMessageGroup(Object groupId) {

		final String groupKey = getKey(groupId);

		jdbcTemplate.update(getQuery(queryProvider.deleteMessageGroup()), new PreparedStatementSetter() {
			public void setValues(PreparedStatement ps) throws SQLException {
				if (logger.isDebugEnabled()){
					logger.debug("Marking messages with group key=" + groupKey);
				}
				ps.setString(1, groupKey);
				ps.setString(2, region);
			}
		});

	}

	/**
	 * Convenience class to be used to unpack a message from a result set row. Uses column named in the result set to
	 * extract the required data, so that select clause ordering is unimportant.
	 *
	 * @author Dave Syer
	 */
	private class MessageMapper implements RowMapper<Message<?>> {
		public Message<?> mapRow(ResultSet rs, int rowNum) throws SQLException {
			Message<?> message = (Message<?>) deserializer.convert(lobHandler.getBlobAsBytes(rs, "MESSAGE_BYTES"));
			return message;
		}
	}

}
