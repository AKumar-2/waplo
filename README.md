# WhatsApp Automation Bot (Spring Boot & Playwright)

This project demonstrates how to automate sending WhatsApp messages and images to multiple contacts using Spring Boot and Playwright Java.

## Features
- Read contacts, text messages, and image paths from a CSV file.
- Send a **global message/image** to all contacts (overrides CSV values).
- Configure an **interval delay** between messages (default 10 seconds).
- Send text-only messages or images with a text caption.

## Prerequisites
- Java 17+
- Maven
- An active internet connection for your browser and mobile device.

## Setup
1. Open `src/main/resources/contacts.csv` and configure your target audience.
   - `phone`: The recipient's phone number including the country code (e.g., `+1234567890`).
   - `message`: The text message or image caption you want to send.
   - `image_path`: (Optional) The local path to an image file you want to send. If left blank, only text is sent.

### Global Configuration
You can optionally broadcast a single message or image to *all* contacts listed in the CSV. Open `src/main/resources/application.properties` and uncomment/modify these settings:

```properties
# Overrides the 'message' column in the CSV for all contacts
whatsapp.global.message=This is a broadcast message!

# Overrides the 'image_path' column in the CSV for all contacts
whatsapp.global.image.path=src/main/resources/sample.jpg

# The time delay between sending messages to different contacts (in milliseconds). Default is 10000ms (10 seconds).
whatsapp.interval.ms=10000
```

## Running the Application

1. Compile and build the project:
   ```bash
   mvn clean install
   ```

2. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```

## How it Works
1. When you start the application, Playwright will launch a Chromium browser window and navigate to WhatsApp Web.
2. **Important:** You will see a QR code. Open WhatsApp on your phone, go to Linked Devices, and scan the QR code to log in.
3. The application will wait up to 60 seconds for you to scan the code. Once logged in, it will start sending messages to the contacts specified in `contacts.csv`.
4. It will wait for the configured interval (e.g., 10 seconds) between sending messages to prevent spam detection.
5. The application will automatically close the browser and exit once all messages have been sent.

## Notes & Disclaimers
- **Anti-Spam Warning:** Do not send unsolicited spam. WhatsApp actively bans accounts that send bulk, unwanted messages. The default 10-second interval helps reduce the risk, but does not guarantee immunity from bans. Use responsibly.
- The `image_path` must exist locally on the machine running this application.
