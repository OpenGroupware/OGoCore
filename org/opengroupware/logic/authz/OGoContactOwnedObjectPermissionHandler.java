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
package org.opengroupware.logic.authz;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.opengroupware.logic.db.OGoContact;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoContactOwnedObjectPermissionHandler
 * <p>
 * Superclass for permission handlers which directly depend on a contact. That
 * is, email addresses, phone numbers, addresses.
 * 
 * <p>
 * @author helge
 */
public class OGoContactOwnedObjectPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public boolean process
    (OGoAuthzFetchContext _ac, EOKeyGlobalID _gid,
     NSKeyValueCoding _object, Object _info)
  {
    /* The type is required, but only if we have access at all. So it
     * doesn't make sense to fetch the records before we know about the
     * permissions of the person itself?
     * => WRONG! We need to fetch the record to know the person-id of the
     * address/email/phone :-)
     */
    
    if (_object == null && _info == null) {
      if (log.isDebugEnabled())
        log.debug("process contact-gid w/o object/info: " + _gid);
      
      /* we do NOT have the info for this GID and need to fetch it */
      _ac.requestFetchOfInfo(this, _gid);
      
      return false; /* pending, need to fetch info */
    }
    
    
    /* determine base entity (Person, Company, Team, Resource) */
    
    char baseEntity = _gid.entityName().charAt(0);
    
    /* first find the GID of the contact we depend on */
    
    EOKeyGlobalID contactGID = null;
    
    if (_object != null) {
      // TBD: T for team, R for resource
      // TBD: what to do first, check object or check attribute?
      OGoContact c = null;
      if (baseEntity == 'P')
        c = (OGoContact)_object.valueForKey("person");
      else if (baseEntity == 'C')
        c = (OGoContact)_object.valueForKey("company");
      contactGID = (EOKeyGlobalID)_ac.oCtx.globalIDForObject(c);
      
      if (contactGID == null) {
        Number contactId = (Number)_object.valueForKey("companyId");
        contactGID = EOKeyGlobalID.globalIDWithEntityName
          ((baseEntity == 'P' ? "Persons" : "Companies"),
           new Object[] { contactId });
      }
    }
    
    if (contactGID == null && _info != null) {
      Number contactId = (Number)NSKeyValueCoding.Utility.valueForKey
        (_info, "companyId");
      contactGID = EOKeyGlobalID.globalIDWithEntityName
        ((baseEntity == 'P' ? "Persons" : "Companies"),
            new Object[] { contactId });
    }
    
    if (contactGID == null) {
      log.error("could not determine GID of contact for: " + _gid);
      return true; // TBD: return 'true' on error so that the process stops?
    }
    
    
    String contactPermission = _ac.gidToPermission.get(contactGID);
    if (contactPermission == null) {
      /* OK, permissions of contact itself are not yet fetched! Request them. */
      
      if (log.isDebugEnabled())
        log.debug("    requesting contact permissions: " + contactGID);
      
      _ac.registerObjectDependency(this, _gid, contactGID);
      return false; /* not yet done */
    }

    /* OK, we have a GID from the object, lets process it */
    
    String perm = this.objectPermissionForContactPermission
      (contactPermission, _object, _info);
    if (perm == null) {
      _ac.requestFetchOfInfo(this, _gid);
      return false; /* pending, need to fetch info on the object (eg type) */
    }
    
    if (log.isDebugEnabled())
      log.debug("    DONE: found contact permissions: " + contactGID);
    
    _ac.recordPermissionsForGlobalID(perm, _gid);
    return true; /* ok, found it */
  }
  
  /**
   * Calculates the object permission depending on the permission of the
   * contact. Subclasses usually override this method.
   * <p>
   * The default implementation knows about 'r', 'w' and 'l' permissions. If
   * the contact has 'w' permission, we return 'rw', if he has 'r' permission,
   * we return 'r' in any other case we return noPermission (so subclasses need
   * to detect them to allow for broader access when no 'r' permission is set).
   * <p>
   * @param _contactPerm - permissions set on the contact
   * @return permissions for the contact-subobject or null to request the info
   */
  public String objectPermissionForContactPermission
    (String _contactPerm, NSKeyValueCoding _object, Object _info)
  {
    // TBD: now we need to derive the subobject permissions based on the
    //      contact permissions, eg '' for private items if the user has
    //      no access
    // for now, we just copy the parent permissions
    if (_contactPerm == null)
      return null;
    
    int count = _contactPerm.length();
    if (count == 0) /* no permissions on contact, no permission on object */
      return OGoAuthzFetchContext.noPermission;
    
    if (_contactPerm.indexOf('w') >= 0)
      return "rw"; // 'w' implies 'r'
    if (_contactPerm.indexOf('r') >= 0)
      return "r";
    
    return OGoAuthzFetchContext.noPermission;
  }

  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    // TBD: fetch the relevant information
    log.error("fetch of company subobject not implement: " + _gids);
    return null;
  }
  
}
