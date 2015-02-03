/*
  Copyright (C) 2008 Helge Hess

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
package org.opengroupware.logic.ops;

import org.getobjects.foundation.NSException;
import org.opengroupware.logic.db.OGoObject;

/**
 * OGoVersionMismatchException
 * <p>
 * Created if the 'object_version' of an OGo object changed in the database. 
 * 
 * @author helge
 */
public class OGoVersionMismatchException extends NSException {
  private static final long serialVersionUID = 1L;
  
  protected OGoObject object;
  protected Number    databaseVersion;
  protected Number    cacheVersion;

  public OGoVersionMismatchException
    (OGoObject _object, Number _databaseVersion, Number _cacheVersion)
  {
    super("version mismatch");
    this.object          = _object;
    this.databaseVersion = _databaseVersion;
    this.cacheVersion    = _cacheVersion;
  }
  
  /* accessors */
  
  public OGoObject object() {
    return this.object;
  }
  
  public Number databaseVersion() {
    return this.databaseVersion;
  }
  
  public Number cacheVersion() {
    return this.cacheVersion;
  }
  

  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    _d.append(" current=");
    _d.append(this.databaseVersion);
    _d.append(" cache=");
    _d.append(this.cacheVersion);
    _d.append(" object=");
    _d.append(this.object);
  }
}
