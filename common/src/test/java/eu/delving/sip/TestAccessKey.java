/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.1 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.junit.Test;

/**
 * Make sure the services key is working as planned
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestAccessKey {
    private Logger log = Logger.getLogger(getClass());

    @Test
    public void check() {
        String keyString = AccessKey.createKey("uzer", "delving-pass");
        log.info("Created "+keyString);
        AccessKey accessKey = new AccessKey();
        accessKey.setServicesPassword("delving-pass");
        Assert.assertTrue("Should have matched!", accessKey.checkKey(keyString));
        Assert.assertFalse("Should not have matched", accessKey.checkKey("gumby"));
    }
}
