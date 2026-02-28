# WhatsApp Automation Bot (Spring Boot & Playwright)

This project demonstrates how to automate sending WhatsApp messages to multiple contacts using Spring Boot and Playwright Java.

## Prerequisites
- Java 17+
- Maven

## Setup
1. Open `src/main/resources/contacts.csv` and add your contacts.
   - The first column should be `phone` (including country code, e.g., `+1234567890`).
   - The second column should be `message`.

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
4. The application will automatically close the browser and exit once all messages have been sent.

## Notes
- **Do not send spam.** WhatsApp actively bans accounts that send bulk, unsolicited messages. Add a reasonable delay between messages to avoid this.
- Ensure your phone remains connected to the internet while the automation runs.
