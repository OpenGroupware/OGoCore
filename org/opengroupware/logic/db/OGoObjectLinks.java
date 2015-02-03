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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOGlobalID;
import org.getobjects.eocontrol.EOKeyGlobalID;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EOOrQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.opengroupware.logic.core.OGoObjectContext;

public class OGoObjectLinks extends OGoDataSource {

  public OGoObjectLinks(EOEditingContext _ec, String _entityName) {
    super(_ec, _entityName);
  }

  @SuppressWarnings("unchecked")
  public Map<OGoObject, List<OGoObject>> fetchTargetsForSources
    (List<OGoObject> _sources, String _linkType)
  {
    // TBD: optimize
    if (_sources == null)
      return null;
    
    OGoObjectContext oc = this.objectContext();
    
    EOQualifier q =
      qualifierForSourceGlobalIDs(oc.globalIDsForObjects(_sources));
    if (_linkType != null)
      q = new EOAndQualifier(q, new EOKeyValueQualifier("type", _linkType));
    
    EOFetchSpecification fs = new EOFetchSpecification
      (this.entity().name(), q, null);
    fs.setFetchesReadOnly(true);
    this.setFetchSpecification(fs);
    
    List<OGoObjectLink> links = this.fetchObjects();
    System.err.println("TBD: fetched links: " + links);
    
    return null;
  }
  
  public static EOQualifier qualifierForSourceGlobalIDs(EOGlobalID[] _gids) {
    if (_gids == null)
      return null;
    if (_gids.length == 0)
      return null;
    
    List<EOQualifier> qs = new ArrayList<EOQualifier>(_gids.length);
    for (EOGlobalID gid: _gids) {
      if (!(gid instanceof EOKeyGlobalID))
        continue;
      
      if (false) {
        /* Can't do this, because the entityName is *NOT* the external DB name.
         * Would be best to store that info as a user-info field in the
         * EOEntity object?
         */
        EOKeyGlobalID kgid = (EOKeyGlobalID)gid;
        EOQualifier q = new EOAndQualifier(
            new EOKeyValueQualifier("sourceType", kgid.entityName()),
            new EOKeyValueQualifier("sourceId",   kgid.toNumber()));
        qs.add(q);
      }
      else
        qs.add(new EOKeyValueQualifier("sourceId", gid));
    }
    
    if (qs.size() == 0) return null;
    if (qs.size() == 1) return qs.get(0);
    return new EOOrQualifier(qs);
  }
}
