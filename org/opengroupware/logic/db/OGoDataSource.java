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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.getobjects.eoaccess.EODatabaseDataSource;
import org.getobjects.eocontrol.EOEditingContext;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.UMap;
import org.opengroupware.logic.core.OGoObjectContext;

/**
 * OGoDataSource
 * <p>
 * Enhances the {@link EODatabaseDataSource} with the concept of 
 * an {@link OGoResultSet}. The latter remembers whether a fetch-limit was
 * hit and whether permissions got applied on the results.

 * <p>
 * @author helge
 */
public class OGoDataSource extends EODatabaseDataSource {

  public OGoDataSource(EOEditingContext _ec, String _entityName) {
    super(_ec, _entityName);
  }
  
  /* accessors */
  
  public OGoObjectContext objectContext() {
    return (OGoObjectContext)this.ec;
  }

  /* support */

  protected static final Pattern splitPattern = Pattern.compile("[\\s]+");;
  protected static final Pattern emailQPattern =
    Pattern.compile("([^@]+)@(\\w+)?(\\.\\w+)*");
  protected static final Pattern phoneQPattern =
    Pattern.compile("^[\\+]?[\\d\\s\\-\\(\\)]{3,}");
  
  /* fetching */
  
  /**
   * Convenience method to fetch a result using the datasource. This selects
   * a fetch specification, sets prefetches and applies bindings.
   * <p>
   * Example:<pre>
   *   OGoResultSet results = ds.fetchResultSet(
   *     "fetchByName",
   *     null, // no prefetches
   *     "lastname" = "Duck");</pre>
   * The method configures the datasource and then calls
   * fetchResultSet().
   */
  @SuppressWarnings("unchecked")
  public OGoResultSet fetchResultSet
    (String _fsname, String[] _prefetchPathes, Object... _args)
  {
    //System.err.println("FETCH: " + _fsname + " in " + this.entity().name());
    
    Map<String, Object> bindings = UMap.createArgs(_args);
    if (!bindings.containsKey("authIds"))
      bindings.put("authIds", this.objectContext().authenticatedIDs());
    
    if (_fsname != null) {
      EOFetchSpecification fs = this.entity().fetchSpecificationNamed(_fsname);
      if (fs == null) {
        log.error("did not find fetch specification '" + _fsname +
            "' in entity: " + this.entity().name());
        return null;
      }

      if (_prefetchPathes != null) {
        fs = fs.copy();
        fs.setPrefetchingRelationshipKeyPaths(_prefetchPathes);
      }
      
      this.setFetchSpecification(fs);
    }
    else
      log.warn("missing fetch specification name in fetchResultSet()");
    
    if (bindings.size() > 0)
      this.setQualifierBindings(bindings);
    
    return this.fetchResultSet();
  }
  
  /**
   * This method calls fetchObjects() and wraps the results into an
   * {@link OGoResultSet} object.
   * Further it sets the 'authIds' bindings when required.
   * 
   * @return an OGoResultSet representing the fetch results (and errors)
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public OGoResultSet fetchResultSet() {
    EOFetchSpecification fs = this.fetchSpecification();
    
    EOQualifier q = fs != null ? fs.qualifier() : null;
    if (q != null && q.hasUnresolvedBindings()) {
      // TBD: I don't like this crap, this is too hackish
      Object v = NSKeyValueCoding.Utility.valueForKey
        (this.qualifierBindings, "authIds");
      if (v == null) {
        if (this.qualifierBindings == null) {
          this.setQualifierBindings("authIds",
              this.objectContext().authenticatedIDs());
        }
        else if (this.qualifierBindings instanceof Map) {
          Map qb = new HashMap((Map)this.qualifierBindings);
          qb.put("authIds", this.objectContext().authenticatedIDs());
          this.setQualifierBindings(qb);
        }
        else
          log.warn("could not apply 'authIds' binding");
      }
    }
    
    /* perform fetch */
    
    List objects = this.fetchObjects();
    if (newFS != null)
      this.setFetchSpecification(fs); /* restore old FS */
    
    if (objects == null)
      return new OGoResultSet(this.consumeLastException());
    
    /* check whether limit was hit */
    
    int     limit = 0;
    boolean hitLimit = false;
    
    if (fs != null) {
      if ((limit = fs.fetchLimit()) > 0)
        hitLimit = objects.size() == limit;
    }
    
    /* check whether permissions got resolved */
    
    OGoObjectContext oc = this.objectContext();
    boolean didCheckPerms = oc != null ? oc.autoFetchPermissions() : false;
    
    /* return results */
    
    return new OGoResultSet(objects, limit, hitLimit, didCheckPerms);
  }

}
