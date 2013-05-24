<!--
Copyright (C) 2013 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<%@ page import="com.google.api.client.auth.oauth2.Credential" %>
<%@ page import="com.google.api.services.mirror.model.Contact" %>
<%@ page import="com.google.glassware.MirrorClient" %>
<%@ page import="com.google.glassware.WebUtil" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.io.IOException" %>
<%@ page import="com.google.api.services.mirror.model.TimelineItem" %>
<%@ page import="com.google.api.services.mirror.model.Subscription" %>
<%@ page import="com.google.api.services.mirror.model.Attachment" %>
<%@ page import="com.google.glassware.MainServlet" %>
<%@ page import="com.google.api.services.drive.Drive" %>
<%@ page import="com.google.api.services.drive.model.File" %>
<%@ page import="com.google.api.services.drive.model.FileList" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<!doctype html>
<%

  Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

  String userId = com.google.glassware.AuthUtil.getUserId(request);
  String appBaseUrl = WebUtil.buildUrl(request, "/");

  Credential credential = com.google.glassware.AuthUtil.getCredential(userId);

  Contact contact = MirrorClient.getContact(credential, MainServlet.CONTACT_NAME);
  if (contact == null) {
    MainServlet.insertGlass2DriveContact(credential);
  }

  List<TimelineItem> timelineItems = MirrorClient.listItems(credential, 3L).getItems();

  List<Subscription> subscriptions = MirrorClient.listSubscriptions(credential).getItems();
  boolean timelineSubscriptionExists = false;


  if (subscriptions != null) {
    for (Subscription subscription : subscriptions) {
      if (subscription.getId().equals("timeline")) {
        timelineSubscriptionExists = true;
      } else {
        MainServlet.insertSubscription(credential, userId);
        timelineSubscriptionExists = true;
      }
    }
  }

  Drive drive = com.google.glassware.DriveUtils.buildDriveService(credential);
  List<File> notes = new ArrayList<File>();
  Drive.Files.List filesRequest = drive.files().list();
  do {
      try {
          FileList files = filesRequest.execute();

          notes.addAll(files.getItems());
          filesRequest.setPageToken(files.getNextPageToken());
      } catch (IOException e) {
          filesRequest.setPageToken(null);
      }
  } while (filesRequest.getPageToken() != null &&
          filesRequest.getPageToken().length() > 0);



%>
<html lang="en">
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta charset="utf-8" />
<title>Preserve</title>
<link rel="stylesheet" href="static/css/main.css" type="text/css" />
<link rel="stylesheet" href="static/css/bootstrap.css"> 
<link href="/static/bootstrap/css/bootstrap.min.css" rel="stylesheet" media="screen"> 
 
<!--[if IE]>
	<script src="http://html5shiv.googlecode.com/svn/trunk/html5.js"></script><![endif]-->
<!--[if lte IE 7]>
	<script src="js/IE8.js" type="text/javascript"></script><![endif]-->
<!--[if lt IE 7]>
 
	<link rel="stylesheet" type="text/css" media="all" href="css/ie6.css"/><![endif]-->
</head>

<body bgcolor="#252525">
<div class="container">

	<div id="top">

		<div class="header"></div>

	</div>

	<div id="endlessbottom">

    <div class="pin">

    <form action="<%= WebUtil.buildUrl(request, "/app/main") %>" method="post">
        <input type="hidden" name="operation" value="insertPinCard">
        <INPUT TYPE="image" SRC="static/images/btn_pin.png" WIDTH="310"  HEIGHT="75" BORDER="0" ALT="Send Preserve Card">
        </form>

    </div>

    <div class="signout">
      
      <form action="/signout" method="post">
          <INPUT TYPE="image" SRC="static/images/btn_signout.png" WIDTH="185"  HEIGHT="75" BORDER="0" ALT="Signout">
      </form>

    </div>



		  <div id="timeline">Your Files</div>

    <% String flash = WebUtil.getClearFlash(request);
      if (flash != null) { %>
    <span class="label label-warning">Message: <%= flash %> </span>
    <% } %>

      <div style="margin-top: 5px;">

      <ol>
      <% if (notes != null) {
        for (File note : notes) {
            if (note.getExplicitlyTrashed() != null && note.getExplicitlyTrashed() == true) continue;%>
            <div id="links"><li>
                <% if (note.getMimeType().equalsIgnoreCase("application/vnd.google-apps.document")) { %>
                    <img src="static/images/ic_voice.png"/>
                <% } else if (note.getMimeType().equalsIgnoreCase("image/jpeg")) { %>
                    <img src="static/images/ic_photo.png"/>
                <% } else if (note.getMimeType().equalsIgnoreCase("video/mp4")) { %>
                    <img src="static/images/ic_video.png"/>
                <% } %>
            <a href="<%= note.getDownloadUrl() %>"><%= note.getTitle() %>
            </a></li></div>
      <% }
      } %>
      </div>

        <div style="clear:both;"></div>
  </div>


		

		

	</div>

</body>
</html>