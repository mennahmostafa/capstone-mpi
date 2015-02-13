package ca.mcmaster.capstone.networking;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.structures.PayloadObject;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import static ca.mcmaster.capstone.networking.util.JsonUtil.fromJson;

public class CapstoneServer extends NanoHTTPD {

    public static final String LOG_TAG = "CapstoneHttpServer";

    public enum RequestMethod {
        UPDATE,
        IDENTIFY,
        SEND_TOKEN,
        SEND_EVENT,
    }

    @AllArgsConstructor
    public enum MimeType {
        APPLICATION_JSON("application/json; charset=utf-8"),
        TEXT_PLAIN("text/plain; charset=utf-8");
        @NonNull @Getter private final String contentType;

        @Override
        public String toString() {
            return getContentType();
        }

    }

    public static final String KEY_REQUEST_METHOD = "request_method";
    private final InetAddress inetAddress;
    private final CapstoneService capstoneService;

    public CapstoneServer(@NonNull final InetAddress inetAddress, @NonNull final CapstoneService capstoneService) {
        super(inetAddress, 0);
        this.capstoneService = capstoneService;
        this.inetAddress = inetAddress;
        log("Created");
    }

    @Override
    public void start() throws IOException {
        super.start();
        log("Started on " + this.inetAddress + ": " + getListeningPort());
    }

    @Override
    public Response serve(@NonNull final IHTTPSession session) {
        log("Got " + session.getMethod() + " request on " + session.getUri());
        if (!(session.getMethod().equals(Method.GET) || session.getMethod().equals(Method.POST))) {
            return errorResponse("Only GET and POST requests are supported");
        }

        final Map<String, String> headers = session.getHeaders();
        final RequestMethod method = RequestMethod.valueOf(headers.get(KEY_REQUEST_METHOD));
        final Map<String, String> contentBody = new HashMap<>();
        try {
            session.parseBody(contentBody);
        } catch (final ResponseException | IOException e) {
            log("Error parsing body: " + contentBody);
            log("Headers: " + headers);
            log("Stack trace: (" + e.getClass() + ")" + e.getLocalizedMessage());
            return genericError();
        }
        log("Method: " + method);
        log("Content Body: " + contentBody);

        if (method == null) {
            log("Request method is invalid (null), cannot serve request");
            return genericError();
        }

        if (session.getMethod().equals(Method.GET)) {
            if (method.equals(RequestMethod.UPDATE)) {
                log("Responding with OK and current DeviceInfo");
                return serveGetRequest();
            }
        } else if (session.getMethod().equals(Method.POST)) {
            switch (method) {
                case UPDATE:
                    log("Cannot serve POST with UPDATE method, returning error");
                    return genericError();
                case IDENTIFY:
                    return servePostIdentify(contentBody);
                case SEND_TOKEN:
                    return servePostReceiveToken(contentBody);
                case SEND_EVENT:
                    return servePostReceiveEvent(contentBody);
            }
        }

        log("No known handler for request, returning generic error");
        return genericError();
    }

    private Response serveGetRequest() {
        final PayloadObject<DeviceInfo> getRequestResponse = new PayloadObject<>(capstoneService.getStatus(), getPayloadPeerSet(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, getRequestResponse);
    }

    private static <T> T parseContentBody(@NonNull final Map<String, String> contentBody, @NonNull final Class<T> type) {
        final String postData = contentBody.get("postData");
        final T t = fromJson(postData, type);
        log("Parsed POST data as: " + t);
        return t;
    }

    private Response servePostIdentify(@NonNull final Map<String, String> contentBody) {
        final NetworkPeerIdentifier networkPeerIdentifier = parseContentBody(contentBody, NetworkPeerIdentifier.class);
        capstoneService.addSelfIdentifiedPeer(networkPeerIdentifier);
        final PayloadObject<NetworkPeerIdentifier> postIdentifyResponse = new PayloadObject<>(capstoneService.getLocalNetworkPeerIdentifier(), getPayloadPeerSet(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, postIdentifyResponse);
    }

    private Response servePostReceiveToken(@NonNull final Map<String, String> contentBody) {
        final Token token = parseContentBody(contentBody, Token.class);
        capstoneService.receiveTokenInternal(token);
        return genericSuccess();
    }

    private Response servePostReceiveEvent(@NonNull final Map<String, String> contentBody) {
        final Event event = parseContentBody(contentBody, Event.class);
        capstoneService.receiveEventExternal(event);
        return genericSuccess();
    }

    private Response errorResponse(@NonNull final String errorMessage) {
        final PayloadObject<String> errorPayload = new PayloadObject<>(errorMessage, getPayloadPeerSet(), PayloadObject.Status.ERROR);
        return JSONResponse(Response.Status.BAD_REQUEST, errorPayload);
    }

    private Response genericError() {
        final PayloadObject<String> errorPayload = new PayloadObject<>("Error", getPayloadPeerSet(), PayloadObject.Status.ERROR);
        return JSONResponse(Response.Status.BAD_REQUEST, errorPayload);
    }

    private Response genericSuccess() {
        final PayloadObject<String> successPayload = new PayloadObject<>("Success", getPayloadPeerSet(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, successPayload);
    }

    private static Response JSONResponse(@NonNull final Response.Status status, @NonNull final Object object) {
        return new Response(status, MimeType.APPLICATION_JSON.getContentType(), object.toString());
    }

    private Set<NetworkPeerIdentifier> getPayloadPeerSet() {
        return capstoneService.getAllNetworkDevices();
    }

    private static void log(final String message) {
        Log.v(LOG_TAG, message);
    }
}
