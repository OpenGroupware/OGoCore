/*
  Copyright (C) 2007-2008 Helge Hess

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.getobjects.eoaccess.EOAdaptorChannel;
import org.getobjects.eoaccess.EOAdaptorOperation;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UObject;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoCompany;
import org.opengroupware.logic.db.OGoEmployment;
import org.opengroupware.logic.db.OGoObject;
import org.opengroupware.logic.db.OGoResultSet;

/**
 * OGoPersonEmploymentsChange
 * <p>
 * Change the employment association between a person and a company record.
 * <p>
 * This is somewhat tricky because we allow the company to be specified by
 * name, not only by id. So when we process the target company, we need to
 * fetch the company associated with the given id and compare the name of it.
 * If it doesn't match, the companyId is invalid.
 * 
 * <p>
 * To change a permission you need to have 'w' access to the person and to
 * the company?
 * 
 * <p>
 * Example:<pre>
 *   OGoPersonEmploymentsChange employmentsUpdate = 
 *     new OGoPersonEmploymentsChange(oc);
 *   
 *   employmentsUpdate.addChangeSet(10000,
 *     null, // updates
 *     null, // deletes
 *     null) // creates
 *   Exception error = oc.performOperations(employmentsUpdate);</pre>
 * 
 * @author helge
 */
public class OGoPersonEmploymentsChange extends OGoOperation {
  
  protected EOEntity baseEntity;
  
  protected Map<Number, Map<String, Object>> updates;
  protected List<Map<String, Object>>        inserts;
  protected List<Number>                     deletedIds;
  protected Set<String>                      companyNames;
  protected Set<Number>                      companyIds;
  
  protected List<OGoEmployment> objectsToProcess;
  protected Map<String, List<OGoEmployment>> companyNameToObjects;
  
  public OGoPersonEmploymentsChange(OGoObjectContext _oc) {
    super(_oc);
    this.baseEntity   = _oc.oDatabase().entityNamed("Employments");
    
    this.updates      = new HashMap<Number, Map<String,Object>>(16);
    this.deletedIds   = new ArrayList<Number>(16);
    this.inserts      = new ArrayList<Map<String,Object>>(16);
    this.companyNames = new HashSet<String>(4);
    this.companyIds   = new HashSet<Number>(4);
    
    this.objectsToProcess     = new ArrayList<OGoEmployment>(16);
    this.companyNameToObjects = new HashMap<String, List<OGoEmployment>>(4);
  }
  
  
  /* accessors */
  
  protected boolean recordCompanyName(Map<String, Object> row) {
    String compName = (String)row.remove("company");
    Number compId   = (Number)row.get("companyId");
    
    if (compName != null) {
      compName = compName.trim();
      if (compName.length() == 0)
        compName = null;
      else
        row.put("company", compName); // push clean name
    }
    
    if (compName == null) {
      if (compId != null) {
        /* we have just the companyId, no trouble comin' up */
        return true;
      }
      
      /* we got neither name nor companyId */
      log.warn("got no company id in employment record: " + row);
      return false;
    }
    
    this.companyNames.add(compName);
    
    if (row.containsKey("companyId")) {
      /* we have the name and the id, we will need to compare */
      this.companyIds.add(compId);
    }
    return true;
  }
  
  public boolean addChangeSet(final Number _contactId,
      final Map<Number, Map<String, Object>> _update,
      final List<Number>                     _delete,
      final List<Map<String, Object>>        _insert)
  {
    if (_contactId == null)
      return false;
    
    if (_update != null) {
      for (final Map<String, Object> row: _update.values()) {
        final Object v = row.get("companyId");
        if (v != null && !(v instanceof Number))
          row.put("companyId", UObject.intValue(v));

        row.put("personId", _contactId);
        if (!this.recordCompanyName(row))
          return false;
      }
      this.updates.putAll(_update);
    }
    if (_insert != null) {
      //System.err.println("ADD: " + _insert);
      for (final Map<String, Object> row: _insert) {
        if (this.isEmptyNewRow(row))
          continue; /* this is just an empty row which we ignore ... */
        
        final Object v = row.get("companyId");
        if (v != null && !(v instanceof Number))
          row.put("companyId", UObject.intValue(v));
        
        row.put("personId", _contactId);
        if (!this.recordCompanyName(row))
          return false;
        
        this.inserts.add(row);
      }
      //System.err.println("ADD: " + this.inserts);
    }
    
    if (_delete != null)
      this.deletedIds.addAll(_delete);
    
    return true;
  }
  
  public boolean isEmptyNewRow(final Map<String, Object> _row) {
    if (_row == null)
      return true;

    if (_row.get("function")  == null &&
        _row.get("companyId") == null &&
        _row.get("company")   == null)
      return true;
    
    return false;
  }

