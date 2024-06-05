/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task.video;

import java.awt.Dimension;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe de armazenamento de dados do resultado de saída do processo de geração
 * de imagens com cenas extraídas de vídeos.
 *
 * @author Wladimir Leite
 */
public class VideoProcessResult implements Closeable {

    private String videoStream;
    private long videoDuration;
    private Dimension dimension;
    private int rotation;
    private float FPS;
    private long bitRate;
    private String videoFormat, videoCodec;
    private Map<String, String> clipInfos = new HashMap<String, String>();
    private List<File> frames = new ArrayList<>();
    private boolean success, timeout;
    private long processingTime;
    private File subTmp;

    public VideoProcessResult(File subTmp) {
        this.subTmp = subTmp;
    }

    public long getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(long videoDuration) {
        this.videoDuration = videoDuration;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public int getRotation() {
        return rotation;
    }

    public String getVideoStream() {
        return videoStream;
    }

    public void setVideoStream(String videoStream) {
        this.videoStream = videoStream;
    }

    public Map<String, String> getClipInfos() {
        return this.clipInfos;
    }

    public void setFrames(List<File> frames) {
        this.frames = frames;
    }

    public List<File> getFrames() {
        return this.frames;
    }

    public void setVideoInfo(String info) throws Exception {
        if (info == null)
            return;

        videoDuration = getDuration(info);
        dimension = getDimension(info);
        videoStream = getVideoStream(info, false);
        if (videoStream == null) {
            videoStream = getVideoStream(info, true);
        }
        rotation = getRotation(info);

        setFPS(getFPS(info));
        setBitRate(getBitRate(info));
        setVideoFormat(getStringInfo(info, "ID_VIDEO_FORMAT")); //$NON-NLS-1$
        setVideoCodec(getStringInfo(info, "ID_VIDEO_CODEC")); //$NON-NLS-1$

        getClipInfos(info);

    }

    private void getClipInfos(String info) throws Exception {
        String nameKey = "ID_CLIP_INFO_NAME"; //$NON-NLS-1$
        String valueKey = "ID_CLIP_INFO_VALUE"; //$NON-NLS-1$
        int i = 0;
        while (true) {
            String s1 = nameKey + i + "="; //$NON-NLS-1$
            int p1 = info.indexOf(s1);
            if (p1 == -1)
                break;
            int p2 = info.indexOf('\n', p1);
            String name = info.substring(p1 + s1.length(), p2).trim();

            s1 = valueKey + i + "="; //$NON-NLS-1$
            p1 = info.indexOf(s1);
            if (p1 == -1)
                break;
            p2 = info.indexOf('\n', p1);
            String value = info.substring(p1 + s1.length(), p2).trim();

            if (!value.isEmpty())
                clipInfos.put(name, value);
            i++;
        }
    }

    private long getDuration(String info) throws Exception {
        String s1 = "ID_LENGTH="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return -1;
        }
        int p2 = info.indexOf('\n', p1);
        String s = info.substring(p1 + s1.length(), p2);
        try {
            return (long) (1000 * Double.parseDouble(s.trim()));

        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float getFPS(String info) throws Exception {
        String s1 = "ID_VIDEO_FPS="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return -1;
        }
        int p2 = info.indexOf('\n', p1);
        String s = info.substring(p1 + s1.length(), p2);
        try {
            return Float.parseFloat(s.trim());

        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int getRotation(String info) throws Exception {
        String s1 = "_ROTATE="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return 0;
        }
        int p2 = info.indexOf('\n', p1);
        String s = info.substring(p1 + s1.length(), p2);
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getStringInfo(String info, String s1) throws Exception {
        s1 += "="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return null;
        }
        int p2 = info.indexOf('\n', p1);
        String s = info.substring(p1 + s1.length(), p2);
        return s.trim();
    }

    private long getBitRate(String info) throws Exception {
        String s1 = "ID_VIDEO_BITRATE="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return -1;
        }
        int p2 = info.indexOf('\n', p1);
        String s = info.substring(p1 + s1.length(), p2);
        try {
            return Long.parseLong(s.trim());

        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getVideoStream(String info, boolean alt) throws Exception {
        String s1 = alt ? ", -vid " : "Video stream found, -vid ";
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return null;
        }
        int p2 = info.indexOf('\n', p1);
        if (p2 < 0) {
            return null;
        }
        String s = info.substring(p1 + s1.length(), p2);
        if (s.length() != 1) {
            return null;
        }
        if (!Character.isDigit(s.charAt(0))) {
            return null;
        }
        return s;
    }

    private Dimension getDimension(String info) throws Exception {
        String s1 = "ID_VIDEO_WIDTH="; //$NON-NLS-1$
        int p1 = info.indexOf(s1);
        if (p1 < 0) {
            return null;
        }
        int p2 = info.indexOf('\n', p1);
        if (p2 < 0) {
            return null;
        }
        String s3 = "ID_VIDEO_HEIGHT="; //$NON-NLS-1$
        int p3 = info.indexOf(s3);
        if (p3 < 0) {
            return null;
        }
        int p4 = info.indexOf('\n', p3);
        if (p4 < 0) {
            return null;
        }
        return new Dimension(Integer.parseInt(info.substring(p1 + s1.length(), p2).trim()),
                Integer.parseInt(info.substring(p3 + s3.length(), p4).trim()));
    }

    public float getFPS() {
        return FPS;
    }

    public void setFPS(float fPS) {
        FPS = fPS;
    }

    public long getBitRate() {
        return bitRate;
    }

    public void setBitRate(long bitRate) {
        this.bitRate = bitRate;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public String getVideoFormat() {
        return videoFormat;
    }

    public void setVideoFormat(String videoFormat) {
        this.videoFormat = videoFormat;
    }

    @Override
    public void close() throws IOException {
        if (subTmp != null) {
            File[] files = subTmp.listFiles();
            for (File file : files) {
                file.delete();
            }
            subTmp.delete();
        }
    }

}
