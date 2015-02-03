/*
  Copyright (C) 2008-2009 Helge Hess

  This file is part of OpenGroupware.org (OGo)

  OGo is free software; you can redistribute it and/or modify it under
  the terms of the GNU General Public License as published by the
  Free Software Foundation; either version 2, or (at your option) any
  later version.

  OGo is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU General Public
  License along with OGo; see the file COPYING.  If not, write to the
  Free Software Foundation, 59 Temple Place - Suite 330, Boston, MA
  02111-1307, USA.
*/
package org.opengroupware.logic.auth;

import org.getobjects.eoaccess.EODatabase;
import org.getobjects.jaas.EODatabaseJaasConfig;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoDefaultLoginConfig
 * <p>
 * A JAAS configuration object which just configures the OGo database config in
 * the default way.
 * 
 * @author helge
 */
public class OGoDefaultLoginConfig extends EODatabaseJaasConfig {
  
  public OGoDefaultLoginConfig(final EODatabase _db) {
    super(_db, OGoLoginModule.class);
  }
  
  public OGoDefaultLoginConfig(final String _dburl) {
    // TBD: this looks dangerous? No docpath? well, should be OK for login!
    this(OGoDatabase.databaseForURL(_dburl, null /* docpath */));
  }

}
