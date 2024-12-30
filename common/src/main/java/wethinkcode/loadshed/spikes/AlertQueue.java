package wethinkcode.loadshed.spikes;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AlertQueue {

    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String ALERT_QUEUE_NAME = "alert";
    private static final String TOPIC_NAME = "phonealert"; // Replace with your chosen TOPIC name

    public static void main(String[] args) {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        Connection connection = null;

        try {
            connection = connectionFactory.createConnection();
            connection.start();

            // Create a session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create the alert queue (if it doesn't exist)
            Destination destination = session.createQueue(ALERT_QUEUE_NAME);

            // Create a consumer
            MessageConsumer consumer = session.createConsumer(destination);

            System.out.println("Alert Service started. Listening for messages...");

            // Set up a message listener
            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage) {
                    try {
                        String alertMessage = ((TextMessage) message).getText();
                        System.out.println("Received message: " + alertMessage);

                        // Send the alert message to ntfy.sh
                        sendAlertToNtfySh(alertMessage);

                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });

            // Keep the program running to listen for messages
            Thread.sleep(Long.MAX_VALUE);

        } catch (JMSException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void sendAlertToNtfySh(String message) {
        try {
            URL url = new URL("https://ntfy.sh/" + TOPIC_NAME);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Write the alert message as the body of the POST request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = message.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Alert sent successfully to ntfy.sh");
            } else {
                System.out.println("Failed to send alert to ntfy.sh. Response code: " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
