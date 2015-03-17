OpenGroupware.org Database Setup Scripts
========================================

Imported the database SQL setup scripts from OGo 5.5 2015-03-17. Only the PG
one is supposed to really work with OGoCore/J.

Sybase and Oracle scripts are Skyrix only and not available as part of OGo.

Note: The naming in the PostgreSQL database schema, in fact the whole PG setup,
is not particularily beautiful. This is because:
a) the original schema was done for Sybase
b) old PG versions lacked a lot of features back then
Today the situation is very different, starting around 8.x, PostgreSQL evolved
into something awesome :-)

On could and probably should come up with a modern OGo schema.
Though I think the functionality of it is mostly OK, with only few conceptual
flaws.
