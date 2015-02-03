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
package org.opengroupware.logic.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengroupware.logic.db.OGoAccount;
import org.opengroupware.logic.db.OGoTeam;

public class TestAccounts extends OGoTest {
  
  protected OGoAccount johnDoe;

  @Before
  public void setUp() {
    super.setUp();
    
    johnDoe = (OGoAccount)
      this.db.accounts().findByMatchingAll
        ("login", testUserLogin, "isAccount", 1);
  }

  @After
  public void tearDown() {
    johnDoe = null;
    super.tearDown();
  }

  @Test
  public void testDirectLoginJohnDoe() {
    OGoAccount account = this.db.accounts().login(testUserLogin, testUserPwd);
    assertNotNull("could not login john.doe", account);
    assertTrue("login does not match", testUserLogin.equals(account.getName()));
  }
  
  @Test
  public void testTeamsOfJohnDoe() {
    Number[] teamIDs = this.johnDoe.teamIDs();
    assertNotNull("got no team ids", teamIDs);
    assertEquals("expected team count did not match", 3, teamIDs.length);

    Number[] authIDs = this.johnDoe.authIDs();
    assertNotNull("got no auth ids", teamIDs);
    assertEquals("expected auth-id count did not match", 4, authIDs.length);
    
    List<OGoTeam> teams = this.johnDoe.teams();
    assertNotNull("got no team objects", teams);
    assertEquals("expected team count did not match", 3, teams.size());
  }
  
  @Test
  public void testTeamIDsAfterTeamsOfJohnDoe() {
    /* this time we should only see a single fetch because john caches */
    List<OGoTeam> teams = this.johnDoe.teams();
    assertNotNull("got no team objects", teams);
    assertEquals("expected team count did not match", 3, teams.size());
    
    Number[] teamIDs = this.johnDoe.teamIDs();
    assertNotNull("got no team ids", teamIDs);
    assertEquals("expected team count did not match", 3, teamIDs.length);
  }
}
