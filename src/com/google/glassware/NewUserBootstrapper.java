/*
 * Copyright (C) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.MenuValue;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utility functions used when users first authenticate with this service
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NewUserBootstrapper {

    private static final Logger LOG = Logger.getLogger(NewUserBootstrapper.class.getSimpleName());

    public static void bootstrapNewUser(HttpServletRequest req, String userId) throws IOException {

        Credential credential = AuthUtil.newAuthorizationCodeFlow().loadCredential(userId);

        // Send welcome timeline item
        TimelineItem timelineItem = new TimelineItem();
        timelineItem.setText("Thank you for signing up got Glass2Drive. Pin the next message you receive from us.");
        timelineItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
        List<MenuItem> menuItemList = new ArrayList<MenuItem>();
        menuItemList.add(new MenuItem().setAction("DELETE"));
        timelineItem.setMenuItems(menuItemList);
        TimelineItem insertedItem = MirrorClient.insertTimelineItem(credential, timelineItem);
        LOG.info("Bootstrapper inserted Welcome Message " + insertedItem.getId() + " for user "
                + userId);

        MainServlet.insertPinCard(credential);
        LOG.info("Bootstrapper inserted Pin Card");

    }
}
