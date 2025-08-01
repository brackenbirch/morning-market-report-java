package com.marketreport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    private final String gmailUser;
    private final String gmailPassword;
    private final String workEmailList;

    public EmailService() {
        this.gmailUser = System.getenv("GMAIL_USER");
        this.gmailPassword = System.getenv("GMAIL_PASSWORD");
        this.workEmailList = System.getenv("WORK_EMAIL_LIST");
        
        if (gmailUser == null || gmailPassword == null || workEmailList == null) {
            logger.warn("Email credentials not fully configured. Email sending will be skipped.");
        }
    }

    public void sendReport(String htmlContent, List<MarketData> marketData, List<NewsHeadline> headlines) {
        if (!isConfigured()) {
            logger.warn("Email not configured, skipping email send");
            return;
        }

        try {
            logger.info("Sending email report...");
            
            // Setup mail properties
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

            // Create session
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(gmailUser, gmailPassword);
                }
            });

            // Create message
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(gmailUser));
            
            // Add recipients (split by comma)
            String[] recipients = workEmailList.split(",");
            for (String recipient : recipients) {
                message.addRecipient(Message.RecipientType.TO, 
                    new InternetAddress(recipient.trim()));
            }

            // Set subject
            String subject = String.format("🌅 Morning Market Report - %s", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
            message.setSubject(subject);

            // Set content
            message.setContent(htmlContent, "text/html; charset=utf-8");

            // Send email
            Transport.send(message);
            
            logger.info("Email report sent successfully to {} recipients", recipients.length);
            
        } catch (Exception e) {
            logger.error("Failed to send email report", e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private boolean isConfigured() {
        return gmailUser != null && gmailPassword != null && workEmailList != null;
    }
}
