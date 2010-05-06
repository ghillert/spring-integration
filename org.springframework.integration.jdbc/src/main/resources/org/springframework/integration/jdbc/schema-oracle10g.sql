-- Autogenerated: do not edit this file

CREATE TABLE INT_MESSAGE  (
	MESSAGE_ID VARCHAR2(100) NOT NULL PRIMARY KEY,
	REGION VARCHAR2(100),
	CREATED_DATE TIMESTAMP NOT NULL,
	MESSAGE_BYTES BLOB
);

CREATE TABLE INT_MESSAGE_GROUP  (
	MESSAGE_ID VARCHAR2(100) NOT NULL,
	CORRELATION_KEY VARCHAR2(100) NOT NULL,
	REGION VARCHAR2(100),
	MARKED NUMBER(19,0),
	CREATED_DATE TIMESTAMP NOT NULL,
	UPDATED_DATE TIMESTAMP,
	MESSAGE_BYTES BLOB,
	constraint MESSAGE_GROUP_PK primary key (MESSAGE_ID, CORRELATION_KEY)
);
