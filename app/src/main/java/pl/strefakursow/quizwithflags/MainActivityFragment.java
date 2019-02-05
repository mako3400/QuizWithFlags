package pl.strefakursow.quizwithflags;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    /* Znacznik używany przy zapisie błędów w dzienniku Log */
    private static final String TAG = "QuizWithFlags Activity";

    /* Liczba flag biorących udział w quizie */
    private static final int FLAGS_IN_QUIZ = 10;

    /* Nazwy plików z obrazami flag */
    private List<String> fileNameList;

    /* Lista plików z obrazami flag biorących udział w bieżącym quizie */
    private List<String> quizCountriesList;

    /* Wybrane obszary biorące udział w quizie */
    private Set<String> regionSet;

    /* Poprawna nazwa kraju przypisana do bieżącej flagi */
    private String correctAnswer;

    /* Całkowita liczba odpowiedzi */
    private int totalGuesses;

    /* Liczba poprawnych odpowiedzi */
    private int correctAnswers;

    /* Liczba wierszy przycisków odpowiedzi wyświetlanych na ekranie */
    private int guessRows;

    /* Obiekt służący do losowania */
    private SecureRandom random;

    /* Obiekt używany podczas opóźniania procesu ładowania kolejnej flagi w quizie */
    private Handler handler;

    /* Animacja błędnej odpowiedzi */
    private Animation shakeAnimation;

    /* Główny rozkład aplikacji */
    private LinearLayout quizLinearLayout;

    /* Widok wyświetlający numer bieżącego pytania quizu */
    private TextView questionNumberTextView;

    /* Widok wyświetlający bieżącą flagę */
    private ImageView flagImageView;

    /* Tablica zawierająca wiersze przycisków odpowiedzi */
    private LinearLayout[] guessLinearLayouts;

    /* Widok wyświetlający poprawną odpowiedź w quizie */
    private TextView answerTextView;

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        /* Zainicjowanie graficznego interfejsu użytkownika dla fragmentu */
        super.onCreateView(inflater, container, savedInstanceState);

        /* Pobranie rozkładu dla fragmentu */
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        /* Inicjalizacja wybranych pól */
        fileNameList = new ArrayList<>();
        quizCountriesList = new ArrayList<>();
        random = new SecureRandom();
        handler = new Handler();

        /* Inicjalizacja animacji */
        shakeAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.incorrect_shake);
        shakeAnimation.setRepeatCount(3);

        /* Inicjalizacja komponentów graficznego interfejsu użytkownika */
        quizLinearLayout = (LinearLayout) view.findViewById(R.id.quizLinearLayout);
        questionNumberTextView = (TextView) view.findViewById(R.id.questionNumberTextView);
        flagImageView = (ImageView) view.findViewById(R.id.flagImageView);
        guessLinearLayouts = new LinearLayout[4];
        guessLinearLayouts[0] = (LinearLayout) view.findViewById(R.id.row1LinearLayout);
        guessLinearLayouts[1] = (LinearLayout) view.findViewById(R.id.row2LinearLayout);
        guessLinearLayouts[2] = (LinearLayout) view.findViewById(R.id.row3LinearLayout);
        guessLinearLayouts[3] = (LinearLayout) view.findViewById(R.id.row4LinearLayout);
        answerTextView = (TextView) view.findViewById(R.id.answerTextView);

        /* Konfiguracja nasłuchiwania zdarzeń w przyciskach odpowiedzi */
        for (LinearLayout row : guessLinearLayouts) {
            for (int column = 0; column < row.getChildCount(); column++) {
                Button button = (Button) row.getChildAt(column);
                button.setOnClickListener(guessButtonListener);
            }
        }

        /* Wyświetlenie formatowanego tekstu w widoku TextView */
        questionNumberTextView.setText(getString(R.string.question, 1, FLAGS_IN_QUIZ));

        /* Zwróć widok fragmentu do wyświetlenia */
        return view;
    }

    public void updateGuessRows(SharedPreferences sharedPreferences) {

        /* Pobranie informacji o ilości przycisków odpowiedzi do wyświetlenia */
        String choices = sharedPreferences.getString(MainActivity.CHOICES, null);

        /* Liczba wierszy z przyciskami odpowiedzi do wyświetlenia */
        guessRows = Integer.parseInt(choices) / 2;

        /* Ukrycie wszystkich wierszy z przyciskami */
        for (LinearLayout layout : guessLinearLayouts) {
            layout.setVisibility(View.GONE);
        }

        /* Wyświetlenie określonej liczby wierszy z przyciskami odpowiedzi */
        for (int row = 0; row < guessRows; row++) {
            guessLinearLayouts[row].setVisibility(View.VISIBLE);
        }
    }

    public void updateRegions(SharedPreferences sharedPreferences) {

        /* Pobranie informacji na temat wybranych przez użytkownika obszarów */
        regionSet = sharedPreferences.getStringSet(MainActivity.REGIONS, null);
    }

    public void resetQuiz() {

        /* Uzyskaj dostęp do folderu assets */
        AssetManager assets = getActivity().getAssets();

        /* Wyczyść listę z nazwami flag */
        fileNameList.clear();

        /* Pobierz nazwy plików obrazów flag z wybranych przez użytkownika obszarów */
        try {
            /* Pętla przechodząca przez każdy obszar - czyli przez każdy folder w folderze assets */
            for (String region : regionSet) {

                /* Pobranie nazw wszystkich plików znajdujących się w folderze danego obszaru */
                String[] paths = assets.list(region);

                /* Usunięcie z nazw plików ich rozszerzenia formatu */
                for (String path : paths) {
                    fileNameList.add(path.replace(".png", ""));
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Bład podczas ładowania plików z obrazami flag", ex);
        }

        /* Zresetowanie liczby poprawnych i wszystkich udzielonych odpowiedzi */
        correctAnswers = 0;
        totalGuesses = 0;

        /* Wyczyszczenie listy krajów */
        quizCountriesList.clear();

        /* Inicjalizacja zmiennych wykorzystywanych przy losowaniu flag */
        int flagCounter = 1;
        int numberOfFlags = fileNameList.size();

        /* Losowanie flag */
        while (flagCounter <= FLAGS_IN_QUIZ) {

            /* Wybierz losową wartość z zakresu od "0" do "liczby flag" biorących udział w quizie */
            int randomIndex = random.nextInt(numberOfFlags);

            /* Pobierz nazwę pliku o wylosowanym indeksie */
            String fileName = fileNameList.get(randomIndex);

            /* Jeżeli plik o tej nazwie nie został jeszcze wylosowany, to dodaj go do listy wybranych krajów */
            if (!quizCountriesList.contains(fileName)) {
                quizCountriesList.add(fileName);
                ++flagCounter;
            }
        }

        /* Załaduj flagę */
        loadNextFlag();
    }

    private void loadNextFlag() {

        /* Ustalenie nazwy pliku bieżącej flagi */
        String nextImage = quizCountriesList.remove(0);

        /* Zaktualizowanie poprawnej odpowiedzi */
        correctAnswer = nextImage;

        /* Wyczyszczenie widoku TextView */
        answerTextView.setText("");

        /* Wyświetlenie numeru bieżącego pytania */
        questionNumberTextView.setText(getString(R.string.question, (correctAnswers + 1), FLAGS_IN_QUIZ));

        /* Pobieranie nazwy obszaru bieżącej flagi */
        String region = nextImage.substring(0, nextImage.indexOf("-"));

        /* Uzyskanie dostepu do folderu assets */
        AssetManager assets = getActivity().getAssets();

        /* Otworzenie, załadowanie oraz obsadzenie obrazu flagi w widoku ImageView */
        try (InputStream inputStreamFlag = assets.open(region + "/" + nextImage + ".png")) {

            /* Załadowanie obrazu flagi jako obiekt Drawable */
            Drawable drawableFlag = Drawable.createFromStream(inputStreamFlag, nextImage);

            /* Obsadzenie obiektu Drawable (flagi) w widoku ImageView */
            flagImageView.setImageDrawable(drawableFlag);

            /* Animacja wejścia flagi na ekran */
            animate(false);

        } catch (IOException ex) {
            Log.e(TAG, "Bład podczas ładowania " + nextImage, ex);
        }

        /* Przemieszanie nazw plików */
        Collections.shuffle(fileNameList);

        /* Umieszczenie prawidłowej odpowiedzi na końcu listy */
        int correct = fileNameList.indexOf(correctAnswer);
        fileNameList.add(fileNameList.remove(correct));

        /* Dodanie tekstu do przycisków odpowiedzi */
        for (int row = 0; row < guessRows; row++) {
            for (int column = 0; column < 2; column++) {

                /* Uzyskanie dostępu do przycisku i zmienienie jego stanu na "enabled" */
                Button guessButton = (Button) guessLinearLayouts[row].getChildAt(column);
                guessButton.setEnabled(true);

                /* Pobierz nazwę kraju i ustaw ją w widoku Button */
                String fileName = fileNameList.get((row * 2) + column);
                guessButton.setText(getCountryName(fileName));
            }
        }

        /* Dodanie poprawnej odpowiedzi do losowo wybranego przycisku */
        int row = random.nextInt(guessRows);
        int column = random.nextInt(2);
        LinearLayout randomRow = guessLinearLayouts[row];
        String countryName = getCountryName(correctAnswer);
        ((Button) randomRow.getChildAt(column)).setText(countryName);
    }

    private String getCountryName(String name) {
        return name.substring(name.indexOf("-") + 1).replace("_", " ");
    }

    private void animate(boolean animateOut) {

        /* Nie tworzymy animacji przy wyświetlaniu pierwszej flagi */
        if (correctAnswers == 0) return;

        /* Obliczenie współrzędnych środka rozkładu */
        int centerX = (quizLinearLayout.getLeft() + quizLinearLayout.getRight()) / 2;
        int centerY = (quizLinearLayout.getTop() + quizLinearLayout.getBottom()) / 2;

        /* Obliczenie promienia animacji */
        int radius = Math.max(quizLinearLayout.getWidth(), quizLinearLayout.getHeight());

        /* Zdefiniowanie obiektu animacji */
        Animator animator;

        /* Wariant animacji zakrywającej flagę */
        if (animateOut) {

            /* Utworzenie animacji */
            animator = ViewAnimationUtils.createCircularReveal(quizLinearLayout, centerX, centerY, radius, 0);

            /* Gdy animacja się skończy... */
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadNextFlag();
                }
            });
        }

        /* Wariant animacji odkrywającej falgę */
        else {
            animator = ViewAnimationUtils.createCircularReveal(quizLinearLayout, centerX, centerY, 0, radius);
        }

        /* Określenie czasu trwania animacji */
        animator.setDuration(500);

        /* Uruchomienie animacji */
        animator.start();
    }

    private View.OnClickListener guessButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            /* Pobranie naciśniętego przycisku oraz wyświetlanego przez niego tekstu */
            Button guessButton = (Button) v;
            String guess = guessButton.getText().toString();
            String answer = getCountryName(correctAnswer);

            /* Inkrementacja liczby odpowiedzi udzielonych przez użytkownika w quizie */
            ++totalGuesses;

            /* Jeżeli udzielona odpowiedź jest poprawna */
            if (guess.equals(answer)) {

                /* Inkrementacja liczby poprawnych odpowiedzi */
                ++correctAnswers;

                /* Wyświetlenie informacji zwrotnej dla użytkownika o udzieleniu poprawnej odpowiedzi */
                answerTextView.setText(answer + "!");
                answerTextView.setTextColor(getResources().getColor(R.color.correct_answer, getContext().getTheme()));

                /* Dezaktywacja wszystkich przycisków odpowiedzi */
                disableButtons();

                /* Jeżeli użytkownik udzielił odpowiedzi na wszystkie pytania */
                if (correctAnswers == FLAGS_IN_QUIZ) {

                    /* Utworzenie obiektu AlertDialog z spersonalizowanym tekstem oraz przyciskiem */
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle("Quiz results");
                    builder.setMessage(getString(R.string.results, totalGuesses, (1000 / (double) totalGuesses)));
                    builder.setPositiveButton(R.string.reset_quiz, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            resetQuiz();
                        }
                    });

                    builder.setCancelable(false);
                    builder.show();
                }

                /* Jeżeli użytkownik nie udzielił odpowiedzi na wszystkie pytania */
                else {
                    /* Odczekaj 2 sekundy i załaduj kolejną flagę */
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            animate(true);
                        }
                    }, 2000);
                }
            }

            /* Jeżeli udzielona odpowiedź nie jest poprawna */
            else {
                /* Odtworzenie animacji trzęsącej się flagi */
                flagImageView.startAnimation(shakeAnimation);

                /* Wyświetlenie informacji zwrotnej dla użytkownika o udzieleniu błędnej odpowiedzi */
                answerTextView.setText(R.string.incorrect_answer);
                answerTextView.setTextColor(getResources().getColor(R.color.incorrect_answer, getContext().getTheme()));

                /* Dezaktywacja przycisku z błędną odpowiedzią */
                guessButton.setEnabled(false);
            }
        }
    };

    private void disableButtons() {
        for (int row = 0; row < guessRows; row++) {
            LinearLayout guessRow = guessLinearLayouts[row];
            for (int column = 0; column < 2; column++) {
                guessRow.getChildAt(column).setEnabled(false);
            }
        }
    }
}
















