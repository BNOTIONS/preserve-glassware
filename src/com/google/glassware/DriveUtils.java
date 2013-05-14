package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.drive.Drive;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class DriveUtils {

    public static Drive buildDriveService(Credential credential) throws IOException {

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

}
