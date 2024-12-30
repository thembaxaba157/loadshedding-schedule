package wethinkcode.web;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.rendering.template.JavalinThymeleaf;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.ScheduleDO;
import wethinkcode.loadshed.spikes.TopicReceiver;
import wethinkcode.places.PlaceNameService;
import wethinkcode.schedule.ScheduleService;
import wethinkcode.stage.StageService;

import javax.jms.*;
import java.util.*;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import java.util.logging.Logger;

/**
 * I am the front-end web server for the LightSched project.
 * <p>
 * Remember that we're not terribly interested in the web front-end part of this server, more in the way it communicates
 * and interacts with the back-end services.
 */
public class WebService
{

    public static final int DEFAULT_PORT = 8080;

    public static final String STAGE_SVC_URL = "http://localhost:" + StageService.DEFAULT_PORT;

    public static final String PLACES_SVC_URL = "http://localhost:" + PlaceNameService.DEFAULT_PORT;

    public static final String SCHEDULE_SVC_URL = "http://localhost:" + ScheduleService.DEFAULT_PORT;

    private static final String PAGES_DIR = "/templates/";

    private static final String TEMPLATES_DIR = "/templates/";

    private static int LOAD_SHEDDING_STAGE = 0;

    private Connection connection;

    public static final String MQ_TOPIC = "stage";
    private static final String ALERT_QUEUE_NAME = "alert";

    private Connection alertConnection;
    private Session alertSession;
    private MessageProducer alertProducer;
    // private StageService stageService;

    public WebService(){
        JavalinThymeleaf.configure(templateEngine());
    }

    public static void main( String[] args ){
        final WebService svc = new WebService().initialise();
        svc.start();
        TopicReceiver topicReceiver = new TopicReceiver();
        topicReceiver.run();
    }

    private Javalin server;

    private int servicePort;

    private ScheduleService scheduleService;

    @VisibleForTesting
    WebService initialise(){
        // FIXME: Initialise HTTP client, MQ machinery and server from here
        server = configureHttpServer();
        configureHttpClient();
        scheduleService = new ScheduleService();
        server.get("/",this::mainPage);
        server.post("/towns",this::townsRequest);
        server.post("/schedule",this::scheduleRequest);
        server.post("/updateStage",this::updateStage);
        return this;
    }

    public void mainPage(Context ctx){
        HttpResponse<kong.unirest.JsonNode> responseProvinces = provincesHttpRequest();
        JSONArray jsonArrayProvinces = responseProvinces.getBody().getArray();
        List<String> provinces = new ArrayList<>();

        for (Object province: jsonArrayProvinces){
            if(isProvince(province.toString())){
                provinces.add(province.toString());
            }
        }
        // updateStage(ctx);
        Map<String, Object> viewModel = Map.of("stage",LOAD_SHEDDING_STAGE,"provinces",provinces);

        ctx.render("index.html", viewModel);
    }

    public void townsRequest(Context ctx){
        String selectedProvince = ctx.formParam("province");

        HttpResponse<kong.unirest.JsonNode> responseTowns = townsHttpRequest(selectedProvince);
        HttpResponse<kong.unirest.JsonNode> responseProvinces = provincesHttpRequest();
        JSONArray jsonArrayTowns = responseTowns.getBody().getArray();
        JSONArray jsonArrayProvinces = responseProvinces.getBody().getArray();
        List<String> provinces = new ArrayList<>();
        List<String> towns = new ArrayList<>();

        for (Object province: jsonArrayProvinces){
            if(isProvince(province.toString())){
                provinces.add(province.toString());
            }
        }

        for (int i = 0; i < jsonArrayTowns.length(); i++) {
            JSONObject townObject = jsonArrayTowns.getJSONObject(i);
            String name = townObject.getString("name");
            towns.add(name);
        }
        Map<String, Object> viewModel = Map.of("stage",LOAD_SHEDDING_STAGE,"province",selectedProvince,"towns",towns);
        ctx.render("townsRequest.html", viewModel);
    }

    public void scheduleRequest(Context ctx){
        String selectedProvince = ctx.formParam("province");
        String selectedTown = ctx.formParam("town");

        HttpResponse<kong.unirest.JsonNode> responseSchedule = scheduleHttpRequest(selectedProvince,selectedTown);
        Optional<ScheduleDO> schedule = scheduleService.getSchedule(selectedProvince, selectedTown, LOAD_SHEDDING_STAGE);
    
        ScheduleDO scheduleInfo = schedule.get();
  
        HttpResponse<kong.unirest.JsonNode> responseTowns = townsHttpRequest(selectedProvince);
        HttpResponse<kong.unirest.JsonNode> responseProvinces = provincesHttpRequest();
        JSONArray jsonArrayTowns = responseTowns.getBody().getArray();
        JSONArray jsonArrayProvinces = responseProvinces.getBody().getArray();
        // JSONArray jsonArraySchedule = responseSchedule.getBody().getArray();

        // System.out.println("eSchedule  :"+selectedProvince+" "+selectedTown+" "+jsonArraySchedule);
        List<String> provinces = new ArrayList<>();
        List<String> towns = new ArrayList<>();

        for (Object province: jsonArrayProvinces){
            if(isProvince(province.toString())){
                provinces.add(province.toString());
            }
        }

        for (int i = 0; i < jsonArrayTowns.length(); i++) {
            JSONObject townObject = jsonArrayTowns.getJSONObject(i);
            String name = townObject.getString("name");
            towns.add(name);
        }
        // updateStage(ctx);
        Map<String, Object> viewModel = Map.of("stage",LOAD_SHEDDING_STAGE,"province",selectedProvince,"selectedTown",selectedTown,"scheduleInfo",scheduleInfo);
        ctx.render("scheduleRequest.html", viewModel);
    }

