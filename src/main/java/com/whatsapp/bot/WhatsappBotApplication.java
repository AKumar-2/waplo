package com.whatsapp.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class WhatsappBotApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(WhatsappBotApplication.class, args);

        WhatsappService whatsappService = context.getBean(WhatsappService.class);
        whatsappService.startAutomation();

        // Ensure the app exits after finishing the automation
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
