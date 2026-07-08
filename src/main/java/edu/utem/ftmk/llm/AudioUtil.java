package edu.utem.ftmk.llm;

import java.io.File;
import java.io.FileInputStream;

public class AudioUtil {
    public static int getDuration(String filePath) {
        if (filePath == null || filePath.isEmpty()) return 62;
        File file = new File(filePath);
        if (!file.exists()) return 62; 

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = fis.readAllBytes();
            int i = 0;
            if (data.length > 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
                int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
                i = size + 10;
            }
            double totalDuration = 0.0;
            while (i < data.length - 4) {
                if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xE0) == 0xE0) {
                    int version = (data[i + 1] & 0x18) >> 3;
                    int layer = (data[i + 1] & 0x06) >> 1;
                    int bitrateIdx = (data[i + 2] & 0xF0) >> 4;
                    int sampleRateIdx = (data[i + 2] & 0x0C) >> 2;
                    int padding = (data[i + 2] & 0x02) >> 1;

                    if (layer == 1 && bitrateIdx != 15 && sampleRateIdx != 3) {
                        int sampleRate = 44100;
                        int[][] sampleRates = { {11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000}, {44100, 48000, 32000} };
                        if (version >= 0 && version < 4 && sampleRateIdx >= 0 && sampleRateIdx < 3) sampleRate = sampleRates[version][sampleRateIdx];
                        int[] bitratesMpeg1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
                        int[] bitratesMpeg2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
                        int bitrate = (version == 3) ? bitratesMpeg1[bitrateIdx] : bitratesMpeg2[bitratesMpeg2.length - 1];

                        if (bitrate > 0 && sampleRate > 0) {
                            int samplesPerFrame = (version == 3) ? 1152 : 576;
                            totalDuration += (double) samplesPerFrame / sampleRate;
                            int frameSize = (version == 3) ? (144 * bitrate * 1000 / sampleRate) + padding : (72 * bitrate * 1000 / sampleRate) + padding;
                            if (frameSize <= 0) break;
                            i += frameSize;
                            continue;
                        }
                    }
                }
                i++;
            }
            if (totalDuration > 0) return (int) Math.round(totalDuration);
        } catch (Exception e) {}
        return 62; 
    }
}