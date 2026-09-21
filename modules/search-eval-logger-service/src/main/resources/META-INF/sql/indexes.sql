create index IX_62DE8BD3 on SEL_SearchEvent (companyId, createDate);
create index IX_F5E0D8A1 on SEL_SearchEvent (uuid_[$COLUMN_LENGTH:75$]);

create index IX_7AEF143A on SEL_SearchHit (companyId, createDate);
create index IX_3446BAF1 on SEL_SearchHit (searchEventUuid[$COLUMN_LENGTH:75$]);