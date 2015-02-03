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
package org.opengroupware.logic.tests;

import javax.security.auth.login.LoginContext;

import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.foundation.NSException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.opengroupware.logic.auth.OGoLoginModule;
import org.opengroupware.logic.db.OGoDatabase;

/**
 * Required environment:<pre>
 *   Account 'john.doe' with those teams:
 *     Magdeburg
 *     all intranet
 *     news editors</pre>
 */
public class OGoTest extends Object {
  
  protected static final String dbURL =
    "jdbc:postgresql://move.in.skyrix.com/OGo?user=OGo&password=OGo";
  
  protected static final String testUserLogin = "john.doe";
  protected static final String testUserPwd   = "abc123";
  protected static final String rootUserLogin = "root";
  
  protected static EOAdaptor   adaptor;
  protected OGoDatabase  db;
  protected LoginContext lc;

  @Before
  public void setUp() {
    this.db = new OGoDatabase(adaptor, null /* LSAttachmentPath */); 
    this.lc = OGoLoginModule.jaasLogin(this.db, testUserLogin, testUserPwd);
    if (this.lc == null)
      throw new NSException("could not login");
  }

  @After
  public void tearDown() {
    this.db = null;
  }

  @BeforeClass
  public static void setUpClass() {
    adaptor = OGoDatabase.dbAdaptorForURL(OGoDatabase.class, dbURL);
  }

  @AfterClass
  public static void tearDownClass() {
    adaptor.dispose();
    adaptor = null;
  }
}
