create table SEL_SearchEvent (
	mvccVersion LONG default 0 not null,
	uuid_ VARCHAR(75) null,
	searchEventId LONG not null primary key,
	companyId LONG,
	createDate DATE null,
	queryText VARCHAR(2000) null,
	queryTruncated BOOLEAN,
	locale VARCHAR(20) null,
	scopeGroupIds VARCHAR(500) null,
	entryClassNames VARCHAR(2000) null,
	appliedFacets TEXT null,
	facetCaptureStatus VARCHAR(20) null,
	blueprintId VARCHAR(100) null,
	audienceType VARCHAR(20) null,
	cohortHash VARCHAR(64) null,
	requestedSize INTEGER,
	requestedFrom INTEGER,
	totalHits LONG,
	loggedHitCount INTEGER,
	sourceType VARCHAR(20) null
);

create table SEL_SearchHit (
	mvccVersion LONG default 0 not null,
	searchHitId LONG not null primary key,
	searchEventUuid VARCHAR(75) null,
	companyId LONG,
	createDate DATE null,
	rank_ INTEGER,
	score DOUBLE,
	docUid VARCHAR(500) null,
	entryClassName VARCHAR(200) null,
	entryClassPK LONG,
	title VARCHAR(1000) null,
	snippet TEXT null,
	extraFields TEXT null
);