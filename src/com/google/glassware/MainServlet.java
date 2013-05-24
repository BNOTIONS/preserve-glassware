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
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.mirror.model.*;
import com.google.common.collect.Lists;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles POST requests from app.jsp
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class MainServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());
    public static final String CONTACT_NAME = "Preserve";

    /**
     * Do stuff when buttons on app.jsp are clicked
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {

        String userId = AuthUtil.getUserId(req);
        Credential credential = AuthUtil.newAuthorizationCodeFlow().loadCredential(userId);
        String message = "";

        if (req.getParameter("operation").equals("insertSubscription")) {

            // subscribe (only works deployed to production)
            try {
                MirrorClient.insertSubscription(credential, WebUtil.buildUrl(req, "/app/notify"), userId,
                        req.getParameter("collection"));
                message = "Application is now subscribed to updates.";
            } catch (GoogleJsonResponseException e) {
                LOG.warning("Could not subscribe " + WebUtil.buildUrl(req, "/app/notify") + " because "
                        + e.getDetails().toPrettyString());
                message = "Failed to subscribe. Check your log for details";
            }

        } else if (req.getParameter("operation").equals("deleteSubscription")) {

            // subscribe (only works deployed to production)
            MirrorClient.deleteSubscription(credential, req.getParameter("subscriptionId"));

            message = "Application has been unsubscribed.";

        } else if (req.getParameter("operation").equals("insertItem")) {
            LOG.fine("Inserting Timeline Item");
            TimelineItem timelineItem = new TimelineItem();

            if (req.getParameter("message") != null) {
                timelineItem.setText(req.getParameter("message"));
            }

            // Triggers an audible tone when the timeline item is received
            timelineItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));

            if (req.getParameter("imageUrl") != null) {
                // Attach an image, if we have one
                URL url = new URL(req.getParameter("imageUrl"));
                String contentType = req.getParameter("contentType");
                MirrorClient.insertTimelineItem(credential, timelineItem, contentType, url.openStream());
            } else {
                MirrorClient.insertTimelineItem(credential, timelineItem);
            }

            message = "A timeline item has been inserted.";

        } else if (req.getParameter("operation").equals("insertPinCard")) {
            LOG.fine("Inserting Timeline Item");
            insertPinCard(credential);

            message = "A timeline item with actions has been inserted.";

        } else if (req.getParameter("operation").equals("insertItemAllUsers")) {
            if (req.getServerName().contains("glass-java-starter-demo.appspot.com")) {
                message = "This function is disabled on the demo instance.";
            }

            List<String> users = AuthUtil.getAllUserIds();
            LOG.info("found " + users.size() + " users");
            if (users.size() > 10) {
                // We wouldn't want you to run out of quota on your first day!
                message =
                        "Total user count is " + users.size() + ". Aborting broadcast " + "to save your quota.";
            } else {
                TimelineItem allUsersItem = new TimelineItem();
                allUsersItem.setText("Hello Everyone!");

                // TODO: add a picture of a cat
                for (String user : users) {
                    Credential userCredential = AuthUtil.getCredential(user);
                    MirrorClient.insertTimelineItem(userCredential, allUsersItem);
                }
                message = "Sent cards to " + users.size() + " users.";
            }

        } else if (req.getParameter("operation").equals("insertContact")) {
            // Insert a contact
            LOG.fine("Inserting contact Item");
            insertGlass2DriveContact(credential);

            message = "Inserted contact: " + req.getParameter("name");

        } else {
            String operation = req.getParameter("operation");
            LOG.warning("Unknown operation specified " + operation);
            message = "I don't know how to do that";
        }
        WebUtil.setFlash(req, message);
        res.sendRedirect(WebUtil.buildUrl(req, "/"));
    }

    /*
    {
  "text": "\n\nHello Explorers,\n\nWelcome to Preserve!\n\n\n",
  "creator": {
  "displayName": "Project Glass",
  "imageUrls": [
  "http://www.skewable.com/picture_library/card_custom.png?sz=360"
  ]
  },
  "notification": {
  "level": "DEFAULT"
  }
}
     */

    public static void insertPinCard(Credential credential) throws IOException {

        TimelineItem timelineItem = new TimelineItem();
        timelineItem.setHtml("<article>\n  " +
                "<figure>\n       " +
                "<img src=\"http://www.skewable.com/picture_library/card.png?sz=360\" >\n  " +
                "</figure>\n  " +
                "<section>\n    " +
                "<h1 class=\"text-large\">Preserve</h1>\n    " +
                "<p class=\"text-x-small\">\n      " +
                "Welcome, Explorers!\n    " +
                "</p>\n    " +
                "<hr>\n    " +
                "<p class=\"text-x-small\">\n " +
                "</section>\n" +
                "</article>\n");

        List<MenuItem> menuItemList = new ArrayList<MenuItem>();
        // Built in actions
        List<MenuValue> textNoteValues = new ArrayList<MenuValue>();
        textNoteValues.add(new MenuValue().setState("DEFAULT").setDisplayName("Voice Note"));
        menuItemList.add(new MenuItem().setAction("REPLY").setValues(textNoteValues));
        menuItemList.add(new MenuItem().setAction("TOGGLE_PINNED"));
        menuItemList.add(new MenuItem().setAction("DELETE"));


        timelineItem.setMenuItems(menuItemList);
        timelineItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));

        MirrorClient.insertTimelineItem(credential, timelineItem);

    }

    public static void insertGlass2DriveContact(Credential credential) throws IOException {

        Contact contact = new Contact();
        contact.setId(CONTACT_NAME);
        contact.setDisplayName(CONTACT_NAME);
        contact.setImageUrls(Lists.newArrayList("http://www.skewable.com/picture_library/contact_card.png"));
        MirrorClient.insertContact(credential, contact);

    }

}
