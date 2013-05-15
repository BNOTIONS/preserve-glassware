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
      if (!subscription.getId().equals("timeline")) {
        timelineSubscriptionExists = false;
      } else {
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
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Glass2Drive</title>
  <link href="/static/bootstrap/css/bootstrap.min.css" rel="stylesheet"
        media="screen">

  <style>
    .button-icon {
      max-width: 75px;
    }

    .tile {
      border-left: 1px solid #444;
      padding: 5px;
      list-style: none;
    }

    .btn {
      width: 100%;
    }
  </style>
</head>
<body>
<div class="navbar navbar-inverse navbar-fixed-top">
  <div class="navbar-inner">
    <div class="container">
      <a class="brand" href="#">Glass2Drive</a>

      <div class="nav-collapse collapse">
        <form class="navbar-form pull-right" action="/signout" method="post">
          <button type="submit" class="btn">Sign out</button>
        </form>
      </div>
      <!--/.nav-collapse -->
    </div>
  </div>
</div>

<div class="container">

  <!-- Main hero unit for a primary marketing message or call to action -->
  <div class="hero-unit">
    <h1>Your Recent Timeline</h1>
    <% String flash = WebUtil.getClearFlash(request);
      if (flash != null) { %>
    <span class="label label-warning">Message: <%= flash %> </span>
    <% } %>

    <div style="margin-top: 5px;">

      <ol>
      <% if (notes != null) {
        for (File note : notes) { %>
        <li><a href="<%= note.getWebViewLink() %>"><%= note.getTitle() %></a></li>
      <% }
      } %>
    </div>
    <div style="clear:both;"></div>
  </div>

  <!-- Example row of columns -->
  <div class="row">
    <div class="span4">
      <h2>Timeline</h2>

      <form action="<%= WebUtil.buildUrl(request, "/main") %>" method="post">
        <input type="hidden" name="operation" value="insertPinCard">
        <button class="btn" type="submit">Insert Pin Card</button>
      </form>

    </div>

    <div class="span4">
      <h2>Contacts</h2>
    </div>

    <div class="span4">
      <h2>Subscriptions</h2>

      <% if (timelineSubscriptionExists) { %>
      <form action="<%= WebUtil.buildUrl(request, "/main") %>"
            method="post">
        <input type="hidden" name="subscriptionId" value="timeline">
        <input type="hidden" name="operation" value="deleteSubscription">
        <button class="btn" type="submit" class="delete">Unsubscribe from
          timeline updates
        </button>
      </form>
      <% } else { %>
      <form action="<%= WebUtil.buildUrl(request, "/main") %>" method="post">
        <input type="hidden" name="operation" value="insertSubscription">
        <input type="hidden" name="collection" value="timeline">
        <button class="btn" type="submit">Subscribe to timeline updates</button>
      </form>
      <% }%>

    </div>
  </div>
</div>

<script
    src="//ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
<script src="/static/bootstrap/js/bootstrap.min.js"></script>
</body>
</html>
