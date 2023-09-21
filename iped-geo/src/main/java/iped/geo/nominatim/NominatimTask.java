package iped.geo.nominatim;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.AbstractTask;
import iped.geo.parsers.GeofileParser;
import iped.properties.ExtraProperties;

public class NominatimTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(NominatimTask.class);

    public static final String NOMINATIM_METADATA = "nominatim:geojson";
    protected static final long CUSTOM_KEEP_ALIVE = 5000;

    public static final String NOMINATIM_COUNTRY_METADATA = "nominatim:country";
    public static final String NOMINATIM_STATE_METADATA = "nominatim:state";
    public static final String NOMINATIM_CITY_METADATA = "nominatim:city";
    public static final String NOMINATIM_ADDRESSTYPE_METADATA = "nominatim:addrestype";
    public static final String NOMINATIM_SUBURB_METADATA = "nominatim:suburb";

    static int MAXTOTAL = 200;

    /* The http connection pool is static to be used application wide */
    static PoolingHttpClientConnectionManager cm = null;
    static ConnectionKeepAliveStrategy myStrategy;
    static IdleConnectionMonitorThread staleMonitor;
    static CloseableHttpClient httpClient;

    AtomicInteger count = new AtomicInteger(0);

    static String baseUrl = "";

    static boolean unresolvedServer = true;

    private NominatimConfig nominatimConfig;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new NominatimConfig());
    }

    static private ExecutorService queryExecutor = Executors.newFixedThreadPool(10);

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        count.incrementAndGet();
        if (cm == null) {
            nominatimConfig = (NominatimConfig) configurationManager.findObject(NominatimConfig.class);

            cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(MAXTOTAL);
            cm.setDefaultMaxPerRoute(nominatimConfig.getConnectionPoolSize());
            myStrategy = new ConnectionKeepAliveStrategy() {
                @Override
                public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                    HeaderElementIterator it = new BasicHeaderElementIterator(
                            response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                    while (it.hasNext()) {
                        HeaderElement he = it.nextElement();
                        String param = he.getName();
                        String value = he.getValue();
                        if (value != null && param.equalsIgnoreCase("timeout")) {
                            return Long.parseLong(value) * 1000;
                        }
                    }
                    return CUSTOM_KEEP_ALIVE;
                }
            };

            try {
                httpClient = HttpClients.custom().setKeepAliveStrategy(myStrategy)
                        .setConnectionManager(cm).build();
                String defaultTestCountry = "brazil";
                baseUrl = nominatimConfig.getProtocol() + "://" + nominatimConfig.getHostName() + ":"
                        + nominatimConfig.getHostPort();
                HttpGet get = new HttpGet(baseUrl + nominatimConfig.getServiceTestUrlQuery());
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    unresolvedServer = false;
                } catch (ClientProtocolException cpe) {
                    unresolvedServer = true;
                }
            } catch (Exception e) {
                unresolvedServer = true;
            }

            /* thread to monitor connections closed by the host */
            staleMonitor = new IdleConnectionMonitorThread(cm);
            staleMonitor.start();
        }
    }

    @Override
    public void finish() throws Exception {
        int remaining = count.decrementAndGet();

        if (remaining == 0) {
            httpClient.close();
            staleMonitor.shutdown();
            cm.shutdown();
        }
    }

    private Future<String> executeQuery(String lat, String longit) {
        HttpGet get = new HttpGet(baseUrl + "/reverse?addressdetails=1&format=geojson&lat=" + lat + "&lon=" + longit);

        Future<String> f = queryExecutor.submit(() -> {
            try {
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent()))) {
                        String inputLine;
                        StringBuffer content = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }

                        return content.toString();
                    }
                }
            } catch (ClientProtocolException cpe) {
                cpe.printStackTrace();
                LOGGER.warn(cpe.getMessage());
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
                e.printStackTrace();
            }
            return null;
        });

        return f;
    }

    static HashMap<IItem, Future<String>> queries = new HashMap<IItem, Future<String>>();

    @Override
    protected void process(IItem evidence) throws Exception {
        if (!unresolvedServer) {
            Future<String> result = queries.get(evidence);
            if (result != null) {
                if (result.isDone()) {
                    String content = result.get();
                    if (content != null) {
                        processNominatimResult(evidence, content);
                    }
                    queries.remove(evidence);
                } else {
                    reEnqueueItem(evidence);
                }
            } else {
                String featureString = evidence.getMetadata().get(GeofileParser.FEATURE_STRING);

                if (featureString == null || featureString.length() < 5) {
                    String location = evidence.getMetadata().get(ExtraProperties.LOCATIONS);

                    if (location != null && location.length() >= 1) {
                        String[] locs = location.split(";"); //$NON-NLS-1$
                        String lat = locs[0].trim();
                        String longit = locs[1].trim();

                        Future<String> f = executeQuery(lat, longit);
                        queries.put(evidence, f);

                        reEnqueueItem(evidence);
                    }
                }
            }

        }
    }

    private void processNominatimResult(IItem evidence, String content) {
        evidence.getMetadata().add(NOMINATIM_METADATA, content);

        try {
            JSONObject obj = (JSONObject) new JSONParser().parse(content);

            JSONArray features = (JSONArray) obj.get("features");
            if (features != null) { // no error
                JSONObject properties = (JSONObject) ((JSONObject) features.get(0)).get("properties");
                JSONObject address = (JSONObject) properties.get("address");
                String country = (String) address.get("country");
                evidence.getMetadata().add(NOMINATIM_COUNTRY_METADATA, country);
                String state = (String) address.get("state");
                evidence.getMetadata().add(NOMINATIM_STATE_METADATA, country + ":" + state);
                String city = (String) address.get("city");
                if (city == null) {
                    city = (String) address.get("town");
                }
                evidence.getMetadata().add(NOMINATIM_CITY_METADATA, country + ":" + state + ":" + city);
                String suburb = (String) address.get("suburb");
                if (suburb != null) {
                    evidence.getMetadata().add(NOMINATIM_SUBURB_METADATA,
                            country + ":" + state + ":" + city + ":" + suburb);
                }
                String addresstype = (String) properties.get("addresstype");
                evidence.getMetadata().add(NOMINATIM_ADDRESSTYPE_METADATA, addresstype);
            }

        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    class IdleConnectionMonitorThread extends Thread {
        private final HttpClientConnectionManager connMgr;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(PoolingHttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(CUSTOM_KEEP_ALIVE);
                        connMgr.closeExpiredConnections();
                        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                shutdown();
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }

}