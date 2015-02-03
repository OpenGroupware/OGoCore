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

import java.util.Map;

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.opengroupware.logic.core.IOGoOperation;

/**
 * IOGoInsertOperation
 * <p>
 * Standard interface for operations which create a bulk set of objects.
 * 
 * <p>
 * @author helge
 */
public interface IOGoInsertOperation extends IOGoOperation {
  // TBD: this probably needs some refactoring

  public int add(final EOEnterpriseObject _eo);
  public int add(final Map<String, Object> _values);
  
  public EOEnterpriseObject get(final int _idx);
  
  
  /**
   * Interface implemented by 'patches'. Patches are used to push primary keys
   * of inserted parent objects into their children.
   * Example: after a 'person' object got added, the primary key of the record
   * is pushed into associated addresses.
   * 
   * <p>
   * @author helge
   */
  public interface IOGoInsertPatch {
    public Exception apply(OGoOperationTransaction _tx, EOEnterpriseObject _eo);
  }

  
  /**
   * Callback interface for objects which want to modify the way the insert
   * operation works. Eg patch in standard relationships or values.
   */
  public interface IOGoInsertOperationDelegate {
    
    /**
     * This is called just before an EO to be added will be scanned for its
     * contained relationships.
     * 
     * @param _op  - the operation in charge
     * @param _eo  - the EO to be scanned
     * @param _idx - the index of the EO in the insert operation
     * @return true if the process should continue, false to abort the Add
     */
    public boolean operationWillAddRelationshipsOfEO
      (IOGoInsertOperation _op, final EOEnterpriseObject _eo, final int _idx);

    /**
     * This is called after an EO got scanned for its relationships. The
     * operation did add child-ops and patches for the contained relationships.
     * 
     * @param _op  - the operation in charge
     * @param _eo  - the EO which got scanned
     * @param _idx - the index of the EO in the insert operation
     */
    public void operationDidAddRelationshipsOfEO
      (IOGoInsertOperation _op, final EOEnterpriseObject _eo, final int _idx);
    
    /**
     * Gives the delegate the change to patch the record before its processed
     * by the operation.
     * It can return the same record, or a replacement record.
     * 
     * @param _op     - the insert operation
     * @param _record - the main record to be added
     * @return null if the add should be stopped, the record otherwise
     */
    public Map<String, Object> operationWillProcessMap
      (final IOGoInsertOperation _op, final Map<String, Object> _record);
    
  }
}
