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
import org.getobjects.foundation.UString;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * OGoDocumentPermissionHandler
 * <p>
 * Permissions for OGo database
 * <ul>
 *   <li>folders (entity is also 'Documents'!, with is_folder = 1)
 *   <li>documents (called 'attachments' when the parent_id is not a folder)
 *   <li>notes (called 'comments' if they are attached to a note)
 * </ul>
 * Documents and Notes are not necessarily bound to a project,
 * they can also be attached to contacts
 * or appointments (contact_id, date_id).<br />
 * But *when* they are bound to a project, the user must have access to that
 * project to have access to the note/doc.
 * And the permission mask of the document is always a subset of the project
 * (even if you have 'rw' on the document via ACL, you only get 'r' if the
 *  project has just 'r').
 * 
 * <p>Rules:
 * <ul>
 *   <li>if the doc has a project, the doc ACL is restricted to the project ACL
 *   <li>if the doc has a parent_doc (eg a folder), the doc must have read
 *       access to the parent
 *   <li>if the doc has a parent_doc (eg a folder), the delete ('d') permission
 *       is derived from the folder
 *   <li>if a date or contact is associated with the document/note, the user
 *       must have 'r' access to one of them
 *   <li>the creator of a document has no special permissions
 *   <li>the owner of a document has full access to the document, unless she
 *       lost access to the relevant associated objects
 * </ul>
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li><code>l</code> - filename is visibile
 *   <li><code>r</code> - document/note can be read
 *   <li><code>w</code> - document/note can be written
 *   <li><code>i</code> - you can insert into a folder (doc)
 *   <li><code>d</code> - you can delete from a folder (doc)
 * </ul>
 * 
 * <p>
 * Relevant attributes
 * <ul>
 *   <li>projectId
 *   <li>parentDocumentId
 *   <li>dateId
 *   <li>creatorId
 *   <li>ownerId
 *   <li>companyId (the contact the object is attached to)
 * </ul>
 * 
 * <p>
 * Fetch Info<br />
 * The fetch info is a Map.
 * 
 * <p>
 * @author helge
 */