  private boolean requestNewCompanyForChild
    (final String _companyName, final OGoEmployment _child)
  {
    if (_companyName == null || _child == null)
      return false;
    
    log.error("TBD: autocreate company!");
    return false;
  }


  /* prepare */

  @SuppressWarnings("unchecked")
  @Override
  public Exception prepareForTransactionInContext
    (final OGoOperationTransaction _tx, final OGoObjectContext _ctx)
  {
    if (this.updates.size() == 0 && this.inserts.size() == 0 &&
        this.deletedIds.size() == 0)
      return null; /* nothing to be done */
    
    Exception error;
    
    /* fetch child objects for all contacts */
    
    final List<Number> fetchIds = new ArrayList<Number>(16);
    if (this.updates   != null) fetchIds.addAll(this.updates.keySet());
    
    /* Note: we fetch the deletes to perform the permission check */
    if (this.deletedIds != null) fetchIds.addAll(this.deletedIds);
    
    List<OGoEmployment> children = null;
    if (fetchIds.size() > 0) {
      EOFetchSpecification fs = new EOFetchSpecification(
          this.baseEntity.name(),
          EOQualifier.parse("id IN %@", fetchIds),
          null);

      children =
        _tx.objectContext().objectsWithFetchSpecification(fs);
      if (children == null) {
        error = _tx.objectContext().consumeLastException();
        return error != null ? error : new NSException("could not fetch objs");
      }

      /* Request stored company names of stored children. Only necessary if
       * we need to compare them */
      if (this.companyNames.size() > 0) {
        for (final OGoEmployment child: children) {
          final Map<String, Object> row = this.updates.get(child.id());
          if (row == null) continue;
          
          if (row.containsKey("company")) /* has a need to compare the name */
            this.companyIds.add(child.companyId());
        }
      }
    }
    
    
    /* fetch requested companies */
    
    final Map<String, Number> companyNameToId = new HashMap<String, Number>(4);
    final Map<Number, String> companyIdToName = new HashMap<Number, String>(4);
    if (this.companyNames.size() > 0 || this.companyIds.size() > 0) {
      EOQualifier q = null;
      if (this.companyNames.size() > 0)
        q = EOQualifier.parse("name IN %@", this.companyNames);
      if (this.companyIds.size() > 0) {
        final EOQualifier q1 = EOQualifier.parse("id IN %@", this.companyIds);
        q = q != null ? new EOAndQualifier(q, q1) : q1;
      }
      
      // TBD: do a raw fetch w/o permissions?
      final OGoResultSet comps = this.oc.doFetch("Companies::default", 
          "qualifier", q, "limit", 10000, "distinct", true,
          "attributes",
            "id,name,ownerId,isPrivate,isReadOnlyFlag,contactId,isCompany");
      if (comps == null || comps.hasError()) {
        log.error("could not fetch companies ..");
        return comps != null ? comps.error() : new NSException("FetchError");
      }
      
      for (final OGoCompany o: (List<OGoCompany>)comps.objects()) {
        final Number cid  = o.id();
        String name = (String)o.valueForKey("name");
        if (name != null) name = name.trim();
        
        if (cid  != null) companyIdToName.put(cid, name);
        if (name != null) companyNameToId.put(name, cid);
      }
    }
    
    /* request permissions for update/delete objects */
    
    if (children != null) {
      for (final OGoEmployment child: children) {
        if (this.deletedIds.contains(child.id())) {
          _tx.requestPermissionOnId("d", this.baseEntity.name(), child.id());
          continue;
        }
        
        final Map<String, Object> row = this.updates.remove(child.id());
        if (row == null) {
          log.warn("fetched unrequested child: " + child);
          continue; // TBD: did not find child?
        }
        
        
        /* check for company target */
        
        // TBD: move to own method
        /* Note: this is non-obvious, company overrides companyId! */
        final String companyName = (String)row.get("company");
        if (companyName != null && companyName.length() > 0) {
          /* ok, has a company-name */
          Number companyId     = (Number)row.get("companyId");
          String companyIdName = companyIdToName.get(companyId /*canbe-null*/);
          Number companyNameId = companyNameToId.get(companyName);
          Number childId       = child.id();
          String childName     = companyIdToName.get(childId /*cantbe-null*/);
          
          if (childId == null) {
            log.error("DB inconsistency, got a child w/o an id: " + child);
            continue;
          }
          
          /* changeset had no companyId, but our object of course has one!
           * OR
           * the companyId in the changeset is different to the stored one
           * But it could be that the name of the *stored* id still matches
           * the changeset-name!
           */

          if (companyIdName != null && companyIdName.equals(companyName)) {
            /* ok the name of the given companyId matches, just use it */
            row.remove("company"); /* the id properly maps */
          }
          else if (childName != null && childName.equals(companyName)) {
            /* ok, the new name matches the stored name. keep it */
            row.remove("companyId"); // the new id does not match the name
            row.remove("company"); // we have a proper stored id
          }
          else {
            /* neither given companyId nor stored id names match our name */
            if (companyNameId != null) {
              /* The new companyName differs to the stored one, but a company
               * exists for that name. Add it to the changeset.
               */
              row.put("companyId", companyNameId);
              row.remove("company"); // we mapped the companyName to the id
            }
            else {
              /* companyName differs, but is not yet stored => new one */
              if (!this.requestNewCompanyForChild(companyName, child))
                return new NSException("autocreate not implemented");
            }
          }

          // TBD: interesting situations arise when the login user does not
          // have name access on the company record!
        }
        // else: row has a 'companyId' or a no id. Both are fine for updates!
        
        
        /* apply changes */
        
        child.takeValuesFromDictionary(row);
        
        if (child.hasChanges()) {
          this.objectsToProcess.add(child);
          
          // Interesting. technically we modify *two* companies if we change
          // the company in the employment (removed from the old, added to the
          // new)
          _tx.requestPermissionOnId("w", this.baseEntity.name(), child.id());
        }
      }
    }
    
    /* request insert permissions */
    
    if (this.inserts != null) {
      for (Map<String, Object> row: this.inserts) {
        /* Note: ID is generated when the request gets run */
        OGoEmployment child = this.createObject();
        
        System.err.println("NEW CHILD: " + child);

        /* process companyName */
        
        String companyName = (String)row.get("company");
        if (companyName != null && companyName.length() > 0) {
          /* ok, has a company-name */
          Number companyId     = (Number)row.get("companyId");
          String companyIdName = companyIdToName.get(companyId /*canbe-null*/);
          Number companyNameId = companyNameToId.get(companyName);

          if (companyIdName != null && companyIdName.equals(companyName)) {
            /* ok the name of the given companyId matches, just use it */
            row.remove("company"); /* the id properly maps */
          }
          else {
            /* the name of the given companyId does NOT match, remove it */
            row.remove("companyId");

            if (companyNameId != null) {
              /* a company exists for that name. Add it to the changeset. */
              row.put("companyId", companyNameId);
              row.remove("company"); // we mapped the companyName to the id
            }
            else {
              /* companyName differs, but is not yet stored => new one */
              if (!this.requestNewCompanyForChild(companyName, child))
                return new NSException("autocreate not implemented");
            }
          }
        }
        else if (!row.containsKey("companyId"))
          return new NSException("new row misses companyId!"); // TBD
        
        /* apply changes */

        child.takeValuesFromDictionary(row);
        
        _tx.requestPermissionOnId("w", "Persons", child.personId());
        
        this.objectsToProcess.add(child);
      }
    }
    
    return null;
  }

  
  /* do it */
  
