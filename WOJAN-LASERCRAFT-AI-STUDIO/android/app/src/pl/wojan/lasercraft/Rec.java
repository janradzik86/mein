package pl.wojan.lasercraft;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * Nagrywanie mowy do pliku WAV 16 kHz / mono / PCM16 — format, który przyjmuje whisper.cpp.
 * Zapis lokalny, bez wysyłania dźwięku do sieci.
 */
public final class Rec {

    public static final int RATE = 16000;
    private AudioRecord rec;
    private Thread thread;
    private volatile boolean running = false;
    private File out;
    private long samples = 0;

    public boolean isRunning() {
        return running;
    }

    public boolean start(File file) {
        if (running) return false;
        out = file;
        int minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) minBuf = 4096;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) return false;
            rec.startRecording();
        } catch (Exception e) {
            return false;
        }
        running = true;
        samples = 0;
        thread = new Thread(new Runnable() {
            public void run() {
                writeLoop();
            }
        });
        thread.start();
        return true;
    }

    private void writeLoop() {
        try {
            FileOutputStream fo = new FileOutputStream(out);
            byte[] zeros = new byte[44];
            fo.write(zeros); // nagłówek WAV uzupełniany po zakończeniu
            byte[] buf = new byte[3200];
            while (running) {
                int n = rec.read(buf, 0, buf.length);
                if (n > 0) {
                    fo.write(buf, 0, n);
                    samples += n / 2;
                }
            }
            fo.flush();
            fo.close();
            patchHeader(out, samples);
        } catch (Exception e) {
        }
    }

    public long stop() {
        running = false;
        try {
            if (rec != null) {
                rec.stop();
                rec.release();
            }
        } catch (Exception e) {
        }
        rec = null;
        try {
            if (thread != null) thread.join(1500);
        } catch (Exception e) {
        }
        thread = null;
        return samples / RATE;
    }

    static void patchHeader(File f, long samples) {
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            long dataLen = samples * 2;
            raf.seek(0);
            raf.writeBytes("RIFF");
            raf.writeInt(Integer.reverseBytes((int) (36 + dataLen)));
            raf.writeBytes("WAVE");
            raf.writeBytes("fmt ");
            raf.writeInt(Integer.reverseBytes(16));
            raf.writeShort(Short.reverseBytes((short) 1));
            raf.writeShort(Short.reverseBytes((short) 1));
            raf.writeInt(Integer.reverseBytes(RATE));
            raf.writeInt(Integer.reverseBytes(RATE * 2));
            raf.writeShort(Short.reverseBytes((short) 2));
            raf.writeShort(Short.reverseBytes((short) 16));
            raf.writeBytes("data");
            raf.writeInt(Integer.reverseBytes((int) dataLen));
            raf.close();
        } catch (Exception e) {
        }
    }
}