public class OGoDocumentPermissionHandler extends NSObject
  implements IOGoPermissionHandler
{
  protected static final Log log = LogFactory.getLog("OGoAuthz");

  public static final IOGoPermissionHandler defaultHandler =
    new OGoDocumentPermissionHandler();

  /* the owner always has 'rw' access to the document */
  public static final String ownerPermissions   = "rwd";
  
  /* no special powers for the creator */
  public static final String creatorContactPermissions = "";
  
  /* If the object has no ACL and NO project (attached to a contact or date). In
   * this case others can read, but not write.
   */
  public static final String publicNoProjectPermissions = "r";
  
  /* If the object has no ACL, but a project. All project members can
   * access the document */
  public static final String publicProjectPermissions = "rwd";
  
  /* If the object has no ACL and no associated project, contact or date. Its
   * a plain, unassociated note. We'll keep that private!
   */
  public static final String publicUnattachedPermissions = "";
  
  
  @SuppressWarnings("rawtypes")
  public boolean process
    (final OGoAuthzFetchContext _ac, final EOKeyGlobalID _gid,
     final NSKeyValueCoding _object, final Object _info)
  {
    final boolean debugOn = log.isDebugEnabled();
    
    if (_object == null && _info == null) {
      if (debugOn)
        log.debug("        process doc-gid w/o object/info: " + _gid);
      
      /* we do NOT have the info for this GID and need to fetch it */
      _ac.requestFetchOfInfo(this, _gid);
      
      /* We first need to check whether we have access at all (can access the
       * project) before we need the ACL. So if he fetches ACLs, get ours too,
       * otherwise we delay the request.
       */
      _ac.considerFetchOfACL(this, _gid);
      
      return false; /* pending, need to fetch info */
    }
    
    
    /* first collect relevant object ids */
    
    Number projectId = null;
    Number contactId = null;
    Number eventId   = null;
    Number parentId  = null;
    Number creatorId = null;
    Number ownerId   = null;
    
    if (_object != null) {
      projectId = (Number)_object.valueForKey("projectId");
      contactId = (Number)_object.valueForKey("companyId");
      eventId   = (Number)_object.valueForKey("eventId");
      parentId  = (Number)_object.valueForKey("parentId");
      creatorId = (Number)_object.valueForKey("creatorId");
      ownerId   = (Number)_object.valueForKey("ownerId");
    }
    if (_info != null) {
      if (projectId == null)
        projectId = (Number)((Map)_info).get("project_id"); // raw fetches
      if (contactId == null)
        contactId = (Number)((Map)_info).get("company_id"); // raw fetches
      if (eventId == null)
        eventId   = (Number)((Map)_info).get("date_id"); // raw fetches
      if (parentId == null)
        parentId  = (Number)((Map)_info).get("parent_document_id"); // raw
      if (creatorId == null)
        creatorId  = (Number)((Map)_info).get("first_owner_id"); // raw
      if (ownerId == null)
        ownerId  = (Number)((Map)_info).get("current_owner_id"); // raw
    }
    
    EOKeyGlobalID projectGlobalID    = null;
    String  projectPerm = null;
    Boolean contactPerm = null;
    Boolean eventPerm   = null;
    String  parentPerms = null;

    
    /* Stage A, first we check all caches to avoid fetches in case we have some
     * 'master object'. (an object which can block access in any case)
     */
    
    /* First thing to do is to check whether the doc is attached to a
     * project, and if so, whether we have access to the project.
     * 
     * As mentioned the project is the ultimate blocker :-)
     * 
     * Note that if you have access to the project, access to the contact or
     * event does not matter anymore.
     */
    if (projectId != null) {
      /* Optimization: first we check whether we have access to the project
       * at all. This can use prefetched lists of project ids in the authz
       * context.
       */
      final Boolean canAccessProject =
        _ac.contextHasAccessToProjectWithPrimaryKey(projectId);

      if (canAccessProject != null && !canAccessProject.booleanValue()) {
        /* we have the cached info that we may not access the project */
        if (debugOn)
          log.debug("        done: object has no project access" + _gid);
        _ac.recordPermissionsForGlobalID
          (OGoAuthzFetchContext.noPermission, _gid);
        return true;
      }

      projectGlobalID = EOKeyGlobalID.globalIDWithEntityName
        ("Projects", new Object[] { projectId });

      if ((projectPerm = _ac.gidToPermission.get(projectGlobalID)) != null) {
        /* Ok, we have a cached project permission, check it. Any project
         * permission is OK for general access
         */
        if (projectPerm.length() == 0) {
          /* no permission to project */
          if (debugOn)
            log.debug("        done: object has no project perm" + _gid);
          _ac.recordPermissionsForGlobalID
            (OGoAuthzFetchContext.noPermission, _gid);
          return true;
        }
      }
    }
    else {
      /* If no project is assigned, access depends on the contact
       * or on the date. Actually we only need to know about 'read' access?
       * 
       * Check whether we have read access to the associated contact/date,
       * a requirement if no project is assigned. If a project IS assigned,
       * the contact or date permission does not matter.
       * 
       * Hm. Tricky whats best here.
       * In any case its faster if just one 'master object' is sufficient. :-)
       */
      if (contactId != null) {
        contactPerm = _ac.hasReadAccessToObject("Persons", contactId);
        if (contactPerm == null)
          contactPerm = _ac.hasReadAccessToObject("Companies", contactId);

        if (contactPerm != null && !contactPerm.booleanValue()) {
          /* no read permission, reject access to document */
          if (debugOn)
            log.debug("        done: object has no contact access" + _gid);
          _ac.recordPermissionsForGlobalID
            (OGoAuthzFetchContext.noPermission, _gid);
          return true;
        }
      }
      /* If we would do 'else if', either contact or date access would be
       * sufficient. This way we need both.
       */
      if (eventId != null) {
        eventPerm = _ac.hasReadAccessToObject("Events", eventId);
        if (eventPerm != null && !eventPerm.booleanValue()) {
          /* no read permission, reject access to document */
          if (debugOn)
            log.debug("        done: object has no event access" + _gid);
          _ac.recordPermissionsForGlobalID
            (OGoAuthzFetchContext.noPermission, _gid);
          return true;
        }
      }
    }
    
    /* next check the hierarchy */ 

    if (parentId != null) {
      // TBD: should we allow access for owners anyways?
      parentPerms = _ac.permissionsForObject("Documents", parentId);
      if (parentPerms == null) {
        // TBD: document, does this check for comments on notes?
        parentPerms = _ac.permissionsForObject("Notes", parentId);
      }
      
      if (parentPerms != null && parentPerms.indexOf('r') < 0) {
        /* no read permission, reject access to document */
        // TBD: hm, we can have 'd' w/o 'r'?
        if (debugOn)
          log.debug("        done: object has no parent access" + _gid);
        _ac.recordPermissionsForGlobalID
          (OGoAuthzFetchContext.noPermission, _gid);
        return true;
      }
    }
    
    /* Next we check whether we have a cached ACL which rejects access
     * (ACLs don't matter for owners)
     */

    String  aclPerms  = _ac.gidToACLPermission.get(_gid);
    boolean hasACL    = false; /* only valid when aclPerms != null */
    boolean weOwn     = _ac.contextHasAccountId(ownerId);
    //boolean weCreated = _ac.contextHasAccountId(creatorId);
    if (!weOwn && aclPerms != null) {
      /* ok, the ACL got fetched. Check whether there actually was an ACL
       * for the object
       */
      hasACL = _ac.gidHasACL.get(_gid);
      if (hasACL && aclPerms.length() == 0) {
        /* OK, the object *has* an ACL and we got no permission. */
        if (debugOn)
          log.debug("        done: object has ACL not listing us: " + _gid);
        _ac.recordPermissionsForGlobalID
          (OGoAuthzFetchContext.noPermission, _gid);
        return true;
      }
    }
    

    
    
    
    /* Stage B: requesting fetch of necessary information */ 
    /* Consider ACL fetch in the next iteration. Won't be triggered by the
     * project fetch, but possibly by other objects in the context (eg
     * contacts).
     */

    if (projectId != null) {
      if (projectPerm == null) {
        /* request project permissions */
        if (debugOn) {
          log.debug("        request: need perms of project: " 
              + _gid + " / " + projectGlobalID);
        }
        _ac.registerObjectDependency(this, _gid, projectGlobalID);
        if (!weOwn) _ac.considerFetchOfACL(this, _gid);

        // TBD: check whether there are pending document fetches, if so,
        //      request the parent-id.
        
        return false; /* first fetch the project permissions */
      }
    }
    else {
      if (contactId != null && contactPerm == null) {
        // TBD: could be a team or a resource? ;-)
        EOKeyGlobalID personGlobalID = EOKeyGlobalID.globalIDWithEntityName
          ("Persons", contactId);
        EOKeyGlobalID companyGlobalID = EOKeyGlobalID.globalIDWithEntityName
          ("Companies", contactId);
        
        /* request contact permissions */
        if (debugOn)
          log.debug("        request: need perms of contact: " + _gid);
        _ac.registerObjectDependency(this, _gid, personGlobalID);
        _ac.registerObjectDependency(this, _gid, companyGlobalID);
        if (!weOwn) _ac.considerFetchOfACL(this, _gid);

        // TBD: would be nice to have optional fetches here, we could
        //      optionally fetch dateId and/or parentId
        return false; /* need contact info */
      }
      /* If we would do 'else if', either contact or date access would be
       * sufficient. This way we need both.
       */
      if (eventId != null && eventPerm == null) {
        /* request event permissions */
        EOKeyGlobalID eventGlobalID = EOKeyGlobalID.globalIDWithEntityName
          ("Events", eventId);

        if (debugOn)
          log.debug("        request: need perms of event: " + _gid);
        _ac.registerObjectDependency(this, _gid, eventGlobalID);
        if (!weOwn) _ac.considerFetchOfACL(this, _gid);
        return false; /* need event info */
      }
    }
    
    
    /* Next check the hierachy of the document, that is, check whether we have
     * access to the document's folder (but could be a regular document for
     * attachments)
     */
    
    if (parentId != null && parentPerms == null) {
      /* request parent permissions
       * Note: could be a folder (Documents entity) or a note (Notes entity).
       */
      EOKeyGlobalID parentDocGlobalID = EOKeyGlobalID.globalIDWithEntityName
        ("Documents", parentId);
      EOKeyGlobalID parentNoteGlobalID = EOKeyGlobalID.globalIDWithEntityName
        ("Notes", parentId);
      
      if (debugOn)
        log.debug("        request: need perms of parent: " + _gid);
      _ac.registerObjectDependency(this, _gid, parentDocGlobalID);
      _ac.registerObjectDependency(this, _gid, parentNoteGlobalID);
      _ac.considerFetchOfACL(this, _gid);
      return false; /* need another object */
    }
    
    
    /* Extract the permissions on the document. There are those cases:
     * a) we are the owner => ownerAccess intersect projectAccess
     * b) there is no ACL  => projectAccess
     * c) there is an ACL  => ACL intersect projectAccess
     */
    String perm = null;
    
    if (weOwn) {
      // TBD: need to check this. Eg if the owner is reduced to read access,
      //      what happens with checked-out documents? He won't be able to
      //      check them in? Or maybe she keeps the permission on the editing
      //      object.
      perm = ownerPermissions;
    }
    else if (aclPerms == null) {
      /* We are not the owner and the ACL was not yet fetched yet. Request the
       * fetch.
       */
      _ac.requestFetchOfACL(this, _gid);
      return false; /* need contact info */
    }
    else {
      /* we are not the owner, check the (already fetched) ACL */
      
      if (_ac.gidHasACL.get(_gid).booleanValue()) {
        /* ACL is assigned, get the permission. This can be noPermission! If the
         * ACE doesn't list us, we won't have access.
         * 
         * Except of course for the owner which is handled above (otherwise it
         * would be too easy for the owner to remove himself from access while
         * editing an ACL ...).
         */
        perm = _ac.gidToACLPermission.get(_gid);
      }
      else {
        /* Object has no ACL, so it more or less readonly, public. You still
         * need to have access to the project, event or contact.
         * 
         * See below for the exact rules.
         */
        if (projectPerm != null)
          perm = publicProjectPermissions;
        else if (eventId != null || contactId != null)
          perm = publicNoProjectPermissions;
        else
          perm = publicUnattachedPermissions;
      }
    }
    
    /* The delete permissions are attached to the parent folder. To be able to
     * delete the document, you must have the 'd' permission on its folder.
     * (a 'd' in the ACL is also OK, but documents really only have 'r' and 'w'
     *  in the interface, like in Unix).
     */
    if (parentPerms != null && parentPerms.indexOf('d') >= 0)
      perm = UString.unionCharacterSets(perm, "d");
    
    /* Restrict permission to what is defined in the project. You never get more
     * permissions than your project ACL entry.
     * 
     * Example:<pre>
     *   Project: r
     *   File:    rw
     *   Result:  r</pre>
     * 
     * Aka: a file can't have more permissions than a project.
     */
    // TBD: do we really need to have project 'd' permissions to delete files?
    //      sounds more like a 'project can be deleted' setting? Probably we
    //      rather attach the folder/file delete to the project 'w' permission?
    if (projectPerm != null)
      perm = UString.intersectCharacterSets(perm, projectPerm);
    
    _ac.recordPermissionsForGlobalID(perm, _gid);
    return true;
  }
  
  
  
  /* fetching */

  @SuppressWarnings("rawtypes")
  private static final Map emptyMap = Collections.emptyMap();

  public Map<EOKeyGlobalID, Object> fetchInfosForGlobalIDs
    (OGoAuthzFetchContext _ac, OGoDatabase _db, Collection<EOKeyGlobalID> _gids)
  {
    if (_gids == null || _gids.size() == 0) return null;
    boolean debugOn = log.isDebugEnabled();
    
    /* find entities and fetch-specs */
    
    // TBD: notes as containers
    final EOEntity docEntity  = _db.entityNamed("Documents");
    final EOEntity noteEntity = _db.entityNamed("Notes");
    
    EOFetchSpecification docFS =
      docEntity.fetchSpecificationNamed("authzFetch");
    EOFetchSpecification noteFS = 
      noteEntity.fetchSpecificationNamed("authzFetch");
    
    /* prepare result set */
    
    final Map<EOKeyGlobalID, Object> infos =
      new HashMap<EOKeyGlobalID, Object>(_gids.size());
    
    
    /* Extract primary key values from global-ids.
     * Also: we set the minimum access-level for each gid in the map, so that
     * we can be sure that entries w/o ACEs do get the no-access permission. 
     */
    final Map<Number, EOKeyGlobalID> docPkeyToGlobalID =
      new HashMap<Number, EOKeyGlobalID>(_gids.size());
    final Map<Number, EOKeyGlobalID> notePkeyToGlobalID =
      new HashMap<Number, EOKeyGlobalID>(_gids.size());
    for (final EOKeyGlobalID gid: _gids) {
      /* extract key */
      final String ename = gid.entityName();
      if (ename.equals("Documents"))
        docPkeyToGlobalID.put((Number)gid.keyValues()[0], gid);
      else if (ename.equals("Notes"))
        notePkeyToGlobalID.put((Number)gid.keyValues()[0], gid);
    }
    final Collection<Number> docPkeys  = docPkeyToGlobalID.keySet();
    final Collection<Number> notePkeys = notePkeyToGlobalID.keySet();
    if (debugOn) {
      log.debug("fetch doc-infos:  #" + docPkeys.size());
      log.debug("fetch note-infos: #" + notePkeys.size());
    }

    /* This is a hack to avoid issues when checking Document IDs against
     * Note primary keys.
     */
    for (final EOKeyGlobalID gid: _gids)
      infos.put(gid, emptyMap);
    
    /* qualify fetch-specs */
    
    docFS = docFS.fetchSpecificationWithQualifierBindings
      ("ids", docPkeys, "authIds", _ac.authIds);
    noteFS = noteFS.fetchSpecificationWithQualifierBindings
      ("ids", notePkeys, "authIds", _ac.authIds);
    
    /* prepare channel and fetch */
    
    final EOAdaptorChannel channel = _db.adaptor().openChannelFromPool();
    try {
      /* Note: the query already filters all projects, so we need to check
       *       the requested pkeys and if they are not in the resultset,
       *       we have no access.
       */
      List<Map<String,Object>> docs =
        channel.selectAttributes(null, docFS, false, docEntity);

      for (final Map<String,Object> doc: docs) {
        final Number        pkey = (Number)doc.get("document_id"); /* rawrows */
        final EOKeyGlobalID gid  = docPkeyToGlobalID.get(pkey);
        
        infos.put(gid, doc);
      }
      
      docs = channel.selectAttributes(null, noteFS, false, noteEntity);

      for (final Map<String,Object> doc: docs) {
        final Number        pkey = (Number)doc.get("document_id"); /* rawrows */
        final EOKeyGlobalID gid  = notePkeyToGlobalID.get(pkey);
        
        infos.put(gid, doc);
      }
    }
    finally {
      if (channel != null)
        _db.adaptor().releaseChannel(channel);
    }
    
    return infos;
  }
  
  
  // Some old notes below:
  /* Check whether we have *cached* ACL information.
   * If we don't have it, we will fetch it later. But if we have it, we might
   * already reject access.
   * 
   * TBD: do we need to know whether there is an ACL at all? Eg if an ACL
   *      exists but does not contain the account, the account indeed has NO
   *      permission in any case. But if there is NO ACL, he *does* have
   *      permission.
   *      For contacts this is not relevant, because the ACL is only
   *      considered if the contact is private. In this case you MUST have an
   *      ACL entry to have access.
   *      => we should allow 'r' access to the document even if the user is
   *         not in an existing ACL?
   *         => nah, that s****, we couldn't easily reject access for other
   *            users
   *      Summary: we need to fetch the whole ACL or do a second query.
   *               Not sure which option is better.
   */

}
