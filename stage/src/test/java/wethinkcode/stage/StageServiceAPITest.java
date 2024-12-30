package wethinkcode.stage;

import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import wethinkcode.loadshed.common.transfer.StageDO;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("expensive")
public class StageServiceAPITest {

    public static final int TEST_PORT = 7777;

    private static StageService server;

    @BeforeAll
    public static void startServer() {
        server = new StageService().initialise();
        server.start(TEST_PORT);
    }

    @AfterAll
    public static void stopServer() {
        server.stop();
    }

    @Test
    public void setNewStage_validStage() {
        final int NEW_STAGE = 4;
        HttpResponse<JsonNode> post = Unirest.post(serverUrl() + "/stage")
                .header("Content-Type", "application/json")
                .body(new StageDO(NEW_STAGE))
                .asJson();
        assertEquals(HttpStatus.OK, post.getStatus());

        // Fetch the current stage after setting a new stage
        HttpResponse<JsonNode> response = Unirest.get(serverUrl() + "/stage").asJson();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));

        final int stage = getStageFromResponse(response);
        assertEquals(NEW_STAGE, stage);
    }

    @Test
    public void setNewStage_illegalStageValue() {
        // Fetch the current stage before attempting to set an illegal stage
        HttpResponse<JsonNode> response = Unirest.get(serverUrl() + "/stage").asJson();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
        final int oldStage = getStageFromResponse(response);

        final int NEW_STAGE = -1;
        final HttpResponse<JsonNode> post = Unirest.post(serverUrl() + "/stage")
                .header("Content-Type", "application/json")
                .body(new StageDO(NEW_STAGE))
                .asJson();
        assertEquals(HttpStatus.BAD_REQUEST, post.getStatus());

        // Verify that the stage remains unchanged after attempting to set an illegal stage
        final HttpResponse<JsonNode> check = Unirest.get(serverUrl() + "/stage").asJson();
        assertEquals(HttpStatus.OK, check.getStatus());
        assertEquals("application/json", check.getHeaders().getFirst("Content-Type"));

        final int stage = getStageFromResponse(check);
        assertEquals(oldStage, stage);
    }

    private static int getStageFromResponse(HttpResponse<JsonNode> response) {
        return response.getBody().getObject().getInt("stage");
    }

    private String serverUrl() {
        return "http://localhost:" + TEST_PORT;
    }
}
