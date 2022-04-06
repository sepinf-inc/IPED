package com.whatsapp;

public class MediaData implements java.io.Serializable {
    private static final long serialVersionUID = -3211751283609594L;

    public boolean autodownloadRetryEnabled;
    public long cachedDownloadedBytes;
    public int faceX;
    public int faceY;
    public int failErrorCode;
    public long fileSize;
    public int gifAttribution;
    public boolean hasStreamingSidecar;
    public int height;
    public long progress;
    public boolean showDownloadedBytes;
    public int suspiciousContent;
    public float thumbnailHeightWidthRatio;
    public boolean transcoded;
    public boolean transferred;
    public long trimFrom;
    public long trimTo;
    public boolean uploadRetry;
    public int width;
    public byte[] cipherKey;
    public java.lang.String doodleId;
    public java.io.File file;
    public byte[] hmacKey;
    public byte[] iv;
    public byte[] mediaKey;
    public byte[] refKey;
    public java.lang.String uploadUrl;
}