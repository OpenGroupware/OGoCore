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
package org.opengroupware.logic.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOActiveRecord;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOKey;
import org.getobjects.eocontrol.EOQualifierEvaluation;
import org.getobjects.foundation.NSJavaRuntime;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.authz.OGoAuthzFetchContext;

/**
 * OGoObject
 * <p>
 * An OGo database object. All OGo objects share a set of common features, eg
 * they all have a primary key mapped to 'id'.
 */
public abstract class OGoObject extends EOActiveRecord implements IOGoObject {
  // TODO: add generic stuff:
  // - ACL support
  // - Logs

  protected static final Log log = LogFactory.getLog("OGoObject");
  public static final EOKey<Number> keyId = new EOKey<Number>("id");

  public Number id;
  public String appliedPermissions;

  public OGoObject(final EOEntity _entity) {
    super(_entity);
    
    if (_entity == null) {
      log.warn("got no entity for OGoObject: " + 
          this.getClass().getSimpleName());
    }
  }

  /* accessors */
  
  /**
   * Sets the primary key value of the object. This should be called only
   * once.
   */
  public void setId(final Number _id) {
    if (this.id == _id)
      return;
    
    if (this.id != null && _id != null && this.id.equals(_id))
      return;
    
    if (this.id != null) {
      log.warn("" + this.getClass().getSimpleName() + " id change " +
          this.id + "=>" + _id + ": " + this);
    }
    
    this.id = _id;
  }
  
  /**
   * Returns the primary key of the OGo object. Or null, if its a new object.
   * 
   * @return the primary key of the OGo object, or null for new objects
   */
  public Number id() {
    return this.id;
  }
  
  /**
   * Returns the 'objectVersion' which was active when this record was fetched.
   * 
   * @return the objectVersion, or null if there is none
   */
  public Number baseVersion() {
    Number ov;
    
    if (this.snapshot != null) {
      if ((ov = (Number)this.snapshot.get("objectVersion")) != null)
        return ov;
    }
    
    return (Number)this.valueForKey("objectVersion");
  }
  
  /* convenience accessors to avoid excessive casting */
  
  public OGoDatabase oDatabase() {
    EODatabase db = this.database();
    if (db == null) return null;
    return (OGoDatabase)db;
  }
  
  /* permissions */
  
  /**
   * Returns the permissions which has been applied on this object.
   */
  public String appliedPermissions() {
    return this.appliedPermissions;
  }
  
  /**
   * This returns a trampoline-object which can be used to check for
   * object permissions.
   * <p>
   * Example:<pre>
   *   person.hasPermission.w
   *   person.hasPermission.lbhM</pre>
   * The trampoline returns true/false depending on whether the object has
   * the requested permission.
   * 
   * @return the trampoline which can be queries for permissions
   */
  public NSObject hasPermission() {
    return new OGoObjectHasPermTrampoline(this); // TBD: cache it?
  }
  
  /**
   * This method returns 'true' if the login context has absolutely no access
   * to this object. All values should be cleared.
   * 
   * @return true if the object is just an inaccessible shadow of a forbidden
   *   record
   */
  public boolean isForbidden() {
    return this.appliedPermissions != null
      ? (this.appliedPermissions.length() == 0) /* no permissions on object */
      : false /* not yet checked */;
  }
  
  /**
   * Subclasses should override this method to remove protected values from the
   * object, when necessary. This default implementation just clears all values
   * by calling reset() for the no-permissions ("").
   * <p>
   * @param _perms
   */
  public void applyPermissions(final String _perms) {
    if (_perms.length() == 0) {
      // TBD: do not remove values, but replace them with a 'Forbidden' value?
      if (this.values != null)
        this.values.clear();
      
      /* remove snapshot, we are read-only */
      this.snapshot = null;
    }
  }

  /**
   * This is the entry method called by the security manager. It will call the
   * applyPermissions() method.
   * 
   * @param _perms
   */
  public void enforcePermissions(String _perms) {
    if (_perms == null) {
      log().warn("enforcePermissions() was called w/o permissions, using ''.");
      _perms = OGoAuthzFetchContext.noPermission;
    }
    
    if (this.appliedPermissions != null) {
      if (this.appliedPermissions.equals(_perms))
        return; /* already applied the given set */
      
      /* hm, unusual? */
      if (log.isInfoEnabled()) {
        log().info("applying a second permission set: '" + _perms + 
            "', already applied: '" + this.appliedPermissions + "'");
      }
    }
    
    this.applyPermissions(_perms);
    
    // TBD: this is wrong! we need the intersection of the two sets
    if (this.appliedPermissions == null)
      this.appliedPermissions = _perms;
  }
  
