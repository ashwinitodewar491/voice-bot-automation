package com.qa.fakemic.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.util.List;

/**
 * Launches Chromium with its microphone replaced by a WAV file.
 *
 * <p>The three flags that make it work:
 * <ul>
 *   <li>{@code --use-fake-device-for-media-stream} — use a fake capture device instead of real hardware</li>
 *   <li>{@code --use-fake-ui-for-media-stream} — auto-accept the "allow microphone?" prompt</li>
 *   <li>{@code --use-file-for-fake-audio-capture=<wav>} — the fake mic plays this file (on a loop)</li>
 * </ul>
 * The WAV is read when the browser launches, so each new query means a new browser.
 */
public final class FakeMicBrowser implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    /**
     * @param wavFile  the audio the fake microphone will "speak"
     * @param headless run without a visible window
     */
    public FakeMicBrowser(Path wavFile, boolean headless) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(List.of(
                        "--use-fake-device-for-media-stream",
                        "--use-fake-ui-for-media-stream",
                        "--use-file-for-fake-audio-capture=" + wavFile.toAbsolutePath())));
        context = browser.newContext(new Browser.NewContextOptions().setPermissions(List.of("microphone")));
        page = context.newPage();
    }

    public Page page() {
        return page;
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }
}
