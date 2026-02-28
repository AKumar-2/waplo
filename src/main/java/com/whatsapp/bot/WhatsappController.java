package com.whatsapp.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WhatsappController {

    @Autowired
    private WhatsappService whatsappService;

    @GetMapping("/contacts")
    public List<WhatsappService.Contact> getContacts() {
        return whatsappService.readContactsFromCsv();
    }

    @PostMapping("/send")
    public String sendMessages(@RequestBody SendRequest request) {
        if (request.getPhones() == null || request.getPhones().isEmpty()) {
            return "Error: No phone numbers provided.";
        }
        whatsappService.queueMessages(request.getPhones(), request.getMessage(), request.getImagePath());
        return "Messages queued successfully.";
    }

    public static class SendRequest {
        private List<String> phones;
        private String message;
        private String imagePath;

        public List<String> getPhones() { return phones; }
        public void setPhones(List<String> phones) { this.phones = phones; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getImagePath() { return imagePath; }
        public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    }
}
