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
package org.opengroupware.logic.authz;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoContactPermissionHandler
 * <p>
 * Abstract superclass for handling permissions of 'company' objects. All
 * 'company' object permissions work more or less the same way.
 * <ul>
 * <li>the owner (ownerId) always has full permissions
 * <li>if the 'isPrivate' flag is not set, the contact is PUBLIC, the ACL is
 * <b>not</b> checked. (<b>OGo ObjC has exactly the opposite behaviour!!</b>, so
 * we ignore OGo ACLs, and OGo does not show our ACL protected contacts)
 * <li>if the 'isReadOnlyFlag' flag is set, the permission of <em>public</em>
 * contacts is restricted to that. Except for the owner. An ACL entry also
 * overrides readonly.
 * <li>the primary contact (contactId) always has read permission
 * <li>and if the contact is one of the logged in accounts, we also have at
 * least read access (plus access granted by the ACL)
 * <li><code>w</code> (write) always implies <code>r</code>
 * </ul>
 * 
 * <p>
 * Permissions
 * <ul>
 * <li><code>r</code> - all details can be shown (lbpiPM)
 * <li><code>w</code> - contact can be edited
 * </ul>
 * 
 * <p>
 * Data Read Restriction
 * <ul>
 * <li><code>l</code> - list
 * <li><code>b</code> - business contact data (phone/address)
 * <li><code>p</code> - private contact data (phone/address)
 * <li><code>I</code> - IM data
 * <li><code>P</code> - private data like birthday etc
 * <li><code>M</code> - mobile number
 * <li><code>s</code> - may send private messages
 * <li><code>c</code> - may connect with me in the system?
 * </ul>
 * 
 * <p>
 * Relevant attributes:
 * <ul>
 * <li>ownerId (owner_id)
 * <li>contactId (contact_id)
 * <li>isPrivate (is_private)
 * <li>isReadOnlyFlag (is_readonly)
 * </ul>
 * plus the standard ACL
 * 
 * <p>
 * 
 * @author helge
 */
