package com.example.saharaa.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.saharaa.R;

import java.util.ArrayList;
import java.util.Locale;

public class LanguageSelectionActivity extends AppCompatActivity {

    private TextToSpeech     tts;
    private SpeechRecognizer speechRecognizer;
    private Intent           speechIntent;

    private boolean ttsReady         = false;
    private boolean isListening      = false;
    private boolean isActivityActive = false;
    private boolean waitingForInput  = false;
    private boolean isBlindUser      = false;
    private String  selectedLanguage = "en";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI Elements
    private TextView tvTitle, tvSubtitle, tvHeaderSelected, tvHeaderAll;
    private TextView tvSelectedFlag, tvSelectedName;
    private RadioButton rbEnglish, rbSpanish, rbFrench, rbGerman, rbHindi, rbGujarati, rbKorean;
    private EditText etSearch;
    private com.google.android.material.button.MaterialButton btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_selection);

        // Read Blind Mode Preference
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        isBlindUser = prefs.getBoolean("IS_BLIND", false);

        initUI();
        initListeners();
        updateLocalizedText("en"); // Initial text setup

        // Back → Accessibility page
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(LanguageSelectionActivity.this, AccessibilityModeActivity.class));
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                startActivity(new Intent(LanguageSelectionActivity.this, AccessibilityModeActivity.class));
                finish();
            }
        });

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(0.85f);
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) {
                    if (!isBlindUser) return;
                    mainHandler.post(() -> {
                        switch (id) {
                            case "ASK_LANG":
                            case "RETRY_LANG":
                                if (waitingForInput) startListeningNow();
                                break;
                            case "CONFIRM_LANG":
                                goToLogin();
                                break;
                        }
                    });
                }
                @Override public void onError(String id) {}
            });

            if (isBlindUser) {
                initSpeechRecognizer();
                askLanguage();
            }
        });
    }

    private void initUI() {
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvHeaderSelected = findViewById(R.id.tvHeaderSelected);
        tvHeaderAll = findViewById(R.id.tvHeaderAll);

        tvSelectedFlag = findViewById(R.id.tvSelectedFlag);
        tvSelectedName = findViewById(R.id.tvSelectedName);

        rbEnglish = findViewById(R.id.rbEnglish);
        rbSpanish = findViewById(R.id.rbSpanish);
        rbFrench = findViewById(R.id.rbFrench);
        rbGerman = findViewById(R.id.rbGerman);
        rbHindi = findViewById(R.id.rbHindi);
        rbGujarati = findViewById(R.id.rbGujarati);
        rbKorean = findViewById(R.id.rbKorean);

        etSearch = findViewById(R.id.etSearch);
        btnContinue = findViewById(R.id.btnContinue);
    }

    private void initListeners() {
        // List Item Clicks
        findViewById(R.id.itemEnglish).setOnClickListener(v -> updateSelection("en", false));
        findViewById(R.id.itemSpanish).setOnClickListener(v -> updateSelection("es", false));
        findViewById(R.id.itemFrench).setOnClickListener(v -> updateSelection("fr", false));
        findViewById(R.id.itemGerman).setOnClickListener(v -> updateSelection("de", false));
        findViewById(R.id.itemHindi).setOnClickListener(v -> updateSelection("hi", false));
        findViewById(R.id.itemGujarati).setOnClickListener(v -> updateSelection("gu", false));
        findViewById(R.id.itemKorean).setOnClickListener(v -> updateSelection("ko", false));

        // Continue Button
        btnContinue.setOnClickListener(v -> goToLogin());

        // Search Logic
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLanguages(s.toString().toLowerCase());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    // Toggle visibility based on search
    private void filterLanguages(String query) {
        setVisible(R.id.itemEnglish, "english".contains(query));
        setVisible(R.id.itemSpanish, "spanish".contains(query) || "español".contains(query));
        setVisible(R.id.itemFrench, "french".contains(query) || "français".contains(query));
        setVisible(R.id.itemGerman, "german".contains(query) || "deutsch".contains(query));
        setVisible(R.id.itemHindi, "hindi".contains(query) || "हिन्दी".contains(query));
        setVisible(R.id.itemGujarati, "gujarati".contains(query) || "ગુજરાતી".contains(query));
        setVisible(R.id.itemKorean, "korean".contains(query) || "한국어".contains(query));
    }

    private void setVisible(int id, boolean visible) {
        findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // Update UI & State
    private void updateSelection(String lang, boolean isVoiceCommand) {
        selectedLanguage = lang;
        saveLanguage(lang);

        // Reset RBs
        rbEnglish.setChecked(false);
        rbSpanish.setChecked(false);
        rbFrench.setChecked(false);
        rbGerman.setChecked(false);
        rbHindi.setChecked(false);
        rbGujarati.setChecked(false);
        rbKorean.setChecked(false);

        String flag = "🇺🇸";
        String name = "English";
        String speechMsg = "English selected";
        Locale locale = Locale.US;

        switch (lang) {
            case "en":
                rbEnglish.setChecked(true);
                break;
            case "es":
                rbSpanish.setChecked(true);
                flag = "🇪🇸";
                name = "Spanish"; // Or Español
                speechMsg = "Español seleccionado";
                locale = new Locale("es", "ES");
                break;
            case "fr":
                rbFrench.setChecked(true);
                flag = "🇫🇷";
                name = "French";
                speechMsg = "Français sélectionné";
                locale = Locale.FRENCH;
                break;
            case "de":
                rbGerman.setChecked(true);
                flag = "🇩🇪";
                name = "German";
                speechMsg = "Deutsch ausgewählt";
                locale = Locale.GERMAN;
                break;
            case "hi":
                rbHindi.setChecked(true);
                flag = "🇮🇳";
                name = "Hindi";
                speechMsg = "हिंदी चुनी गई है";
                locale = new Locale("hi", "IN");
                break;
            case "gu":
                rbGujarati.setChecked(true);
                flag = "🇮🇳";
                name = "Gujarati";
                speechMsg = "ગુજરાતી પસંદ કરવામાં આવી છે";
                locale = new Locale("gu", "IN");
                break;
            case "ko":
                rbKorean.setChecked(true);
                flag = "🇰🇷";
                name = "Korean";
                speechMsg = "한국어가 선택되었습니다";
                locale = Locale.KOREA;
                break;
        }

        // Update Top Card
        tvSelectedFlag.setText(flag);
        tvSelectedName.setText(name);

        // Update Localized Text
        updateLocalizedText(lang);

        // Announce selection
        if (tts != null && isBlindUser) {
            tts.setLanguage(locale);
            String id = isVoiceCommand ? "CONFIRM_LANG" : "JUST_ANNOUNCE";
            speak(speechMsg, id);
        }
    }

    // Dynamic Text Update
    private void updateLocalizedText(String lang) {
        String title = "Choose the language";
        String subtitle = "Select your preferred language below. This helps us serve you better.";
        String youSelected = "You Selected";
        String allLanguages = "All Languages";
        String searchHint = "Search";
        String cont = "Continue";

        switch (lang) {
            case "es":
                title = "Elige el idioma";
                subtitle = "Selecciona tu idioma preferido abajo. Esto nos ayuda a servirte mejor.";
                youSelected = "Seleccionaste";
                allLanguages = "Todos los idiomas";
                searchHint = "Buscar";
                cont = "Continuar";
                break;
            case "fr":
                title = "Choisir la langue";
                subtitle = "Sélectionnez votre langue préférée ci-dessous.";
                youSelected = "Vous avez sélectionné";
                allLanguages = "Toutes les langues";
                searchHint = "Rechercher";
                cont = "Continuer";
                break;
            case "de":
                title = "Sprache wählen";
                subtitle = "Wählen Sie unten Ihre bevorzugte Sprache aus.";
                youSelected = "Ausgewählt";
                allLanguages = "Alle Sprachen";
                searchHint = "Suche";
                cont = "Weiter";
                break;
            case "hi":
                title = "भाषा चुनें";
                subtitle = "नीचे अपनी पसंद की भाषा चुनें. यह हमें आपकी बेहतर सेवा करने में मदद करता है.";
                youSelected = "आपने चुना";
                allLanguages = "सभी भाषाएं";
                searchHint = "खोजें";
                cont = "जारी रखें";
                break;
            case "gu":
                title = "ભાષા પસંદ કરો";
                subtitle = "નીચે તમારી પસંદગીની ભાષા પસંદ કરો. આ અમને તમારી વધુ સારી સેવા કરવામાં મદદ કરે છે.";
                youSelected = "તમે પસંદ કર્યું";
                allLanguages = "બધી ભાષાઓ";
                searchHint = "શોધો";
                cont = "ચાલુ રાખો";
                break;
            case "ko":
                title = "언어 선택";
                subtitle = "아래에서 선호하는 언어를 선택하세요.";
                youSelected = "선택됨";
                allLanguages = "모든 언어";
                searchHint = "검색";
                cont = "계속하다";
                break;
        }

        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);
        tvHeaderSelected.setText(youSelected);
        tvHeaderAll.setText(allLanguages);
        etSearch.setHint(searchHint);
        btnContinue.setText(cont);
    }

    // ─── STT ───────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        speechIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}

            @Override
            public void onError(int error) {
                isListening = false;
                if (!isActivityActive || !waitingForInput) return;
                long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 0L : 300L;
                mainHandler.postDelayed(() -> startListeningNow(), delay);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processVoiceResult(matches.get(0).toLowerCase());
                } else if (isActivityActive && waitingForInput) {
                    startListeningNow();
                }
            }
        });
    }

    private void startListeningNow() {
        if (!isActivityActive || speechRecognizer == null) return;
        if (isListening) { speechRecognizer.cancel(); isListening = false; }
        runOnUiThread(() -> speechRecognizer.startListening(speechIntent));
    }

    private void processVoiceResult(String spoken) {
        if (spoken.contains("english"))                            updateSelection("en", true);
        else if (spoken.contains("spanish") || spoken.contains("español")) updateSelection("es", true);
        else if (spoken.contains("french")  || spoken.contains("français")) updateSelection("fr", true);
        else if (spoken.contains("german")  || spoken.contains("deutsch"))  updateSelection("de", true);
        else if (spoken.contains("hindi"))                         updateSelection("hi", true);
        else if (spoken.contains("gujarati") || spoken.contains("gujrati")) updateSelection("gu", true);
        else if (spoken.contains("korean"))                        updateSelection("ko", true);
        else retry();
    }

    // Ask user for language
    private void askLanguage() {
        waitingForInput = true;
        speak("Please select your language. Say English, Hindi, or Gujarati.", "ASK_LANG");
    }

    // Retry automatically
    private void retry() {
        if (!isBlindUser) return;
        speak("Sorry, I didn't catch that. Say English, Hindi, or Gujarati.", "RETRY_LANG");
    }

    private void saveLanguage(String lang) {
        SharedPreferences prefs = getSharedPreferences("SaharaaPrefs", MODE_PRIVATE);
        prefs.edit().putString("LANGUAGE", lang).apply();
    }

    private void goToLogin() {
        startActivity(new Intent(LanguageSelectionActivity.this, LoginRegisterActivity.class));
        finish();
    }

    private void speak(String text, String id) {
        if (tts == null || !ttsReady) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (isBlindUser && speechRecognizer == null) initSpeechRecognizer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        waitingForInput = false;
        if (tts != null) tts.stop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            isListening = false;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }
}
