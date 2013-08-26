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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles the notifications sent back from subscriptions
 *
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NotifyServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

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
            LOG.info("Impacted Item RAW: " + timelineItem.toPrettyString());

            if (notification.getUserActions().contains(new UserAction().setType("SHARE"))
                    && timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
                LOG.info("Received a share request.");

                String attachmentId = timelineItem.getAttachments().get(0).getId();
                LOG.info("Found attachment with ID " + attachmentId);

                InputStream driveStream = MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);

                String contentType = timelineItem.getAttachments().get(0).getContentType();
                String fileId;
                String fileName;
                if (contentType.equalsIgnoreCase("video/mp4")) {
                    fileName = "g2d-video-" + System.currentTimeMillis() + ".mp4";
                    fileId = sendVideoToDrive(credential, driveStream, fileName);
                } else {
                    fileName = "g2d-image-" + System.currentTimeMillis() + ".jpg";
                    fileId = sendImageToDrive(credential, driveStream, fileName);
                }

                InputStream timelineStream = MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);
                TimelineItem echoPhotoItem = new TimelineItem();
                echoPhotoItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
                echoPhotoItem.setHtml("<article class=\"photo\">\n  " +
                        "<img src=\"http://www.skewable.com/picture_library/note_saved_bg.png\" width=\"100%\" height=\"100%\">\n  " +
                        "<div class=\"photo-overlay\"/>\n  " +
                        "<section>\n    " +
                        "<p class=\"text-auto-size\">" + fileName + "</p>\n  " +
                        "</section>\n  " +
                        "<footer>\n    " +
                        "<img src=\"http://skewable.com/preserve/app_icon.png\" class=\"left\">\n    " +
                        "<p>Preserve</p>\n  " +
                        "</footer>\n" +
                        "</article>\n");
                echoPhotoItem.setSourceItemId(fileId);
                List<MenuItem> menuItemList = new ArrayList<MenuItem>();
                menuItemList.add(new MenuItem().setAction("SHARE"));
                menuItemList.add(new MenuItem().setAction("DELETE"));
                echoPhotoItem.setMenuItems(menuItemList);
                MirrorClient.insertTimelineItem(credential, echoPhotoItem, "image/jpeg", timelineStream);

            } else if (notification.getUserActions().contains(new UserAction().setType("REPLY"))) {

                LOG.info("Received a new note request");

                TimelineItem replyItem = mirrorClient.timeline().get(notification.getItemId()).execute();
                String textFileName = "g2d-text-" + System.currentTimeMillis() + ".txt";
                String fileId = sendTextNoteToDrive(credential, replyItem.getText(), textFileName);



                /*String attachmentUrl;
                LOG.info(replyItem.toPrettyString());
                if (replyItem.getAttachments().get(0).getContentType().equals("text/vnd.google.audio-download-url")) {
                    attachmentUrl = replyItem.getAttachments().get(0).getContentUrl();
                } else {
                    attachmentUrl = replyItem.getAttachments().get(1).getContentUrl();
                }
                InputStream audioStream = getInputStream(attachmentUrl);
                String audioFileName = "g2d-audio-" + System.currentTimeMillis();
                sendAudioNoteToDrive(credential, audioStream, audioFileName);*/

                TimelineItem echoNoteItem = new TimelineItem();
                echoNoteItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
                echoNoteItem.setHtml("<article class=\"photo\">\n  " +
                        "<img src=\"http://skewable.com/preserve/note_saved_bg.png\" width=\"100%\" height=\"100%\">\n  " +
                        "<div class=\"photo-overlay\"/>\n  " +
                        "<section>\n    " +
                        "<p class=\"text-auto-size\">" + textFileName + "</p>\n  " +
                        "</section>\n  " +
                        "<footer>\n    " +
                        "<img src=\"http://skewable.com/preserve/app_icon.png\" class=\"left\">\n    " +
                        "<p>Preserve</p>\n  " +
                        "</footer>\n" +
                        "</article>\n");
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
                Drive drive = DriveUtils.buildDriveService(credential);
                drive.files().delete(deleteItem.getSourceItemId());
                LOG.info("Note Deleted: " + deleteItem.getSourceItemId());

            } else {
                LOG.warning("I don't know what to do with this notification, so I'm ignoring it.");
            }
        }
    }

    private InputStream getInputStream(String targetUrl) {

        URL url;
        HttpURLConnection connection = null;
        try {
            url = new URL(targetUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setDoOutput(false);
            InputStream is = connection.getInputStream();
            return is;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String sendImageToDrive(Credential credential, InputStream stream, String fileName) throws IOException {

        Drive drive = DriveUtils.buildDriveService(credential);

        File body = new File();
        body.setTitle(fileName);
        body.setMimeType("image/jpeg");

        InputStreamContent content = new InputStreamContent("image/jpeg", stream);
        File file = drive.files().insert(body, content).execute();
        LOG.info("Drive result: " + file.getTitle());

        return file.getId();
    }

    private String sendVideoToDrive(Credential credential, InputStream stream, String fileName) throws IOException {

        Drive drive = DriveUtils.buildDriveService(credential);

        File body = new File();
        body.setTitle(fileName);
        body.setMimeType("video/mp4");

        InputStreamContent content = new InputStreamContent("video/mp4", stream);
        File file = drive.files().insert(body, content).execute();
        LOG.info("Drive result: " + file.getTitle());

        return file.getId();
    }

    private String sendAudioNoteToDrive(Credential credential, InputStream stream, String fileName) throws IOException {

        Drive drive = DriveUtils.buildDriveService(credential);

        File body = new File();
        body.setTitle(fileName);
        body.setMimeType("audio/mpeg");

        InputStreamContent content = new InputStreamContent("audio/mpeg", stream);
        File file = drive.files().insert(body, content).execute();
        LOG.info("Drive result: " + file.getTitle());

        return file.getId();
    }

    private String sendTextNoteToDrive(Credential credential, String message, String fileName) throws IOException {

        Drive drive = DriveUtils.buildDriveService(credential);

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
