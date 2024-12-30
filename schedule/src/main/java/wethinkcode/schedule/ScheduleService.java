package wethinkcode.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import org.apache.activemq.ActiveMQConnectionFactory;
import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.DayDO;
import wethinkcode.loadshed.common.transfer.ScheduleDO;
import wethinkcode.loadshed.common.transfer.SlotDO;

import javax.jms.*;

/**
 * I provide a REST API providing the current loadshedding schedule for a given
 * town (in a specific province) at a given
 * loadshedding stage.
 */
public class ScheduleService implements Runnable {
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7002;

    public static final String MQ_TOPIC = "stage";

    public static final String STAGE_SVC_URL = "http://localhost:" + 7001;

    private Javalin server;

    private int servicePort;

    /* These are Receiver variables */
    private Connection connection;

    private static long NAP_TIME = 2000; // ms

    private boolean running = true;

    private static int LOAD_SHEDDING_STAGE = 0;

    /* End of Receiver variables */

    private static final Logger WEB_LOG = Logger.getLogger("wethinkcode.web.WebService");

    public static void main(String[] args) {
        final ScheduleService svc = new ScheduleService().initialise();
        svc.start();
        svc.run();
    }

    @VisibleForTesting
    ScheduleService initialise() {
        server = initHttpServer();
        configureHttpClient();
        return this;
    }

    public void start() {
        start(DEFAULT_PORT);
    }

    @VisibleForTesting
    void start(int networkPort) {
        servicePort = networkPort;
        runServer();
    }

    @Override
    public void run() {
        setUpMessageListener();
        while (running) {
            // System.out.println( "Still doing stufff..." );
            snooze();
        }
        closeConnection();
        System.out.println("Bye...");
    }

    public void runServer() {
        server.start(servicePort);
    }

    public void stop() {
        server.stop();
    }

    private Javalin initHttpServer() {
        return Javalin.create()
                .get("/{province}/{town}/{stage}", this::getSchedule)
                .get("/{province}/{town}", this::getDefaultSchedule);
    }

    private Context getSchedule(Context ctx) {
        final String province = ctx.pathParam("province");
        final String townName = ctx.pathParam("town");
        final String stageStr = ctx.pathParam("stage");
        if (province.isEmpty() || townName.isEmpty() || stageStr.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return ctx;
        }
        final int stage = Integer.parseInt(stageStr);
        if (stage < 0 || stage > 8) {
            return ctx.status(HttpStatus.BAD_REQUEST);
        }

        final Optional<ScheduleDO> schedule = getSchedule(province, townName, stage);

        ctx.status(schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND);
        return ctx.json(schedule.orElseGet(ScheduleService::emptySchedule));
    }

    private Context getDefaultSchedule(Context ctx) {
        final String province = ctx.pathParam("province");
        final String townName = ctx.pathParam("town");
        if (province.isEmpty() || townName.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return ctx;
        }

        final Optional<ScheduleDO> schedule = getSchedule(province, townName, LOAD_SHEDDING_STAGE);

        ctx.status(schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND);
        return ctx.json(schedule.orElseGet(ScheduleService::emptySchedule));
    }

    // There *must* be a better way than this...
    public Optional<ScheduleDO> getSchedule(String province, String town, int stage) {
        return province.equalsIgnoreCase("Mars")
                ? Optional.empty()
                : Optional.of(mockSchedule());
    }

    private static ScheduleDO mockSchedule() {
        final List<SlotDO> slots = List.of(
                new SlotDO(LocalTime.of(2, 0), LocalTime.of(4, 0)),
                new SlotDO(LocalTime.of(10, 0), LocalTime.of(12, 0)),
                new SlotDO(LocalTime.of(18, 0), LocalTime.of(20, 0)));
        final List<DayDO> days = List.of(
                new DayDO(slots),
                new DayDO(slots),
                new DayDO(slots),
                new DayDO(slots));
        return new ScheduleDO(days);
    }

    private static ScheduleDO emptySchedule() {
        final List<SlotDO> slots = Collections.emptyList();
        final List<DayDO> days = Collections.emptyList();
        return new ScheduleDO(days);
    }

    /* Receiver methods/functions */

    private void setUpMessageListener() {
        try {
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MQ.URL);
            connection = factory.createConnection(MQ.USER, MQ.PASSWD);

            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Destination dest = session.createTopic(MQ_TOPIC); // <-- NB: Topic, not Queue!

            final MessageConsumer receiver = session.createConsumer(dest);
            receiver.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message m) {
                    String body = null;
                    try {
                        body = ((TextMessage) m).getText().trim();
                        JSONObject json = new JSONObject(body);

                        // Extract the value of the "stage" field
                        int stageValue = json.getInt("stage");

                        // Now 'stageValue' contains the extracted value
                        LOAD_SHEDDING_STAGE = stageValue;
                    } catch (JMSException e) {
                        WEB_LOG.log(Level.SEVERE, "Error from the stage services trying to catch the current stage", e);
                        throw new RuntimeException(e);
                    }

                }
            });
            connection.start();

        } catch (JMSException erk) {
            WEB_LOG.log(Level.SEVERE, "Error from the stage services trying to setup the message listener for the stage", erk);
            throw new RuntimeException(erk);
        }
    }

    private void snooze() {
        try {
            Thread.sleep(NAP_TIME);
        } catch (InterruptedException eek) {
            WEB_LOG.log(Level.SEVERE,"Couldn't snooze the schedule services",eek);
        }
    }

    private void closeConnection() {
        if (connection != null)
            try {
                connection.close();
            } catch (JMSException ex) {
                WEB_LOG.log(Level.SEVERE, "Couldn't close the the Schedule services due to JMSException error", ex);
            }
    }

    /* Initial stage request */
    private void configureHttpClient() {
        try {
            HttpResponse<JsonNode> responseStage = stageHttpRequest();
            int httpStage = responseStage.getBody().getObject().getInt("stage");
            LOAD_SHEDDING_STAGE = httpStage;
        } catch (Exception e) {
            WEB_LOG.log(Level.SEVERE, "Loaded the default stage since the stage couldn't be received properly from the stage services", e);
            LOAD_SHEDDING_STAGE = DEFAULT_STAGE;
        }

    }

    public HttpResponse<JsonNode> stageHttpRequest() {
        return Unirest.get(STAGE_SVC_URL + "/stage").asJson();
    }

}
