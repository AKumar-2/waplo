package com.whatsapp.bot;

import com.microsoft.playwright.*;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Service
public class WhatsappService {

    public void startAutomation() {
        System.out.println("Starting WhatsApp automation...");

        List<Contact> contacts = readContactsFromCsv();
        if (contacts.isEmpty()) {
            System.out.println("No contacts found in contacts.csv. Exiting.");
            return;
        }

        try (Playwright playwright = Playwright.create()) {
            // Launch Chromium in non-headless mode so the user can scan the QR code
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            // Go to WhatsApp Web
            System.out.println("Opening WhatsApp Web. Please scan the QR code to log in...");
            page.navigate("https://web.whatsapp.com/");

            // Wait for the user to scan the QR code. We check if the chat list is visible.
            // The "#pane-side" element is the left pane containing chats.
            page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(60000));
            System.out.println("Logged in successfully! Starting to send messages...");

            // Small delay to let the page fully initialize
            page.waitForTimeout(3000);

            for (Contact contact : contacts) {
                sendMessage(page, contact.phone, contact.message);
                // Add a small delay between messages to avoid being flagged as spam
                page.waitForTimeout(2000);
            }

            System.out.println("All messages sent successfully!");
            browser.close();
        } catch (Exception e) {
            System.err.println("An error occurred during WhatsApp automation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendMessage(Page page, String phone, String message) {
        System.out.println("Sending message to " + phone + "...");

        try {
            // Clean phone number (remove any spaces, dashes, etc.)
            String cleanPhone = phone.replaceAll("[^0-9]", "");

            // Encode the message for the URL
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

            // Navigate directly to the chat using the API link
            String url = "https://web.whatsapp.com/send?phone=" + cleanPhone + "&text=" + encodedMessage;
            page.navigate(url);

            // Wait for the chat to load and the send button or input field to appear
            // The input field where we type the message usually has a specific title or role.
            // When using the 'send' link, the message is pre-filled, we just need to hit enter or click send.

            // Wait for the "Send" button. WhatsApp uses complex dynamic classes,
            // but the send button usually has a span with data-icon="send"
            page.waitForSelector("span[data-icon='send']", new Page.WaitForSelectorOptions().setTimeout(30000));

            // Click the send button
            page.click("span[data-icon='send']");

            // Wait a little bit for the message to actually be sent
            page.waitForTimeout(2000);

            System.out.println("Successfully sent message to " + phone);
        } catch (Exception e) {
            System.err.println("Failed to send message to " + phone + ": " + e.getMessage());
        }
    }

    private List<Contact> readContactsFromCsv() {
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
                String message = record.get("message");
                if (phone != null && !phone.isEmpty() && message != null && !message.isEmpty()) {
                    contacts.add(new Contact(phone, message));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading contacts.csv: " + e.getMessage());
        }
        return contacts;
    }

    private static class Contact {
        String phone;
        String message;

        Contact(String phone, String message) {
            this.phone = phone;
            this.message = message;
        }
    }
}
