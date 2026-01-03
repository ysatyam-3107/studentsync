package com.example.studysync;

public class Note {
    private String id;
    private String fileName;
    private String fileUrl;
    private String uploaderId;
    private String uploaderName;
    private long uploadedAt;
    private String fileType; // "pdf" or "text"

    public Note() {
        // Required for Firebase
    }

    public Note(String id, String fileName, String fileUrl, String uploaderId,
                String uploaderName, long uploadedAt, String fileType) {
        this.id = id;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.uploaderId = uploaderId;
        this.uploaderName = uploaderName;
        this.uploadedAt = uploadedAt;
        this.fileType = fileType;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getUploaderId() { return uploaderId; }
    public void setUploaderId(String uploaderId) { this.uploaderId = uploaderId; }

    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }

    public long getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(long uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}