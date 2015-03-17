CAREFUL: OUTDATED INFORMATION, based on MySQL ~2004

MySQL database creation scripts
===============================

Surprisingly the PostgreSQL script loads almost unchanged.

Changes
=======

TIMESTAMP WITH TIMEZONE => DATETIME
pg table inheritance    => views
date_x                  => appointment

TODO
====

- create index does not work

Views
=====

Apparently there are no views with MySQL 4, so we will need:
  "Views (including updatable views) are implemented in the 5.0 version of
   MySQL Server. Views are available in binary releases from 5.0.1 and up."

Sequences
=========

create the sequence:
CREATE TABLE sequence (id INT NOT NULL);
INSERT INTO sequence VALUES (0);

fetch the next value:
UPDATE sequence SET id=LAST_INSERT_ID(id+1);
SELECT LAST_INSERT_ID();


MySQL Notes
===========

Creating new users ... using 'GRANT':

  GRANT ALL PRIVILEGES ON *.* 
        TO OGo@"%"
        IDENTIFIED BY 'OGo'
        WITH GRANT OPTION;

Cmdline Tool
============
mysql --protocol=tcp --host=localhost -u OGo --password=abc

mysql --protocol=tcp --host=localhost -u OGo --password=abc \
      < build-schema.mysql5

Starting OGo
============

ogo-webui-1.0a \
  -LSAdaptor MySQL4 \
  -LSConnectionDictionary '{hostName = "127.0.0.1"; userName=OGo; password=OGo; databaseName=OGo;}' \
  -LSModelName OpenGroupware.org_MySQL5
