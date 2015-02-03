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

import org.getobjects.appserver.core.WOCoreContext;
import org.opengroupware.logic.db.OGoDatabase;

public interface IOGoDatabaseProvider {

  /**
   * Used by OGoContext and OGoJoAuthenticator to retrieve the OGoDatabase
   * object.
   * <p>
   * This takes a context to allow for multiple OGoDatabase objects per
   * application. (even though this implementation just provides one).
   * 
   * @param _ctx - the context the operation happens in
   * @return the OGoDatabase object
   */
  public abstract OGoDatabase databaseForContext(WOCoreContext _ctx);

}