package com.qa.fakemic.tests;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.qa.fakemic.browser.FakeMicBrowser;
import com.qa.fakemic.tts.VoiceQueryGenerator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Speaks into a free public speech-to-text website through the fake microphone and checks the
 * transcript it produces.
 *
 * <p>Site under test: <a href="https://huggingface.co/spaces/Xenova/whisper-web">Whisper Web</a> —
 * OpenAI's Whisper model running entirely in the browser. It records through the standard
 * {@code getUserMedia} microphone API, which is exactly what Chromium's fake-audio flags replace.
 *
 * <p>All page interaction and assertions use Playwright's own API (role-based locators and
 * {@code PlaywrightAssertions.assertThat}); TestNG only runs the tests and supplies the data.
 */
public class WhisperWebTranscriptionTest {

    private static final String SITE_URL = "https://xenova-whisper-web.static.hf.space/";
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("headless", "true"));

    /** First run in a fresh browser downloads the Whisper model (~40 MB), so allow plenty of time. */
    private static final double TRANSCRIBE_TIMEOUT_MS = 180_000;

    private Path wavFile;
    private FakeMicBrowser browser;

    @DataProvider
    public Object[][] spokenPhrases() {
        // { what the fake user says, words the transcript must contain }
        return new Object[][]{
                {"Purple elephants dance on the moon.", new String[]{"purple", "elephants", "moon"}},
                {"What is the weather like today?", new String[]{"weather", "today"}},
                {"Please set a timer for ten minutes.", new String[]{"timer", "minutes"}},
        };
    }

    @Test(dataProvider = "spokenPhrases")
    public void transcriptMatchesSpokenPhrase(String phrase, String[] expectedWords) throws Exception {
        // 1. Generate the voice query.
        wavFile = VoiceQueryGenerator.generate(phrase);

        // 2. Launch a browser whose microphone "speaks" that file.
        browser = new FakeMicBrowser(wavFile, HEADLESS);
        Page page = browser.page();
        page.navigate(SITE_URL);

        // 3. Record exactly one play-through of the file, like a user pressing record and talking.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start Recording")).click();
        Locator stopButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Stop Recording"));
        assertThat(stopButton).isVisible();
        page.waitForTimeout(VoiceQueryGenerator.durationMs(wavFile));
        stopButton.click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Load")).click();

        // 4. Transcribe. The transcript row is "<timestamp> <text>", e.g. "00:00 Purple elephants…".
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Transcribe Audio")).click();
        Locator transcript = page.getByText(Pattern.compile("^\\d{2}:\\d{2}$")).first().locator("..");
        assertThat(transcript).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(TRANSCRIBE_TIMEOUT_MS));

        // 5. Assert on meaning, not exact punctuation/casing.
        for (String word : expectedWords) {
            assertThat(transcript).containsText(Pattern.compile(word, Pattern.CASE_INSENSITIVE));
        }
    }

    @AfterMethod(alwaysRun = true)
    public void cleanUp() {
        if (browser != null) browser.close();
        VoiceQueryGenerator.delete(wavFile);
        browser = null;
        wavFile = null;
    }
}
