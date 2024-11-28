/*
  Copyright (C) 2008-2024 Helge Hess

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

import org.getobjects.foundation.NSTimeRange;

/**
 * Implemented by objects which have a timerange attached. For example
 * company attributes or employment records.
 * 
 * Implementers:
 * - OGoCompanyRelationship
 * - OGoContactExtValue
 * - OGoEmployment
 * - OGoEvent
 * - OGoProject
 * 
 * @author helge
 */
public interface IOGoTimeRangeObject {

  /**
   * The timerange in which the value of the object is active.
   * 
   * @return an NSTimeRange
   */
  public NSTimeRange timeRange();
  
}
