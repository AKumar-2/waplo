package com.whatsapp.bot;

import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Service
public class WhatsappService {

    @Value("${whatsapp.interval.ms:10000}")
    private long intervalMs;

    @Value("${whatsapp.global.message:#{null}}")
    private String globalMessage;

    @Value("${whatsapp.global.image.path:#{null}}")
    private String globalImagePath;

    @Value("${whatsapp.browser.headless:true}")
    private boolean headless;

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private Thread playwrightThread;
    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        playwrightThread = new Thread(() -> {
            try {
                System.out.println("Starting Playwright automation...");
                playwright = Playwright.create();
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
                BrowserContext context = browser.newContext();
                page = context.newPage();

                System.out.println("Opening WhatsApp Web. Please scan the QR code to log in...");
                page.navigate("https://web.whatsapp.com/");

                // Increase login timeout to 180 seconds to allow for scanning and syncing
                page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(180000));
                System.out.println("Logged in successfully! Ready to send messages...");

                // Wait a bit more for all initial sync processes to finish
                page.waitForTimeout(5000);

                while (running) {
                    Runnable task = taskQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    if (task != null) {
                        try {
                            task.run();
                        } catch (Exception e) {
                            System.err.println("Error executing task: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("An error occurred during WhatsApp automation initialization: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (browser != null) {
                    browser.close();
                }
                if (playwright != null) {
                    playwright.close();
                }
            }
        });
        playwrightThread.start();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (playwrightThread != null) {
            playwrightThread.interrupt();
        }
    }

    public void queueMessages(List<String> phones, String message, String imagePath) {
        taskQueue.offer(() -> {
            List<Contact> allContacts = readContactsFromCsv();

            for (String phone : phones) {
                // Find contact's default message/image if any
                String defaultMessage = null;
                String defaultImagePath = null;
                for (Contact c : allContacts) {
                    if (c.phone.equals(phone)) {
                        defaultMessage = c.message;
                        defaultImagePath = c.imagePath;
                        break;
                    }
                }

                // Prioritize global > explicit UI override > contact default
                String resolvedMessage = (globalMessage != null && !globalMessage.trim().isEmpty()) ? globalMessage : message;
                if (resolvedMessage == null || resolvedMessage.trim().isEmpty()) {
                    resolvedMessage = defaultMessage;
                }

                String resolvedImagePath = (globalImagePath != null && !globalImagePath.trim().isEmpty()) ? globalImagePath : imagePath;
                if (resolvedImagePath == null || resolvedImagePath.trim().isEmpty()) {
                    resolvedImagePath = defaultImagePath;
                }

                sendMessageAndImage(page, phone, resolvedMessage, resolvedImagePath);

                System.out.println("Waiting " + (intervalMs/1000) + " seconds before the next message...");
                page.waitForTimeout(intervalMs);
            }
        });
    }

    private void sendMessageAndImage(Page page, String phone, String message, String imagePath) {
        System.out.println("Preparing to send to " + phone + "...");

        try {
            String cleanPhone = phone.replaceAll("[^0-9]", "");

            // Generate the link
            String url = "https://web.whatsapp.com/send?phone=" + cleanPhone;
            if (message != null && (imagePath == null || imagePath.isEmpty())) {
                 // If no image, we can pre-fill text. Replace '+' with '%20' for WhatsApp compatibility.
                 String encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
                 url += "&text=" + encodedMsg;
            }

            // Inject an anchor tag into the DOM and click it to avoid full page reload
            String jsInject = "() => {" +
                "let a = document.createElement('a');" +
                "a.href = '" + url + "';" +
                "a.id = 'dynamic-chat-link';" +
                "document.body.appendChild(a);" +
                "a.click();" +
                "a.remove();" +
            "}";
            page.evaluate(jsInject);

            // Wait for the chat to load completely by checking for the attach menu or message input.
            // Increase timeout to 60 seconds because WhatsApp UI can be slow.
            page.waitForSelector("div[title='Attach'], span[data-icon='clip']", new Page.WaitForSelectorOptions().setTimeout(60000));

            if (imagePath != null && !imagePath.trim().isEmpty()) {
                Path path = Paths.get(imagePath);
                if (Files.exists(path)) {
                    System.out.println("Attaching image: " + imagePath);
                    // Click the attach (paperclip) icon
                    page.click("span[data-icon='clip'], div[title='Attach']");

                    // Wait for the file input to be attached to the DOM
                    page.waitForTimeout(1000);

                    // The photo input usually accepts images
                    page.setInputFiles("input[accept='image/*,video/mp4,video/3gpp,video/quicktime']", path);

                    // Wait for the image preview and caption box to load
                    page.waitForSelector("div[contenteditable='true'][data-tab='10']", new Page.WaitForSelectorOptions().setTimeout(10000));

                    // Type the message into the caption box if provided
                    if (message != null && !message.trim().isEmpty()) {
                        page.fill("div[contenteditable='true'][data-tab='10']", message);
                    }

                    // Click the send button for the attachment preview
                    page.click("span[data-icon='send']");
                    System.out.println("Sent image with caption to " + phone);

                } else {
                    System.err.println("Image file not found: " + imagePath + ". Sending text only.");
                    sendTextOnly(page, phone, message);
                }
            } else {
                sendTextOnly(page, phone, message);
            }

            page.waitForTimeout(2000);

        } catch (Exception e) {
            System.err.println("Failed to send message to " + phone + ": " + e.getMessage());
        }
    }

    private void sendTextOnly(Page page, String phone, String message) {
        try {
             if(message == null || message.trim().isEmpty()){
                 System.out.println("Message is empty, skipping sending.");
                 return;
             }
             // For text only, the message is prefilled by URL, just wait for send button
             page.waitForSelector("span[data-icon='send']", new Page.WaitForSelectorOptions().setTimeout(10000));
             page.click("span[data-icon='send']");
             System.out.println("Successfully sent text message to " + phone);
        } catch (Exception e) {
             System.err.println("Failed to click send button for " + phone + ": " + e.getMessage());
        }
    }

    public List<Contact> readContactsFromCsv() {
        List<Contact> contacts = new ArrayList<>();
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("contacts.csv");
            if (is == null) {
                System.out.println("Could not find contacts.csv in resources.");
                return contacts;
            }
            Reader in = new InputStreamReader(is);
            CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(in);

            for (CSVRecord record : parser) {
                String phone = record.get("phone");
                String message = record.isMapped("message") ? record.get("message") : null;
                String imagePath = record.isMapped("image_path") ? record.get("image_path") : null;

                if (phone != null && !phone.isEmpty()) {
                    contacts.add(new Contact(phone, message, imagePath));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading contacts.csv: " + e.getMessage());
        }
        return contacts;
    }

    public static class Contact {
        public String phone;
        public String message;
        public String imagePath;

        public Contact(String phone, String message, String imagePath) {
            this.phone = phone;
            this.message = message;
            this.imagePath = imagePath;
        }

        public String getPhone() { return phone; }
        public String getMessage() { return message; }
        public String getImagePath() { return imagePath; }
    }
}
