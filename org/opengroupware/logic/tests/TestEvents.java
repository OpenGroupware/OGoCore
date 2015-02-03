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
package org.opengroupware.logic.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.getobjects.foundation.UString;
import org.junit.Test;
import org.opengroupware.logic.core.OGoObjectContext;
import org.opengroupware.logic.db.OGoEvents;

public class TestEvents extends OGoTest {

  @Test
  public void testEventsDSClass() {
    Object ds = this.db.events();
    assertNotNull("got no events datasource!", ds);

    assertTrue("events datasource is not a subclass of OGoEvents!",
               ds instanceof OGoEvents);
    
    assertEquals("events datasource is not OGoEvents!",
                 OGoEvents.class, ds.getClass());
  }
  
  @SuppressWarnings("rawtypes")
  @Test
  public void testUpcomingEvents() {
    OGoEvents ds = this.db.events();
    assertNotNull("got no events datasource!", ds);
    
    OGoObjectContext oc =
      new OGoObjectContext(null /* parent store */, this.lc);
    
    List results =
      ds.fetchObjects("upcomingEvents", 
         "authIdsAsStringList",
         UString.componentsJoinedByString(oc.authenticatedIDs(), ","));
    assertNotNull("got no results from datasource!", results);
    
    // TODO: add some test data and check for results ...
  }
}