    public void updateStage(Context ctx){
        String stage = ctx.body().trim();
        LOAD_SHEDDING_STAGE = Integer.parseInt(stage);
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
        setUpMessageListener();
    }

    private void configureHttpClient(){
        HttpResponse<kong.unirest.JsonNode> responseStage = stageHttpRequest();
        int httpStage = responseStage.getBody().getObject().getInt( "stage" );
        LOAD_SHEDDING_STAGE = httpStage;
    }


    private Javalin configureHttpServer(){
        return Javalin.create(config -> {
            config.addStaticFiles(PAGES_DIR, Location.CLASSPATH);
        });
    }

    private TemplateEngine templateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATES_DIR);
        templateEngine.setTemplateResolver(resolver);
        templateEngine.addDialect(new LayoutDialect());
        return templateEngine;
    }

    public static boolean isProvince(String province){
        Set<String> provinces = Set.of("Gauteng","Limpopo","Free State",
                "Eastern Cape","KwaZulu-Natal","Mpumalanga","North West","Northern Cape","Western Cape");
        if (provinces.contains(province)){
            return true;
        }
        return false;
    }


    private void sendAlert(String serviceName) {
        try {
            // Send alert message to the alert queue
            TextMessage alertMessage = alertSession.createTextMessage();
            alertMessage.setText("Error connecting to " + serviceName);
            alertProducer.send(alertMessage);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }


    private HttpResponse<JsonNode> safeHttpRequest(String url, String serviceName) {
        try {
            return Unirest.get(url).asJson();
        } catch (Exception e) {
            // If an exception occurs during the HTTP request, send an alert
            sendAlert(serviceName);
            throw e; // Propagate the exception after sending the alert
        }
    }


    public HttpResponse<JsonNode> stageHttpRequest() {
        return safeHttpRequest(STAGE_SVC_URL + "/stage", "Stage Service");
    }

    public HttpResponse<JsonNode> provincesHttpRequest() {
        return safeHttpRequest(PLACES_SVC_URL + "/provinces", "Place-Name Service");
    }

    public HttpResponse<JsonNode> townsHttpRequest(String selectedProvince) {
        return safeHttpRequest(PLACES_SVC_URL + "/towns/" + selectedProvince, "Place-Name Service");
    }

    public HttpResponse<JsonNode> scheduleHttpRequest(String province, String town) {
        return safeHttpRequest(SCHEDULE_SVC_URL + "/" + province + "/" + town, "Schedule Service");
    }

    // public HttpResponse<JsonNode> stageHttpRequest(){
    //     return Unirest.get( STAGE_SVC_URL + "/stage" ).asJson();
    // }

    // public HttpResponse<JsonNode> provincesHttpRequest(){
    //     return Unirest.get( PLACES_SVC_URL + "/provinces" ).asJson();
    // }

    // public HttpResponse<JsonNode> townsHttpRequest(String selectedProvince){
    //     return Unirest.get( PLACES_SVC_URL+ "/towns/"+ selectedProvince ).asJson();
    // }

    // public HttpResponse<JsonNode> scheduleHttpRequest(String province, String town){
    //     return Unirest.get( SCHEDULE_SVC_URL + "/"+province+"/"+town ).asJson();
    // }


    private void setUpMessageListener(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ.URL );
            connection = factory.createConnection( MQ.USER, MQ.PASSWD );

            final Session session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Destination dest = session.createTopic( MQ_TOPIC ); // <-- NB: Topic, not Queue!

            final MessageConsumer receiver = session.createConsumer( dest );
            receiver.setMessageListener( new MessageListener(){
                @Override
                public void onMessage( Message m ){
                    String body = null;
                    try {
                        body = ((TextMessage) m).getText().trim();
                        JSONObject json = new JSONObject(body);
        
                        // Extract the value of the "stage" field
                        int stageValue = json.getInt("stage");
                        
                        // Now 'stageValue' contains the extracted value
                        LOAD_SHEDDING_STAGE = stageValue;
                        // System.out.println("yes       "+ body);
                        // LOAD_SHEDDING_STAGE = Integer.parseInt(body);
                    } catch (JMSException e) {
                        throw new RuntimeException(e);
                    }

                }
            }
            );
            connection.start();

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }

        try {
            // Set up connection, session, and producer for alert messages
            ActiveMQConnectionFactory alertFactory = new ActiveMQConnectionFactory(MQ.URL);
            alertConnection = alertFactory.createConnection(MQ.USER, MQ.PASSWD);
            alertSession = alertConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination alertDest = alertSession.createQueue(ALERT_QUEUE_NAME);
            alertProducer = alertSession.createProducer(alertDest);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }


}
