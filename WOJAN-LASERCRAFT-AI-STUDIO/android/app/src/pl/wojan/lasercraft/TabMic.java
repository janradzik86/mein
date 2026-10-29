package pl.wojan.lasercraft;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Zakładka 2 — Mikrofon i prompt: nagranie WAV 16 kHz + rozpoznawanie mowy offline (systemowe). */
public final class TabMic {

    static final int REQ_MIC = 31;

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout info = Ui.card(a);
        info.addView(Ui.section(a, "Dyktowanie opisu projektu"));
        info.addView(Ui.text(a, "Nagranie zostaje na telefonie (WAV 16 kHz mono) — gotowe dla whisper.cpp.\n"
                + "Aplikacja NIE zawiera własnego modelu Whisper: rozpoznawanie offline korzysta z modelu "
                + "systemowego (jeśli producent go dostarczył). Gdy go brak — wpisz tekst z klawiatury, "
                + "nagranie zostaje zapisane do późniejszego przetworzenia.", 12, Ui.MUT, false));
        col.addView(info);

        final Rec rec = new Rec();
        final File wav = new File(a.getFilesDir(), "nagranie.wav");
        final TextView status = Ui.text(a, "Gotowe do nagrania.", 13, Ui.TXT, false);
        final TextView timer = Ui.text(a, "00:00", 26, Ui.ACC, true);

        LinearLayout row = Ui.row(a);
        final Button recBtn = Ui.button(a, "● NAGRAJ", null);
        final Button stopBtn = Ui.buttonAlt(a, "■ STOP", null);
        row.addView(recBtn);
        row.addView(stopBtn);
        col.addView(row);
        col.addView(timer);
        col.addView(status);

        final EditText transcript = Ui.edit(a, "Tu pojawi się rozpoznany tekst (możesz go poprawić)",
                "", 4);
        col.addView(transcript);

        recBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!hasMic(a)) {
                    if (Build.VERSION.SDK_INT >= 23)
                        a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    else Ui.toast(a, "Brak uprawnienia mikrofonu.");
                    return;
                }
                if (rec.start(wav)) {
                    status.setText("Nagrywam... mów wyraźnie, ok. 20–30 s.");
                    stopBtn.setEnabled(true);
                    startTimer(timer, rec);
                } else {
                    status.setText("Nie udało się uruchomić mikrofonu.");
                }
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long sec = rec.stop();
                status.setText("Zapisano nagranie: " + wav.getName() + " (" + sec + " s, "
                        + (wav.length() / 1024) + " kB).");
                Ui.toast(a, "Nagranie zapisane lokalnie.");
            }
        });

        LinearLayout row2 = Ui.row(a);
        row2.addView(Ui.buttonAlt(a, "ODTWÓRZ", new View.OnClickListener() {
            public void onClick(View v) {
                if (!wav.exists() || wav.length() < 1000) {
                    Ui.toast(a, "Brak nagrania.");
                    return;
                }
                try {
                    android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    mp.setDataSource(wav.getAbsolutePath());
                    mp.prepare();
                    mp.start();
                    Ui.toast(a, "Odtwarzam nagranie.");
                } catch (Exception e) {
                    Ui.toast(a, "Błąd odtwarzania: " + e.getMessage());
                }
            }
        }));
        row2.addView(Ui.buttonAcc2(a, "ROZPOZNAJ (offline)", new View.OnClickListener() {
            public void onClick(View v) {
                recognize(a, transcript, status);
            }
        }));
        col.addView(Ui.hscroll(a, row2));

        LinearLayout row3 = Ui.row(a);
        row3.addView(Ui.button(a, "WSTAW DO PROMPTU", new View.OnClickListener() {
            public void onClick(View v) {
                String t = transcript.getText().toString().trim();
                if (t.isEmpty()) {
                    Ui.toast(a, "Brak tekstu — najpierw rozpoznaj lub wpisz.");
                    return;
                }
                a.checkpoint();
                Core.Spec sp = Core.analyze(t);
                a.design.prompt = t;
                Gen.applySpec(a, a.design, sp, 0);
                a.commit("Prompt z dyktowania");
                a.showTab(0);
            }
        }));
        row3.addView(Ui.buttonAlt(a, "WYCZYŚĆ", new View.OnClickListener() {
            public void onClick(View v) {
                transcript.setText("");
            }
        }));
        col.addView(Ui.hscroll(a, row3));

        LinearLayout tips = Ui.card(a);
        tips.addView(Ui.section(a, "Wskazówki dla dobrego dyktowania"));
        tips.addView(Ui.text(a, "• mów wymiary w formie „200 na 150 milimetrów”\n"
                + "• podaj napis w cudzysłowie lub po słowie „napis”\n"
                + "• nazwij przedmiot: tabliczka, brelok, piersiówka, deska…\n"
                + "• powiedz, czy ma być cięcie: „tylko grawer” lub „wytnij kontur”", 12, Ui.MUT, false));
        col.addView(tips);

        recBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        return Ui.scroll(a, col);
    }

    static boolean hasMic(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    static void startTimer(final TextView tv, final Rec rec) {
        tv.postDelayed(new Runnable() {
            int sec = 0;

            public void run() {
                if (!rec.isRunning()) {
                    tv.setText(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
                    return;
                }
                sec++;
                tv.setText(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
                tv.postDelayed(this, 1000);
            }
        }, 1000);
    }

    static void recognize(final MainActivity a, final EditText out, final TextView status) {
        if (!SpeechRecognizer.isRecognitionAvailable(a)) {
            status.setText("System nie ma usługi rozpoznawania mowy. Wpisz tekst ręcznie — nagranie WAV zostaje w pamięci aplikacji.");
            return;
        }
        SpeechRecognizer sr = null;
        boolean onDevice = false;
        try {
            if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(a)) {
                sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(a);
                onDevice = true;
            }
        } catch (Exception e) {
        }
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(a);
        final boolean device = onDevice;
        final SpeechRecognizer recognizer = sr;
        status.setText(device ? "Rozpoznaję offline (model systemowy)…" : "Rozpoznaję (tryb offline preferowany)…");
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) {
            }

            public void onBeginningOfSpeech() {
            }

            public void onRmsChanged(float rms) {
            }

            public void onBufferReceived(byte[] buffer) {
            }

            public void onEndOfSpeech() {
            }

            public void onError(int error) {
                status.setText("Błąd rozpoznawania (kod " + error + "). Wpisz tekst ręcznie; "
                        + "nagranie WAV pozostaje zapisane lokalnie.");
            }

            public void onResults(Bundle results) {
                java.util.ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    out.setText(list.get(0));
                    status.setText((device ? "Rozpoznano offline: " : "Rozpoznano: ") + list.size() + " propozycji.");
                } else {
                    status.setText("Brak wyniku — wpisz tekst ręcznie.");
                }
            }

            public void onPartialResults(Bundle partialResults) {
            }

            public void onEvent(int eventType, Bundle params) {
            }
        });
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            status.setText("Nie można uruchomić rozpoznawania: " + e.getMessage());
        }
    }
}
