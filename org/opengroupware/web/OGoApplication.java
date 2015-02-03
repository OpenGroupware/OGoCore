/*
  Copyright (C) 2007-2014 Helge Hess

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
package org.opengroupware.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.appserver.core.WOApplication;
import org.getobjects.appserver.core.WOCoreContext;
import org.getobjects.eoaccess.EOAdaptor;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * Subclass of WOApplication which sets up an OGo database connection based
 * on configuration data.
 * 
 * <h3>Configuration</h3>
 * Add something like this to your applications Defaults.properties file:
 * <pre>
 *   DB = jdbc:postgresql://127.0.0.1/OGo?user=OGo&password=OGo
 *   LSAttachmentPath = /var/lib/ogo/documents
 * </pre>
 * LSAttachmentPath corresponds to the LSAttachmentPath default of the
 * Objective-C version of OGo. It points to the location where OGo puts
 * BLOBs (documents, notes).
 * 
 * <p>
 * @author helge
 */
public class OGoApplication extends WOApplication
  implements IOGoDatabaseProvider
{
  protected static final Log log = LogFactory.getLog("OGoApplication");

  protected OGoDatabase database;

  public OGoApplication() {
    super();
  }

  public void init() {
    super.init();
    
    this.database = OGoDatabase.databaseForURL(
        this.databaseURL(),
        (String)(this.properties != null
            ? this.properties.get("LSAttachmentPath"): null));
    if (this.database == null)
      log().error("got no EODatabase for database URL");
  }
  
  /* database */
  
  public EOAdaptor adaptor() {
    return this.database.adaptor();
  }
  
  public OGoDatabase databaseForContext(WOCoreContext _ctx) {
    return this.database;
  }
  
  /* defaults */
  
  protected String databaseURL() {
    return this.properties.getProperty("DB");
  }
  
  /* log */
  
  public Log log() {
    return log;
  }

}