  @Override
  public Exception runInContext
    (OGoOperationTransaction _tx, EOAdaptorChannel _ch, OGoObjectContext _ctx)
  {
    final boolean isDebugOn = log.isDebugEnabled();
    
    if (this.objectsToProcess.size() == 0 && this.deletedIds.size() == 0)
      return null; /* nothing to be done */
    
    List<EOAdaptorOperation> ops = new ArrayList<EOAdaptorOperation>(16);

    /* first: autocreate company records */

    if (this.companyNameToObjects!=null && this.companyNameToObjects.size()>0) {
      // TBD: autocreate company names
      return new NSException("company autocreate not yet implemented");
      // TBD: hook up results
    }
    
    /* process employment records */
    
    ops.clear(); /* reuse */

    List<EOAdaptorOperation> delOps =
      this.deleteOperations(this.baseEntity, this.deletedIds);
    if (delOps != null) ops.addAll(delOps); delOps = null;
    
    List<EOAdaptorOperation> addOps = new ArrayList<EOAdaptorOperation>(16);
    for (OGoObject child: this.objectsToProcess) {
      if (isDebugOn) log.debug("DO: " + child);
      
      if (child.isNew())
        addOps.add(this.insertOperation(child, this.baseEntity));
      else
        ops.add(this.updateOperation(child, this.baseEntity, null /* rev */));
    }
    if (addOps != null) ops.addAll(addOps);
    
    Exception error = _ch.performAdaptorOperations(ops);
    if (error != null) return error;

    return null /* everything is fine */;
  }


  /* support */
  
  public OGoEmployment createObject() {
    return new OGoEmployment(this.baseEntity);
  }
}