  @Override
  public boolean isReadOnly() {
    if (this.appliedPermissions != null) {
      if (this.appliedPermissions.length() == 0)
        return true;
    }
    return super.isReadOnly(); /* this will check whether the entity is 'r' */
  }
  
  /**
   * Overridden for smart UIs which do not attempt to display 'empty'
   * objects.
   */
  @Override
  public boolean isEmpty() {
    return (this.appliedPermissions != null &&
            this.appliedPermissions.length() == 0);
  }
  
  /* logging */
  
  public Log log() {
    return log;
  }
  
  
  /* saving */
  
  private static final int one = new Integer(1);
  
  @Override
  public Exception validateForInsert() {
    Exception error = super.validateForInsert();
    if (error != null) return error;
    
    EOEntity lEntity = this.entity();
    if (lEntity != null) {
      if (lEntity.attributeNamed("objectVersion") != null)
        this.takeValueForKey(one, "objectVersion");
      
      if (lEntity.attributeNamed("db_status") != null)
        this.takeValueForKey("inserted", "db_status");
      
      Date now = new Date();
      if (lEntity.attributeNamed("creationDate") != null)
        this.takeValueForKey(now, "creationDate");
      if (lEntity.attributeNamed("lastmodified_date") != null)
        this.takeValueForKey(now, "lastmodified_date");
      if (lEntity.attributeNamed("lastModified") != null)
        this.takeValueForKey(now, "lastModified");
    }
    
    return null; /* no errors */
  }

  @Override
  public Exception validateForUpdate() {
    Exception error = super.validateForUpdate();
    if (error != null) return error;
    
    EOEntity lEntity = this.entity();
    if (lEntity != null) {
      if (lEntity.attributeNamed("objectVersion") != null) {
        int v = NSJavaRuntime.intValueForKey(this, "objectVersion") + 1;
        this.takeValueForKey(new Integer(v), "objectVersion");
      }
      
      if (lEntity.attributeNamed("db_status") != null)
        this.takeValueForKey("updated", "db_status");

      Date now = new Date();
      if (lEntity.attributeNamed("lastModified") != null)
        this.takeValueForKey(now, "lastModified");
      if (lEntity.attributeNamed("lastmodified_date") != null)
        this.takeValueForKey(now, "lastmodified_date");
      if (lEntity.attributeNamed("lastmodified") != null)
        // hh(2024-11-12): Who is using this for what?
        this.takeValueForKey(now.getTime(), "lastmodified");
    }
    
    return null; /* no errors */
  }
  
  
  /* utility */
  
  /**
   * Extracts the toMany relationship for the given key (using valueForKey())
   * and filters the result using the given qualifier.
   * 
   * @param _relship - name of relationship, eg 'employments'
   * @param _q       - qualifier used to filter the relationship
   * @return the filtered collection
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Collection filterRelationship
    (final String _relship, final EOQualifierEvaluation _q)
  {
    Collection destObjects = (Collection)this.valueForKey(_relship);
    
    if (destObjects == null) {
      // no problem, can be NULL if there are no employements?
      if (log.isInfoEnabled()) {
        log.info("tried to query relship '" + _relship +
                 "', but it is not fetched: " + this);
      }
      return null;
    }
    if (_q == null)
      return destObjects;
    
    boolean didFilter = false;
    List matches = null;
    for (Object destObject: destObjects) {
      if (!_q.evaluateWithObject(destObject)) {
        didFilter = true;
        continue;
      }
      
      if (matches == null)
        matches = new ArrayList(destObjects.size());
      matches.add(destObject);
    }
    
    return didFilter ? matches : destObjects;
  }
  
  
  /* legacy */
  
  public String entityNameInOGo5() {
    return null;
  }
  

  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    if (this.id != null) {
      _d.append(" id=");
      _d.append(this.id);
      
      if (this.isNew())
        _d.append(" new-with-id");
      else if (this.hasChanges()) // too expensive?
        _d.append(" changed");
    }
    else
      _d.append(" new");
    
    if (this.isForbidden())
      _d.append(" FORBIDDEN");
    else if (this.appliedPermissions != null) {
      _d.append(" perm='");
      _d.append(this.appliedPermissions);
      _d.append('\'');
    }
    
    super.appendAttributesToDescription(_d);
  }

}