public class OGoContactPermissionHandler extends NSObject implements
    IOGoPermissionHandler {
  protected static final Log log                              = LogFactory
                                                                  .getLog("OGoAuthz");

  // This way we might 'add' additional permissions using ACEs, even though
  // those settings should always imply full rw access. Not sure whether this
  // makes sense.
  public static final String publicContactReadOnlyPermissions = "r";
  public static final String publicContactPermissions         = "rw";
  public static final String ownerContactPermissions          = "rw";
  public static final String primaryContactPermissions        = "r";
  public static final String authorizedContactPermissions     = "r";

  public static final String rPermissions                     = "rlbpIPM";
  public static final String wPermissions                     = "rwlbpIPMsc";

  public static String resolveCompoundPermissions(String _perm) {
    if (_perm == null)
      return null;
    int count = _perm.length();
    if (count == 0)
      return _perm; /* no permissions */

    if (_perm.indexOf('w') >= 0) /* 'w' implies 'r' */
      return UString.unionCharacterSets(_perm, wPermissions);

    if (_perm.indexOf('r') >= 0)
      return UString.unionCharacterSets(_perm, rPermissions);

    if (count == 1 && _perm.charAt(0) == 'l')
      return _perm; /* just list */

    if (_perm.indexOf('l') == -1) /* 'l' is implied by any other permission */
      _perm = UString.unionCharacterSets(_perm, "l");

    return _perm;
  }

  public boolean process(OGoAuthzFetchContext _ac, EOKeyGlobalID kgid,
      NSKeyValueCoding object, Object _info) {
    return this.processWithStrictPublic(_ac, kgid, object, _info);
  }

  @SuppressWarnings("rawtypes")
  public boolean processWithStrictPublic(OGoAuthzFetchContext _ac,
      EOKeyGlobalID kgid, NSKeyValueCoding object, Object _info) 
  {
    if (object == null && _info == null) {
      if (log.isDebugEnabled())
        log.debug("process contact-gid w/o object/info: " + kgid);

      /* we do NOT have the info for this GID and need to fetch it */
      _ac.requestFetchOfInfo(this, kgid);

      /* and in case we fetch ACLs, we also fetch the ACLs of this object */
      _ac.considerFetchOfACL(this, kgid);

      return false; /* pending, need to fetch info */
    }

    if (log.isDebugEnabled())
      log.debug("    contact-gid with object/info: " + kgid);

    /* extract relevant fields */

    Boolean isPrivate = null;
    Boolean isReadOnly = null;
    Number ownerId = null;
    Number contactId = null;
    if (object != null) {
      // TBD: can we checked whether field was fetched?
      isPrivate = UObject.boolValue(object.valueForKey("isPrivate"));
      isReadOnly = UObject.boolValue(object.valueForKey("isReadOnlyFlag"));
      ownerId = (Number) object.valueForKey("ownerId");
      contactId = (Number) object.valueForKey("contactId");
    }
    if (_info != null) {
      Map m = (Map) _info;

      if (isPrivate == null)
        isPrivate = UObject.boolValue(m.get("is_private"));
      if (isReadOnly == null)
        isReadOnly = UObject.boolValue(m.get("is_readonly"));
      if (ownerId == null)
        ownerId = (Number) m.get("owner_id"); // raw fetches
      if (contactId == null)
        contactId = (Number) m.get("contact_id"); // raw fetches
    }

    /* first we check whether we are the owner */

    if (_ac.contextHasAccountId(ownerId)) {
      /* Its private, but we are the owner. Hurray! */
      if (log.isDebugEnabled())
        log.debug("    DONE: we own the contact: " + kgid);
      _ac.recordPermissionsForGlobalID(
          resolveCompoundPermissions(ownerContactPermissions), kgid);
      return true;
    }

    /* check whether its public (not marked private) */
    // TBD: hm

    if (isPrivate == null || !isPrivate.booleanValue()) {
      /*
       * yes, isPrivate != 1 => public object, no ACL checks necessary
       */
      if (log.isDebugEnabled())
        log.debug("    DONE: contact is public: " + kgid);

      String perm = (isReadOnly == null || !isReadOnly.booleanValue()) ? publicContactPermissions
          : publicContactReadOnlyPermissions;

      _ac.recordPermissionsForGlobalID(resolveCompoundPermissions(perm), kgid);
      return true;
    }

    /*
     * ok, its private, we are NOT the owner. Check the ACL to see whether the
     * owner granted us access
     */

    String aclPerm = _ac.processACLOfObject(kgid,
        (object != null) ? (Collection) object.valueForKey("acl") : null);
    if (aclPerm == null) {
      _ac.requestFetchOfACL(this, kgid);
      return false; /* pending, need to fetch ACL */
    }

    /* give read-access to the contact */

    if (_ac.contextHasPrincipalId(contactId)) {
      /* Its private, but we are the primary contact */
      if (log.isDebugEnabled())
        log.debug("    we are the primary contact: " + kgid);
      aclPerm = UString.unionCharacterSets(aclPerm, primaryContactPermissions);
    }
    if (_ac.contextHasAccountId(kgid.toNumber())) {
      // TBD: also check teams?
      /* Its private, but this is us :-) */
      if (log.isDebugEnabled())
        log.debug("    DONE: we ARE the contact: " + kgid);
      aclPerm = UString.unionCharacterSets(aclPerm,
          authorizedContactPermissions);
      return true;
    }

    if (log.isDebugEnabled())
      log.debug("    DONE: we have the ACL: " + kgid);
    _ac.recordPermissionsForGlobalID(resolveCompoundPermissions(aclPerm), kgid);
    return true;
  }

  /* TBD: implement men
  public boolean processWithStrictPrivate(OGoAuthzFetchContext _ac,
      EOKeyGlobalID kgid, NSKeyValueCoding object, Object _info) 
  {
    // TBD: implement me
    // return processWithStrictPublic(_ac, kgid, object, _info);
  }
  */

  /* fetching infos */

  @SuppressWarnings("rawtypes")
  private static final Map emptyMap = Collections.emptyMap();

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs(
      OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids) {
    if (_gids == null || _gids.size() == 0)
      return null;
    final boolean debugOn = log.isDebugEnabled();

    /* extract primary keys */

    final Map<Number, EOKeyGlobalID> pkeyToGlobalID = new HashMap<Number, EOKeyGlobalID>(
        _gids.size());
    for (EOKeyGlobalID gid : _gids)
      /* extract key */
      pkeyToGlobalID.put(gid.toNumber(), gid);

    /* prepare result set */

    final Map<EOKeyGlobalID, Object> infos = new HashMap<EOKeyGlobalID, Object>(
        _gids.size());

    /*
     * This is a hack to avoid issues when checking Company IDs against Person
     * primary keys.
     */
    for (EOKeyGlobalID gid : _gids)
      infos.put(gid, emptyMap);

    /* group GIDs by entity (should be just one entity, but who knows ;-) */

    Map gidsByEntityName = UList.groupByKey(_gids, "entityName");

    final EOAdaptorChannel channel = _db.adaptor().openChannelFromPool();
    try {
      for (String entityName : (Collection<String>) gidsByEntityName.keySet()) {
        final EOEntity entity = _db.entityNamed(entityName);
        if (entity == null) {
          log.error("could not locate entity: '" + entityName + "'");
          continue;
        }

        EOFetchSpecification fs = entity.fetchSpecificationNamed("authzFetch");
        if (fs == null) {
          log.error("entity has no 'authzFetch' specification: " + entityName);
          continue;
        }

        final List gids = (List) gidsByEntityName.get(entityName);
        fs = fs.fetchSpecificationWithQualifierBindings("ids", gids, "authIds",
            _ac.authIds);

        /* fetch */

        final List<Map<String, Object>> contacts = channel.selectAttributes(
            null, fs, false, entity);

        if (contacts == null) {
          log.error("no contacts found in entity " + entityName
              + " using fetch spec: " + fs);
          continue;
        }

        for (Map<String, Object> contact : contacts) {
          final Number pkey = (Number) contact.get("company_id");
          final EOKeyGlobalID pgid = pkeyToGlobalID.get(pkey);

          /*
           * Check owner inline, if we have the owner, we don't need to check
           * anything else, he has always full access
           */
          final Number ownerId = (Number) contact.get("owner_id");
          if (_ac.contextHasPrincipalId(ownerId)) {
            if (debugOn)
              log.debug("  detected ownership: " + pkey);
            _ac.recordPermissionsForGlobalID(ownerContactPermissions, pgid);
            continue;
          }

          infos.put(pgid, contact);
        }
      }
    }
    finally {
      if (channel != null)
        _db.adaptor().releaseChannel(channel);
    }

    return infos;
  }
}
