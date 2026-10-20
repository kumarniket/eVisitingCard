package com.krniket.platform.integration;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriveLogoService {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private final ObjectProvider<Drive> driveProvider;
    private final String folderId;

    public DriveLogoService(ObjectProvider<Drive> driveProvider,
            @Value("${app.drive.folder-id:}") String folderId) {
        this.driveProvider = driveProvider;
        this.folderId = folderId;
    }

    public Upload upload(String slug, MultipartFile upload) {
        byte[] bytes = validate(upload);
        Drive drive = drive();
        if (folderId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The administrator Drive folder is not configured.");
        try {
            String contentType = upload.getContentType();
            File metadata = new File().setName(slug + "-logo").setParents(List.of(folderId));
            File created = drive.files().create(metadata,
                    new InputStreamContent(contentType, new ByteArrayInputStream(bytes)).setLength(bytes.length))
                    .setFields("id,mimeType").execute();
            String url = "/api/v1/public/shops/" + slug + "/logo";
            return new Upload(created.getId(), url, contentType);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The logo could not be uploaded to Google Drive.", exception);
        }
    }

    public Download download(String fileId) {
        try {
            File metadata = drive().files().get(fileId).setFields("id,mimeType").execute();
            String mimeType = metadata.getMimeType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Assigned logo is not an image.");
            }
            try (var stream = drive().files().get(fileId).executeMediaAsInputStream()) {
                byte[] bytes = stream.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Assigned logo exceeds the size limit.");
                return new Download(bytes, mimeType);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The assigned logo could not be retrieved from Google Drive.", exception);
        }
    }

    public void deleteSuperseded(String slug, String keepFileId) {
        if (folderId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The administrator Drive folder is not configured.");
        try {
            String query = "'" + folderId + "' in parents and name = '" + slug + "-logo' and trashed = false";
            String pageToken = null;
            List<String> fileIds = new ArrayList<>();
            do {
                FileList files = drive().files().list()
                        .setQ(query)
                        .setFields("nextPageToken,files(id)")
                        .setPageSize(1000)
                        .setPageToken(pageToken)
                        .execute();
                if (files.getFiles() != null) files.getFiles().stream().map(File::getId).forEach(fileIds::add);
                pageToken = files.getNextPageToken();
            } while (pageToken != null);
            for (String fileId : supersededFileIds(fileIds, keepFileId)) drive().files().delete(fileId).execute();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Superseded shop logos could not be removed from Google Drive.", exception);
        }
    }

    static List<String> supersededFileIds(List<String> fileIds, String keepFileId) {
        return fileIds.stream().filter(fileId -> !fileId.equals(keepFileId)).toList();
    }

    private byte[] validate(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a non-empty image smaller than 10 MB.");
        }
        String type = upload.getContentType();
        if (!List.of("image/png", "image/jpeg", "image/gif").contains(type)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG, and GIF logos are accepted.");
        }
        try {
            byte[] bytes = upload.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload is not a readable image.");
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload is not a readable image.");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels > MAX_PIXELS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo dimensions must be 16 megapixels or smaller.");
                } finally {
                    reader.dispose();
                }
            }
            return bytes;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload is not a readable image.", exception);
        }
    }

    private Drive drive() {
        Drive drive = driveProvider.getIfAvailable();
        if (drive == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google Drive integration is not configured.");
        return drive;
    }

    public record Upload(String fileId, String imageUrl, String contentType) {}
    public record Download(byte[] bytes, String contentType) {}
}
