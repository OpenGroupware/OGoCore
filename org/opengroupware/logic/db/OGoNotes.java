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
import java.util.HashSet;
import java.util.List;

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOSortOrdering;
import org.getobjects.foundation.UList;
import org.opengroupware.logic.core.OGoObjectContext;

public class OGoNotes extends OGoDataSource {

  public OGoNotes(EOEditingContext _ec, String _entityName) {
    super(_ec, _entityName);
  }

  
  /* fetching */
  
  public OGoResultSet fetchResultSet
    (String _fsname, int _limit, String[] _prefetches,
     EOQualifier _auxQualifier, EOSortOrdering[] _sorts,
     int _retryCount)
  {
    if (_fsname == null) _fsname = "default";

    EOFetchSpecification fs = 
      this.entity().fetchSpecificationNamed(_fsname).copy();
    fs.setFetchLimit(_limit);
    
    if (_prefetches != null) fs.setPrefetchingRelationshipKeyPaths(_prefetches);
    if (_sorts      != null) fs.setSortOrderings(_sorts);
    
    OGoObjectContext oc = this.objectContext();
    Number[] authIds = oc.authenticatedIDs();
    this.setQualifierBindings("authIds", authIds);
    
    if (_auxQualifier != null) this.setAuxiliaryQualifier(_auxQualifier);
    
    return this.fetchResultSet(fs, _auxQualifier, _limit, _retryCount);
  }

  
  @SuppressWarnings({"unchecked", "rawtypes"})
  public OGoResultSet fetchResultSet
    (EOFetchSpecification fs, EOQualifier _auxQualifier,
     int _limit, int _retryCount)
  {
    this.setFetchSpecification(fs);

    Collection<Number> forbiddenIds = new HashSet<Number>(_limit);
    List<EOEnterpriseObject> forbidden =
      new ArrayList<EOEnterpriseObject>(_limit);
    
    Exception error = null;
    List    results  = null;
    boolean hitLimit = false;
    
    OGoObjectContext oc = this.objectContext();
    int fetchCount = 0;
    do {
      /* exclude forbidden objects in next fetch iteration */
      
      if (forbidden.size() > 0) {
        log.info("retrying limited-fetch w/o forbidden objects ...");
        forbiddenIds.addAll(UList.valuesForKey(forbidden, "id"));
        forbidden.clear();
        
        EOQualifier q = new EOKeyValueQualifier
            ("id", EOQualifier.ComparisonOperation.CONTAINS, forbiddenIds);
        if (_auxQualifier != null) q = new EOAndQualifier(q, _auxQualifier);
        this.setAuxiliaryQualifier(q);
      }
      
      /* perform fetch */
      
      if ((results = this.fetchObjects()) == null) {
        error = this.consumeLastException();
        break;
      }
      /* detect whether we hit a limit */
      hitLimit = results != null && (_limit > 0) && results.size() == _limit;
      
      
      /* enforce check of permissions to check whether we should retry */
      
      if (!oc.autoFetchPermissions())
        oc.processPermissionsAfterFetch(fs, results);
      
      for (Object note: results) {
        if (((OGoObject)note).isForbidden())
          forbidden.add((OGoObject) note);
      }
      
      results.removeAll(forbidden);
      
      fetchCount++;
    }
    while (hitLimit && fetchCount <= _retryCount && forbidden.size() > 0);
    
    return (error != null)
      ? new OGoResultSet(error)
      : new OGoResultSet(results, _limit, hitLimit, true /* did check */);
  }
}
