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

    public void startAutomation() {
        System.out.println("Starting WhatsApp automation...");

        List<Contact> contacts = readContactsFromCsv();
        if (contacts.isEmpty()) {
            System.out.println("No contacts found in contacts.csv. Exiting.");
            return;
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            System.out.println("Opening WhatsApp Web. Please scan the QR code to log in...");
            page.navigate("https://web.whatsapp.com/");

            page.waitForSelector("#pane-side", new Page.WaitForSelectorOptions().setTimeout(60000));
            System.out.println("Logged in successfully! Starting to send messages...");

            page.waitForTimeout(3000);

            for (Contact contact : contacts) {
                // Use global settings if provided, otherwise use CSV data
                String finalMessage = (globalMessage != null && !globalMessage.trim().isEmpty()) ? globalMessage : contact.message;
                String finalImagePath = (globalImagePath != null && !globalImagePath.trim().isEmpty()) ? globalImagePath : contact.imagePath;

                sendMessageAndImage(page, contact.phone, finalMessage, finalImagePath);

                System.out.println("Waiting " + (intervalMs/1000) + " seconds before the next message...");
                page.waitForTimeout(intervalMs);
            }

            System.out.println("All messages sent successfully!");
            browser.close();
        } catch (Exception e) {
            System.err.println("An error occurred during WhatsApp automation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendMessageAndImage(Page page, String phone, String message, String imagePath) {
        System.out.println("Preparing to send to " + phone + "...");

        try {
            String cleanPhone = phone.replaceAll("[^0-9]", "");

            // Navigate directly to the chat
            String url = "https://web.whatsapp.com/send?phone=" + cleanPhone;
            if (message != null && (imagePath == null || imagePath.isEmpty())) {
                 // If no image, we can pre-fill text
                 url += "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
            }
            page.navigate(url);

            // Wait for the chat to load completely by checking for the attach menu or message input
            page.waitForSelector("div[title='Attach'], span[data-icon='clip']", new Page.WaitForSelectorOptions().setTimeout(30000));

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

    private static class Contact {
        String phone;
        String message;
        String imagePath;

        Contact(String phone, String message, String imagePath) {
            this.phone = phone;
            this.message = message;
            this.imagePath = imagePath;
        }
    }
}
