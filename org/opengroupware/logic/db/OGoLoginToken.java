/*
  Copyright (C) 2007 Helge Hess

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
package org.opengroupware.logic.db;

import java.util.Date;

import org.getobjects.eoaccess.EOActiveRecord;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSPropertyListSerialization;

/**
 * OGoLoginToken
 * <p>
 * Represents a login token in the database.
 * 
 * <p>
 * Note: this object is used in a multithreaded way in OGoLoginTokenManager!
 * Be careful not to modify state after fetches.
 * 
 * <p>
 * @author helge
 */
public class OGoLoginToken extends EOActiveRecord {
  
  public String  token;
  public Integer accountId;
  
  public Date    creationDate;
  public Date    expirationDate;
  public Date    touchDate;
  public Integer timeout;
  
  public String  environment;
  public String  info;
  public Object  envPList;
  public Object  infoPList;

  public OGoLoginToken(EOEntity _entity) {
    super(_entity);
  }
  
  /* expiration */
  
  public boolean isExpired(Date _now, Date _lastTouch) {
    /* The last touch argument is because the _actual_ touches are stored in a
     * separate entity (the token manager). The 'touchDate' in this object only
     * stores the last value fetched (not even the database value).
     * 
     * Remember that we may not update the ivars due to threading!
     */
    
    if (_lastTouch == null) _lastTouch = this.touchDate;
    if (_lastTouch == null) /* no touchdate is invalid in any case*/
      return true;
    
    if (_now == null) _now = new Date();
    
    /* Note: expirationDate is a *hard* expiration date. A new token must be
     *       aquired after this one expired. This one was intended for cookie
     *       style login ("keep me logged in for one day").
     *       Again: the expiration date is *immutable*!!
     *       
     *       This could also be used as a security measure (*require* users to
     *       relogin every 24h or so).
     */
    if (this.expirationDate != null && this.expirationDate.before(_now))
      return true; /* the absolute expiration date was reached */
    
    
    /* this much time has passed since the user clicked */
    long ageInMS = _now.getTime() - _lastTouch.getTime();
    if (ageInMS < 0) {
      /* This *can* happen because we are multithreaded and another thread
       * might have touched the token. Anyways, in this case we are fine :-)
       */
      return false; /* someone else just touched the token, valid */
    }
    
    long timeoutInMS =
      this.timeout != null ? (this.timeout * 1000) : (3600 * 1000);
      
    if (ageInMS > timeoutInMS)
      return true; /* token is older than timeout */
    
    /* OK, we did not timeout yet */
    return false;
  }
  public boolean isExpired() {
    return this.isExpired(null /* now */, null /* last touch */);
  }

  /* awake */
  
  @Override
  public void awakeFromFetch(EODatabase _db) {
    super.awakeFromFetch(_db);
    
    if (this.environment != null) {
      this.envPList = 
        NSPropertyListSerialization.propertyListFromString(this.environment);
    }
    
    if (this.info != null) {
      this.infoPList = 
        NSPropertyListSerialization.propertyListFromString(this.info);
    }
  }
  
}
