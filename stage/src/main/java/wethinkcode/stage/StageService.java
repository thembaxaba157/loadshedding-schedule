package wethinkcode.stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

import wethinkcode.loadshed.common.mq.MQ;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import wethinkcode.loadshed.common.transfer.StageDO;
import wethinkcode.loadshed.spikes.TopicSender;

/**
 * I provide a REST API that reports the current loadshedding "stage". I provide
 * two endpoints:
 * <dl>
 * <dt>GET /stage
 * <dd>report the current stage of loadshedding as a JSON serialisation
 *      of a {@code StageDO} data/transfer object
 * <dt>POST /stage
 * <dd>set a new loadshedding stage/level by POSTing a JSON-serialised {@code StageDO}
 *      instance as the body of the request.
 * </ul>
 */
public class StageService
{
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7001;
    public static final String MQ_TOPIC_NAME = "stage";

    private static final Logger WEB_LOG = Logger.getLogger("wethinkcode.web.WebService");
    


    public static void main( String[] args ){
        final StageService svc = new StageService().initialise();
        svc.start();
    }

    private int loadSheddingStage;

    private Javalin server;

    private int servicePort;

    public int getCurrentLoadSheddingStage(){
        return 3;
    }

    @VisibleForTesting
    StageService initialise(){
        return initialise( DEFAULT_STAGE );
    }

    @VisibleForTesting
    StageService initialise( int initialStage ){
        loadSheddingStage = initialStage;
        assert loadSheddingStage >= 0;

        server = initHttpServer();
        return this;
    }

    public void start(){
        start( DEFAULT_PORT );
    }

    @VisibleForTesting
    void start( int networkPort ){
        servicePort = networkPort;
        run();
    }

    public void stop(){
        server.stop();
    }

    public void run(){
        server.start( servicePort );
    }

    private void getStage(Context ctx){
        ctx.json("{\n" + "\"stage\": " + this.loadSheddingStage + "\n" +"}");

        ctx.status(200);
    }

    private void setStage(Context ctx){
        boolean isbroadcast = false;
        try {
            String requestBody = ctx.body();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(requestBody);
            
            int newStage = Integer.parseInt(jsonNode.get("stage").toString());
            if (newStage >= 0 && newStage <= 8) {
                this.loadSheddingStage = newStage;
                isbroadcast = true;
                broadcastStageChangeEvent(ctx);
                ctx.status(200).result("Loadshedding stage set to: " + newStage);
            } else {
                ctx.status(400).result("Invalid stage value. Stage must be between 0 and 8.");
            }
        } catch (IOException e) {
            WEB_LOG.log(Level.SEVERE, "Invalid stage value. Please provide a valid integer.", e);
            ctx.status(400).result("Invalid stage value. Please provide a valid integer.");
        }
        // if (isbroadcast){
        //     broadcastStageChangeEvent(ctx);
        // }
    }


    // private Javalin initHttpServer(){
    //     Javalin server = Javalin.create()
    //     .get("/stage", this::getStage)
    //     .post("/stage", this::setStage);
    //     return server;
    // }

    private Javalin initHttpServer() {
        server = Javalin.create()
            .get("/stage", this::getStage)
            .post("/stage", this::setStage);
    
        return server;
    }
    // private void broadcastStageChangeEvent( Context ctx ){
    //     final StageDO stageData = ctx.bodyAsClass( StageDO.class );
    //     final int newStage = stageData.getStage();
    //     System.out.println("this is the new stage"+newStage);
    //     TopicSender topicSender = new TopicSender();
    //     String[] stageArray = new String[]{"{ \"stage\":"+String.valueOf(newStage)+" }"};
    //     topicSender.setMessage(stageArray);
    //     topicSender.run();
    // }

    private void broadcastStageChangeEvent(Context ctx) {
        try {
            // Create a JMS connection to ActiveMQ
            final StageDO stageData = ctx.bodyAsClass( StageDO.class );
            final int newStage = stageData.getStage();
            System.out.println("this is the new stage"+newStage);
            String stage = "{ \"stage\":"+String.valueOf(newStage)+" }";
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MQ.URL);
            Connection connection = factory.createConnection(MQ.USER, MQ.PASSWD);
            connection.start();

            // Create a JMS session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a JMS topic
            javax.jms.Topic topic = session.createTopic(MQ_TOPIC_NAME);

            // Create a JMS producer for the topic
            MessageProducer producer = session.createProducer(topic);

            // Create a JMS text message with the new stage value
            TextMessage message = session.createTextMessage(stage);

            // Send the message to the topic
            producer.send(message);

            // Close JMS resources
            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            // Handle JMS exception
            WEB_LOG.log(Level.SEVERE, "Error broadcasting stage change event", e);
            throw new RuntimeException("Error broadcasting stage change event", e);
        }
    }



}


