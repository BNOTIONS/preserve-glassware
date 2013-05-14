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
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Handles the notifications sent back from subscriptions
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NotifyServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

    static Drive buildDriveService(Credential credential) throws IOException {

        FileInputStream authPropertiesStream = new FileInputStream("oauth.properties");
        Properties authProperties = new Properties();
        authProperties.load(authPropertiesStream);
        String clientId = authProperties.getProperty("client_id");
        String clientSecret = authProperties.getProperty("client_secret");

        GoogleCredential credentials = new GoogleCredential.Builder().setTransport(credential.getTransport())
                .setJsonFactory(credential.getJsonFactory()).setClientSecrets(clientId, clientSecret).build();
        credentials.setAccessToken(credential.getAccessToken());
        credentials.setRefreshToken(credential.getRefreshToken());

        return new Drive.Builder(credential.getTransport(), credential.getJsonFactory(), credentials).setApplicationName("checkin2glass").build();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Respond with OK and status 200 in a timely fashion to prevent redelivery
        response.setContentType("text/html");
        Writer writer = response.getWriter();
        writer.append("OK");
        writer.close();

        BufferedReader notificationReader = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String notificationString = "";

        int lines = 0;
        while (notificationReader.ready()) {
            notificationString += notificationReader.readLine();
            lines++;
            if(lines > 1000) {
                throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
            }
        }

        LOG.info("got raw notification " + notificationString);

        JsonFactory jsonFactory = new JacksonFactory();

        Notification notification = jsonFactory.fromString(notificationString, Notification.class);

        LOG.info("Got a notification with ID: " + notification.getItemId());

        String userId = notification.getUserToken();
        Credential credential = AuthUtil.getCredential(userId);
        Mirror mirrorClient = MirrorClient.getMirror(credential);


        if (notification.getCollection().equals("timeline")) {

            TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
            LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

            if (notification.getUserActions().contains(new UserAction().setType("SHARE"))
                    && timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
                LOG.info("It was a share of a photo.");

                String attachmentId = timelineItem.getAttachments().get(0).getId();
                LOG.info("Found attachment with ID " + attachmentId);

                InputStream driveStream = MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);
                String fileName = "g2d-image-" + System.currentTimeMillis();
                String fileId = sendImageToDrive(credential, driveStream, fileName);

                InputStream timelineStream = MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);
                TimelineItem echoPhotoItem = new TimelineItem();
                echoPhotoItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
                echoPhotoItem.setText("Image Saved: " + fileName);
                echoPhotoItem.setSourceItemId(fileId);
                List<MenuItem> menuItemList = new ArrayList<MenuItem>();
                menuItemList.add(new MenuItem().setAction("SHARE"));
                menuItemList.add(new MenuItem().setAction("DELETE"));
                echoPhotoItem.setMenuItems(menuItemList);
                MirrorClient.insertTimelineItem(credential, echoPhotoItem, "image/jpeg", timelineStream);

            } else if (notification.getUserActions().contains(new UserAction().setType("REPLY"))) {

                LOG.info("Received a new note request");
                LOG.info("Removing standard speech to text card");
                deleteMostRecentTimelineItem(credential);

                TimelineItem replyItem = mirrorClient.timeline().get(notification.getItemId()).execute();
                String fileName = "g2d-note-" + System.currentTimeMillis();
                String fileId = sendTextNoteToDrive(credential, replyItem.getText(), fileName);

                TimelineItem echoNoteItem = new TimelineItem();
                echoNoteItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
                echoNoteItem.setText("Note Saved: " + fileName);
                List<MenuItem> menuItemList = new ArrayList<MenuItem>();
                menuItemList.add(new MenuItem().setAction("SHARE"));
                menuItemList.add(new MenuItem().setAction("READ_ALOUD"));
                menuItemList.add(new MenuItem().setAction("DELETE"));
                echoNoteItem.setMenuItems(menuItemList);
                echoNoteItem.setSpeakableText(replyItem.getText());
                echoNoteItem.setSourceItemId(fileId);
                MirrorClient.insertTimelineItem(credential, echoNoteItem);

            } else if (notification.getUserActions().contains(new UserAction().setType("DELETE"))) {

                LOG.info("Received a delete note request");
                TimelineItem deleteItem = mirrorClient.timeline().get(notification.getItemId()).execute();
                Drive drive = buildDriveService(credential);
                drive.files().delete(deleteItem.getSourceItemId());
                LOG.info("Note Deleted: " + deleteItem.getSourceItemId());

            } else {
                LOG.warning("I don't know what to do with this notification, so I'm ignoring it.");
            }
        }
    }

    private String sendImageToDrive(Credential credential, InputStream stream, String fileName) throws IOException {

        Drive drive = buildDriveService(credential);

        File body = new File();
        body.setTitle(fileName);
        body.setMimeType("image/jpeg");

        InputStreamContent content = new InputStreamContent("image/jpeg", stream);
        File file = drive.files().insert(body, content).setConvert(true).execute();
        LOG.info("Drive result: " + file.getTitle());

        return file.getId();
    }

    private String sendTextNoteToDrive(Credential credential, String message, String fileName) throws IOException {

        Drive drive = buildDriveService(credential);

        File body = new File();
        body.setTitle(fileName);
        body.setMimeType("text/plain");

        ByteArrayInputStream is = new ByteArrayInputStream(message.getBytes());
        InputStreamContent content = new InputStreamContent("text/plain", is);
        File file = drive.files().insert(body, content).setConvert(true).execute();
        LOG.info("Drive result: " + file.getTitle());

        return file.getId();
    }

    private void deleteMostRecentTimelineItem(Credential credential) throws IOException {

        Mirror mirror = MirrorClient.getMirror(credential);
        Mirror.Timeline.List request = mirror.timeline().list();
        TimelineListResponse timelineItems = request.execute();
        TimelineItem item = timelineItems.getItems().get(0);
        mirror.timeline().delete(item.getId());

    }

}
