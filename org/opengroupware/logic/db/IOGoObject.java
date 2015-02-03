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
package org.opengroupware.logic.db;

import org.getobjects.eoaccess.EOEnterpriseObject;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.NSKeyValueCodingAdditions;

public interface IOGoObject
  extends NSKeyValueCoding, NSKeyValueCodingAdditions, EOEnterpriseObject
{

  public void setId(Number _id);
  public Number id();
  
  public boolean  hasChanges();
  public EOEntity entity();
  
  public String entityNameInOGo5();
  
}
