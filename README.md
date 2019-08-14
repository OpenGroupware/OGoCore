# OGoCore

Section: H's Computer History Museum

OGoCore is a Java implementation of the
[OpenGroupware.org](http://www.opengroupware.org/en/index.html)
server application logic.
This is just the functionality wrapped around an OGo database:
it provides a Java API,
it does *not* include a user interface or web API.

Is it useful for you? Quite likely not :-)

OGo/J is a pretty neat implementation of OGo (which is complex and quite
powerful),
but the OGo database schema itself has quite some bit-rust :-)
But if you still have OGo databases deployed, this can be a good starting point
to put new life into them.

### Implementation

The implementation of OGoCore is focused on high performance. Stuff which used
to be slow in Objective-C OGo is really fast in OGo/J. Plenty of queries have
high performance implementations, this includes stuff like permission checks or
bulk imports of large amounts of data.

Also, the functionality has further advanced over regular OGo.

Note: Not everything of OGo/ObjC is implemented yet. Specifically event/meeting
handling and such are pretty limited.
Things which work well and have been tested in production systems are contacts,
projects and todos.

### Source

The interesting package is org.opengroupware.logic.

#### logic.db

Contains EOActiveRecord subclasses for all the OGo entities. They all inherit
from the OGoObject class which adds additional concepts to EOAccess:

- permissions (attributes the user must not see are removed)
- 'id' as the common primary key (mapped to the proper DB key)
- versioning  (as the conflict detection attribute)

It also contains datasource subclasses with convenience accessors as well as
preconfigured qualifier objects (like fetchTasksInReplyToId()).

#### logic.core

Key objects, primarily the OGoObjectContext which is a subclass of
EOEditingContext that supports login concepts and the permission model of OGo.
This is the main API entry point, you usually create an OGo context object
alongside a WOContext in a Go web application.

Sample:

    EODatabase db = OGoDatabase.databaseForURL(
      "jdbc:postgresql://127.0.0.1/OGoDB?user=OGo&password=OGo",
      "/var/lib/opengroupware.org/documents");
    
    LoginContext      lc = OGoLoginModule.jaasLogin(db, "joe", "user");
    
    EODatabaseContext dc = new EODatabaseContext(db);
    OGoObjectContext  oc = new OGoObjectContext(dc, lc);


#### logic.blobs

The package also includes OGo BLOB handlers. OGo does not store BLOBs
in the database but in the filesystem (remember, this was designed ~1998! when
DBs could not do BLOBs well ;-).

#### logic.auth

Deals with authentication. In OGo/J the authentication subsystem is attached to
JAAS, making it much more flexible than OGo/ObjC.
Database accounts and teams are JAAS Principals,
there is a JAAS OGo login module and so on.

Note: Yes, JAAS is a bit complex, but you'll find a lot of convenience methods.

#### logic.authz

Don't mixup authentication w/ authorization ;-) This package deals with the
latter. Fetching ACLs, deriving ACLs from OGo objects, and deriving ACLs from
OGo object relationships (like: only project members can see a task attached to
one).
All very high performance and highly flexible.

#### logic.ops

This is similiar to the Logic commands in Objective-C OGo.

Instead of just 'saving' an EOActiveRecord to the DB, the API user is supposed
to call an operation to perform edits.
This ensures that permissions are properly handled,
that concurrent edits are detected,
and that all necessary related objects are dealt with.

E.g. in OGo a 'contact' is not just a single database record.
It is a base record with plenty of additional records attached.
Addresses, phone numbers, emails, etc are all own objects in an OGo database.

The OGo/J ops are highly optimized as well. Bulk inserts and bulk updates are
efficiently ordered by type, ACLs are checked in bulk, etc.
Again the API is a bit complex to support this, but there are convenience
methods to make easy cases easy to deal with.
